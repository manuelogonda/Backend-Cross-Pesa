package com.manuelorg.cross_pesa.transaction.service;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.beneficiaries.entity.Beneficiary;
import com.manuelorg.cross_pesa.beneficiaries.repository.BeneficiaryRepository;
import com.manuelorg.cross_pesa.ledger.repository.LedgerEntryRepository;
import com.manuelorg.cross_pesa.rates.dto.FxRateResponse;
import com.manuelorg.cross_pesa.rates.service.FxRateService;
import com.manuelorg.cross_pesa.transaction.dto.TransactionRequest;
import com.manuelorg.cross_pesa.transaction.dto.TransactionResponse;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.enums.TransactionStatus;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.hibernate.validator.internal.util.Contracts.assertNotNull;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class TransactionServiceTest {

    @Mock
    private TransactionRepository transactionRepository;
    @Mock private WalletRepository walletRepository;
    @Mock private BeneficiaryRepository beneficiaryRepository;
    @Mock private FxRateService fxRateService;
    @Mock private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private TransactionService transactionService;

    private User currentUser;
    private Wallet sourceWallet;
    private Beneficiary beneficiary;
    private TransactionRequest.SendMoneyRequest validRequest;
    private UUID idempotencyKey;

    @BeforeEach
    void setUp() {
        currentUser = new User();
        currentUser.setId(UUID.randomUUID());

        sourceWallet = new Wallet();
        sourceWallet.setId(UUID.randomUUID());
        sourceWallet.setUser(currentUser);
        sourceWallet.setBalance(new BigDecimal("5000.00"));

        beneficiary = new Beneficiary();
        beneficiary.setId(UUID.randomUUID());
        beneficiary.setFirstName("John");
        beneficiary.setLastName("Doe");

        idempotencyKey = UUID.randomUUID();

        // FIX: Using the correct nested DTO record from your code
        validRequest = new TransactionRequest.SendMoneyRequest(
                sourceWallet.getId(),
                beneficiary.getId(),
                Currency.KES,
                Currency.USD,
                new BigDecimal("1000.00"),
                idempotencyKey
        );
    }

    @Test
    void processSendMoney_SuccessfulHappyPath() {
        // Arrange
        when(transactionRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false);
        when(walletRepository.findById(sourceWallet.getId())).thenReturn(Optional.of(sourceWallet));
        when(beneficiaryRepository.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));

        FxRateResponse mockFxResponse = new FxRateResponse(UUID.randomUUID(), "KES", "USD", new BigDecimal("0.0076"), OffsetDateTime.now());
        when(fxRateService.getLiveQuote(Currency.KES, Currency.USD)).thenReturn(mockFxResponse);

        Transaction mockSavedTx = new Transaction();
        mockSavedTx.setId(UUID.randomUUID());
        mockSavedTx.setSourceWallet(sourceWallet);
        mockSavedTx.setTransferFee(new BigDecimal("1.0000"));
        mockSavedTx.setSender(currentUser);
        mockSavedTx.setBeneficiary(beneficiary);
        mockSavedTx.setSourceCurrency(Currency.KES);
        mockSavedTx.setDestinationCurrency(Currency.USD);
        mockSavedTx.setSourceAmount(new BigDecimal("1000.00"));
        mockSavedTx.setDestinationAmount(new BigDecimal("7.6000"));
        mockSavedTx.setStatus(TransactionStatus.PROCESSING);

        when(transactionRepository.save(any(Transaction.class))).thenReturn(mockSavedTx);

        // Act
        TransactionResponse.SendMoneyResponse response = transactionService.processSendMoney(currentUser, validRequest);

        // Assert
        assertNotNull(response);

        // Use ArgumentCaptor to verify the correct amount (Amount + 1.00 Fee) was saved
        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());

        Transaction capturedTx = txCaptor.getValue();
        assertEquals(new BigDecimal("1000.00"), capturedTx.getSourceAmount());
        assertEquals(new BigDecimal("1.0000"), capturedTx.getTransferFee()); // Verifying the fee logic!

        verify(ledgerEntryRepository, times(1)).save(any());
    }

    @Test
    void processSendMoney_FailsWhenIdempotencyKeyExists() {
        // Arrange
        when(transactionRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(true);

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            transactionService.processSendMoney(currentUser, validRequest);
        });

        assertEquals("Duplicate transaction detected. Please verify your ledger.", exception.getMessage());
        verify(walletRepository, never()).findById(any());
    }

    @Test
    void processSendMoney_FailsWhenUserDoesNotOwnSourceWallet() {
        // Arrange
        User differentUser = new User();
        differentUser.setId(UUID.randomUUID());
        sourceWallet.setUser(differentUser);

        when(transactionRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false);
        when(walletRepository.findById(sourceWallet.getId())).thenReturn(Optional.of(sourceWallet));
        lenient().when(beneficiaryRepository.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));

        // Act & Assert
        SecurityException exception = assertThrows(SecurityException.class, () -> {
            transactionService.processSendMoney(currentUser, validRequest);
        });

        assertEquals("You do not have permission to deduct from this wallet.", exception.getMessage());
    }

    @Test
    void processSendMoney_FailsWhenInsufficientFunds() {
        // Arrange: Amount is 1000, fee is 1.00. Balance of 1000.50 should fail.
        sourceWallet.setBalance(new BigDecimal("1000.50"));

        when(transactionRepository.existsByIdempotencyKey(idempotencyKey)).thenReturn(false);
        when(walletRepository.findById(sourceWallet.getId())).thenReturn(Optional.of(sourceWallet));
        when(beneficiaryRepository.findById(beneficiary.getId())).thenReturn(Optional.of(beneficiary));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            transactionService.processSendMoney(currentUser, validRequest);
        });

        assertTrue(exception.getMessage().contains("Insufficient funds"));
        verify(fxRateService, never()).getLiveQuote(any(), any());
    }
}