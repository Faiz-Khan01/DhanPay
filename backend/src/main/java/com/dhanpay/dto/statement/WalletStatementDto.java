package com.dhanpay.dto.statement;

import java.time.Instant;
import java.util.List;

public record WalletStatementDto(
        String statementReference,
        Instant generatedAt,
        StatementAccountDetailsDto account,
        StatementSummaryDto summary,
        List<StatementLineItemDto> transactions,
        String digitalVerificationHash,
        String issuer
) {
}
