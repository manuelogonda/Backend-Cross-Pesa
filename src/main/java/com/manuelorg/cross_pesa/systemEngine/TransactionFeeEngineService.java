package com.manuelorg.cross_pesa.systemEngine;

import com.manuelorg.cross_pesa.transaction.dto.QuoteResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Service
public class TransactionFeeEngineService {

    // --- Money scale used consistently across all calculations ---
    private static final int MONEY_SCALE = 4;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;

    // --- 1. System Constants (USD Baseline Tiers) ---
    private static final BigDecimal TIER_1_LIMIT = new BigDecimal("1000.00");
    private static final BigDecimal TIER_2_LIMIT = new BigDecimal("5000.00"); // Capacity of Tier 2 is $4000

    private static final BigDecimal TIER_1_RATE = new BigDecimal("0.0060"); // 0.60%
    private static final BigDecimal TIER_2_RATE = new BigDecimal("0.0040"); // 0.40%
    private static final BigDecimal TIER_3_RATE = new BigDecimal("0.0020"); // 0.20%

    // --- 2. The Specific Pair Cost Routing Table (immutable after construction) ---
    private static final BigDecimal DEFAULT_PAIR_COST = new BigDecimal("0.0030"); // 0.30% Fallback
    private final Map<String, BigDecimal> ROUTING_COSTS;

    public TransactionFeeEngineService() {
        // Pre-loading the corridor liquidity rates; wrapped in Map.copyOf for immutability
        ROUTING_COSTS = Map.copyOf(Map.of(
                "USD_KES", new BigDecimal("0.0025"),
                "GBP_EUR", new BigDecimal("0.0005"),
                "USD_CNY", new BigDecimal("0.0035"),
                "EUR_JPY", new BigDecimal("0.0015"),
                "CAD_AUD", new BigDecimal("0.0020"),
                "AED_PKR", new BigDecimal("0.0030"),
                "SAR_KES", new BigDecimal("0.0040"),
                "USD_SEK", new BigDecimal("0.0010")
        ));
    }

    /**
     * Executes the airtight calculation engine.
     *
     * @param amountSent         The raw amount the user is sending (e.g., 10000.00)
     * @param sourceCurrency     e.g., "GBP"
     * @param targetCurrency     e.g., "KES"
     * @param usdToSourceRate    The FX rate to convert USD to Source (e.g., 1 USD = 0.78 GBP)
     * @param sourceToTargetRate The FX rate to convert Source to Target (e.g., 1 GBP = 166.0256 KES)
     * @return QuoteResult       A DTO containing the airtight ledger breakdown
     * @throws IllegalArgumentException if any input is invalid or amount is too small to cover fees
     */
    public QuoteResult calculateTransaction(
            BigDecimal amountSent,
            String sourceCurrency,
            String targetCurrency,
            BigDecimal usdToSourceRate,
            BigDecimal sourceToTargetRate) {

        // --- Strict input validation ---
        if (amountSent == null || amountSent.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("amountSent must be greater than zero.");
        }
        if (usdToSourceRate == null || usdToSourceRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("usdToSourceRate must be greater than zero.");
        }
        if (sourceToTargetRate == null || sourceToTargetRate.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("sourceToTargetRate must be greater than zero.");
        }
        if (sourceCurrency == null || sourceCurrency.isBlank()) {
            throw new IllegalArgumentException("sourceCurrency must not be blank.");
        }
        if (targetCurrency == null || targetCurrency.isBlank()) {
            throw new IllegalArgumentException("targetCurrency must not be blank.");
        }

        // ====================================================================
        // STEP 1: NORMALIZATION LINE
        // Example: 10,000 GBP / 0.78 = $12,820.51 USD
        // ====================================================================
        BigDecimal normalizedUsdAmount = amountSent.divide(usdToSourceRate, MONEY_SCALE, ROUNDING);

        // ====================================================================
        // STEP 2: THE BRACKET SPLIT (USD Tiers)
        // ====================================================================
        BigDecimal totalMarkupUsd = BigDecimal.ZERO;
        BigDecimal remainingUsd = normalizedUsdAmount;
        StringBuilder tiersApplied = new StringBuilder();

        // Tier 1: First $1,000
        if (remainingUsd.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal tier1Amount = remainingUsd.min(TIER_1_LIMIT);
            totalMarkupUsd = totalMarkupUsd.add(tier1Amount.multiply(TIER_1_RATE).setScale(MONEY_SCALE, ROUNDING));
            remainingUsd = remainingUsd.subtract(tier1Amount);
            tiersApplied.append("TIER_1");
        }

        // Tier 2: Next $4,000 (From $1,001 to $5,000)
        BigDecimal tier2Capacity = TIER_2_LIMIT.subtract(TIER_1_LIMIT);
        if (remainingUsd.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal tier2Amount = remainingUsd.min(tier2Capacity);
            totalMarkupUsd = totalMarkupUsd.add(tier2Amount.multiply(TIER_2_RATE).setScale(MONEY_SCALE, ROUNDING));
            remainingUsd = remainingUsd.subtract(tier2Amount);
            tiersApplied.append(",TIER_2");
        }

        // Tier 3: Anything over $5,000
        if (remainingUsd.compareTo(BigDecimal.ZERO) > 0) {
            totalMarkupUsd = totalMarkupUsd.add(remainingUsd.multiply(TIER_3_RATE).setScale(MONEY_SCALE, ROUNDING));
            tiersApplied.append(",TIER_3");
        }

        // Scale the markup back to the Source Currency (e.g., $37.64 USD * 0.78 = £29.36 GBP)
        BigDecimal totalMarkupSource = totalMarkupUsd.multiply(usdToSourceRate).setScale(MONEY_SCALE, ROUNDING);

        // ====================================================================
        // STEP 3: THE DYNAMIC ROUTING COST
        // ====================================================================
        String routeKey = sourceCurrency + "_" + targetCurrency;
        BigDecimal routeCostPercentage = ROUTING_COSTS.getOrDefault(routeKey, DEFAULT_PAIR_COST);

        // Example: 10,000 GBP * 0.0030 = £30.00 GBP
        BigDecimal routingCostSource = amountSent.multiply(routeCostPercentage).setScale(MONEY_SCALE, ROUNDING);

        // ====================================================================
        // STEP 4: AIRTIGHT LEDGER MATH
        // ====================================================================
        // Combined Fee: £29.36 + £30.00 = £59.36 GBP
        BigDecimal totalPlatformFee = totalMarkupSource.add(routingCostSource);

        // Deduct upfront: £10,000.00 - £59.36 = £9,940.64 GBP
        BigDecimal amountAfterFees = amountSent.subtract(totalPlatformFee);

        if (amountAfterFees.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException(
                    String.format("Transaction amount %s %s is too small to cover platform fees of %s.",
                            amountSent, sourceCurrency, totalPlatformFee));
        }

        // Final Payout: £9,940.64 * 166.0256 = 1,650,400.75 KES
        BigDecimal payoutAmountTarget = amountAfterFees.multiply(sourceToTargetRate).setScale(MONEY_SCALE, ROUNDING);

        // Return the immutable Quote DTO with correctly mapped variables
        return new QuoteResult(
                amountSent,              // 1. amountSent
                totalPlatformFee,        // 2. totalPlatformFee
                totalMarkupSource,       // 3. platformMarkupFee (Mapped from totalMarkupSource)
                routingCostSource,       // 4. routingCostFee (Mapped from routingCostSource)
                amountAfterFees,         // 5. amountAfterFees
                payoutAmountTarget,      // 6. payoutAmountTarget
                sourceToTargetRate,      // 7. appliedExchangeRate (Mapped from sourceToTargetRate)
                usdToSourceRate,         // 8. usdNormalizationRate
                routeKey,                // 9. routingPair (Mapped from routeKey)
                tiersApplied.toString(), // 10. markupTiersApplied
                normalizedUsdAmount      // 11. usdBaselineAmount (Mapped from normalizedUsdAmount)
        );
    }
}
