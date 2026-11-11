package com.dhanpay.ledger;

import com.dhanpay.entity.Role;
import com.dhanpay.entity.User;
import com.dhanpay.entity.UserStatus;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.repository.WalletRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationServiceTest {

    @Mock
    private WalletRepository walletRepository;
    @Mock
    private LedgerAccountRepository ledgerAccountRepository;
    @Mock
    private LedgerEntryRepository ledgerEntryRepository;

    @InjectMocks
    private ReconciliationService reconciliationService;

    @Test
    void reconcileAllWallets_reportsBalancedWhenWalletsAndLedgerMatch() {
        User u = new User("Aarav", "aarav@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        Wallet w = new Wallet(u, "WLT-IND-10000002", new BigDecimal("25000.0000"), "INR", WalletStatus.ACTIVE);
        ReflectionTestUtils.setField(w, "id", 2L);
        LedgerAccount la = new LedgerAccount(w, "LA-IND-10000002", LedgerAccountType.CUSTOMER_WALLET, "INR", LedgerAccountStatus.ACTIVE);
        ReflectionTestUtils.setField(la, "id", 3L);

        when(walletRepository.findAll()).thenReturn(List.of(w));
        when(ledgerAccountRepository.findByWalletId(2L)).thenReturn(Optional.of(la));
        when(ledgerEntryRepository.sumAmountByAccountIdAndDirection(3L, EntryDirection.CREDIT))
                .thenReturn(new BigDecimal("30000.0000"));
        when(ledgerEntryRepository.sumAmountByAccountIdAndDirection(3L, EntryDirection.DEBIT))
                .thenReturn(new BigDecimal("5000.0000"));
        when(ledgerEntryRepository.sumAllByDirection(EntryDirection.DEBIT)).thenReturn(new BigDecimal("30000.0000"));
        when(ledgerEntryRepository.sumAllByDirection(EntryDirection.CREDIT)).thenReturn(new BigDecimal("30000.0000"));

        ReconciliationReportDto report = reconciliationService.reconcileAllWallets();

        assertThat(report.systemBalanced()).isTrue();
        assertThat(report.totalWalletsAudited()).isEqualTo(1);
        assertThat(report.aggregateWalletFunds()).isEqualByComparingTo("25000.0000");
        assertThat(report.aggregateLedgerFunds()).isEqualByComparingTo("25000.0000");
        assertThat(report.discrepancies()).isEmpty();
    }

    @Test
    void reconcileAllWallets_detectsDiscrepancyWhenWalletDriftsFromLedger() {
        User u = new User("Priya", "priya@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        Wallet w = new Wallet(u, "WLT-IND-10000003", new BigDecimal("15500.0000"), "INR", WalletStatus.ACTIVE);
        ReflectionTestUtils.setField(w, "id", 3L);
        LedgerAccount la = new LedgerAccount(w, "LA-IND-10000003", LedgerAccountType.CUSTOMER_WALLET, "INR", LedgerAccountStatus.ACTIVE);
        ReflectionTestUtils.setField(la, "id", 4L);

        when(walletRepository.findAll()).thenReturn(List.of(w));
        when(ledgerAccountRepository.findByWalletId(3L)).thenReturn(Optional.of(la));
        when(ledgerEntryRepository.sumAmountByAccountIdAndDirection(4L, EntryDirection.CREDIT))
                .thenReturn(new BigDecimal("15000.0000"));
        when(ledgerEntryRepository.sumAmountByAccountIdAndDirection(4L, EntryDirection.DEBIT))
                .thenReturn(new BigDecimal("0.0000"));
        when(ledgerEntryRepository.sumAllByDirection(EntryDirection.DEBIT)).thenReturn(new BigDecimal("15000.0000"));
        when(ledgerEntryRepository.sumAllByDirection(EntryDirection.CREDIT)).thenReturn(new BigDecimal("15000.0000"));

        ReconciliationReportDto report = reconciliationService.reconcileAllWallets();

        assertThat(report.systemBalanced()).isFalse();
        assertThat(report.discrepancies()).hasSize(1);
        DiscrepancyItem item = report.discrepancies().get(0);
        assertThat(item.walletRef()).isEqualTo("WLT-IND-10000003");
        assertThat(item.delta()).isEqualByComparingTo("500.0000");
    }
}
