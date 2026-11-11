package com.dhanpay.dto.auth;

import com.dhanpay.entity.Role;
import com.dhanpay.entity.UserStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Safe user & wallet projection returned to API clients without sensitive credentials.
 */
public record UserSummaryResponse(
        Long id,
        String fullName,
        String email,
        Role role,
        UserStatus status,
        String walletReference,
        BigDecimal balance,
        String currency,
        String ledgerAccountNumber,
        Instant createdAt
) {
}
