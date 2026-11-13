package com.dhanpay.controller;

import com.dhanpay.dto.auth.TwoFactorDisableRequestDto;
import com.dhanpay.dto.auth.TwoFactorEnableRequestDto;
import com.dhanpay.dto.auth.TwoFactorSetupResponseDto;
import com.dhanpay.dto.auth.TwoFactorStatusDto;
import com.dhanpay.security.UserPrincipal;
import com.dhanpay.service.TwoFactorAuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for RFC 6238 TOTP two-factor enrollment, verification, and revocation.
 */
@RestController
@RequestMapping("/api/auth/2fa")
@Tag(name = "Two-Factor Auth", description = "RFC 6238 TOTP authenticator setup, verification, and step-up security")
public class TwoFactorAuthController {

    private final TwoFactorAuthService twoFactorAuthService;

    public TwoFactorAuthController(TwoFactorAuthService twoFactorAuthService) {
        this.twoFactorAuthService = twoFactorAuthService;
    }

    @GetMapping("/status")
    @Operation(summary = "Get 2FA status", description = "Checks whether two-factor authentication is active for the authenticated user")
    public ResponseEntity<TwoFactorStatusDto> getStatus(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(twoFactorAuthService.getStatus(principal));
    }

    @PostMapping("/setup")
    @Operation(summary = "Start 2FA setup", description = "Generates a new base32 secret, SVG QR code, and backup recovery codes")
    public ResponseEntity<TwoFactorSetupResponseDto> startSetup(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(twoFactorAuthService.startSetup(principal));
    }

    @PostMapping("/enable")
    @Operation(summary = "Enable 2FA", description = "Verifies the initial 6-digit code from Google Authenticator / Authy and enables 2FA")
    public ResponseEntity<TwoFactorStatusDto> enable(
            @Valid @RequestBody TwoFactorEnableRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(twoFactorAuthService.enable(principal, request.code(), httpRequest));
    }

    @PostMapping("/disable")
    @Operation(summary = "Disable 2FA", description = "Revokes 2FA protection requiring account password and current code")
    public ResponseEntity<TwoFactorStatusDto> disable(
            @Valid @RequestBody TwoFactorDisableRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(twoFactorAuthService.disable(principal, request, httpRequest));
    }
}
