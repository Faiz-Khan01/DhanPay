package com.dhanpay.service;

import com.dhanpay.dto.admin.AdminTransferItemDto;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.Transfer;
import com.dhanpay.entity.TransferStatus;
import com.dhanpay.entity.User;
import com.dhanpay.entity.UserStatus;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.exception.ConflictException;
import com.dhanpay.ledger.LedgerService;
import com.dhanpay.ledger.ReconciliationService;
import com.dhanpay.repository.AuditLogRepository;
import com.dhanpay.repository.TransferRepository;
import com.dhanpay.repository.UserRepository;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceReversalTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private TransferRepository transferRepository;
    @Mock
    private AuditLogRepository auditLogRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private ReconciliationService reconciliationService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AdminService adminService;

    private static UserPrincipal adminPrincipal() {
        User admin = new User("System Admin", "admin@dhanpay.local", "h", Role.ROLE_ADMIN, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(admin, "id", 1L);
        return UserPrincipal.from(admin);
    }

    @Test
    void reverseTransfer_reversesSettledTransferAndPostsCompensatingLedger() {
        User sender = new User("Aarav", "aarav@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        User recipient = new User("Priya", "priya@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        Wallet senderWallet = new Wallet(sender, "WLT-IND-10000002", new BigDecimal("9000.0000"), "INR", WalletStatus.ACTIVE);
        Wallet recipientWallet = new Wallet(recipient, "WLT-IND-10000003", new BigDecimal("6000.0000"), "INR", WalletStatus.ACTIVE);
        ReflectionTestUtils.setField(senderWallet, "id", 2L);
        ReflectionTestUtils.setField(recipientWallet, "id", 3L);

        Transfer transfer = new Transfer("TXN-REV-1", senderWallet, recipientWallet, new BigDecimal("1000.0000"), "INR", TransferStatus.SUCCESS, "Invoice");
        ReflectionTestUtils.setField(transfer, "id", 50L);

        when(transferRepository.findByReference("TXN-REV-1")).thenReturn(Optional.of(transfer));
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdWithLock(3L)).thenReturn(Optional.of(recipientWallet));
        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

        AdminTransferItemDto result = adminService.reverseTransfer(
                "TXN-REV-1",
                "Duplicate settlement chargeback",
                adminPrincipal(),
                mock(HttpServletRequest.class)
        );

        assertThat(result.status()).isEqualTo(TransferStatus.REVERSED);
        assertThat(senderWallet.getBalance()).isEqualByComparingTo("10000.0000");
        assertThat(recipientWallet.getBalance()).isEqualByComparingTo("5000.0000");
        verify(ledgerService).recordReversal(transfer, senderWallet, recipientWallet, new BigDecimal("1000.0000"));
        verify(auditService).logAction(eq(1L), eq("TRANSFER_REVERSED"), eq("TRANSFER"), eq("TXN-REV-1"), any(), any(HttpServletRequest.class));
    }

    @Test
    void reverseTransfer_rejectsAlreadyReversedTransfer() {
        User u1 = new User("A", "a@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        User u2 = new User("B", "b@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        Wallet w1 = new Wallet(u1, "W1", BigDecimal.TEN, "INR", WalletStatus.ACTIVE);
        Wallet w2 = new Wallet(u2, "W2", BigDecimal.TEN, "INR", WalletStatus.ACTIVE);
        Transfer transfer = new Transfer("TXN-DUP", w1, w2, BigDecimal.ONE, "INR", TransferStatus.REVERSED, null);

        when(transferRepository.findByReference("TXN-DUP")).thenReturn(Optional.of(transfer));

        assertThatThrownBy(() -> adminService.reverseTransfer("TXN-DUP", "Retry reversal", adminPrincipal(), mock(HttpServletRequest.class)))
                .isInstanceOf(ConflictException.class);
        verify(ledgerService, never()).recordReversal(any(), any(), any(), any());
    }

    @Test
    void reverseTransfer_rejectsWhenRecipientLacksSufficientBalance() {
        User sender = new User("Aarav", "aarav@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        User recipient = new User("Priya", "priya@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        Wallet senderWallet = new Wallet(sender, "WLT-IND-10000002", new BigDecimal("1000.0000"), "INR", WalletStatus.ACTIVE);
        Wallet recipientWallet = new Wallet(recipient, "WLT-IND-10000003", new BigDecimal("200.0000"), "INR", WalletStatus.ACTIVE);
        ReflectionTestUtils.setField(senderWallet, "id", 2L);
        ReflectionTestUtils.setField(recipientWallet, "id", 3L);

        Transfer transfer = new Transfer("TXN-LOW", senderWallet, recipientWallet, new BigDecimal("500.0000"), "INR", TransferStatus.SUCCESS, null);
        when(transferRepository.findByReference("TXN-LOW")).thenReturn(Optional.of(transfer));
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdWithLock(3L)).thenReturn(Optional.of(recipientWallet));

        assertThatThrownBy(() -> adminService.reverseTransfer("TXN-LOW", "Dispute reversal", adminPrincipal(), mock(HttpServletRequest.class)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("insufficient balance");
        verify(ledgerService, never()).recordReversal(any(), any(), any(), any());
    }
}
