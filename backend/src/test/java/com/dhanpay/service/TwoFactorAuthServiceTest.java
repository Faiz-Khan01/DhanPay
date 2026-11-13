package com.dhanpay.service;

import com.dhanpay.dto.auth.TwoFactorDisableRequestDto;
import com.dhanpay.dto.auth.TwoFactorSetupResponseDto;
import com.dhanpay.dto.auth.TwoFactorStatusDto;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.User;
import com.dhanpay.entity.UserStatus;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.exception.ConflictException;
import com.dhanpay.repository.UserRepository;
import com.dhanpay.security.TotpUtils;
import com.dhanpay.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TwoFactorAuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private AuditService auditService;

    @InjectMocks
    private TwoFactorAuthService twoFactorAuthService;

    private static User testUser(long id, String email) {
        User u = new User("Aarav", email, "$2a$12$hashedpwd", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(u, "id", id);
        return u;
    }

    @Test
    void startSetup_generatesSecretQrAndBackupCodes() {
        User u = testUser(1L, "aarav@example.com");
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        TwoFactorSetupResponseDto res = twoFactorAuthService.startSetup(UserPrincipal.from(u));

        assertThat(res.secret()).isNotBlank();
        assertThat(res.otpauthUri()).contains("otpauth://totp/DhanPay:");
        assertThat(res.qrSvg()).startsWith("<svg");
        assertThat(res.backupCodes()).hasSize(6);
        assertThat(u.isTotpEnabled()).isFalse();
        assertThat(u.getTotpSecret()).isEqualTo(res.secret());
    }

    @Test
    void enable_activatesTotpWithValidCode() {
        User u = testUser(1L, "aarav@example.com");
        String secret = TotpUtils.generateSecret();
        u.setTotpSecret(secret);
        u.setTotpBackupCodes("1111-2222,3333-4444");

        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        long nowSec = System.currentTimeMillis() / 1000L;
        String validCode = TotpUtils.generateCode(secret, nowSec);

        TwoFactorStatusDto status = twoFactorAuthService.enable(UserPrincipal.from(u), validCode, mock(HttpServletRequest.class));

        assertThat(status.enabled()).isTrue();
        assertThat(u.isTotpEnabled()).isTrue();
        assertThat(status.remainingBackupCodes()).isEqualTo(2);
        verify(auditService).logAction(any(), any(), any(), any(), any(), any(HttpServletRequest.class));
    }

    @Test
    void enable_rejectsInvalidCode() {
        User u = testUser(1L, "aarav@example.com");
        u.setTotpSecret(TotpUtils.generateSecret());

        when(userRepository.findById(1L)).thenReturn(Optional.of(u));

        assertThatThrownBy(() -> twoFactorAuthService.enable(UserPrincipal.from(u), "000000", mock(HttpServletRequest.class)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Invalid 6-digit");
    }

    @Test
    void disable_revokesWithPasswordAndCode() {
        User u = testUser(1L, "aarav@example.com");
        String secret = TotpUtils.generateSecret();
        u.setTotpSecret(secret);
        u.setTotpEnabled(true);
        u.setTotpBackupCodes("1111-2222");

        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(passwordEncoder.matches("secret123", u.getPasswordHash())).thenReturn(true);
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        String code = TotpUtils.generateCode(secret, System.currentTimeMillis() / 1000L);
        TwoFactorStatusDto res = twoFactorAuthService.disable(
                UserPrincipal.from(u),
                new TwoFactorDisableRequestDto("secret123", code),
                mock(HttpServletRequest.class)
        );

        assertThat(res.enabled()).isFalse();
        assertThat(u.isTotpEnabled()).isFalse();
        assertThat(u.getTotpSecret()).isNull();
    }

    @Test
    void validateStepUp_acceptsBackupCodeAndConsumesIt() {
        User u = testUser(1L, "aarav@example.com");
        u.setTotpSecret(TotpUtils.generateSecret());
        u.setTotpEnabled(true);
        u.setTotpBackupCodes("1234-5678,9999-0000");

        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        boolean valid = twoFactorAuthService.validateStepUp(u, "1234-5678");
        assertThat(valid).isTrue();
        assertThat(u.getTotpBackupCodes()).isEqualTo("9999-0000");

        // Attempting to reuse same backup code must now fail
        boolean secondAttempt = twoFactorAuthService.validateStepUp(u, "1234-5678");
        assertThat(secondAttempt).isFalse();
    }
}
