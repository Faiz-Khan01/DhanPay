package com.dhanpay.controller;

import com.dhanpay.dto.qr.GenerateQrRequestDto;
import com.dhanpay.dto.qr.ParseQrRequestDto;
import com.dhanpay.dto.qr.ParsedQrPayloadDto;
import com.dhanpay.dto.qr.QrCodeResponseDto;
import com.dhanpay.security.UserPrincipal;
import com.dhanpay.service.QrService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * UPI / DhanPay QR code generation and payload verification endpoints.
 */
@RestController
@RequestMapping("/api/qr")
@Tag(name = "QR Payments", description = "Dynamic QR generation and counterparty validation for Scan-to-Pay flows")
public class QrController {

    private final QrService qrService;

    public QrController(QrService qrService) {
        this.qrService = qrService;
    }

    @GetMapping("/my-code")
    @Operation(summary = "Get personal QR code", description = "Returns the caller's static UPI/DhanPay payment QR code as an SVG vector")
    public ResponseEntity<QrCodeResponseDto> getMyStaticQr(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(qrService.getMyStaticQr(principal));
    }

    @PostMapping("/generate")
    @Operation(summary = "Generate dynamic amount QR", description = "Generates a dynamic payment QR code with embedded amount and optional note")
    public ResponseEntity<QrCodeResponseDto> generateDynamicQr(
            @Valid @RequestBody(required = false) GenerateQrRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(qrService.generateDynamicQr(principal, request));
    }

    @PostMapping("/parse")
    @Operation(summary = "Verify scanned QR payload", description = "Extracts and verifies recipient wallet, suggested amount, and active status from a scanned QR string")
    public ResponseEntity<ParsedQrPayloadDto> parseQr(
            @Valid @RequestBody ParseQrRequestDto request,
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(qrService.parseAndValidateQr(principal, request.payload()));
    }
}
