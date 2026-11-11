package com.dhanpay.service;

import com.dhanpay.dto.transfer.TransferRequestDto;
import com.dhanpay.dto.transfer.TransferResponseDto;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.Transfer;
import com.dhanpay.entity.TransferStatus;
import com.dhanpay.entity.User;
import com.dhanpay.entity.UserStatus;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.idempotency.IdempotencyService;
import com.dhanpay.idempotency.IdempotencyState;
import com.dhanpay.idempotency.IdempotencyStatus;
import com.dhanpay.ledger.LedgerService;
import com.dhanpay.repository.TransferRepository;
import com.dhanpay.repository.UserRepository;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private TransferRepository transferRepository;
    @Mock
    private LedgerService ledgerService;
    @Mock
    private IdempotencyService idempotencyService;
    @Mock
    private AuditService auditService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private TransferService transferService;

    private User senderUser;
    private UserPrincipal senderPrincipal;
    private Wallet senderWallet;
    private Wallet recipientWallet;

    @BeforeEach
    void setUp() {
        senderUser = new User("Aarav Sharma", "aarav@example.com", "hash", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(senderUser, "id", 2L);
        senderPrincipal = UserPrincipal.from(senderUser);

        senderWallet = new Wallet(senderUser, "WLT-IND-10000002", new BigDecimal("25000.0000"), "INR", WalletStatus.ACTIVE);
        ReflectionTestUtils.setField(senderWallet, "id", 2L);

        User recipientUser = new User("Priya Patel", "priya@example.com", "hash", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(recipientUser, "id", 3L);
        recipientWallet = new Wallet(recipientUser, "WLT-IND-10000003", new BigDecimal("15000.0000"), "INR", WalletStatus.ACTIVE);
        ReflectionTestUtils.setField(recipientWallet, "id", 3L);
    }

    @Test
    void executeTransfer_successfulFlowWithOrderedLocks() {
        TransferRequestDto request = new TransferRequestDto("WLT-IND-10000003", new BigDecimal("500.0000"), "Lunch");

        when(userRepository.findById(2L)).thenReturn(Optional.of(senderUser));
        when(idempotencyService.computeFingerprint(eq("WLT-IND-10000003"), any(), eq("Lunch"))).thenReturn("fp-123");
        when(idempotencyService.lockOrRetrieve(senderUser, "key-1", "fp-123"))
                .thenReturn(new IdempotencyState(IdempotencyStatus.PROCESSING, null));

        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByWalletReference("WLT-IND-10000003")).thenReturn(Optional.of(recipientWallet));

        // Lock lowest ID first (2 then 3)
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdWithLock(3L)).thenReturn(Optional.of(recipientWallet));

        when(transferRepository.save(any(Transfer.class))).thenAnswer(inv -> inv.getArgument(0));

        TransferResponseDto response = transferService.executeTransfer(senderPrincipal, "key-1", request, null);

        assertThat(response.status()).isEqualTo(TransferStatus.SUCCESS);
        assertThat(response.amount()).isEqualByComparingTo("500.0000");
        assertThat(senderWallet.getBalance()).isEqualByComparingTo("24500.0000");
        assertThat(recipientWallet.getBalance()).isEqualByComparingTo("15500.0000");

        verify(ledgerService).recordTransfer(any(Transfer.class), eq(senderWallet), eq(recipientWallet), eq(new BigDecimal("500.0000")));
        verify(idempotencyService).markSuccess(eq(2L), eq("key-1"), any(Transfer.class), any());
    }

    @Test
    void executeTransfer_returnsCachedPayloadOnCompletedIdempotency() throws Exception {
        TransferRequestDto request = new TransferRequestDto("WLT-IND-10000003", new BigDecimal("500.0000"), "Lunch");
        TransferResponseDto cachedDto = new TransferResponseDto(
                "TXN-CACHED-1", "WLT-IND-10000002", "WLT-IND-10000003",
                new BigDecimal("500.0000"), "INR", TransferStatus.SUCCESS, "Lunch",
                new BigDecimal("24500.0000"), null
        );
        String cachedJson = objectMapper.writeValueAsString(cachedDto);

        when(userRepository.findById(2L)).thenReturn(Optional.of(senderUser));
        when(idempotencyService.computeFingerprint(eq("WLT-IND-10000003"), any(), eq("Lunch"))).thenReturn("fp-123");
        when(idempotencyService.lockOrRetrieve(senderUser, "key-cached", "fp-123"))
                .thenReturn(new IdempotencyState(IdempotencyStatus.COMPLETED, cachedJson));

        TransferResponseDto result = transferService.executeTransfer(senderPrincipal, "key-cached", request, null);

        assertThat(result.transferReference()).isEqualTo("TXN-CACHED-1");
        verify(walletRepository, never()).findByIdWithLock(any());
        verify(ledgerService, never()).recordTransfer(any(), any(), any(), any());
    }

    @Test
    void executeTransfer_rejectsSelfTransfer() {
        TransferRequestDto request = new TransferRequestDto("WLT-IND-10000002", new BigDecimal("500.0000"), "Self");

        when(userRepository.findById(2L)).thenReturn(Optional.of(senderUser));
        when(idempotencyService.computeFingerprint(any(), any(), any())).thenReturn("fp-self");
        when(idempotencyService.lockOrRetrieve(senderUser, "key-self", "fp-self"))
                .thenReturn(new IdempotencyState(IdempotencyStatus.PROCESSING, null));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(senderWallet));

        assertThatThrownBy(() -> transferService.executeTransfer(senderPrincipal, "key-self", request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Cannot transfer funds to your own wallet");

        verify(idempotencyService).markFailed(2L, "key-self");
    }

    @Test
    void executeTransfer_rejectsInsufficientFunds() {
        TransferRequestDto request = new TransferRequestDto("WLT-IND-10000003", new BigDecimal("30000.0000"), "TooMuch");

        when(userRepository.findById(2L)).thenReturn(Optional.of(senderUser));
        when(idempotencyService.computeFingerprint(any(), any(), any())).thenReturn("fp-heavy");
        when(idempotencyService.lockOrRetrieve(senderUser, "key-heavy", "fp-heavy"))
                .thenReturn(new IdempotencyState(IdempotencyStatus.PROCESSING, null));

        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByWalletReference("WLT-IND-10000003")).thenReturn(Optional.of(recipientWallet));
        when(walletRepository.findByIdWithLock(2L)).thenReturn(Optional.of(senderWallet));
        when(walletRepository.findByIdWithLock(3L)).thenReturn(Optional.of(recipientWallet));

        assertThatThrownBy(() -> transferService.executeTransfer(senderPrincipal, "key-heavy", request, null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Insufficient wallet balance");

        verify(idempotencyService).markFailed(2L, "key-heavy");
    }
}
