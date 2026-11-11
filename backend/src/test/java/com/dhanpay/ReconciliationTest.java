package com.dhanpay;

import com.dhanpay.dto.transfer.TransferRequestDto;
import com.dhanpay.entity.User;
import com.dhanpay.entity.Wallet;
import com.dhanpay.ledger.DiscrepancyItem;
import com.dhanpay.ledger.ReconciliationReportDto;
import com.dhanpay.ledger.ReconciliationService;
import com.dhanpay.repository.UserRepository;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.UserPrincipal;
import com.dhanpay.service.TransferService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests verifying ledger-to-wallet invariants and discrepancy detection.
 */
class ReconciliationTest extends BaseIntegrationTest {

    private static final String AARAV_WALLET_REF = "WLT-IND-10000002";
    private static final String PRIYA_WALLET_REF = "WLT-IND-10000003";
    private static final String ROHAN_WALLET_REF = "WLT-IND-10000004";
    private static final MockHttpServletRequest MOCK_HTTP = new MockHttpServletRequest();

    @Autowired
    private ReconciliationService reconciliationService;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransferService transferService;

    @Test
    @DisplayName("Reconciliation reports balanced system with 0 discrepancies after valid transfers")
    void reconciliation_balancedAfterTransfers() {
        User aaravUser = userRepository.findByEmail("aarav@example.com").orElseThrow();
        User priyaUser = userRepository.findByEmail("priya@example.com").orElseThrow();

        UserPrincipal aaravPrincipal = UserPrincipal.from(aaravUser);
        UserPrincipal priyaPrincipal = UserPrincipal.from(priyaUser);

        transferService.executeTransfer(
                aaravPrincipal,
                "RECON-T1-" + UUID.randomUUID(),
                new TransferRequestDto(PRIYA_WALLET_REF, new BigDecimal("250.0000"), "Reconciliation transfer 1"),
                MOCK_HTTP
        );
        transferService.executeTransfer(
                priyaPrincipal,
                "RECON-T2-" + UUID.randomUUID(),
                new TransferRequestDto(AARAV_WALLET_REF, new BigDecimal("100.0000"), "Reconciliation transfer 2"),
                MOCK_HTTP
        );

        ReconciliationReportDto report = reconciliationService.reconcileAllWallets();

        assertThat(report).isNotNull();
        assertThat(report.systemBalanced()).isTrue();
        assertThat(report.discrepancies()).isEmpty();
        assertThat(report.totalWalletsAudited()).isGreaterThanOrEqualTo(4);
        assertThat(report.aggregateWalletFunds()).isEqualByComparingTo(report.aggregateLedgerFunds());
    }

    @Test
    @DisplayName("Reconciliation detects and isolates a direct wallet balance corruption")
    void reconciliation_detectsDiscrepancyAfterDirectWalletCorruption() {
        Wallet targetWallet = walletRepository.findByWalletReference(ROHAN_WALLET_REF).orElseThrow();
        BigDecimal originalBalance = targetWallet.getBalance();
        BigDecimal corruptionDelta = new BigDecimal("999.9999");

        try {
            targetWallet.setBalance(originalBalance.add(corruptionDelta));
            walletRepository.saveAndFlush(targetWallet);

            ReconciliationReportDto report = reconciliationService.reconcileAllWallets();

            assertThat(report.systemBalanced()).isFalse();
            assertThat(report.discrepancies()).hasSize(1);

            DiscrepancyItem item = report.discrepancies().get(0);
            assertThat(item.walletRef()).isEqualTo(ROHAN_WALLET_REF);
            assertThat(item.currentBalance()).isEqualByComparingTo(originalBalance.add(corruptionDelta));
            assertThat(item.ledgerDerivedBalance()).isEqualByComparingTo(originalBalance);
            assertThat(item.delta()).isEqualByComparingTo(corruptionDelta);
        } finally {
            // Restore original balance so the shared Testcontainer database remains balanced
            Wallet toRestore = walletRepository.findByWalletReference(ROHAN_WALLET_REF).orElseThrow();
            toRestore.setBalance(originalBalance);
            walletRepository.saveAndFlush(toRestore);
        }
    }
}
