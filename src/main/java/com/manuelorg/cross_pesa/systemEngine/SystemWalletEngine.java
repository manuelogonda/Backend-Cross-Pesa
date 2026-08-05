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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
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
     * and writes fully audited immutable ledger legs with balance_after tracking.
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

        // 1. Lock all participating wallets to prevent race conditions during concurrent settlements
        Wallet lockedUserWallet = lockAndGetWallet(userSourceWallet.getId());
        Wallet markupWallet = lockAndGetWallet(getSystemWallet(sourceCurrency, WalletType.SYSTEM_MARKUP).getId());
        Wallet routingWallet = lockAndGetWallet(getSystemWallet(sourceCurrency, WalletType.SYSTEM_ROUTING).getId());
        Wallet sourceLiquidityWallet = lockAndGetWallet(getSystemWallet(sourceCurrency, WalletType.SYSTEM_LIQUIDITY).getId());
        Wallet targetLiquidityWallet = lockAndGetWallet(getSystemWallet(targetCurrency, WalletType.SYSTEM_LIQUIDITY).getId());

        List<LedgerEntry> entries = new ArrayList<>();

        // --- 2. CALCULATE NEW BALANCES & BUILD USER DEBITS ---
        // Total debit from user = principal + markupFee + routingFee
        BigDecimal totalUserDebit = principal.add(markupFee).add(routingFee);
        if (lockedUserWallet.getWalletType() == WalletType.USER_RETAIL && lockedUserWallet.getBalance().compareTo(totalUserDebit) < 0) {
            throw new IllegalStateException(String.format("Insufficient funds for user wallet ID %s. Balance: %s, Required: %s",
                    lockedUserWallet.getId(), lockedUserWallet.getBalance(), totalUserDebit));
        }

        BigDecimal userNewBalance = lockedUserWallet.getBalance().subtract(totalUserDebit);

        // Individual itemized legs for the user wallet
        entries.add(buildLeg(transaction, lockedUserWallet, EntryClass.PRINCIPAL_TRANSFER, principal, BigDecimal.ZERO,
                sourceCurrency, "Outbound remittance principal", routingPair, tiersApplied, usdBaseline, userNewBalance));

        if (markupFee.compareTo(BigDecimal.ZERO) > 0) {
            entries.add(buildLeg(transaction, lockedUserWallet, EntryClass.MARKUP_FEE, markupFee, BigDecimal.ZERO,
                    sourceCurrency, "Deducting platform profit", routingPair, tiersApplied, usdBaseline, userNewBalance));
        }

        if (routingFee.compareTo(BigDecimal.ZERO) > 0) {
            entries.add(buildLeg(transaction, lockedUserWallet, EntryClass.ROUTING_FEE, routingFee, BigDecimal.ZERO,
                    sourceCurrency, "Deducting banking corridor cost", routingPair, tiersApplied, usdBaseline, userNewBalance));
        }

        lockedUserWallet.setBalance(userNewBalance);

        // --- 3. SYSTEM REVENUE CREDITS ---
        if (markupFee.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal markupNewBalance = markupWallet.getBalance().add(markupFee);
            entries.add(buildLeg(transaction, markupWallet, EntryClass.MARKUP_FEE, BigDecimal.ZERO, markupFee,
                    sourceCurrency, "Crediting platform pure profit", routingPair, tiersApplied, usdBaseline, markupNewBalance));
            markupWallet.setBalance(markupNewBalance);
        }

        if (routingFee.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal routingNewBalance = routingWallet.getBalance().add(routingFee);
            entries.add(buildLeg(transaction, routingWallet, EntryClass.ROUTING_FEE, BigDecimal.ZERO, routingFee,
                    sourceCurrency, "Crediting money to pay external banks", routingPair, tiersApplied, usdBaseline, routingNewBalance));
            routingWallet.setBalance(routingNewBalance);
        }

        // --- 4. FX CLEARING POOLS (Source out, Target in) ---
        BigDecimal totalSourceClearingAmount = principal.subtract(routingFee);
        BigDecimal sourceLiquidityNewBalance = sourceLiquidityWallet.getBalance().add(totalSourceClearingAmount);
        entries.add(buildLeg(transaction, sourceLiquidityWallet, EntryClass.FX_CLEARING, BigDecimal.ZERO, totalSourceClearingAmount,
                sourceCurrency, "Inbound clearing float lock", routingPair, tiersApplied, usdBaseline, sourceLiquidityNewBalance));
        sourceLiquidityWallet.setBalance(sourceLiquidityNewBalance);

        BigDecimal targetLiquidityNewBalance = targetLiquidityWallet.getBalance().subtract(targetPayoutAmount);
        // Note: For target liquidity disbursement, it acts as a credit/outflow from pool perspective or tracking pool depth
        entries.add(buildLeg(transaction, targetLiquidityWallet, EntryClass.FX_CLEARING, targetPayoutAmount, BigDecimal.ZERO,
                targetCurrency, "Local float payout to beneficiary", routingPair, tiersApplied, usdBaseline, targetLiquidityNewBalance));
        targetLiquidityWallet.setBalance(targetLiquidityNewBalance);

        // 5. Persist updated balances and append full double-entry transaction block
        walletRepository.saveAll(List.of(lockedUserWallet, markupWallet, routingWallet, sourceLiquidityWallet, targetLiquidityWallet));
        ledgerEntryRepository.saveAll(entries);

        entityManager.flush();
        entityManager.clear();

        log.info("Executed balanced settlement for Transaction: {}", transaction.getId());
    }

    /**
     * Executes treasury rebalancing between system liquidity wallets with explicit cache updates.
     */
    @Transactional
    public void executeTreasuryRebalance(
            Transaction adminTransaction,
            Currency sourceCurrency,
            BigDecimal withdrawAmount,
            Currency targetCurrency,
            BigDecimal depositAmount,
            String adminNotes) {

        Wallet sourceLiquidity = lockAndGetWallet(getSystemWallet(sourceCurrency, WalletType.SYSTEM_LIQUIDITY).getId());
        Wallet targetLiquidity = lockAndGetWallet(getSystemWallet(targetCurrency, WalletType.SYSTEM_LIQUIDITY).getId());

        if (sourceLiquidity.getBalance().compareTo(withdrawAmount) < 0) {
            throw new IllegalStateException("Insufficient source liquidity for treasury rebalance.");
        }

        BigDecimal sourceNewBalance = sourceLiquidity.getBalance().subtract(withdrawAmount);
        BigDecimal targetNewBalance = targetLiquidity.getBalance().add(depositAmount);

        sourceLiquidity.setBalance(sourceNewBalance);
        targetLiquidity.setBalance(targetNewBalance);

        List<LedgerEntry> rebalanceEntries = new ArrayList<>();

        rebalanceEntries.add(buildLeg(adminTransaction, sourceLiquidity, EntryClass.WITHDRAWAL, withdrawAmount, BigDecimal.ZERO,
                sourceCurrency, "Admin withdrawal: " + adminNotes, "TREASURY", "NONE", withdrawAmount, sourceNewBalance));

        rebalanceEntries.add(buildLeg(adminTransaction, targetLiquidity, EntryClass.DEPOSIT, BigDecimal.ZERO, depositAmount,
                targetCurrency, "Admin deposit: " + adminNotes, "TREASURY", "NONE", withdrawAmount, targetNewBalance));

        walletRepository.saveAll(List.of(sourceLiquidity, targetLiquidity));
        ledgerEntryRepository.saveAll(rebalanceEntries);

        entityManager.flush();
        entityManager.clear();

        log.info("Treasury Rebalanced: -{} {} -> +{} {}", withdrawAmount, sourceCurrency, depositAmount, targetCurrency);
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
