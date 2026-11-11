package com.dhanpay.controller;

import com.dhanpay.dto.auth.AuthResponse;
import com.dhanpay.dto.auth.ChangePasswordRequestDto;
import com.dhanpay.dto.auth.LoginRequest;
import com.dhanpay.dto.auth.RegisterRequest;
import com.dhanpay.security.UserPrincipal;
import com.dhanpay.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Public authentication endpoints for customer registration and JWT login.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Customer registration and JWT login endpoints")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @Operation(summary = "Register a new customer", description = "Creates user, wallet, and customer ledger account atomically")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user", description = "Verifies credentials and issues a stateless JWT Bearer token")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @PatchMapping("/password")
    @Operation(summary = "Change password", description = "Updates the authenticated user's password after verifying the current password")
    public ResponseEntity<Map<String, String>> changePassword(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody ChangePasswordRequestDto request,
            HttpServletRequest httpRequest) {
        authService.changePassword(principal, request, httpRequest);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully"));
    }
}
