package com.dhanpay.service;

import com.dhanpay.dto.auth.TwoFactorDisableRequestDto;
import com.dhanpay.dto.auth.TwoFactorSetupResponseDto;
import com.dhanpay.dto.auth.TwoFactorStatusDto;
import com.dhanpay.entity.User;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.exception.ConflictException;
import com.dhanpay.exception.ResourceNotFoundException;
import com.dhanpay.qr.QrCodeGenerator;
import com.dhanpay.repository.UserRepository;
import com.dhanpay.security.TotpUtils;
import com.dhanpay.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service managing RFC 6238 TOTP two-factor enrollment, verification, and step-up enforcement.
 */
@Service
public class TwoFactorAuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditService auditService;

    public TwoFactorAuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditService auditService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public TwoFactorStatusDto getStatus(UserPrincipal principal) {
        User user = requireUser(principal.getId());
        int remainingCodes = parseBackupCodes(user.getTotpBackupCodes()).size();
        return new TwoFactorStatusDto(user.isTotpEnabled(), remainingCodes);
    }

    @Transactional
    public TwoFactorSetupResponseDto startSetup(UserPrincipal principal) {
        User user = requireUser(principal.getId());
        if (user.isTotpEnabled()) {
            throw new ConflictException("2FA_ALREADY_ENABLED", "Two-factor authentication is already active on your account");
        }

        String secret = TotpUtils.generateSecret();
        List<String> backupCodes = TotpUtils.generateBackupCodes(6);

        user.setTotpSecret(secret);
        user.setTotpBackupCodes(String.join(",", backupCodes));
        user.setTotpEnabled(false);
        userRepository.save(user);

        String encodedEmail = URLEncoder.encode(user.getEmail(), StandardCharsets.UTF_8);
        String otpauthUri = "otpauth://totp/DhanPay:" + encodedEmail + "?secret=" + secret + "&issuer=DhanPay";

        boolean[][] qrMatrix = QrCodeGenerator.encode(otpauthUri);
        String qrSvg = QrCodeGenerator.toSvg(qrMatrix, 4, "#05334D", "#FFFFFF");

        return new TwoFactorSetupResponseDto(secret, otpauthUri, qrSvg, backupCodes);
    }

    @Transactional
    public TwoFactorStatusDto enable(UserPrincipal principal, String code, HttpServletRequest httpRequest) {
        User user = requireUser(principal.getId());
        if (user.isTotpEnabled()) {
            throw new ConflictException("2FA_ALREADY_ENABLED", "Two-factor authentication is already active");
        }
        if (user.getTotpSecret() == null) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "2FA_SETUP_REQUIRED", "Please initiate 2FA setup first");
        }

        if (!TotpUtils.verifyCode(user.getTotpSecret(), code)) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_2FA_CODE", "Invalid 6-digit verification code. Please check your authenticator clock.");
        }

        user.setTotpEnabled(true);
        userRepository.save(user);

        auditService.logAction(user.getId(), "2FA_ENABLED", "SECURITY", user.getEmail(), "{\"action\":\"ENABLED\"}", httpRequest);
        int remaining = parseBackupCodes(user.getTotpBackupCodes()).size();
        return new TwoFactorStatusDto(true, remaining);
    }

    @Transactional
    public TwoFactorStatusDto disable(UserPrincipal principal, TwoFactorDisableRequestDto request, HttpServletRequest httpRequest) {
        User user = requireUser(principal.getId());
        if (!user.isTotpEnabled()) {
            throw new ConflictException("2FA_NOT_ENABLED", "Two-factor authentication is not currently active");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "INVALID_PASSWORD", "Account password verification failed");
        }

        if (!validateOrConsumeCode(user, request.code())) {
            throw new BusinessException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_2FA_CODE", "Invalid authenticator or backup recovery code");
        }

        user.setTotpEnabled(false);
        user.setTotpSecret(null);
        user.setTotpBackupCodes(null);
        userRepository.save(user);

        auditService.logAction(user.getId(), "2FA_DISABLED", "SECURITY", user.getEmail(), "{\"action\":\"DISABLED\"}", httpRequest);
        return new TwoFactorStatusDto(false, 0);
    }

    @Transactional
    public boolean validateStepUp(User user, String providedCode) {
        if (!user.isTotpEnabled()) {
            return true;
        }
        if (providedCode == null || providedCode.isBlank()) {
            return false;
        }
        return validateOrConsumeCode(user, providedCode);
    }

    private boolean validateOrConsumeCode(User user, String code) {
        String trimmed = code.trim();
        if (TotpUtils.verifyCode(user.getTotpSecret(), trimmed)) {
            return true;
        }

        // Check if matching backup code exists
        List<String> codes = parseBackupCodes(user.getTotpBackupCodes());
        if (codes.contains(trimmed)) {
            codes.remove(trimmed);
            user.setTotpBackupCodes(String.join(",", codes));
            userRepository.save(user);
            return true;
        }
        return false;
    }

    private User requireUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found: " + userId));
    }

    private static List<String> parseBackupCodes(String stored) {
        if (stored == null || stored.isBlank()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(stored.split(",")));
    }
}
