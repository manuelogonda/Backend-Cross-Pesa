package com.manuelorg.cross_pesa.transaction;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.exception.DuplicateTransactionException;
import com.manuelorg.cross_pesa.auth.repository.UserRepository;
import com.manuelorg.cross_pesa.ledger.entity.LedgerEntry;
import com.manuelorg.cross_pesa.ledger.enums.EntryClass;
import com.manuelorg.cross_pesa.ledger.repository.LedgerEntryRepository;
import com.manuelorg.cross_pesa.rates.dto.FxRateResponse;
import com.manuelorg.cross_pesa.rates.service.FxRateService;
import com.manuelorg.cross_pesa.transaction.dto.TransactionRequest;
import com.manuelorg.cross_pesa.transaction.dto.TransactionResponse.ExchangeResponse;
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
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

/**
 * Full-lifecycle integration test for the cross-border P2P remittance flow.
 *
 * Runs against a real PostgreSQL database (schema-isolated in the dev instance)
 * with the REAL fee engine, ledger, system wallet engine and repositories.
 * Only external integrations (FX provider, fraud rules) are mocked for determinism.
 *
 * NOTE on idempotency semantics: TransactionService currently REJECTS a replayed
 * idempotency key with IllegalStateException("Duplicate transaction detected.")
 * rather than returning the cached original response. These tests pin that
 * behaviour and prove no double-charging occurs.
 */
@SpringBootTest(properties = {
        // Schema-isolated test database inside the local dev PostgreSQL instance
        "spring.datasource.url=jdbc:postgresql://localhost:5432/crosspesa?currentSchema=crosspesa_test",
        "spring.jpa.properties.hibernate.default_schema=crosspesa_test",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.flyway.enabled=false",           // Hibernate owns the throwaway test schema
        "spring.docker.compose.enabled=false"    // infra is already running locally
})
@DisplayName("TransactionService — Cross-Border Remittance Integration Tests")
class TransactionServiceIntegrationTest {

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

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

    /** Spy keeps real repository behaviour but lets one test inject a mid-transaction failure. */
    @MockitoSpyBean
    private LedgerEntryRepository ledgerEntryRepositorySpy;

    private User sender;
    private Wallet sourceWallet;
    private Wallet destinationWallet;

    private static final BigDecimal USD_TO_KES = new BigDecimal("129.00");
    private static final BigDecimal KES_TO_USD = new BigDecimal("0.0078");

    @BeforeEach
    void seed() {
        // ddl-auto owns the throwaway schema, so the entry_seq sequence (created
        // by Flyway V4 / schema.sql in real environments) must be provisioned here
        jdbcTemplate.execute(
                "CREATE SEQUENCE IF NOT EXISTS ledger_entries_entry_seq_seq AS BIGINT START WITH 1 INCREMENT BY 1");

        sender = userRepository.save(User.builder()
                .firstName("Emmanuel").lastName("Odhiambo")
                .email("sender-" + UUID.randomUUID() + "@crosspesa.dev")
                .build());

        sourceWallet = walletRepository.save(retailWallet(sender, Currency.KES, new BigDecimal("100000.0000")));
        destinationWallet = walletRepository.save(retailWallet(sender, Currency.USD, BigDecimal.ZERO));

        stubFxRates();
    }

    @AfterEach
    void cleanup() {
        jdbcTemplate.execute("DELETE FROM crosspesa_test.ledger_entries");
        jdbcTemplate.execute("DELETE FROM crosspesa_test.transactions");
        jdbcTemplate.execute("DELETE FROM crosspesa_test.wallets");
        jdbcTemplate.execute("DELETE FROM crosspesa_test.users");
    }

    // -------------------------------------------------------------------------
    // a) FULL LIFECYCLE: funds -> fee calculation -> net transfer
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("given funded source wallet, when cross-border P2P transfer, then fees and net amount are calculated correctly and both sides settle")
    void givenFundedSource_whenCrossBorderTransfer_thenFeesAndNetAmountAreCorrect() {
        BigDecimal grossAmount = new BigDecimal("1000.0000");
        ExchangeResponse response = transactionService.processPeerToPeerTransfer(
                sender, exchangeRequest(grossAmount));

        // Fee engine integrity: total = markup + routing; net = gross - total
        assertThat(response.markupFee()).isPositive();
        assertThat(response.routingFee()).isPositive();
        assertThat(response.totalFee())
                .isEqualByComparingTo(response.markupFee().add(response.routingFee()));
        assertThat(response.netAmount())
                .isEqualByComparingTo(response.grossAmount().subtract(response.totalFee()));
        assertThat(response.grossAmount()).isEqualByComparingTo(grossAmount);
        assertThat(response.fxRateApplied()).isEqualByComparingTo(KES_TO_USD);

        // Destination payout must be consistent with the applied FX rate
        BigDecimal expectedPayout = response.netAmount().multiply(KES_TO_USD)
                .setScale(4, RoundingMode.HALF_UP);
        assertThat(response.amountReceived()).isEqualByComparingTo(expectedPayout);

        // Source wallet debited by gross + total fees
        Wallet sourceAfter = walletRepository.findById(sourceWallet.getId()).orElseThrow();
        BigDecimal expectedSourceBalance = new BigDecimal("100000.0000")
                .subtract(grossAmount).subtract(response.totalFee());
        assertThat(sourceAfter.getBalance()).isEqualByComparingTo(expectedSourceBalance);

        // Destination wallet credited with the target-currency payout
        Wallet destAfter = walletRepository.findById(destinationWallet.getId()).orElseThrow();
        assertThat(destAfter.getBalance()).isEqualByComparingTo(expectedPayout);
    }

    @Test
    @DisplayName("given completed remittance, then the posted ledger legs are balanced per currency (debits == credits)")
    void givenRemittance_thenLedgerLegsAreBalancedPerCurrency() {
        transactionService.processPeerToPeerTransfer(sender, exchangeRequest(new BigDecimal("500.0000")));

        List<LedgerEntry> legs = ledgerEntryRepository.findAll();
        assertThat(legs).hasSizeGreaterThanOrEqualTo(6); // principal, markup x2, routing x2, fx x2, received

        for (Currency currency : Currency.values()) {
            BigDecimal debits = sumSide(legs, currency, true);
            BigDecimal credits = sumSide(legs, currency, false);
            if (debits.signum() == 0 && credits.signum() == 0) continue;
            assertThat(debits)
                    .as("double-entry invariant violated for %s", currency)
                    .isEqualByComparingTo(credits);
        }
    }

    // -------------------------------------------------------------------------
    // b) IDEMPOTENCY: same payload + same key must never double-charge
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("given identical payload replayed with the same idempotencyKey, then duplicate is rejected and balances are charged exactly once")
    void givenReplayedIdempotencyKey_whenProcessedTwice_thenNoDoubleChargingOccurs() {
        BigDecimal grossAmount = new BigDecimal("250.0000");
        TransactionRequest.ExchangeFundsRequest request = exchangeRequest(grossAmount);

        ExchangeResponse first = transactionService.processPeerToPeerTransfer(sender, request);
        assertThatThrownBy(() -> transactionService.processPeerToPeerTransfer(sender, request))
                .isInstanceOf(DuplicateTransactionException.class)
                .hasMessageContaining("Duplicate transaction detected");

        // Exactly one transaction persisted despite two attempts
        assertThat(transactionRepository.count()).isEqualTo(1);

        // Balances reflect exactly ONE charge
        Wallet sourceAfter = walletRepository.findById(sourceWallet.getId()).orElseThrow();
        BigDecimal singleChargeDebit = new BigDecimal("100000.0000")
                .subtract(first.totalFee()).subtract(first.grossAmount());
        assertThat(sourceAfter.getBalance()).isEqualByComparingTo(singleChargeDebit);

        Wallet destAfter = walletRepository.findById(destinationWallet.getId()).orElseThrow();
        assertThat(destAfter.getBalance()).isEqualByComparingTo(first.amountReceived());

        // Exactly one set of ledger legs
        long principalLegs = ledgerEntryRepository.findAll().stream()
                .filter(e -> e.getEntryClass() == EntryClass.PRINCIPAL_TRANSFER)
                .count();
        assertThat(principalLegs).isEqualTo(2); // one debit + one credit
    }

    // -------------------------------------------------------------------------
    // c) ROLLBACK: failure mid-flow must leave zero side effects
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("given ledger persistence fails mid-remittance, then the entire DB transaction rolls back leaving no trace")
    void givenMidFlowFailure_whenRemittance_thenEverythingRollsBack() {
        doThrow(new RuntimeException("Simulated ledger persistence outage"))
                .when(ledgerEntryRepositorySpy).saveAll(anyList());

        BigDecimal balanceBefore = sourceWallet.getBalance();

        assertThatThrownBy(() -> transactionService.processPeerToPeerTransfer(
                sender, exchangeRequest(new BigDecimal("300.0000"))))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("Simulated ledger persistence outage");

        // No transaction row survived
        assertThat(transactionRepository.count()).isEqualTo(0);
        // No ledger legs survived
        assertThat(ledgerEntryRepository.count()).isEqualTo(0);
        // Source wallet projection untouched
        Wallet sourceAfter = walletRepository.findById(sourceWallet.getId()).orElseThrow();
        assertThat(sourceAfter.getBalance()).isEqualByComparingTo(balanceBefore);
        // Destination untouched
        Wallet destAfter = walletRepository.findById(destinationWallet.getId()).orElseThrow();
        assertThat(destAfter.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("given insufficient source funds, when transfer, then InsufficientFunds-style rejection occurs before any persistence")
    void givenInsufficientFunds_whenTransfer_thenRejectedWithoutPersistingAnything() {
        long transactionsBefore = transactionRepository.count();

        assertThatThrownBy(() -> transactionService.processPeerToPeerTransfer(
                sender, exchangeRequest(new BigDecimal("9999999.0000"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Insufficient funds");

        assertThat(transactionRepository.count()).isEqualTo(transactionsBefore);
        assertThat(ledgerEntryRepository.count()).isEqualTo(0);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private TransactionRequest.ExchangeFundsRequest exchangeRequest(BigDecimal amount) {
        return new TransactionRequest.ExchangeFundsRequest(
                sourceWallet.getId(),
                destinationWallet.getId(),
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
