package com.dhanpay.controller;

import com.dhanpay.dto.request.CreatePaymentRequestDto;
import com.dhanpay.dto.request.PaymentRequestItemDto;
import com.dhanpay.dto.transfer.TransferResponseDto;
import com.dhanpay.security.UserPrincipal;
import com.dhanpay.service.PaymentRequestService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints for peer-to-peer payment requests and settlement.
 */
@RestController
@RequestMapping("/api/requests")
@Tag(name = "Payment Requests", description = "Create, settle, and decline peer-to-peer INR requests")
public class PaymentRequestController {

    private final PaymentRequestService paymentRequestService;

    public PaymentRequestController(PaymentRequestService paymentRequestService) {
        this.paymentRequestService = paymentRequestService;
    }

    @GetMapping
    @Operation(summary = "List incoming and outgoing payment requests")
    public ResponseEntity<List<PaymentRequestItemDto>> list(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(paymentRequestService.listMyRequests(principal));
    }

    @PostMapping
    @Operation(summary = "Create a payment request")
    public ResponseEntity<PaymentRequestItemDto> create(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreatePaymentRequestDto request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentRequestService.createRequest(principal, request, httpRequest));
    }

    @PostMapping("/{reference}/pay")
    @Operation(summary = "Settle a pending payment request")
    public ResponseEntity<TransferResponseDto> pay(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String reference,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(paymentRequestService.payRequest(principal, reference, idempotencyKey, httpRequest));
    }

    @PostMapping("/{reference}/decline")
    @Operation(summary = "Decline or cancel a pending payment request")
    public ResponseEntity<PaymentRequestItemDto> declineOrCancel(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable String reference,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(paymentRequestService.declineOrCancel(principal, reference, httpRequest));
    }
}
