package com.dhanpay.dto.statement;

public record StatementAccountDetailsDto(
        String fullName,
        String email,
        String walletReference,
        String ledgerAccountNumber,
        String currency,
        String status
) {
}
