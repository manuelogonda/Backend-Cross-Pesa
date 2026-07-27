package com.manuelorg.cross_pesa.admin.dto;

import java.math.BigDecimal;

public record DashboardMetricsResponse(
        long totalTransactionsToday,
        long pendingTransactions,
        long flaggedTransactions,
        BigDecimal totalRevenueToday // Sum of all fees collected
) {}
