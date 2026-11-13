package com.dhanpay.controller;

import com.dhanpay.dto.escrow.CreateEscrowHoldRequestDto;
import com.dhanpay.dto.escrow.EscrowHoldItemDto;
import com.dhanpay.security.UserPrincipal;
import com.dhanpay.service.EscrowService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Escrow hold, release, and refund REST endpoints.
 */
@RestController
@RequestMapping("/api/escrow")
@Tag(name = "Escrow", description = "Lock funds in platform escrow and release to seller or refund to buyer")
public class EscrowController {

    private final EscrowService escrowService;

    public EscrowController(EscrowService escrowService) {
        this.escrowService = escrowService;
    }

    @GetMapping
    @Operation(summary = "List escrow holds", description = "Lists all escrow holds where the caller is the buyer or seller")
    public ResponseEntity<List<EscrowHoldItemDto>> listMyEscrowHolds(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(escrowService.listMyEscrowHolds(principal));
    }

    @PostMapping
    @Operation(summary = "Create escrow hold", description = "Debits the buyer wallet and locks funds into GL-SYS-ESCROW-INR-0001")
    public ResponseEntity<EscrowHoldItemDto> createHold(
            @Valid @RequestBody CreateEscrowHoldRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(escrowService.createHold(principal, request, httpRequest));
    }

    @PostMapping("/{reference}/release")
    @Operation(summary = "Release escrow to seller", description = "Releases held funds from escrow into the seller wallet (buyer or admin)")
    public ResponseEntity<EscrowHoldItemDto> releaseHold(
            @PathVariable String reference,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(escrowService.releaseHold(principal, reference, httpRequest));
    }

    @PostMapping("/{reference}/refund")
    @Operation(summary = "Refund escrow to buyer", description = "Refunds held funds from escrow back into the buyer wallet (seller or admin)")
    public ResponseEntity<EscrowHoldItemDto> refundHold(
            @PathVariable String reference,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(escrowService.refundHold(principal, reference, httpRequest));
    }
}
