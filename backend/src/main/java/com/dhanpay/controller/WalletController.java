package com.dhanpay.controller;

import com.dhanpay.dto.wallet.WalletLimitsDto;
import com.dhanpay.dto.wallet.WalletSummaryDto;
import com.dhanpay.dto.wallet.WalletTopUpRequestDto;
import com.dhanpay.dto.wallet.WalletTopUpResponseDto;
import com.dhanpay.security.UserPrincipal;
import com.dhanpay.service.TransferService;
import com.dhanpay.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Wallet inspection and self-service funding endpoints for authenticated DhanPay customers.
 */
@RestController
@RequestMapping("/api/wallets")
@Tag(name = "Wallets", description = "Customer wallet balance, top-up, and metadata endpoints")
public class WalletController {

    private final WalletService walletService;
    private final TransferService transferService;

    public WalletController(WalletService walletService, TransferService transferService) {
        this.walletService = walletService;
        this.transferService = transferService;
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user's wallet", description = "Returns materialized wallet balance, status, and linked ledger account")
    public ResponseEntity<WalletSummaryDto> getMyWallet(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(walletService.getMyWallet(principal));
    }

    @GetMapping("/limits")
    @Operation(summary = "Get rolling transfer limits", description = "Returns single transfer cap and rolling 24-hour debit usage")
    public ResponseEntity<WalletLimitsDto> getLimits(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(transferService.getTransferLimits(principal));
    }

    @PostMapping("/topup")
    @Operation(summary = "Fund current user's wallet", description = "Credits customer wallet from system holding GL with idempotency protection")
    public ResponseEntity<WalletTopUpResponseDto> topUpWallet(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,

            @Parameter(description = "Client-supplied idempotency key (UUID or alphanumeric)", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,

            @Valid @RequestBody WalletTopUpRequestDto request,
            HttpServletRequest httpRequest) {
        WalletTopUpResponseDto response = walletService.topUpWallet(principal, idempotencyKey, request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
