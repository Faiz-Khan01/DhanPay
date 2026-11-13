package com.dhanpay.controller;

import com.dhanpay.dto.admin.AdminStatsDto;
import com.dhanpay.dto.admin.AdminTransferItemDto;
import com.dhanpay.dto.admin.AdminUserWalletDto;
import com.dhanpay.dto.admin.AuditLogItemDto;
import com.dhanpay.dto.admin.FreezeWalletRequest;
import com.dhanpay.dto.admin.ReverseTransferRequestDto;
import com.dhanpay.entity.TransferStatus;
import com.dhanpay.ledger.ReconciliationReportDto;
import com.dhanpay.security.UserPrincipal;
import com.dhanpay.service.AdminService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Administrative monitoring, wallet governance, audit log, and reconciliation endpoints.
 */
@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Operations", description = "System monitoring, wallet controls, audit logs, and ledger reconciliation")
public class AdminController {

    private final AdminService adminService;

    public AdminController(AdminService adminService) {
        this.adminService = adminService;
    }

    @GetMapping("/stats")
    @Operation(summary = "Get system statistics", description = "Returns total users, active/frozen wallet counts, transfer volume, and failure counts")
    public ResponseEntity<AdminStatsDto> getStats() {
        return ResponseEntity.ok(adminService.getSystemStats());
    }

    @GetMapping("/users")
    @Operation(summary = "List registered customers", description = "Returns paginated customer accounts with materialized wallet balances and statuses")
    public ResponseEntity<Page<AdminUserWalletDto>> getUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(adminService.getCustomers(page, size, search));
    }

    @PatchMapping("/wallets/{id}/freeze")
    @Operation(summary = "Freeze a wallet", description = "Freezes a customer wallet and records the mandatory reason in the audit log")
    public ResponseEntity<AdminUserWalletDto> freezeWallet(
            @PathVariable Long id,
            @Valid @RequestBody FreezeWalletRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(adminService.freezeWallet(id, request.reason(), principal, httpRequest));
    }

    @PatchMapping("/wallets/{id}/unfreeze")
    @Operation(summary = "Unfreeze a wallet", description = "Restores an active state for a frozen customer wallet and writes an audit trail record")
    public ResponseEntity<AdminUserWalletDto> unfreezeWallet(
            @PathVariable Long id,
            @RequestBody(required = false) Map<String, String> body,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        String reason = body == null ? null : body.get("reason");
        return ResponseEntity.ok(adminService.unfreezeWallet(id, reason, principal, httpRequest));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "List system audit logs", description = "Returns paginated immutable security and operational audit records")
    public ResponseEntity<Page<AuditLogItemDto>> getAuditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(adminService.getAuditLogs(page, size));
    }

    @GetMapping("/transfers")
    @Operation(summary = "List platform transfers", description = "Returns paginated P2P transfers filterable by status and reference/wallet/user search")
    public ResponseEntity<Page<AdminTransferItemDto>> getTransfers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) String search) {
        return ResponseEntity.ok(adminService.getTransfers(page, size, status, search));
    }

    @PostMapping("/transfers/{reference}/reverse")
    @Operation(summary = "Reverse a settled transfer", description = "Posts compensating double-entry ledger entries, restores sender balance, debits recipient, and logs an audit entry")
    public ResponseEntity<AdminTransferItemDto> reverseTransfer(
            @PathVariable String reference,
            @Valid @RequestBody ReverseTransferRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(adminService.reverseTransfer(reference, request.reason(), principal, httpRequest));
    }

    @GetMapping("/reconciliation")
    @Operation(summary = "Run ledger reconciliation", description = "Triggers an on-demand full general ledger vs. materialized wallet balance audit")
    public ResponseEntity<ReconciliationReportDto> runReconciliation() {
        return ResponseEntity.ok(adminService.runReconciliation());
    }
}
