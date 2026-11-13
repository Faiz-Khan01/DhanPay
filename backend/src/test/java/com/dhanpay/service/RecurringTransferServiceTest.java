package com.dhanpay.service;

import com.dhanpay.dto.recurring.CreateRecurringTransferRequestDto;
import com.dhanpay.dto.recurring.RecurringTransferItemDto;
import com.dhanpay.dto.transfer.TransferResponseDto;
import com.dhanpay.entity.RecurringFrequency;
import com.dhanpay.entity.RecurringStatus;
import com.dhanpay.entity.RecurringTransfer;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.TransferStatus;
import com.dhanpay.entity.User;
import com.dhanpay.entity.UserStatus;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.repository.RecurringTransferRepository;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RecurringTransferServiceTest {

    @Mock
    private RecurringTransferRepository recurringTransferRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private TransferService transferService;
    @Mock
    private AuditService auditService;
    @Mock
    private NotificationStreamService notificationStreamService;

    @InjectMocks
    private RecurringTransferService recurringTransferService;

    private static User testUser(long id, String name, String email) {
        User u = new User(name, email, "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static Wallet testWallet(long id, User owner, String ref) {
        Wallet w = new Wallet(owner, ref, new BigDecimal("20000.0000"), "INR", WalletStatus.ACTIVE);
        ReflectionTestUtils.setField(w, "id", id);
        return w;
    }

    @Test
    void createRule_schedulesActiveRecurringTransfer() {
        User sender = testUser(1L, "Aarav", "aarav@example.com");
        User recipient = testUser(2L, "Priya", "priya@example.com");
        Wallet senderWallet = testWallet(10L, sender, "WLT-IND-10000002");
        Wallet recipientWallet = testWallet(20L, recipient, "WLT-IND-10000003");

        when(walletRepository.findByUserId(1L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByWalletReference("WLT-IND-10000003")).thenReturn(Optional.of(recipientWallet));
        when(recurringTransferRepository.save(any(RecurringTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        RecurringTransferItemDto res = recurringTransferService.createRule(
                UserPrincipal.from(sender),
                new CreateRecurringTransferRequestDto(
                        "WLT-IND-10000003",
                        new BigDecimal("2500.00"),
                        RecurringFrequency.MONTHLY,
                        "Apartment Maintenance",
                        LocalDate.now(ZoneOffset.UTC),
                        null,
                        12
                ),
                mock(HttpServletRequest.class)
        );

        assertThat(res.status()).isEqualTo(RecurringStatus.ACTIVE);
        assertThat(res.frequency()).isEqualTo(RecurringFrequency.MONTHLY);
        assertThat(res.amount()).isEqualByComparingTo("2500.0000");
        assertThat(res.maxExecutions()).isEqualTo(12);
        verify(auditService).logAction(eq(1L), eq("RECURRING_RULE_CREATED"), eq("RECURRING"), any(), any(), any(HttpServletRequest.class));
    }

    @Test
    void pauseAndResumeRule_updatesStatus() {
        User sender = testUser(1L, "Aarav", "aarav@example.com");
        User recipient = testUser(2L, "Priya", "priya@example.com");
        Wallet senderWallet = testWallet(10L, sender, "WLT-IND-10000002");
        Wallet recipientWallet = testWallet(20L, recipient, "WLT-IND-10000003");

        RecurringTransfer rule = new RecurringTransfer(
                "REC-TEST001", senderWallet, recipientWallet, new BigDecimal("1000.0000"),
                "INR", RecurringFrequency.WEEKLY, "Allowance", LocalDate.now(), null, Instant.now(), null
        );

        when(recurringTransferRepository.findByReference("REC-TEST001")).thenReturn(Optional.of(rule));
        when(recurringTransferRepository.save(any(RecurringTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        RecurringTransferItemDto paused = recurringTransferService.pauseRule(UserPrincipal.from(sender), "REC-TEST001");
        assertThat(paused.status()).isEqualTo(RecurringStatus.PAUSED);

        RecurringTransferItemDto resumed = recurringTransferService.resumeRule(UserPrincipal.from(sender), "REC-TEST001");
        assertThat(resumed.status()).isEqualTo(RecurringStatus.ACTIVE);
    }

    @Test
    void executeDueRules_executesTransferAndCalculatesNextRun() {
        User sender = testUser(1L, "Aarav", "aarav@example.com");
        User recipient = testUser(2L, "Priya", "priya@example.com");
        Wallet senderWallet = testWallet(10L, sender, "WLT-IND-10000002");
        Wallet recipientWallet = testWallet(20L, recipient, "WLT-IND-10000003");

        Instant dueTime = Instant.now().minusSeconds(60);
        RecurringTransfer rule = new RecurringTransfer(
                "REC-DUE001", senderWallet, recipientWallet, new BigDecimal("500.0000"),
                "INR", RecurringFrequency.DAILY, "Daily SIP", LocalDate.now(), null, dueTime, null
        );

        when(recurringTransferRepository.findDueTransfers(eq(RecurringStatus.ACTIVE), any(Instant.class)))
                .thenReturn(List.of(rule));

        TransferResponseDto transferResponse = new TransferResponseDto(
                "TXN-REC-1", "WLT-IND-10000002", "WLT-IND-10000003",
                new BigDecimal("500.0000"), "INR", TransferStatus.SUCCESS, "Daily SIP",
                new BigDecimal("19500.0000"), Instant.now()
        );

        when(transferService.executeTransfer(any(UserPrincipal.class), any(), any(), any())).thenReturn(transferResponse);
        when(recurringTransferRepository.save(any(RecurringTransfer.class))).thenAnswer(inv -> inv.getArgument(0));

        int processed = recurringTransferService.executeDueRules();

        assertThat(processed).isEqualTo(1);
        assertThat(rule.getTotalExecutions()).isEqualTo(1);
        assertThat(rule.getLastExecutedAt()).isNotNull();
        assertThat(rule.getNextExecutionAt()).isAfter(dueTime);
    }
}
