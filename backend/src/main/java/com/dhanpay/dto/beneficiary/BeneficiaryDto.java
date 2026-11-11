package com.dhanpay.dto.beneficiary;

import com.dhanpay.entity.WalletStatus;

import java.time.Instant;

/**
 * Projection of a customer's saved beneficiary contact.
 */
public record BeneficiaryDto(
        Long id,
        String nickname,
        String walletReference,
        String fullName,
        WalletStatus walletStatus,
        Instant createdAt
) {
}
