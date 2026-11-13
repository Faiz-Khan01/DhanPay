package com.dhanpay.service;

import com.dhanpay.dto.escrow.CreateEscrowHoldRequestDto;
import com.dhanpay.dto.escrow.EscrowHoldItemDto;
import com.dhanpay.entity.EscrowHold;
import com.dhanpay.entity.EscrowStatus;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.User;
import com.dhanpay.entity.UserStatus;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.exception.ConflictException;
import com.dhanpay.ledger.LedgerAccount;
import com.dhanpay.ledger.LedgerAccountRepository;
import com.dhanpay.ledger.LedgerAccountStatus;
import com.dhanpay.ledger.LedgerAccountType;
import com.dhanpay.ledger.LedgerService;
import com.dhanpay.repository.EscrowHoldRepository;
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
class EscrowServiceTest {

    @Mock
    private EscrowHoldRepository escrowHoldRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private LedgerAccountRepository ledgerAccountRepository;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private EscrowService escrowService;

    private static User user(long id, String name, String email) {
        User u = new User(name, email, "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    private static Wallet wallet(long id, User owner, String ref, String balance) {
        Wallet w = new Wallet(owner, ref, new BigDecimal(balance), "INR", WalletStatus.ACTIVE);
        ReflectionTestUtils.setField(w, "id", id);
        return w;
    }

    @Test
    void createHold_debitsBuyerAndLocksInEscrow() {
        User buyer = user(10L, "Aarav", "aarav@example.com");
        User seller = user(11L, "Priya", "priya@example.com");
        Wallet buyerWallet = wallet(2L, buyer, "WLT-IND-10000002", "10000.0000");
        Wallet sellerWallet = wallet(3L, seller, "WLT-IND-10000003", "5000.0000");
        LedgerAccount escrowAccount = new LedgerAccount(null, "GL-SYS-ESCROW-INR-0001", LedgerAccountType.ESCROW, "INR", LedgerAccountStatus.ACTIVE);

        when(walletRepository.findByUserId(10L)).thenReturn(Optional.of(buyerWallet));
        when(walletRepository.findByWalletReference("WLT-IND-10000003")).thenReturn(Optional.of(sellerWallet));
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(buyerWallet));
        when(ledgerAccountRepository.findByAccountNumber("GL-SYS-ESCROW-INR-0001")).thenReturn(Optional.of(escrowAccount));
        when(escrowHoldRepository.save(any(EscrowHold.class))).thenAnswer(inv -> inv.getArgument(0));

        EscrowHoldItemDto dto = escrowService.createHold(
                UserPrincipal.from(buyer),
                new CreateEscrowHoldRequestDto("WLT-IND-10000003", new BigDecimal("2500.00"), "Laptop delivery"),
                mock(HttpServletRequest.class)
        );

        assertThat(dto.status()).isEqualTo(EscrowStatus.HELD);
        assertThat(dto.role()).isEqualTo("BUYER");
        assertThat(buyerWallet.getBalance()).isEqualByComparingTo("7500.0000");
        verify(ledgerService).recordEscrowHold(eq(dto.reference()), eq(buyerWallet), eq(escrowAccount), eq(new BigDecimal("2500.0000")));
    }

    @Test
    void releaseHold_rejectsSellerAttemptAndAllowsBuyer() {
        User buyer = user(10L, "Aarav", "aarav@example.com");
        User seller = user(11L, "Priya", "priya@example.com");
        Wallet buyerWallet = wallet(2L, buyer, "WLT-IND-10000002", "7500.0000");
        Wallet sellerWallet = wallet(3L, seller, "WLT-IND-10000003", "5000.0000");
        LedgerAccount escrowAccount = new LedgerAccount(null, "GL-SYS-ESCROW-INR-0001", LedgerAccountType.ESCROW, "INR", LedgerAccountStatus.ACTIVE);
        EscrowHold hold = new EscrowHold("ESC-TEST01", buyerWallet, sellerWallet, escrowAccount, new BigDecimal("2500.0000"), "INR", EscrowStatus.HELD, "Order");

        when(escrowHoldRepository.findByReferenceWithLock("ESC-TEST01")).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> escrowService.releaseHold(UserPrincipal.from(seller), "ESC-TEST01", mock(HttpServletRequest.class)))
                .isInstanceOf(BusinessException.class);
        verify(ledgerService, never()).recordEscrowRelease(any(), any(), any(), any());

        when(walletRepository.findByIdWithLock(3L)).thenReturn(Optional.of(sellerWallet));
        when(escrowHoldRepository.save(any(EscrowHold.class))).thenAnswer(inv -> inv.getArgument(0));

        EscrowHoldItemDto released = escrowService.releaseHold(UserPrincipal.from(buyer), "ESC-TEST01", mock(HttpServletRequest.class));
        assertThat(released.status()).isEqualTo(EscrowStatus.RELEASED);
        assertThat(sellerWallet.getBalance()).isEqualByComparingTo("7500.0000");
        verify(ledgerService).recordEscrowRelease("ESC-TEST01", escrowAccount, sellerWallet, new BigDecimal("2500.0000"));
    }

    @Test
    void refundHold_rejectsAlreadySettledEscrow() {
        User buyer = user(10L, "Aarav", "aarav@example.com");
        User seller = user(11L, "Priya", "priya@example.com");
        Wallet buyerWallet = wallet(2L, buyer, "WLT-IND-10000002", "7500.0000");
        Wallet sellerWallet = wallet(3L, seller, "WLT-IND-10000003", "7500.0000");
        LedgerAccount escrowAccount = new LedgerAccount(null, "GL-SYS-ESCROW-INR-0001", LedgerAccountType.ESCROW, "INR", LedgerAccountStatus.ACTIVE);
        EscrowHold hold = new EscrowHold("ESC-DONE", buyerWallet, sellerWallet, escrowAccount, new BigDecimal("2500.0000"), "INR", EscrowStatus.RELEASED, "Order");

        when(escrowHoldRepository.findByReferenceWithLock("ESC-DONE")).thenReturn(Optional.of(hold));

        assertThatThrownBy(() -> escrowService.refundHold(UserPrincipal.from(seller), "ESC-DONE", mock(HttpServletRequest.class)))
                .isInstanceOf(ConflictException.class);
    }
}
