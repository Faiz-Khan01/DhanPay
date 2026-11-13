package com.dhanpay.controller;

import com.dhanpay.dto.recurring.CreateRecurringTransferRequestDto;
import com.dhanpay.dto.recurring.RecurringTransferItemDto;
import com.dhanpay.security.UserPrincipal;
import com.dhanpay.service.RecurringTransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints for managing automated standing orders and recurring transfers.
 */
@RestController
@RequestMapping("/api/recurring")
@Tag(name = "Recurring Transfers", description = "Automated standing orders, subscriptions, and recurring SIPs")
public class RecurringTransferController {

    private final RecurringTransferService recurringTransferService;

    public RecurringTransferController(RecurringTransferService recurringTransferService) {
        this.recurringTransferService = recurringTransferService;
    }

    @GetMapping
    @Operation(summary = "List standing orders", description = "Returns all recurring transfer rules involving the authenticated wallet")
    public ResponseEntity<List<RecurringTransferItemDto>> listMyRecurring(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(recurringTransferService.listMyRecurring(principal));
    }

    @PostMapping
    @Operation(summary = "Create standing order", description = "Schedules an automated recurring transfer rule (DAILY, WEEKLY, MONTHLY)")
    public ResponseEntity<RecurringTransferItemDto> createRule(
            @Valid @RequestBody CreateRecurringTransferRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recurringTransferService.createRule(principal, request, httpRequest));
    }

    @PatchMapping("/{reference}/pause")
    @Operation(summary = "Pause standing order", description = "Temporarily suspends an active recurring transfer rule")
    public ResponseEntity<RecurringTransferItemDto> pauseRule(
            @PathVariable String reference,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(recurringTransferService.pauseRule(principal, reference));
    }

    @PatchMapping("/{reference}/resume")
    @Operation(summary = "Resume standing order", description = "Re-activates a paused recurring transfer rule")
    public ResponseEntity<RecurringTransferItemDto> resumeRule(
            @PathVariable String reference,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(recurringTransferService.resumeRule(principal, reference));
    }

    @DeleteMapping("/{reference}")
    @Operation(summary = "Cancel standing order", description = "Permanently cancels a recurring transfer rule")
    public ResponseEntity<RecurringTransferItemDto> cancelRule(
            @PathVariable String reference,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(recurringTransferService.cancelRule(principal, reference));
    }
}
