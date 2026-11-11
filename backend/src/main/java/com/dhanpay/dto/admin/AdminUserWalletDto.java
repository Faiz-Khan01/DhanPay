package com.dhanpay.dto.admin;

import com.dhanpay.entity.UserStatus;
import com.dhanpay.entity.WalletStatus;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Customer and wallet status projection for admin user management.
 */
public record AdminUserWalletDto(
        Long userId,
        String fullName,
        String email,
        UserStatus userStatus,
        Long walletId,
        String walletReference,
        BigDecimal balance,
        String currency,
        WalletStatus walletStatus,
        Instant createdAt
) {
}
