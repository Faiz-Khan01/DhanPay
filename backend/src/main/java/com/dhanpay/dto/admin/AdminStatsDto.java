package com.dhanpay.dto.admin;

import java.math.BigDecimal;

/**
 * Aggregated operational KPIs for the DhanPay Admin Console.
 */
public record AdminStatsDto(
        long totalUsers,
        long activeWallets,
        long frozenWallets,
        BigDecimal totalTransferVolume,
        long successfulTransfers,
        long failedTransfers
) {
}
