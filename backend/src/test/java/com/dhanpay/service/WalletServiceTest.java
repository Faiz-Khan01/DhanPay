package com.dhanpay.service;

import com.dhanpay.dto.wallet.WalletTopUpRequestDto;
import com.dhanpay.dto.wallet.WalletTopUpResponseDto;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.User;
import com.dhanpay.entity.UserStatus;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.idempotency.IdempotencyService;
import com.dhanpay.idempotency.IdempotencyState;
import com.dhanpay.idempotency.IdempotencyStatus;
import com.dhanpay.ledger.LedgerAccountRepository;
import com.dhanpay.ledger.LedgerService;
import com.dhanpay.ledger.LedgerTransaction;
import com.dhanpay.ledger.LedgerTransactionStatus;
import com.dhanpay.ledger.LedgerTransactionType;
import com.dhanpay.repository.UserRepository;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private LedgerAccountRepository ledgerAccountRepository;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private AuditService auditService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    @InjectMocks
    private WalletService walletService;

    private User user;
    private UserPrincipal principal;
    private Wallet wallet;

    @BeforeEach
    void setUp() {
        user = new User("Aarav Sharma", "aarav@example.com", "hash", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 2L);
        principal = UserPrincipal.from(user);

        wallet = new Wallet(user, "WLT-IND-10000002", new BigDecimal("1000.0000"), "INR", WalletStatus.ACTIVE);
        ReflectionTestUtils.setField(wallet, "id", 2L);
    }

    @Test
    void topUpWallet_creditsBalanceAndRecordsDoubleEntryLedger() {
        WalletTopUpRequestDto request = new WalletTopUpRequestDto(new BigDecimal("500.0000"), "UPI Instant");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(idempotencyService.computeFingerprint("TOPUP:2", request.amount(), "UPI Instant")).thenReturn("fp-topup");
        when(idempotencyService.lockOrRetrieve(user, "idem-1", "fp-topup"))
                .thenReturn(new IdempotencyState(IdempotencyStatus.PROCESSING, null));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(wallet));

        LedgerTransaction ledgerTx = new LedgerTransaction(
                null, "LTX-TOP-1", LedgerTransactionType.ADJUSTMENT, LedgerTransactionStatus.POSTED
        );
        when(ledgerService.recordTopUp(any(), eq(wallet), eq(new BigDecimal("500.0000")))).thenReturn(ledgerTx);

        WalletTopUpResponseDto response = walletService.topUpWallet(principal, "idem-1", request, null);

        assertThat(response.newBalance()).isEqualByComparingTo("1500.0000");
        assertThat(response.ledgerReference()).isEqualTo("LTX-TOP-1");
        verify(walletRepository).save(wallet);
        verify(idempotencyService).markSuccess(eq(2L), eq("idem-1"), eq(null), any());
    }

    @Test
    void topUpWallet_rejectsFrozenWallet() {
        wallet.setStatus(WalletStatus.FROZEN);
        WalletTopUpRequestDto request = new WalletTopUpRequestDto(new BigDecimal("500.0000"), "UPI Instant");
        when(userRepository.findById(2L)).thenReturn(Optional.of(user));
        when(idempotencyService.computeFingerprint("TOPUP:2", request.amount(), "UPI Instant")).thenReturn("fp-topup");
        when(idempotencyService.lockOrRetrieve(user, "idem-2", "fp-topup"))
                .thenReturn(new IdempotencyState(IdempotencyStatus.PROCESSING, null));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(wallet));
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(wallet));

        assertThatThrownBy(() -> walletService.topUpWallet(principal, "idem-2", request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("FROZEN");
        verify(idempotencyService).markFailed(2L, "idem-2");
    }
}
