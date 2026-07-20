package com.manuelorg.cross_pesa.intergration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.payment.service.FlutterwaveService;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.enums.WalletStatus;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional // Rolls back the database after each test so they don't pollute each other
public class FinancialFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
     @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    // We mock Flutterwave so we don't hit the real API during CI/CD builds
    @MockitoBean
    private FlutterwaveService flutterwaveService;

    private User sender;
    private User receiver;
    private Wallet senderWallet;
    private Wallet receiverWallet;

    @BeforeEach
    void setUp() {
        // 1. Setup Sender
        sender = User.builder()
                .email("sender@afripay.com")
                .password("password123")
                .firstName("John")
                .lastName("Doe")
                .build();
        userRepository.save(sender);

        senderWallet = Wallet.builder()
                .user(sender)
                .currency(Currency.USD)
                .balance(new BigDecimal("100.00")) // Start with $100
                .status(WalletStatus.ACTIVE)
                .build();
        walletRepository.save(senderWallet);

        // 2. Setup Receiver
        receiver = User.builder()
                .email("receiver@crosspesa.com")
                .password("password123")
                .firstName("Johan")
                .lastName("Smith")
                .build();
        userRepository.save(receiver);

        receiverWallet = Wallet.builder()
                .user(receiver)
                .currency(Currency.USD)
                .balance(new BigDecimal("0.00")) // Start with $0
                .status(WalletStatus.ACTIVE)
                .build();
        walletRepository.save(receiverWallet);
    }

    @Test
    @DisplayName("Top-Up Flow: Should verify payment and increase wallet balance")
    @WithMockCustomUser(email = "sender@afripay.com")
    void testEndToEndTopUpFlow() throws Exception {
        // Given
        String txId = "tx_999999";
        String amount = "50.00";
        String currency = "USD";

        // Mock the external Flutterwave verification to return true
        when(flutterwaveService.verifyTransaction(txId, amount, currency)).thenReturn(true);

        // When: We hit the verify endpoint (simulating the React frontend redirect)
        mockMvc.perform(post("/api/v1/wallets/verify")
                        .param("transactionId", txId)
                        .param("amount", amount)
                        .param("currency", currency)
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andDo(print())
                // Then: API should return 200 OK
                .andExpect(status().isOk())
                .andExpect((ResultMatcher) jsonPath("$.status").value("SUCCESS"));

        // And Then: The database balance must reflect the exact top-up
        Wallet updatedWallet = walletRepository.findByUserIdAndCurrency(sender.getId(), Currency.USD).orElseThrow();

        // Started with 100, added 50 -> should be 150.00
        assertEquals(0, new BigDecimal("150.00").compareTo(updatedWallet.getBalance()), "Balance should be updated in DB");
    }

    @Test
    @DisplayName("Transfer Flow: Should deduct from sender and add to receiver")
    @WithMockCustomUser(email = "sender@afripay.com")
    void testEndToEndTransferFlow() throws Exception {
        // Given
        Map<String, Object> transferPayload = Map.of(
                "receiverEmail", receiver.getEmail(),
                "amount", 25.00,
                "currency", "USD"
        );

        // When: Sender initiates a transfer
        mockMvc.perform(post("/api/v1/transfers")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferPayload)))
                // Then: API should return 200 OK
                .andExpect(status().isOk());

        // And Then: Assert Sender Database State
        Wallet updatedSenderWallet = walletRepository.findByUserIdAndCurrency(sender.getId(), Currency.USD).orElseThrow();
        assertEquals(0, new BigDecimal("75.00").compareTo(updatedSenderWallet.getBalance()), "Sender balance should be deducted");

        // And Then: Assert Receiver Database State
        Wallet updatedReceiverWallet = walletRepository.findByUserIdAndCurrency(receiver.getId(), Currency.USD).orElseThrow();
        assertEquals(0, new BigDecimal("25.00").compareTo(updatedReceiverWallet.getBalance()), "Receiver balance should increase");

        // Optional: If you have a LedgerRepository, you can assert that two ledger entries were created here!
    }

    @Test
    @DisplayName("Transfer Flow: Should fail if sender has insufficient funds")
    void testTransferFailsWhenInsufficientFunds() throws Exception {
        // Given: Try to send $500, but sender only has $100
        Map<String, Object> transferPayload = Map.of(
                "receiverEmail", receiver.getEmail(),
                "amount", 500.00,
                "currency", "USD"
        );

        // When & Then
        mockMvc.perform(post("/api/v1/transfers")
                        .with(user(sender.getEmail()).roles("USER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transferPayload)))
                .andExpect(status().isBadRequest()); // Expect a 400 response

        // Assert database untouched
        Wallet unchangedSenderWallet = walletRepository.findByUserIdAndCurrency(sender.getId(), Currency.USD).orElseThrow();
        assertEquals(0, new BigDecimal("100.00").compareTo(unchangedSenderWallet.getBalance()));
    }
}
