package com.manuelorg.cross_pesa.wallet;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.ledger.service.LedgerService;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.transaction.repository.TransactionRepository;
import com.manuelorg.cross_pesa.wallet.dto.WalletResponse;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.enums.WalletStatus;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import com.manuelorg.cross_pesa.wallet.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WalletService Unit Tests")
class WalletServiceTest {

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private TransactionRepository transactionRepository;

    @Mock
    private LedgerService ledgerService;

    private WalletService walletService;

    private User user;

    @BeforeEach
    void setUp() {
        walletService = new WalletService(walletRepository, transactionRepository, ledgerService);
        user = User.builder()
                .id(UUID.randomUUID())
                .firstName("Emmanuel")
                .lastName("Odhiambo")
                .email("emmanuel@crosspesa.dev")
                .build();
    }

    // -------------------------------------------------------------------------
    // 1. WALLET CREATION
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "creates a {0} retail wallet with default zero balances")
    @EnumSource(Currency.class)
    void givenNewUser_whenCreateWallet_thenWalletHasZeroBalances(Currency currency) {
        givenNoExistingRetailWallet();
        when(walletRepository.save(any(Wallet.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Wallet.class));

        WalletResponse response = walletService.createWallet(user, currency);

        assertThat(response.currency()).isEqualTo(currency);
        assertThat(response.walletType()).isEqualTo(WalletType.USER_RETAIL);
        assertThat(response.status()).isEqualTo(WalletStatus.ACTIVE.name());
        assertThat(response.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.lockedBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.availableBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("given existing retail wallet, when createWallet, then IllegalStateException is thrown")
    void givenExistingRetailWallet_whenCreateWallet_thenThrows() {
        when(walletRepository.existsByUserIdAndWalletType(user.getId(), WalletType.USER_RETAIL))
                .thenReturn(true);

        assertThatThrownBy(() -> walletService.createWallet(user, Currency.KES))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already has an active retail wallet");

        verify(walletRepository, never()).save(any(Wallet.class));
    }

    // -------------------------------------------------------------------------
    // 2. TOP-UP — happy path & idempotency
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("given active wallet, when addFunds, then transaction saved and ledger deposit recorded")
    void givenActiveWallet_whenAddFunds_thenDepositIsRecordedOnLedger() {
        Wallet wallet = activeKesWallet(new BigDecimal("100.0000"));
        stubHappyPathTopUp(wallet);

        WalletResponse response = walletService.addFunds(
                user.getId(), Currency.KES, new BigDecimal("500.00"), "GW-REF-001");

        ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
        verify(transactionRepository).save(txCaptor.capture());
        Transaction topUpTx = txCaptor.getValue();

        assertThat(topUpTx.getGrossAmount()).isEqualByComparingTo("500.00");
        assertThat(topUpTx.getGatewayReference()).isEqualTo("GW-REF-001");
        assertThat(topUpTx.getStatus()).isEqualTo(com.manuelorg.cross_pesa.transaction.enums.TransactionStatus.COMPLETED);

        verify(ledgerService).recordGatewayDeposit(
                eq(topUpTx), eq(wallet), eq(new BigDecimal("500.00")), anyString());

        assertThat(response.balance()).isEqualByComparingTo("600.0000");
    }

    @Test
    @DisplayName("given already-processed gateway reference, when addFunds, then no double credit occurs (idempotent)")
    void givenDuplicateGatewayReference_whenAddFunds_thenNoDoubleCredit() {
        Wallet wallet = activeKesWallet(new BigDecimal("100.0000"));
        when(transactionRepository.findByGatewayReference("GW-REF-001"))
                .thenReturn(Optional.of(new Transaction()));
        when(walletRepository.findByUserIdAndWalletType(user.getId(), WalletType.USER_RETAIL))
                .thenReturn(Optional.of(wallet));

        WalletResponse response = walletService.addFunds(
                user.getId(), Currency.KES, new BigDecimal("500.00"), "GW-REF-001");

        assertThat(response.balance()).isEqualByComparingTo("100.0000");
        verify(transactionRepository, never()).save(any(Transaction.class));
        verify(ledgerService, never()).recordGatewayDeposit(any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("given non-positive amount, when addFunds, then IllegalArgumentException is thrown")
    void givenZeroAmount_whenAddFunds_thenThrows() {
        assertThatThrownBy(() -> walletService.addFunds(
                user.getId(), Currency.KES, BigDecimal.ZERO, "GW-REF-002"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("strictly greater than zero");
    }

    @Test
    @DisplayName("given blank gateway reference, when addFunds, then IllegalArgumentException is thrown")
    void givenBlankReference_whenAddFunds_thenThrows() {
        assertThatThrownBy(() -> walletService.addFunds(
                user.getId(), Currency.KES, new BigDecimal("50.00"), " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }

    @Test
    @DisplayName("given currency mismatch between wallet and deposit, when addFunds, then IllegalArgumentException is thrown")
    void givenCurrencyMismatch_whenAddFunds_thenThrows() {
        Wallet wallet = activeKesWallet(BigDecimal.ZERO);
        when(transactionRepository.findByGatewayReference("GW-REF-003")).thenReturn(Optional.empty());
        when(walletRepository.findByUserIdAndWalletTypeWithLock(user.getId(), WalletType.USER_RETAIL))
                .thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletService.addFunds(
                user.getId(), Currency.USD, new BigDecimal("50.00"), "GW-REF-003"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Currency mismatch");

        verify(ledgerService, never()).recordGatewayDeposit(any(), any(), any(), anyString());
    }

    @Test
    @DisplayName("given suspended wallet, when addFunds, then IllegalStateException is thrown and no credit happens")
    void givenInactiveWallet_whenAddFunds_thenThrows() {
        Wallet wallet = activeKesWallet(BigDecimal.ZERO);
        wallet.setStatus(WalletStatus.FROZEN);
        when(transactionRepository.findByGatewayReference("GW-REF-004")).thenReturn(Optional.empty());
        when(walletRepository.findByUserIdAndWalletTypeWithLock(user.getId(), WalletType.USER_RETAIL))
                .thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletService.addFunds(
                user.getId(), Currency.KES, new BigDecimal("50.00"), "GW-REF-004"))
                .isInstanceOf(IllegalStateException.class);

        verify(ledgerService, never()).recordGatewayDeposit(any(), any(), any(), anyString());
    }

    // -------------------------------------------------------------------------
    // 3. CONCURRENT TOP-UPS (thread-safety via pessimistic lock emulation)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("given 20 concurrent top-ups with unique references, then final balance equals sum of all deposits without lost updates")
    void givenConcurrentTopUps_whenAllComplete_thenBalanceEqualsTotalOfDeposits() throws Exception {
        int threads = 20;
        BigDecimal amountPerThread = new BigDecimal("10.0000");

        Wallet wallet = activeKesWallet(BigDecimal.ZERO);

        // Emulate the database row-level pessimistic write lock taken by
        // findByUserIdAndWalletTypeWithLock: only one thread may hold the
        // "row" at a time, exactly like SELECT ... FOR UPDATE in PostgreSQL.
        ReentrantLock dbRowLock = new ReentrantLock();
        when(transactionRepository.findByGatewayReference(anyString())).thenReturn(Optional.empty());
        lenient().when(walletRepository.findByUserIdAndWalletTypeWithLock(user.getId(), WalletType.USER_RETAIL))
                .thenAnswer(invocation -> {
                    dbRowLock.lock();
                    try {
                        return Optional.of(snapshotOf(wallet));
                    } finally {
                        dbRowLock.unlock();
                    }
                });
        when(walletRepository.findById(wallet.getId())).thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Transaction.class));

        // The ledger service mutates the authoritative balance while the caller
        // holds the DB row lock; emulate that critical section here.
        org.mockito.Mockito.doAnswer(invocation -> {
            BigDecimal credit = invocation.getArgument(2, BigDecimal.class);
            dbRowLock.lock();
            try {
                wallet.setBalance(wallet.getBalance().add(credit));
            } finally {
                dbRowLock.unlock();
            }
            return null;
        }).when(ledgerService)
                .recordGatewayDeposit(any(Transaction.class), any(Wallet.class), any(BigDecimal.class), anyString());

        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<WalletResponse>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < threads; i++) {
            String reference = "GW-CONCURRENT-" + i;
            futures.add(executor.submit(() -> {
                startGate.await();
                return walletService.addFunds(user.getId(), Currency.KES, amountPerThread, reference);
            }));
        }

        startGate.countDown(); // release all threads at once to maximise contention

        for (Future<WalletResponse> future : futures) {
            future.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

        BigDecimal expectedTotal = amountPerThread.multiply(BigDecimal.valueOf(threads));
        assertThat(wallet.getBalance())
                .as("no lost updates: balance must equal exactly %s", expectedTotal)
                .isEqualByComparingTo(expectedTotal);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Wallet activeKesWallet(BigDecimal balance) {
        return Wallet.builder()
                .id(UUID.randomUUID())
                .user(user)
                .walletType(WalletType.USER_RETAIL)
                .currency(Currency.KES)
                .balance(balance)
                .lockedBalance(BigDecimal.ZERO)
                .status(WalletStatus.ACTIVE)
                .build();
    }

    private void givenNoExistingRetailWallet() {
        when(walletRepository.existsByUserIdAndWalletType(user.getId(), WalletType.USER_RETAIL))
                .thenReturn(false);
    }

    private void stubHappyPathTopUp(Wallet wallet) {
        when(transactionRepository.findByGatewayReference("GW-REF-001")).thenReturn(Optional.empty());
        when(walletRepository.findByUserIdAndWalletTypeWithLock(user.getId(), WalletType.USER_RETAIL))
                .thenReturn(Optional.of(wallet));
        when(transactionRepository.save(any(Transaction.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Transaction.class));
        // recordGatewayDeposit updates the projection on the same instance
        org.mockito.Mockito.doAnswer(invocation -> {
            BigDecimal credit = invocation.getArgument(2, BigDecimal.class);
            wallet.setBalance(wallet.getBalance().add(credit));
            return null;
        }).when(ledgerService)
                .recordGatewayDeposit(any(Transaction.class), any(Wallet.class), any(BigDecimal.class), anyString());
        when(walletRepository.findById(wallet.getId())).thenReturn(Optional.of(wallet));
    }

    /**
     * Returns a detached copy of the wallet, mimicking how a locked SELECT
     * returns a consistent snapshot that other transactions cannot read-modify
     * concurrently.
     */
    private Wallet snapshotOf(Wallet source) {
        return Wallet.builder()
                .id(source.getId())
                .user(source.getUser())
                .walletType(source.getWalletType())
                .currency(source.getCurrency())
                .balance(source.getBalance())
                .lockedBalance(source.getLockedBalance())
                .status(source.getStatus())
                .build();
    }
}
