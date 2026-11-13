package com.dhanpay.ledger;

import com.dhanpay.service.AuditService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically reconciles all wallet balances against the immutable double-entry general ledger.
 */
@Component
public class ScheduledReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(ScheduledReconciliationJob.class);

    private final ReconciliationService reconciliationService;
    private final AuditService auditService;

    public ScheduledReconciliationJob(
            ReconciliationService reconciliationService,
            AuditService auditService) {
        this.reconciliationService = reconciliationService;
        this.auditService = auditService;
    }

    @Scheduled(
            fixedDelayString = "${dhanpay.reconciliation.fixed-delay-ms:300000}",
            initialDelayString = "${dhanpay.reconciliation.initial-delay-ms:60000}"
    )
    public void runScheduledAudit() {
        ReconciliationReportDto report = reconciliationService.reconcileAllWallets();
        if (report.systemBalanced()) {
            log.info("Scheduled ledger audit passed: {} wallets audited, walletFunds={}, ledgerFunds={}",
                    report.totalWalletsAudited(), report.aggregateWalletFunds(), report.aggregateLedgerFunds());
        } else {
            log.error("Scheduled ledger audit detected {} discrepancies!", report.discrepancies().size());
        }

        String details = "{\"systemBalanced\":" + report.systemBalanced()
                + ",\"totalWalletsAudited\":" + report.totalWalletsAudited()
                + ",\"discrepancyCount\":" + report.discrepancies().size() + "}";
        auditService.logAction(
                null,
                "SCHEDULED_LEDGER_AUDIT",
                "LEDGER",
                "SYSTEM",
                details,
                "127.0.0.1"
        );
    }
}
