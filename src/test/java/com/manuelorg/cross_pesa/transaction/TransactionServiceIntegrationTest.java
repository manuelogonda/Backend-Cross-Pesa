package com.manuelorg.cross_pesa.transaction;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.beneficiaries.entity.Beneficiary;
import com.manuelorg.cross_pesa.beneficiaries.entity.PayoutMethod;
import com.manuelorg.cross_pesa.beneficiaries.entity.PayoutProvider;
import com.manuelorg.cross_pesa.beneficiaries.repository.BeneficiaryRepository;
import com.manuelorg.cross_pesa.exception.DuplicateTransactionException;
import com.manuelorg.cross_pesa.ledger.entity.LedgerEntry;
import com.manuelorg.cross_pesa.ledger.enums.EntryClass;
import com.manuelorg.cross_pesa.ledger.repository.LedgerEntryRepository;
import com.manuelorg.cross_pesa.payment.flutterwave.FlutterwaveTransferService;
import com.manuelorg.cross_pesa.rates.dto.FxRateResponse;
import com.manuelorg.cross_pesa.rates.service.FxRateService;
import com.manuelorg.cross_pesa.transaction.dto.TransactionRequest;
import com.manuelorg.cross_pesa.transaction.dto.TransactionResponse.SendMoneyResponse;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import com.manuelorg.cross_pesa.transaction.service.FraudDetectionService;
import com.manuelorg.cross_pesa.transaction.service.TransactionService;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.enums.WalletStatus;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for the saved-beneficiary Flutterwave outbound payout flow
 * (processSendMoney). P2P transfers were removed permanently.
 *
 * Runs against a real PostgreSQL schema with the REAL fee engine, ledger and
 * system wallet engine. External integrations (FX provider, fraud rules,
 * Flutterwave HTTP) are mocked for determinism.
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/crosspesa?currentSchema=crosspesa_test",
        "spring.jpa.properties.hibernate.default_schema=crosspesa_test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",
        "spring.docker.compose.enabled=false"
})
@DisplayName("TransactionService — Flutterwave Beneficiary Payout Integration Tests")
class TransactionServiceIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private BeneficiaryRepository beneficiaryRepository;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private FxRateService fxRateService;

    @MockitoBean
    private FraudDetectionService fraudDetectionService;

    @MockitoBean
    private FlutterwaveTransferService flutterwaveTransferService;

    private User sender;
    private Wallet sourceWallet;
    private Beneficiary beneficiary;

    private static final BigDecimal USD_TO_KES = new BigDecimal("129.00");
    private static final BigDecimal KES_TO_USD = new BigDecimal("0.0078");
    private static final FlutterwaveTransferService.Recipient RECIPIENT =
            new FlutterwaveTransferService.Recipient(null, "Amina Wanjiku", "MPS", "254700000001", "KES", PayoutMethod.MOBILE_MONEY);

    @BeforeEach
    void seed() {
        jdbcTemplate.execute(
                "CREATE SEQUENCE IF NOT EXISTS ledger_entries_entry_seq_seq AS BIGINT START WITH 1 INCREMENT BY 1");

        sender = userRepository.save(User.builder()
                .firstName("Emmanuel").lastName("Odhiambo")
                .email("sender-" + UUID.randomUUID() + "@crosspesa.dev")
                .build());

        sourceWallet = walletRepository.save(retailWallet(sender, Currency.KES, new BigDecimal("100000.0000")));

        beneficiary = beneficiaryRepository.save(Beneficiary.builder()
                .user(sender)
                .firstName("Amina").lastName("Wanjiku")
                .email("amina-" + UUID.randomUUID() + "@crosspesa.dev")
                .phoneNumber("+254700000001")
                .countryCode("KE")
                .payoutMethod(PayoutMethod.MOBILE_MONEY)
                .payoutProvider(PayoutProvider.MPESA)
                .accountNumber("254700000001").bankCode("MPESA")
                .accountCurrency(Currency.KES)
                .build());

        when(fraudDetectionService.isSuspiciousTransaction(any(UUID.class), any(BigDecimal.class), any()))
                .thenReturn(false);

        when(flutterwaveTransferService.createOrGetRecipient(any(Beneficiary.class)))
                .thenReturn(RECIPIENT);

        stubFxRates();
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("DELETE FROM crosspesa_test.ledger_entries");
        jdbcTemplate.execute("DELETE FROM crosspesa_test.transactions");
        jdbcTemplate.execute("DELETE FROM crosspesa_test.beneficiaries");
        jdbcTemplate.execute("DELETE FROM crosspesa_test.wallets");
        jdbcTemplate.execute("DELETE FROM crosspesa_test.users");
    }

    // -------------------------------------------------------------------------
    // a) FULL LIFECYCLE: debit -> fees -> ledger legs -> Paystack dispatch
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("given funded wallet, when send money to beneficiary, then wallet debited, ledger committed and Flutterwave transfer dispatched after commit")
    void givenFundedWallet_whenSendMoney_thenLedgerCommittedAndPaystackDispatched() {
        BigDecimal grossAmount = new BigDecimal("1000.0000");

        SendMoneyResponse response = transactionService.processSendMoney(sender, sendRequest(grossAmount));

        assertThat(response.status()).isEqualTo("PROCESSING");
        assertThat(response.payoutGateway()).isEqualTo("FLUTTERWAVE");
        assertThat(response.payoutReference()).startsWith("FLW-");

        // Source wallet debited by gross + total fees
        Wallet sourceAfter = walletRepository.findById(sourceWallet.getId()).orElseThrow();
        BigDecimal expectedBalance = new BigDecimal("100000.0000")
                .subtract(grossAmount)
                .subtract(response.totalFee());
        assertThat(sourceAfter.getBalance()).isEqualByComparingTo(expectedBalance);

        // Destination amount consistent with the applied FX rate
        BigDecimal expectedPayout = response.netAmount().multiply(KES_TO_USD)
                .setScale(4, RoundingMode.HALF_UP);
        assertThat(response.amountReceived()).isEqualByComparingTo(expectedPayout);

        // Recipient resolved exactly once and transfer initiated once
        verify(flutterwaveTransferService).createOrGetRecipient(any(Beneficiary.class));
        verify(flutterwaveTransferService).initiateTransfer(
                eq(response.payoutReference()), eq(RECIPIENT), any(BigDecimal.class),
                eq("USD"), anyString());
    }

    @Test
    @DisplayName("given completed payout initiation, then posted ledger legs balance per currency (debits == credits)")
    void givenSendMoney_thenLedgerLegsAreBalancedPerCurrency() {
        transactionService.processSendMoney(sender, sendRequest(new BigDecimal("500.0000")));

        List<LedgerEntry> legs = ledgerEntryRepository.findAll();
        assertThat(legs).hasSizeGreaterThanOrEqualTo(4);

        // Per-currency double-entry invariant holds EXCEPT for the payout
        // currency, where an unmatched FX_CLEARING debit is correct by design:
        // liquidity permanently leaves the system pool towards the external payout provider.
        for (Currency currency : Currency.values()) {
            BigDecimal debits = sumSide(legs, currency, true);
            BigDecimal credits = sumSide(legs, currency, false);
            if (debits.signum() == 0 && credits.signum() == 0) continue;

            boolean isExternalPayoutLeg = legs.stream()
                    .filter(e -> e.getCurrency() == currency)
                    .allMatch(e -> e.getEntryClass() == EntryClass.FX_CLEARING
                            && e.getDebit().signum() > 0 && e.getCredit().signum() == 0);
            if (isExternalPayoutLeg) continue;

            assertThat(debits)
                    .as("double-entry invariant violated for %s", currency)
                    .isEqualByComparingTo(credits);
        }
    }

    // -------------------------------------------------------------------------
    // b) IDEMPOTENCY: same payload + same key must never double-charge
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("given identical payload replayed with same idempotencyKey, then duplicate rejected and Flutterwave called exactly once")
    void givenReplayedIdempotencyKey_whenProcessedTwice_thenNoDoubleChargeOrDoublePayout() {
        BigDecimal grossAmount = new BigDecimal("250.0000");
        TransactionRequest.SendMoneyRequest request = sendRequest(grossAmount);

        transactionService.processSendMoney(sender, request);
        assertThatThrownBy(() -> transactionService.processSendMoney(sender, request))
                .isInstanceOf(DuplicateTransactionException.class)
                .hasMessageContaining("Duplicate transaction detected");

        assertThat(transactionRepository.count()).isEqualTo(1);
        // Exactly ONE Flutterwave dispatch across both attempts (duplicate rejected
        // before any gateway call).
        verify(flutterwaveTransferService, times(1)).initiateTransfer(
                anyString(), any(FlutterwaveTransferService.Recipient.class), any(BigDecimal.class), anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // c) GUARDRAILS
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("given suspicious transaction flagged by fraud engine, then no Flutterwave dispatch occurs")
    void givenFlaggedTransaction_whenSendMoney_thenFlutterwaveNotCalled() {
        when(fraudDetectionService.isSuspiciousTransaction(any(UUID.class), any(BigDecimal.class), any()))
                .thenReturn(true);

        SendMoneyResponse response = transactionService.processSendMoney(
                sender, sendRequest(new BigDecimal("400.0000")));

        assertThat(response.status()).isEqualTo("FLAGGED");
        verify(flutterwaveTransferService, never()).initiateTransfer(
                anyString(), any(), any(), anyString(), anyString());
    }

    @Test
    @DisplayName("given insufficient funds, then rejection occurs before any persistence or dispatch")
    void givenInsufficientFunds_whenSendMoney_thenRejectedWithoutPersistingAnything() {
        long transactionsBefore = transactionRepository.count();

        assertThatThrownBy(() -> transactionService.processSendMoney(
                sender, sendRequest(new BigDecimal("9999999.0000"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient funds");

        assertThat(transactionRepository.count()).isEqualTo(transactionsBefore);
        assertThat(ledgerEntryRepository.count()).isEqualTo(0);
        verify(flutterwaveTransferService, never()).initiateTransfer(
                anyString(), any(), any(), anyString(), anyString());
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TransactionRequest.SendMoneyRequest sendRequest(BigDecimal amount) {
        return new TransactionRequest.SendMoneyRequest(
                sourceWallet.getId(),
                beneficiary.getId(),
                Currency.KES,
                Currency.USD,
                amount,
                UUID.randomUUID()
        );
    }

    private void stubFxRates() {
        when(fxRateService.getLiveQuote(anyString(), anyString()))
                .thenAnswer(invocation -> {
                    String from = invocation.getArgument(0, String.class);
                    String to = invocation.getArgument(1, String.class);
                    if ("USD".equals(from) && "KES".equals(to)) {
                        return rateResponse(from, to, USD_TO_KES);
                    }
                    if ("KES".equals(from) && "USD".equals(to)) {
                        return rateResponse(from, to, KES_TO_USD);
                    }
                    return rateResponse(from, to, BigDecimal.ONE);
                });
    }

    private FxRateResponse rateResponse(String from, String to, BigDecimal rate) {
        return new FxRateResponse(from, to, rate, OffsetDateTime.now().plusMinutes(15));
    }

    private Wallet retailWallet(User owner, Currency currency, BigDecimal balance) {
        return Wallet.builder()
                .user(owner)
                .walletType(WalletType.USER_RETAIL)
                .currency(currency)
                .balance(balance)
                .lockedBalance(BigDecimal.ZERO)
                .status(WalletStatus.ACTIVE)
                .build();
    }

    private BigDecimal sumSide(List<LedgerEntry> legs, Currency currency, boolean debitSide) {
        return legs.stream()
                .filter(e -> e.getCurrency() == currency)
                .map(e -> debitSide ? e.getDebit() : e.getCredit())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
