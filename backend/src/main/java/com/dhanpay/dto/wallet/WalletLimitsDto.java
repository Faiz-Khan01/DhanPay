package com.dhanpay.dto.wallet;

import java.math.BigDecimal;

/**
 * Customer transfer limit telemetry including rolling 24-hour usage.
 */
public record WalletLimitsDto(
        BigDecimal singleTransferLimit,
        BigDecimal dailyLimit,
        BigDecimal dailyUsed,
        BigDecimal dailyRemaining
) {
}
