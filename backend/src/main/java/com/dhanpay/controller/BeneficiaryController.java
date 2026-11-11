package com.dhanpay.controller;

import com.dhanpay.dto.beneficiary.BeneficiaryDto;
import com.dhanpay.dto.beneficiary.CreateBeneficiaryRequestDto;
import com.dhanpay.security.UserPrincipal;
import com.dhanpay.service.BeneficiaryService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Endpoints for managing saved recipient wallet contacts.
 */
@RestController
@RequestMapping("/api/beneficiaries")
@Tag(name = "Beneficiaries", description = "Saved recipient wallet contacts for quick transfers")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    public BeneficiaryController(BeneficiaryService beneficiaryService) {
        this.beneficiaryService = beneficiaryService;
    }

    @GetMapping
    @Operation(summary = "List saved beneficiaries")
    public ResponseEntity<List<BeneficiaryDto>> list(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(beneficiaryService.listBeneficiaries(principal));
    }

    @PostMapping
    @Operation(summary = "Save a recipient wallet contact")
    public ResponseEntity<BeneficiaryDto> create(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateBeneficiaryRequestDto request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(beneficiaryService.addBeneficiary(principal, request, httpRequest));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a saved beneficiary contact")
    public ResponseEntity<Void> delete(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id,
            HttpServletRequest httpRequest) {
        beneficiaryService.removeBeneficiary(principal, id, httpRequest);
        return ResponseEntity.noContent().build();
    }
}
