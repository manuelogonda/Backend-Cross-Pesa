package com.manuelorg.cross_pesa.systemEngine;

import com.manuelorg.cross_pesa.ledger.entity.LedgerEntry;
import com.manuelorg.cross_pesa.ledger.enums.EntryClass;
import com.manuelorg.cross_pesa.ledger.repository.LedgerEntryRepository;
import com.manuelorg.cross_pesa.transaction.entity.Transaction;
import com.manuelorg.cross_pesa.wallet.entity.Wallet;
import com.manuelorg.cross_pesa.wallet.enums.Currency;
import com.manuelorg.cross_pesa.wallet.enums.WalletStatus;
import com.manuelorg.cross_pesa.wallet.enums.WalletType;
import com.manuelorg.cross_pesa.wallet.repository.WalletRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemWalletEngine {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final EntityManager entityManager;

    @PostConstruct
    public void init() {
        try {
            initializeSystemWallets();
        } catch (Exception e) {
            log.error("Failed to auto-initialize system wallets on startup", e);
        }
    }

    private static final List<Currency> SUPPORTED_CURRENCIES = List.of(
            Currency.KES, Currency.USD, Currency.CNY, Currency.JPY,
            Currency.GBP, Currency.CAD, Currency.AUD, Currency.PKR,
            Currency.AED, Currency.SAR, Currency.EUR, Currency.SEK
    );

    @Transactional
    public void initializeSystemWallets() {
        log.info("Verifying 12-Corridor System Wallet Grid...");
        for (Currency currency : SUPPORTED_CURRENCIES) {
            ensureSystemWalletExists(currency, WalletType.SYSTEM_LIQUIDITY);
            ensureSystemWalletExists(currency, WalletType.SYSTEM_MARKUP);
            ensureSystemWalletExists(currency, WalletType.SYSTEM_ROUTING);
        }
        log.info("System Wallet Grid is fully initialized.");
    }

    private void ensureSystemWalletExists(Currency currency, WalletType type) {
        walletRepository.findByCurrencyAndWalletType(currency, type)
                .orElseGet(() -> {
                    Wallet newSysWallet = Wallet.builder()
                            .currency(currency)
                            .walletType(type)
                            .balance(BigDecimal.ZERO)
                            .lockedBalance(BigDecimal.ZERO)
                            .status(WalletStatus.ACTIVE)
                            .build();
                    log.info("Created missing system wallet: {} for {}", type, currency);
                    return walletRepository.save(newSysWallet);
                });
    }

    public Wallet getSystemWallet(Currency currency, WalletType type) {
        return walletRepository.findByCurrencyAndWalletType(currency, type)
                .orElseGet(() -> {
                    log.warn("System wallet [{}] for {} was missing. Auto-provisioning on-demand.", type, currency);
                    Wallet newSysWallet = Wallet.builder()
                            .currency(currency)
                            .walletType(type)
                            .balance(BigDecimal.ZERO)
                            .lockedBalance(BigDecimal.ZERO)
                            .status(WalletStatus.ACTIVE)
                            .build();
                    return walletRepository.save(newSysWallet);
                });
    }

    /**
     * Executes cross-border settlement, updates wallet balances explicitly,
     * and writes fully audited double-entry ledger legs with correct running balanceAfter per leg.
     * All wallets are locked in deterministic UUID order to prevent deadlocks.
     */
    @Transactional
    public void executeCrossBorderSettlement(
            Transaction transaction,
            Wallet userSourceWallet,
            BigDecimal principal,
            BigDecimal markupFee,
            BigDecimal routingFee,
            Currency targetCurrency,
            BigDecimal targetPayoutAmount,
            String routingPair,
            String tiersApplied,
            BigDecimal usdBaseline) {

        Currency sourceCurrency = userSourceWallet.getCurrency();

        // 1. Resolve all wallet IDs before locking, then lock in deterministic UUID order
        UUID userWalletId = userSourceWallet.getId();
        UUID markupWalletId = getSystemWallet(sourceCurrency, WalletType.SYSTEM_MARKUP).getId();
        UUID routingWalletId = getSystemWallet(sourceCurrency, WalletType.SYSTEM_ROUTING).getId();
        UUID sourceLiquidityId = getSystemWallet(sourceCurrency, WalletType.SYSTEM_LIQUIDITY).getId();
        UUID targetLiquidityId = getSystemWallet(targetCurrency, WalletType.SYSTEM_LIQUIDITY).getId();

        List<UUID> lockOrder = new ArrayList<>(List.of(
                userWalletId, markupWalletId, routingWalletId, sourceLiquidityId, targetLiquidityId));
        lockOrder.sort(Comparator.naturalOrder());

        for (UUID id : lockOrder) {
            lockAndGetWallet(id); // acquire pessimistic lock in deterministic order
        }

        // Re-fetch locked instances by their known IDs
        Wallet lockedUserWallet = lockAndGetWallet(userWalletId);
        Wallet markupWallet = lockAndGetWallet(markupWalletId);
        Wallet routingWallet = lockAndGetWallet(routingWalletId);
        Wallet sourceLiquidityWallet = lockAndGetWallet(sourceLiquidityId);
        Wallet targetLiquidityWallet = lockAndGetWallet(targetLiquidityId);

        List<LedgerEntry> entries = new ArrayList<>();

        // --- 2. DERIVE CURRENT BALANCES FROM LEDGER (source of truth) ---
        BigDecimal userCurrentBalance = getCurrentBalance(lockedUserWallet);
        BigDecimal markupCurrentBalance = getCurrentBalance(markupWallet);
        BigDecimal routingCurrentBalance = getCurrentBalance(routingWallet);
        BigDecimal sourceLiqCurrentBalance = getCurrentBalance(sourceLiquidityWallet);
        BigDecimal targetLiqCurrentBalance = getCurrentBalance(targetLiquidityWallet);

        // --- 3. VALIDATE USER FUNDS ---
        BigDecimal totalUserDebit = principal.add(markupFee).add(routingFee);
        if (lockedUserWallet.getWalletType() == WalletType.USER_RETAIL
                && userCurrentBalance.compareTo(totalUserDebit) < 0) {
            throw new IllegalStateException(String.format(
                    "Insufficient funds for user wallet ID %s. Balance: %s, Required: %s",
                    lockedUserWallet.getId(), userCurrentBalance, totalUserDebit));
        }

        // --- 4. USER DEBIT LEGS — running balance per leg ---
        BigDecimal userRunningBalance = userCurrentBalance;

        userRunningBalance = userRunningBalance.subtract(principal);
        entries.add(buildLeg(transaction, lockedUserWallet, EntryClass.PRINCIPAL_TRANSFER,
                principal, BigDecimal.ZERO, sourceCurrency,
                "Outbound remittance principal", routingPair, tiersApplied, usdBaseline, userRunningBalance));

        if (markupFee.compareTo(BigDecimal.ZERO) > 0) {
            userRunningBalance = userRunningBalance.subtract(markupFee);
            entries.add(buildLeg(transaction, lockedUserWallet, EntryClass.MARKUP_FEE,
                    markupFee, BigDecimal.ZERO, sourceCurrency,
                    "Deducting platform profit", routingPair, tiersApplied, usdBaseline, userRunningBalance));
        }

        if (routingFee.compareTo(BigDecimal.ZERO) > 0) {
            userRunningBalance = userRunningBalance.subtract(routingFee);
            entries.add(buildLeg(transaction, lockedUserWallet, EntryClass.ROUTING_FEE,
                    routingFee, BigDecimal.ZERO, sourceCurrency,
                    "Deducting banking corridor cost", routingPair, tiersApplied, usdBaseline, userRunningBalance));
        }

        lockedUserWallet.setBalance(userRunningBalance);

        // --- 5. SYSTEM REVENUE CREDITS ---
        if (markupFee.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal markupNewBalance = markupCurrentBalance.add(markupFee);
            entries.add(buildLeg(transaction, markupWallet, EntryClass.MARKUP_FEE,
                    BigDecimal.ZERO, markupFee, sourceCurrency,
                    "Crediting platform pure profit", routingPair, tiersApplied, usdBaseline, markupNewBalance));
            markupWallet.setBalance(markupNewBalance);
        }

        if (routingFee.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal routingNewBalance = routingCurrentBalance.add(routingFee);
            entries.add(buildLeg(transaction, routingWallet, EntryClass.ROUTING_FEE,
                    BigDecimal.ZERO, routingFee, sourceCurrency,
                    "Crediting money to pay external banks", routingPair, tiersApplied, usdBaseline, routingNewBalance));
            routingWallet.setBalance(routingNewBalance);
        }

        // --- 6. FX CLEARING POOLS (Source receives principal net of routing; Target disburses payout) ---
        BigDecimal totalSourceClearingAmount = principal.subtract(routingFee);
        BigDecimal sourceLiqNewBalance = sourceLiqCurrentBalance.add(totalSourceClearingAmount);
        entries.add(buildLeg(transaction, sourceLiquidityWallet, EntryClass.FX_CLEARING,
                BigDecimal.ZERO, totalSourceClearingAmount, sourceCurrency,
                "Inbound clearing float lock", routingPair, tiersApplied, usdBaseline, sourceLiqNewBalance));
        sourceLiquidityWallet.setBalance(sourceLiqNewBalance);

        BigDecimal targetLiqNewBalance = targetLiqCurrentBalance.subtract(targetPayoutAmount);
        entries.add(buildLeg(transaction, targetLiquidityWallet, EntryClass.FX_CLEARING,
                targetPayoutAmount, BigDecimal.ZERO, targetCurrency,
                "Local float payout to beneficiary", routingPair, tiersApplied, usdBaseline, targetLiqNewBalance));
        targetLiquidityWallet.setBalance(targetLiqNewBalance);

        // 7. Persist ledger entries first (source of truth), then update wallet balance projections
        ledgerEntryRepository.saveAll(entries);
        walletRepository.saveAll(List.of(
                lockedUserWallet, markupWallet, routingWallet, sourceLiquidityWallet, targetLiquidityWallet));

        entityManager.flush();
        entityManager.clear();

        log.info("Executed balanced cross-border settlement for Transaction: {} [{} -> {}]",
                transaction.getId(), sourceCurrency, targetCurrency);
    }

    /**
     * Executes treasury rebalancing between system liquidity wallets with correct running balanceAfter.
     * Wallets are locked in deterministic UUID order to prevent deadlocks.
     */
    @Transactional
    public void executeTreasuryRebalance(
            Transaction adminTransaction,
            Currency sourceCurrency,
            BigDecimal withdrawAmount,
            Currency targetCurrency,
            BigDecimal depositAmount,
            String adminNotes) {

        UUID sourceId = getSystemWallet(sourceCurrency, WalletType.SYSTEM_LIQUIDITY).getId();
        UUID targetId = getSystemWallet(targetCurrency, WalletType.SYSTEM_LIQUIDITY).getId();

        // Lock in deterministic UUID order
        Wallet sourceLiquidity;
        Wallet targetLiquidity;
        if (sourceId.compareTo(targetId) < 0) {
            sourceLiquidity = lockAndGetWallet(sourceId);
            targetLiquidity = lockAndGetWallet(targetId);
        } else {
            targetLiquidity = lockAndGetWallet(targetId);
            sourceLiquidity = lockAndGetWallet(sourceId);
        }

        // Derive current balances from ledger (source of truth)
        BigDecimal sourceCurrentBalance = getCurrentBalance(sourceLiquidity);
        BigDecimal targetCurrentBalance = getCurrentBalance(targetLiquidity);

        if (sourceCurrentBalance.compareTo(withdrawAmount) < 0) {
            throw new IllegalStateException(String.format(
                    "Insufficient source liquidity for treasury rebalance. Available: %s, Required: %s",
                    sourceCurrentBalance, withdrawAmount));
        }

        BigDecimal sourceNewBalance = sourceCurrentBalance.subtract(withdrawAmount);
        BigDecimal targetNewBalance = targetCurrentBalance.add(depositAmount);

        List<LedgerEntry> rebalanceEntries = new ArrayList<>();

        rebalanceEntries.add(buildLeg(adminTransaction, sourceLiquidity, EntryClass.WITHDRAWAL,
                withdrawAmount, BigDecimal.ZERO, sourceCurrency,
                "Admin withdrawal: " + adminNotes, "TREASURY", "NONE", withdrawAmount, sourceNewBalance));

        rebalanceEntries.add(buildLeg(adminTransaction, targetLiquidity, EntryClass.DEPOSIT,
                BigDecimal.ZERO, depositAmount, targetCurrency,
                "Admin deposit: " + adminNotes, "TREASURY", "NONE", withdrawAmount, targetNewBalance));

        // Persist ledger entries first (source of truth), then update wallet balance projections
        ledgerEntryRepository.saveAll(rebalanceEntries);

        sourceLiquidity.setBalance(sourceNewBalance);
        targetLiquidity.setBalance(targetNewBalance);
        walletRepository.saveAll(List.of(sourceLiquidity, targetLiquidity));

        entityManager.flush();
        entityManager.clear();

        log.info("Treasury Rebalanced: -{} {} -> +{} {}", withdrawAmount, sourceCurrency, depositAmount, targetCurrency);
    }

    /**
     * Derives the current balance for a wallet from the most recent ledger entry.
     * Falls back to the wallet's cached balance field if no ledger entries exist yet.
     */
    private BigDecimal getCurrentBalance(Wallet wallet) {
        return ledgerEntryRepository
                .findTopByWalletIdOrderByCreatedAtDescIdDesc(wallet.getId())
                .map(LedgerEntry::getBalanceAfter)
                .orElse(wallet.getBalance());
    }

    private Wallet lockAndGetWallet(UUID walletId) {
        return walletRepository.findByIdWithLock(walletId)
                .orElseThrow(() -> new IllegalArgumentException("Wallet not found with ID: " + walletId));
    }

    private LedgerEntry buildLeg(
            Transaction tx, Wallet wallet, EntryClass entryClass,
            BigDecimal debit, BigDecimal credit, Currency currency, String desc,
            String routingPair, String tiersApplied, BigDecimal usdBaseline, BigDecimal balanceAfter) {

        return LedgerEntry.builder()
                .transaction(tx)
                .wallet(wallet)
                .entryClass(entryClass)
                .debit(debit)
                .credit(credit)
                .currency(currency)
                .description(desc)
                .routingPair(routingPair)
                .markupTiersApplied(tiersApplied)
                .usdBaselineAmount(usdBaseline)
                .balanceAfter(balanceAfter)
                .build();
    }
}
