package com.dhanpay.controller;

import com.dhanpay.dto.transfer.TransferHistoryItemDto;
import com.dhanpay.dto.transfer.TransferJournalDto;
import com.dhanpay.dto.transfer.TransferRequestDto;
import com.dhanpay.dto.transfer.TransferResponseDto;
import com.dhanpay.entity.TransferStatus;
import com.dhanpay.security.UserPrincipal;
import com.dhanpay.service.TransferService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints for initiating and tracking idempotent peer-to-peer wallet transfers.
 */
@RestController
@RequestMapping("/api/transfers")
@Tag(name = "Transfers", description = "Fund transfer operations with database-backed idempotency")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @Operation(
            summary = "Execute fund transfer",
            description = "Transfers funds between wallets with pessimistic row locking and database-backed idempotency"
    )
    public ResponseEntity<TransferResponseDto> transfer(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,

            @Parameter(description = "Client-supplied idempotency key (UUID or alphanumeric)", required = true)
            @RequestHeader("Idempotency-Key") String idempotencyKey,

            @Valid @RequestBody TransferRequestDto request,
            HttpServletRequest httpRequest) {
        TransferResponseDto response = transferService.executeTransfer(principal, idempotencyKey, request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(
            summary = "Get transfer history",
            description = "Returns paginated transfer history for the authenticated customer's wallet with search, status, and direction filters"
    )
    public ResponseEntity<Page<TransferHistoryItemDto>> history(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,

            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) TransferStatus status,
            @RequestParam(required = false) String direction) {
        return ResponseEntity.ok(transferService.getTransferHistory(principal, page, size, search, status, direction));
    }

    @GetMapping("/{reference}/journal")
    @Operation(
            summary = "Inspect double-entry journal",
            description = "Returns the balanced DEBIT and CREDIT ledger postings for a settled transfer"
    )
    public ResponseEntity<TransferJournalDto> journal(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String reference) {
        return ResponseEntity.ok(transferService.getTransferJournal(principal, reference));
    }
}
