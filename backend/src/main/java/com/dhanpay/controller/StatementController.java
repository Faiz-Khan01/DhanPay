package com.dhanpay.controller;

import com.dhanpay.dto.statement.StatementPeriod;
import com.dhanpay.dto.statement.WalletStatementDto;
import com.dhanpay.security.UserPrincipal;
import com.dhanpay.service.StatementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

/**
 * Controller providing tamper-evident certified account statements in JSON and printable HTML formats.
 */
@RestController
@RequestMapping("/api/wallets/statement")
@Tag(name = "Account Statement", description = "Certified double-entry ledger account statements and printable exports")
public class StatementController {

    private final StatementService statementService;

    public StatementController(StatementService statementService) {
        this.statementService = statementService;
    }

    @GetMapping
    @Operation(summary = "Get wallet account statement", description = "Generates certified statement with opening/closing balances and digital verification seal")
    public ResponseEntity<?> getStatement(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(required = false, defaultValue = "CURRENT_MONTH")
            StatementPeriod period,

            @RequestParam(required = false, defaultValue = "json")
            String format) {

        WalletStatementDto statement = statementService.generateStatement(principal, startDate, endDate, period);

        if ("html".equalsIgnoreCase(format)) {
            String html = statementService.generatePrintableHtml(statement);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"DhanPay_Statement_" + statement.statementReference() + ".html\"")
                    .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                    .body(html);
        }

        return ResponseEntity.ok(statement);
    }

    @GetMapping("/download")
    @Operation(summary = "Download printable statement", description = "Returns self-contained printable HTML document with DhanPay letterhead")
    public ResponseEntity<String> downloadStatement(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate endDate,

            @RequestParam(required = false, defaultValue = "CURRENT_MONTH")
            StatementPeriod period) {

        WalletStatementDto statement = statementService.generateStatement(principal, startDate, endDate, period);
        String html = statementService.generatePrintableHtml(statement);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"DhanPay_Statement_" + statement.statementReference() + ".html\"")
                .contentType(new MediaType("text", "html", StandardCharsets.UTF_8))
                .body(html);
    }
}
