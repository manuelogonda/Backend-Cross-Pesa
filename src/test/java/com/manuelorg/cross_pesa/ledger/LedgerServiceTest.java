package com.manuelorg.cross_pesa.ledger;

import com.manuelorg.cross_pesa.auth.entity.User;
import com.manuelorg.cross_pesa.exception.InsufficientFundsException;
import com.manuelorg.cross_pesa.ledger.entity.LedgerEntry;
import com.manuelorg.cross_pesa.ledger.enums.EntryClass;
import com.manuelorg.cross_pesa.ledger.repository.LedgerEntryRepository;
import com.manuelorg.cross_pesa.ledger.service.LedgerService;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.enums.WalletStatus;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LedgerService — Double-Entry Bookkeeping Unit Tests")
class LedgerServiceTest {

    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @Mock
    private WalletRepository walletRepository;

    @Mock
    private EntityManager entityManager;

    private LedgerService ledgerService;

    private Transaction transaction;

    private Wallet sourceWallet;
    private Wallet targetWallet;

    @BeforeEach
    void setUp() {
        ledgerService = new LedgerService(ledgerEntryRepository, walletRepository, entityManager);

        transaction = new Transaction();
        sourceWallet = retailWallet(new BigDecimal("1000.0000"));
        targetWallet = retailWallet(BigDecimal.ZERO);

        // Default: no prior ledger history -> balance derivation falls back to wallet cache
        lenient().when(ledgerEntryRepository.findTopByWalletIdOrderByCreatedAtDescIdDesc(any(UUID.class)))
                .thenReturn(Optional.empty());
        lenient().when(walletRepository.findByIdWithLock(any(UUID.class)))
                .thenAnswer(invocation -> {
                    UUID id = invocation.getArgument(0, UUID.class);
                    if (sourceWallet.getId().equals(id)) return Optional.of(sourceWallet);
                    if (targetWallet.getId().equals(id)) return Optional.of(targetWallet);
                    return Optional.empty();
                });
        lenient().when(ledgerEntryRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0, List.class));
        lenient().when(walletRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0, List.class));
    }

    // -------------------------------------------------------------------------
    // 1. DOUBLE-ENTRY BALANCE VALIDATION (Debit == Credit)
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("given funded source wallet, when simple transfer, then exactly two balanced legs are posted")
    void givenFundedSource_whenSimpleTransfer_thenPostsBalancedDebitAndCreditLegs() {
        BigDecimal amount = new BigDecimal("250.0000");

        ledgerService.recordSimpleTransfer(
                transaction, sourceWallet, targetWallet, amount, EntryClass.PRINCIPAL_TRANSFER, "P2P Transfer");

        ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntryRepository).saveAll(captor.capture());
        List<LedgerEntry> legs = captor.getValue();

        assertThat(legs).hasSize(2);

        LedgerEntry debitLeg = legs.stream()
                .filter(e -> e.getDebit().compareTo(BigDecimal.ZERO) > 0)
                .findFirst().orElseThrow();
        LedgerEntry creditLeg = legs.stream()
                .filter(e -> e.getCredit().compareTo(BigDecimal.ZERO) > 0)
                .findFirst().orElseThrow();

        // Golden double-entry rule: total debits must equal total credits
        BigDecimal totalDebits = legs.stream().map(LedgerEntry::getDebit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredits = legs.stream().map(LedgerEntry::getCredit)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalDebits).isEqualByComparingTo(totalCredits);

        assertThat(debitLeg.getDebit()).isEqualByComparingTo(amount);
        assertThat(debitLeg.getCredit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(debitLeg.getWallet()).isSameAs(sourceWallet);
        assertThat(debitLeg.getBalanceAfter()).isEqualByComparingTo("750.0000");
        assertThat(debitLeg.getDescription()).contains("(Outgoing)");

        assertThat(creditLeg.getCredit()).isEqualByComparingTo(amount);
        assertThat(creditLeg.getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(creditLeg.getWallet()).isSameAs(targetWallet);
        assertThat(creditLeg.getBalanceAfter()).isEqualByComparingTo("250.0000");
        assertThat(creditLeg.getDescription()).contains("(Incoming)");
    }

    @Test
    @DisplayName("given a transfer, then both legs share the same transaction and entry class (audit integrity)")
    void givenTransfer_thenBothLegsShareTransactionAndEntryClass() {
        ledgerService.recordSimpleTransfer(
                transaction, sourceWallet, targetWallet,
                new BigDecimal("10.0000"), EntryClass.MARKUP_FEE, "Fee movement");

        ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntryRepository).saveAll(captor.capture());

        assertThat(captor.getValue())
                .allSatisfy(entry -> {
                    assertThat(entry.getTransaction()).isSameAs(transaction);
                    assertThat(entry.getEntryClass()).isEqualTo(EntryClass.MARKUP_FEE);
                });
    }

    @Test
    @DisplayName("given prior ledger history, when transfer, then balances derive from latest balanceAfter not the wallet cache")
    void givenPriorLedgerHistory_whenTransfer_thenDerivesFromLedgerNotCache() {
        LedgerEntry lastKnown = LedgerEntry.builder()
                .wallet(sourceWallet)
                .balanceAfter(new BigDecimal("500.0000"))
                .build();
        when(ledgerEntryRepository.findTopByWalletIdOrderByCreatedAtDescIdDesc(sourceWallet.getId()))
                .thenReturn(Optional.of(lastKnown));

        ledgerService.recordSimpleTransfer(
                transaction, sourceWallet, targetWallet,
                new BigDecimal("100.0000"), EntryClass.PRINCIPAL_TRANSFER, "P2P Transfer");

        ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
        verify(ledgerEntryRepository).saveAll(captor.capture());
        LedgerEntry debitLeg = captor.getValue().stream()
                .filter(e -> e.getDebit().compareTo(BigDecimal.ZERO) > 0)
                .findFirst().orElseThrow();

        assertThat(debitLeg.getBalanceAfter()).isEqualByComparingTo("400.0000");
    }

    @Test
    @DisplayName("given a completed transfer, then both wallet balance projections are updated to the new balances")
    void givenTransfer_whenComplete_thenWalletProjectionsAreUpdated() {
        ledgerService.recordSimpleTransfer(
                transaction, sourceWallet, targetWallet,
                new BigDecimal("100.0000"), EntryClass.PRINCIPAL_TRANSFER, "P2P Transfer");

        assertThat(sourceWallet.getBalance()).isEqualByComparingTo("900.0000");
        assertThat(targetWallet.getBalance()).isEqualByComparingTo("100.0000");
    }

    // -------------------------------------------------------------------------
    // 2. INSUFFICIENT FUNDS / NEGATIVE BALANCE RESTRICTIONS
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("given retail source with less than requested amount, when transfer, then InsufficientFundsException is thrown and nothing is posted")
    void givenInsufficientRetailBalance_whenTransfer_thenThrowsInsufficientFundsException() {
        Throwable thrown = catchThrowableOfType(
                () -> ledgerService.recordSimpleTransfer(
                        transaction, sourceWallet, targetWallet,
                        new BigDecimal("5000.0000"), EntryClass.PRINCIPAL_TRANSFER, "Too big"),
                InsufficientFundsException.class);

        assertThat(thrown)
                .as("insufficient funds must surface as InsufficientFundsException")
                .isNotNull()
                .hasMessageContaining("Insufficient funds in wallet");

        verify(ledgerEntryRepository, never()).saveAll(anyList());
        assertThat(sourceWallet.getBalance()).isEqualByComparingTo("1000.0000");
        assertThat(targetWallet.getBalance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("given zero-balance retail source, when transfer, then InsufficientFundsException is thrown (no negative balances)")
    void givenZeroBalance_whenTransfer_thenNegativeBalanceIsBlocked() {
        sourceWallet.setBalance(BigDecimal.ZERO);

        assertThatThrownBy(() -> ledgerService.recordSimpleTransfer(
                transaction, sourceWallet, targetWallet,
                new BigDecimal("1.0000"), EntryClass.PRINCIPAL_TRANSFER, "Overdraft attempt"))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    @DisplayName("given SYSTEM liquidity wallet with zero balance, when it is debited, then posting succeeds (system wallets may go negative)")
    void givenSystemWallet_whenDebited_thenNoRestrictionApplies() {
        Wallet systemLiquidity = systemWallet(Currency.KES, WalletType.SYSTEM_LIQUIDITY);
        when(walletRepository.findByIdWithLock(systemLiquidity.getId()))
                .thenReturn(Optional.of(systemLiquidity));

        ledgerService.recordSimpleTransfer(
                transaction, systemLiquidity, targetWallet,
                new BigDecimal("500.0000"), EntryClass.FX_CLEARING, "Float payout");

        assertThat(systemLiquidity.getBalance()).isEqualByComparingTo("-500.0000");
    }

    // -------------------------------------------------------------------------
    // 3. GUARD RAILS & DEPOSITS
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("given cross-currency wallets, when simple transfer, then IllegalArgumentException is thrown (must use FX engine)")
    void givenCrossCurrencyWallets_whenSimpleTransfer_thenThrows() {
        targetWallet.setCurrency(Currency.USD);

        assertThatThrownBy(() -> ledgerService.recordSimpleTransfer(
                transaction, sourceWallet, targetWallet,
                new BigDecimal("10.0000"), EntryClass.PRINCIPAL_TRANSFER, "Bad transfer"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("same currency");

        verify(ledgerEntryRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("given gateway deposit, when recorded, then single CREDIT leg posted with EntryClass.DEPOSIT and correct balanceAfter")
    void givenGatewayDeposit_whenRecorded_thenSingleCreditLegPosted() {
        when(walletRepository.save(any(Wallet.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, Wallet.class));
        when(ledgerEntryRepository.save(any(LedgerEntry.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, LedgerEntry.class));

        ledgerService.recordGatewayDeposit(
                transaction, targetWallet, new BigDecimal("750.0000"), "External Gateway Top-Up: GW-REF-001");

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerEntryRepository).save(captor.capture());
        LedgerEntry depositLeg = captor.getValue();

        assertThat(depositLeg.getCredit()).isEqualByComparingTo("750.0000");
        assertThat(depositLeg.getDebit()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(depositLeg.getEntryClass()).isEqualTo(EntryClass.DEPOSIT);
        assertThat(depositLeg.getBalanceAfter()).isEqualByComparingTo("750.0000");
        assertThat(targetWallet.getBalance()).isEqualByComparingTo("750.0000");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private Wallet retailWallet(BigDecimal balance) {
        return Wallet.builder()
                .id(UUID.randomUUID())
                .user(User.builder()
                        .id(UUID.randomUUID())
                        .firstName("Test").lastName("User").email("test@crosspesa.dev")
                        .build())
                .walletType(WalletType.USER_RETAIL)
                .currency(Currency.KES)
                .balance(balance)
                .lockedBalance(BigDecimal.ZERO)
                .status(WalletStatus.ACTIVE)
                .build();
    }

    private Wallet systemWallet(Currency currency, WalletType type) {
        return Wallet.builder()
                .id(UUID.randomUUID())
                .walletType(type)
                .currency(currency)
                .balance(BigDecimal.ZERO)
                .lockedBalance(BigDecimal.ZERO)
                .status(WalletStatus.ACTIVE)
                .build();
    }
}
