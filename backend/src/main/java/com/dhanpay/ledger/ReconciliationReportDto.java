package com.dhanpay.ledger;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Automated ledger vs. materialized wallet reconciliation summary report.
 */
public record ReconciliationReportDto(
        Instant auditedAt,
        int totalWalletsAudited,
        boolean systemBalanced,
        BigDecimal aggregateWalletFunds,
        BigDecimal aggregateLedgerFunds,
        List<DiscrepancyItem> discrepancies
) {
}
