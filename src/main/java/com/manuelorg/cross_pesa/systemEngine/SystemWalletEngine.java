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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.persistence.EntityManager;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemWalletEngine {

    private final WalletRepository walletRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final EntityManager entityManager; // ADDED: Directly clears Hibernate cache constraints

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
                            .balance(BigDecimal.ZERO) // Fixed: mapped to your 'balance' table field
                            .lockedBalance(BigDecimal.ZERO)
                            .status(WalletStatus.ACTIVE)
                            .build();
                    log.info("Created missing system wallet: {} for {}", type, currency);
                    return walletRepository.save(newSysWallet);
                });
    }

    public Wallet getSystemWallet(Currency currency, WalletType type) {
        return walletRepository.findByCurrencyAndWalletType(currency, type)
                .orElseThrow(() -> new IllegalStateException(
                        "Critical Treasury Error: Missing system wallet [" + type + "] for " + currency));
    }

    /**
     * FIXED: Includes Pricing Engine Audit attributes.
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
            // AUDIT TRAIL EXTRACTION INPUTS
            String routingPair,
            String tiersApplied,
            BigDecimal usdBaseline) {

        Currency sourceCurrency = userSourceWallet.getCurrency();
        List<LedgerEntry> entries = new ArrayList<>();

        // --- USER DEBITS ---
        entries.add(buildLeg(transaction, userSourceWallet, EntryClass.PRINCIPAL_TRANSFER, principal, BigDecimal.ZERO,
                sourceCurrency, "Outbound remittance principal", routingPair, tiersApplied, usdBaseline));

        if (markupFee.compareTo(BigDecimal.ZERO) > 0) {
            entries.add(buildLeg(transaction, userSourceWallet, EntryClass.MARKUP_FEE, markupFee, BigDecimal.ZERO,
                    sourceCurrency, "Deducting platform profit", routingPair, tiersApplied, usdBaseline));
        }

        if (routingFee.compareTo(BigDecimal.ZERO) > 0) {
            entries.add(buildLeg(transaction, userSourceWallet, EntryClass.ROUTING_FEE, routingFee, BigDecimal.ZERO,
                    sourceCurrency, "Deducting banking corridor cost", routingPair, tiersApplied, usdBaseline));
        }

        // --- SYSTEM CREDITS & PAYOUTS ---
        if (markupFee.compareTo(BigDecimal.ZERO) > 0) {
            Wallet markupWallet = getSystemWallet(sourceCurrency, WalletType.SYSTEM_MARKUP);
            entries.add(buildLeg(transaction, markupWallet, EntryClass.MARKUP_FEE, BigDecimal.ZERO, markupFee,
                    sourceCurrency, "Crediting platform pure profit", routingPair, tiersApplied, usdBaseline));
        }

        if (routingFee.compareTo(BigDecimal.ZERO) > 0) {
            Wallet routingWallet = getSystemWallet(sourceCurrency, WalletType.SYSTEM_ROUTING);
            entries.add(buildLeg(transaction, routingWallet, EntryClass.ROUTING_FEE, BigDecimal.ZERO, routingFee,
                    sourceCurrency, "Crediting money to pay external banks", routingPair, tiersApplied, usdBaseline));
        }

        Wallet targetLiquidityWallet = getSystemWallet(targetCurrency, WalletType.SYSTEM_LIQUIDITY);
        entries.add(buildLeg(transaction, targetLiquidityWallet, EntryClass.FX_CLEARING, targetPayoutAmount, BigDecimal.ZERO,
                targetCurrency, "Local float payout to beneficiary", routingPair, tiersApplied, usdBaseline));

        // Save everything atomically
        ledgerEntryRepository.saveAll(entries);

        // CRITICAL FIX: Flush changes and evict from cache so Hibernate reads trigger calculations!
        entityManager.flush();
        entityManager.clear();

        log.info("Executed 6-leg balanced settlement for Transaction: {}", transaction.getId());
    }

    /**
     * SCENARIO C FIXED: Uses valid 'DEPOSIT' / 'WITHDRAWAL' strings matching your CHECK constraints.
     */
    @Transactional
    public void executeTreasuryRebalance(
            Transaction adminTransaction,
            Currency sourceCurrency,
            BigDecimal withdrawAmount,
            Currency targetCurrency,
            BigDecimal depositAmount,
            String adminNotes) {

        Wallet sourceLiquidity = getSystemWallet(sourceCurrency, WalletType.SYSTEM_LIQUIDITY);
        Wallet targetLiquidity = getSystemWallet(targetCurrency, WalletType.SYSTEM_LIQUIDITY);

        List<LedgerEntry> rebalanceEntries = new ArrayList<>();

        rebalanceEntries.add(buildLeg(adminTransaction, sourceLiquidity, EntryClass.WITHDRAWAL, withdrawAmount, BigDecimal.ZERO,
                sourceCurrency, "Admin withdrawal: " + adminNotes, "TREASURY", "NONE", withdrawAmount));

        rebalanceEntries.add(buildLeg(adminTransaction, targetLiquidity, EntryClass.DEPOSIT, BigDecimal.ZERO, depositAmount,
                targetCurrency, "Admin deposit: " + adminNotes, "TREASURY", "NONE", withdrawAmount));

        ledgerEntryRepository.saveAll(rebalanceEntries);

        entityManager.flush();
        entityManager.clear();

        log.info("Treasury Rebalanced: -{} {} -> +{} {}", withdrawAmount, sourceCurrency, depositAmount, targetCurrency);
    }

    private LedgerEntry buildLeg(
            Transaction tx, Wallet wallet, EntryClass entryClass,
            BigDecimal debit, BigDecimal credit, Currency currency, String desc,
            String routingPair, String tiersApplied, BigDecimal usdBaseline) {

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
                .build();
    }
}
