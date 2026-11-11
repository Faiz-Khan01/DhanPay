package com.dhanpay.service;

import com.dhanpay.dto.beneficiary.CreateBeneficiaryRequestDto;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.User;
import com.dhanpay.entity.UserStatus;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.exception.ConflictException;
import com.dhanpay.repository.BeneficiaryRepository;
import com.dhanpay.repository.UserRepository;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BeneficiaryServiceTest {

    @Mock
    private BeneficiaryRepository beneficiaryRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private WalletRepository walletRepository;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private BeneficiaryService beneficiaryService;

    private User ownerUser;
    private UserPrincipal principal;
    private Wallet ownerWallet;
    private Wallet targetWallet;

    @BeforeEach
    void setUp() {
        ownerUser = new User("Aarav Sharma", "aarav@example.com", "hash", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(ownerUser, "id", 2L);
        principal = UserPrincipal.from(ownerUser);

        ownerWallet = new Wallet(ownerUser, "WLT-IND-10000002", BigDecimal.TEN, "INR", WalletStatus.ACTIVE);
        ReflectionTestUtils.setField(ownerWallet, "id", 2L);

        User targetUser = new User("Priya Patel", "priya@example.com", "hash", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(targetUser, "id", 3L);
        targetWallet = new Wallet(targetUser, "WLT-IND-10000003", BigDecimal.TEN, "INR", WalletStatus.ACTIVE);
        ReflectionTestUtils.setField(targetWallet, "id", 3L);
    }

    @Test
    void addBeneficiary_rejectsOwnWallet() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(ownerUser));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(ownerWallet));
        when(walletRepository.findByWalletReference("WLT-IND-10000002")).thenReturn(Optional.of(ownerWallet));

        assertThatThrownBy(() -> beneficiaryService.addBeneficiary(
                principal, new CreateBeneficiaryRequestDto("WLT-IND-10000002", "Myself"), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("own wallet");
    }

    @Test
    void addBeneficiary_rejectsDuplicateContact() {
        when(userRepository.findById(2L)).thenReturn(Optional.of(ownerUser));
        when(walletRepository.findByUserId(2L)).thenReturn(Optional.of(ownerWallet));
        when(walletRepository.findByWalletReference("WLT-IND-10000003")).thenReturn(Optional.of(targetWallet));
        when(beneficiaryRepository.existsByOwnerUserIdAndTargetWalletId(2L, 3L)).thenReturn(true);

        assertThatThrownBy(() -> beneficiaryService.addBeneficiary(
                principal, new CreateBeneficiaryRequestDto("WLT-IND-10000003", "Priya"), null))
                .isInstanceOf(ConflictException.class);
    }
}
