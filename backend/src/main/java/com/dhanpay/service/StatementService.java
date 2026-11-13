package com.dhanpay.service;

import com.dhanpay.dto.statement.StatementAccountDetailsDto;
import com.dhanpay.dto.statement.StatementLineItemDto;
import com.dhanpay.dto.statement.StatementPeriod;
import com.dhanpay.dto.statement.StatementSummaryDto;
import com.dhanpay.dto.statement.WalletStatementDto;
import com.dhanpay.entity.Transfer;
import com.dhanpay.entity.Wallet;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.exception.ResourceNotFoundException;
import com.dhanpay.ledger.EntryDirection;
import com.dhanpay.ledger.LedgerAccount;
import com.dhanpay.ledger.LedgerAccountRepository;
import com.dhanpay.ledger.LedgerEntry;
import com.dhanpay.ledger.LedgerEntryRepository;
import com.dhanpay.ledger.LedgerTransaction;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.UserPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Generates tamper-evident certified account statements and printable ledger documents.
 */
@Service
public class StatementService {

    private static final int MONETARY_SCALE = 4;
    private static final ZoneId IST_ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DATETIME_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a", Locale.ENGLISH);
    private static final String ISSUER_STRING = "DhanPay Technologies Pvt Ltd - RBI Licensed Prepaid Payment Instrument (PPI)";
    private static final String ALPHABET = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final WalletRepository walletRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public StatementService(
            WalletRepository walletRepository,
            LedgerAccountRepository ledgerAccountRepository,
            LedgerEntryRepository ledgerEntryRepository) {
        this.walletRepository = walletRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
    }

    @Transactional(readOnly = true)
    public WalletStatementDto generateStatement(
            UserPrincipal principal,
            LocalDate startDate,
            LocalDate endDate,
            StatementPeriod period) {
        Wallet wallet = walletRepository.findByUserId(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet not found for user"));

        return generateStatementForWallet(wallet, startDate, endDate, period);
    }

    @Transactional(readOnly = true)
    public WalletStatementDto generateStatementForWallet(
            Wallet wallet,
            LocalDate startDate,
            LocalDate endDate,
            StatementPeriod period) {
        // Resolve date boundaries
        LocalDate[] range = resolveDateRange(startDate, endDate, period);
        LocalDate start = range[0];
        LocalDate end = range[1];

        if (start.isAfter(end)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "Start date cannot be after end date");
        }
        if (start.plusYears(1).isBefore(end)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "DATE_RANGE_TOO_WIDE", "Statement date range cannot exceed 365 days");
        }

        Instant startInstant = start.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endInstant = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).minusNanos(1).toInstant();

        LedgerAccount ledgerAccount = ledgerAccountRepository.findByWalletId(wallet.getId())
                .orElseThrow(() -> new ResourceNotFoundException("LEDGER_ACCOUNT_NOT_FOUND", "Ledger account not found for wallet"));

        // 1. Opening balance before startInstant
        BigDecimal openingCredits = scale(ledgerEntryRepository.sumAmountByAccountIdAndDirectionBefore(
                ledgerAccount.getId(), EntryDirection.CREDIT, startInstant));
        BigDecimal openingDebits = scale(ledgerEntryRepository.sumAmountByAccountIdAndDirectionBefore(
                ledgerAccount.getId(), EntryDirection.DEBIT, startInstant));
        BigDecimal openingBalance = openingCredits.subtract(openingDebits).setScale(MONETARY_SCALE, RoundingMode.HALF_UP);

        // 2. Fetch period ledger entries
        List<LedgerEntry> entries = ledgerEntryRepository.findEntriesForAccountBetween(
                ledgerAccount.getId(), startInstant, endInstant);

        BigDecimal totalCredits = BigDecimal.ZERO.setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY);
        BigDecimal totalDebits = BigDecimal.ZERO.setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY);
        BigDecimal runningBalance = openingBalance;

        List<StatementLineItemDto> lineItems = new ArrayList<>(entries.size());

        for (LedgerEntry entry : entries) {
            BigDecimal amt = scale(entry.getAmount());
            BigDecimal debitAmt = null;
            BigDecimal creditAmt = null;

            if (entry.getDirection() == EntryDirection.CREDIT) {
                creditAmt = amt;
                totalCredits = totalCredits.add(amt);
                runningBalance = runningBalance.add(amt);
            } else {
                debitAmt = amt;
                totalDebits = totalDebits.add(amt);
                runningBalance = runningBalance.subtract(amt);
            }

            LedgerTransaction lt = entry.getLedgerTransaction();
            Transfer transfer = lt.getTransfer();

            String type;
            String counterpartyRef = null;
            String counterpartyName = null;
            String description;

            if (transfer != null) {
                boolean isSender = wallet.getId().equals(transfer.getSenderWallet().getId());
                if (isSender) {
                    type = "TRANSFER_OUT";
                    counterpartyRef = transfer.getRecipientWallet().getWalletReference();
                    counterpartyName = transfer.getRecipientWallet().getUser().getFullName();
                    description = transfer.getDescription() != null ? transfer.getDescription() : "Transfer to " + counterpartyName;
                } else {
                    type = "TRANSFER_IN";
                    counterpartyRef = transfer.getSenderWallet().getWalletReference();
                    counterpartyName = transfer.getSenderWallet().getUser().getFullName();
                    description = transfer.getDescription() != null ? transfer.getDescription() : "Transfer from " + counterpartyName;
                }
            } else {
                type = lt.getTransactionType().name();
                counterpartyRef = "DHANPAY-RESERVE";
                counterpartyName = "DhanPay Central Treasury";
                description = lt.getTransactionType() + " [" + lt.getReference() + "]";
            }

            lineItems.add(new StatementLineItemDto(
                    entry.getId(),
                    entry.getCreatedAt(),
                    lt.getReference(),
                    type,
                    counterpartyRef,
                    counterpartyName,
                    description,
                    debitAmt,
                    creditAmt,
                    runningBalance
            ));
        }

        BigDecimal netMovement = totalCredits.subtract(totalDebits).setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
        BigDecimal closingBalance = openingBalance.add(netMovement).setScale(MONETARY_SCALE, RoundingMode.HALF_UP);

        Instant now = Instant.now();
        String stmtRef = generateStatementReference(start);

        StatementAccountDetailsDto accountDetails = new StatementAccountDetailsDto(
                wallet.getUser().getFullName(),
                wallet.getUser().getEmail(),
                wallet.getWalletReference(),
                ledgerAccount.getAccountNumber(),
                wallet.getCurrency(),
                wallet.getStatus().name()
        );

        StatementSummaryDto summary = new StatementSummaryDto(
                start,
                end,
                openingBalance,
                totalDebits,
                totalCredits,
                netMovement,
                closingBalance,
                lineItems.size()
        );

        String digitalHash = computeDigitalSeal(
                stmtRef,
                wallet.getWalletReference(),
                openingBalance,
                totalCredits,
                totalDebits,
                closingBalance,
                lineItems.size(),
                now
        );

        return new WalletStatementDto(
                stmtRef,
                now,
                accountDetails,
                summary,
                lineItems,
                digitalHash,
                ISSUER_STRING
        );
    }

    private LocalDate[] resolveDateRange(LocalDate startDate, LocalDate endDate, StatementPeriod period) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (startDate != null && endDate != null) {
            return new LocalDate[]{startDate, endDate};
        }

        StatementPeriod effectivePeriod = period != null ? period : StatementPeriod.CURRENT_MONTH;
        return switch (effectivePeriod) {
            case CURRENT_MONTH -> new LocalDate[]{today.withDayOfMonth(1), today};
            case LAST_MONTH -> {
                LocalDate lastMonthDay = today.minusMonths(1);
                yield new LocalDate[]{lastMonthDay.withDayOfMonth(1), lastMonthDay.withDayOfMonth(lastMonthDay.lengthOfMonth())};
            }
            case LAST_30_DAYS -> new LocalDate[]{today.minusDays(30), today};
            case LAST_90_DAYS -> new LocalDate[]{today.minusDays(90), today};
            case FINANCIAL_YEAR -> {
                int year = (today.getMonthValue() >= 4) ? today.getYear() : today.getYear() - 1;
                yield new LocalDate[]{LocalDate.of(year, 4, 1), today};
            }
            case CUSTOM -> {
                LocalDate start = (startDate != null) ? startDate : today.withDayOfMonth(1);
                LocalDate end = (endDate != null) ? endDate : today;
                yield new LocalDate[]{start, end};
            }
        };
    }

    private String generateStatementReference(LocalDate start) {
        String yearMonth = DateTimeFormatter.ofPattern("yyyyMM").format(start);
        StringBuilder sb = new StringBuilder("STMT-IND-").append(yearMonth).append("-");
        for (int i = 0; i < 6; i++) {
            sb.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private String computeDigitalSeal(
            String stmtRef,
            String walletRef,
            BigDecimal opening,
            BigDecimal credits,
            BigDecimal debits,
            BigDecimal closing,
            int txCount,
            Instant generatedAt) {
        String raw = String.format(Locale.ROOT, "%s|%s|%s|%s|%s|%s|%d|%d",
                stmtRef, walletRef, opening.toPlainString(), credits.toPlainString(),
                debits.toPlainString(), closing.toPlainString(), txCount, generatedAt.toEpochMilli());
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private BigDecimal scale(BigDecimal v) {
        if (v == null) {
            return BigDecimal.ZERO.setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY);
        }
        return v.setScale(MONETARY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Generates a self-contained, printable HTML ledger statement with professional styling and RBI compliance stamps.
     */
    public String generatePrintableHtml(WalletStatementDto statement) {
        StatementAccountDetailsDto acc = statement.account();
        StatementSummaryDto s = statement.summary();

        StringBuilder rows = new StringBuilder();
        if (statement.transactions().isEmpty()) {
            rows.append("""
                    <tr>
                        <td colspan="7" style="text-align: center; padding: 24px; color: #64748B; font-style: italic;">
                            No transaction activity recorded during this period.
                        </td>
                    </tr>
                    """);
        } else {
            for (StatementLineItemDto t : statement.transactions()) {
                String dateStr = DATETIME_FMT.format(t.timestamp().atZone(IST_ZONE));
                String debitStr = t.debitAmount() != null ? "-₹" + t.debitAmount().setScale(2, RoundingMode.HALF_UP) : "-";
                String creditStr = t.creditAmount() != null ? "+₹" + t.creditAmount().setScale(2, RoundingMode.HALF_UP) : "-";
                String balStr = "₹" + t.balanceAfter().setScale(2, RoundingMode.HALF_UP);
                String debitColor = t.debitAmount() != null ? "#DC2626" : "#64748B";
                String creditColor = t.creditAmount() != null ? "#059669" : "#64748B";

                rows.append(String.format(Locale.ROOT, """
                        <tr>
                            <td style="padding: 10px 12px; border-bottom: 1px solid #E2E8F0; font-size: 12px; color: #475569;">%s</td>
                            <td style="padding: 10px 12px; border-bottom: 1px solid #E2E8F0; font-family: monospace; font-size: 11px; color: #05334D; font-weight: 600;">%s</td>
                            <td style="padding: 10px 12px; border-bottom: 1px solid #E2E8F0; font-size: 12px; color: #0F172A;">
                                <div style="font-weight: 600;">%s</div>
                                <div style="font-size: 11px; color: #64748B;">%s</div>
                            </td>
                            <td style="padding: 10px 12px; border-bottom: 1px solid #E2E8F0; font-size: 11px; color: #475569; font-family: monospace;">%s</td>
                            <td style="padding: 10px 12px; border-bottom: 1px solid #E2E8F0; font-size: 12px; text-align: right; color: %s; font-weight: 600;">%s</td>
                            <td style="padding: 10px 12px; border-bottom: 1px solid #E2E8F0; font-size: 12px; text-align: right; color: %s; font-weight: 600;">%s</td>
                            <td style="padding: 10px 12px; border-bottom: 1px solid #E2E8F0; font-size: 12px; text-align: right; font-weight: 700; color: #05334D;">%s</td>
                        </tr>
                        """,
                        dateStr,
                        escapeHtml(t.reference()),
                        escapeHtml(t.description()),
                        escapeHtml(t.type()),
                        escapeHtml(t.counterpartyWalletReference() != null ? t.counterpartyWalletReference() : "-"),
                        debitColor, debitStr,
                        creditColor, creditStr,
                        balStr
                ));
            }
        }

        String startDateStr = DATE_FMT.format(s.startDate());
        String endDateStr = DATE_FMT.format(s.endDate());
        String genDateStr = DATETIME_FMT.format(statement.generatedAt().atZone(IST_ZONE));

        return String.format(Locale.ROOT, """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                    <meta charset="UTF-8">
                    <title>DhanPay Certified Statement - %s</title>
                    <style>
                        @page { size: A4 portrait; margin: 16mm; }
                        body {
                            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
                            color: #0F172A;
                            margin: 0;
                            padding: 24px;
                            background: #F8FAFC;
                        }
                        .page-container {
                            max-width: 900px;
                            margin: 0 auto;
                            background: #FFFFFF;
                            padding: 36px 44px;
                            border-radius: 16px;
                            box-shadow: 0 4px 20px rgba(5, 51, 77, 0.08);
                            border: 1px solid #E2E8F0;
                        }
                        .header {
                            display: flex;
                            justify-content: space-between;
                            align-items: flex-start;
                            padding-bottom: 24px;
                            border-bottom: 2px solid #05334D;
                        }
                        .logo {
                            font-size: 26px;
                            font-weight: 900;
                            color: #05334D;
                            letter-spacing: -0.5px;
                        }
                        .logo span { color: #00D084; }
                        .rbi-badge {
                            display: inline-block;
                            background: #E6FBF5;
                            color: #00875A;
                            font-size: 11px;
                            font-weight: 700;
                            padding: 4px 10px;
                            border-radius: 20px;
                            margin-top: 6px;
                            letter-spacing: 0.3px;
                        }
                        .meta-block {
                            text-align: right;
                            font-size: 12px;
                            color: #475569;
                            line-height: 1.5;
                        }
                        .meta-block strong { color: #05334D; }
                        .info-grid {
                            display: grid;
                            grid-template-columns: 1fr 1fr;
                            gap: 20px;
                            margin: 28px 0;
                            padding: 20px;
                            background: #F1F9F8;
                            border-radius: 12px;
                            border: 1px solid #C6E8E8;
                        }
                        .info-col h3 {
                            font-size: 11px;
                            text-transform: uppercase;
                            letter-spacing: 0.8px;
                            color: #00875A;
                            margin: 0 0 10px 0;
                        }
                        .info-row {
                            display: flex;
                            justify-content: space-between;
                            font-size: 12px;
                            padding: 3px 0;
                        }
                        .info-label { color: #64748B; }
                        .info-val { font-weight: 600; color: #05334D; }
                        .summary-grid {
                            display: grid;
                            grid-template-columns: repeat(5, 1fr);
                            gap: 12px;
                            margin-bottom: 28px;
                        }
                        .summary-card {
                            background: #FFFFFF;
                            border: 1px solid #E2E8F0;
                            border-radius: 10px;
                            padding: 14px;
                            text-align: center;
                        }
                        .summary-card.highlight {
                            background: #05334D;
                            color: #FFFFFF;
                            border-color: #05334D;
                        }
                        .summary-card.highlight .summary-title { color: #8ED1FC; }
                        .summary-card.highlight .summary-amount { color: #FFFFFF; }
                        .summary-title {
                            font-size: 11px;
                            color: #64748B;
                            text-transform: uppercase;
                            letter-spacing: 0.5px;
                            margin-bottom: 6px;
                        }
                        .summary-amount {
                            font-size: 15px;
                            font-weight: 800;
                            color: #05334D;
                        }
                        table.transactions-table {
                            width: 100%%;
                            border-collapse: collapse;
                            margin-top: 10px;
                        }
                        table.transactions-table th {
                            background: #05334D;
                            color: #FFFFFF;
                            font-size: 11px;
                            text-transform: uppercase;
                            letter-spacing: 0.6px;
                            padding: 10px 12px;
                            text-align: left;
                        }
                        table.transactions-table th.num { text-align: right; }
                        .digital-seal-box {
                            margin-top: 36px;
                            padding: 18px 22px;
                            background: #F8FAFC;
                            border: 1px dashed #94A3B8;
                            border-radius: 10px;
                            font-size: 11px;
                            color: #475569;
                            line-height: 1.6;
                        }
                        .seal-hash {
                            font-family: monospace;
                            background: #E2E8F0;
                            padding: 4px 8px;
                            border-radius: 4px;
                            color: #05334D;
                            font-weight: 700;
                            word-break: break-all;
                            display: block;
                            margin-top: 6px;
                        }
                        .btn-print {
                            position: fixed;
                            bottom: 24px;
                            right: 24px;
                            background: #05334D;
                            color: #FFFFFF;
                            border: none;
                            padding: 12px 24px;
                            font-size: 14px;
                            font-weight: 700;
                            border-radius: 30px;
                            cursor: pointer;
                            box-shadow: 0 4px 14px rgba(5, 51, 77, 0.35);
                            transition: all 0.2s;
                        }
                        .btn-print:hover {
                            background: #00D084;
                            color: #05334D;
                        }
                        @media print {
                            body { background: #FFFFFF; padding: 0; }
                            .page-container { box-shadow: none; border: none; padding: 0; max-width: 100%%; }
                            .btn-print { display: none !important; }
                        }
                    </style>
                </head>
                <body>
                    <button class="btn-print" onclick="window.print()">Print / Save as PDF</button>

                    <div class="page-container">
                        <div class="header">
                            <div>
                                <div class="logo">Dhan<span>Pay</span></div>
                                <div style="font-size: 11px; color: #475569; margin-top: 2px;">Official Certified Account Statement</div>
                                <span class="rbi-badge">✓ RBI AUTHORIZED PPI</span>
                            </div>
                            <div class="meta-block">
                                <div><strong>Statement Ref:</strong> %s</div>
                                <div><strong>Statement Period:</strong> %s to %s</div>
                                <div><strong>Generated On:</strong> %s</div>
                                <div><strong>Currency:</strong> %s</div>
                            </div>
                        </div>

                        <div class="info-grid">
                            <div class="info-col">
                                <h3>Account Holder Details</h3>
                                <div class="info-row"><span class="info-label">Name:</span><span class="info-val">%s</span></div>
                                <div class="info-row"><span class="info-label">Email:</span><span class="info-val">%s</span></div>
                                <div class="info-row"><span class="info-label">Status:</span><span class="info-val">%s</span></div>
                            </div>
                            <div class="info-col">
                                <h3>Wallet & Ledger Details</h3>
                                <div class="info-row"><span class="info-label">Wallet Reference:</span><span class="info-val">%s</span></div>
                                <div class="info-row"><span class="info-label">General Ledger A/C:</span><span class="info-val">%s</span></div>
                                <div class="info-row"><span class="info-label">Transactions Count:</span><span class="info-val">%d</span></div>
                            </div>
                        </div>

                        <div class="summary-grid">
                            <div class="summary-card">
                                <div class="summary-title">Opening Balance</div>
                                <div class="summary-amount">₹%s</div>
                            </div>
                            <div class="summary-card">
                                <div class="summary-title">Total Inflow (+)</div>
                                <div class="summary-amount" style="color: #059669;">₹%s</div>
                            </div>
                            <div class="summary-card">
                                <div class="summary-title">Total Outflow (-)</div>
                                <div class="summary-amount" style="color: #DC2626;">₹%s</div>
                            </div>
                            <div class="summary-card">
                                <div class="summary-title">Net Movement</div>
                                <div class="summary-amount">₹%s</div>
                            </div>
                            <div class="summary-card highlight">
                                <div class="summary-title">Closing Balance</div>
                                <div class="summary-amount">₹%s</div>
                            </div>
                        </div>

                        <h3 style="font-size: 13px; text-transform: uppercase; letter-spacing: 0.6px; color: #05334D; margin: 24px 0 10px 0;">
                            Certified Transaction Journal
                        </h3>

                        <table class="transactions-table">
                            <thead>
                                <tr>
                                    <th>Date & Time (IST)</th>
                                    <th>Ref / ID</th>
                                    <th>Description & Type</th>
                                    <th>Counterparty</th>
                                    <th class="num">Debit</th>
                                    <th class="num">Credit</th>
                                    <th class="num">Balance</th>
                                </tr>
                            </thead>
                            <tbody>
                                %s
                            </tbody>
                        </table>

                        <div class="digital-seal-box">
                            <div style="font-weight: 700; color: #05334D; margin-bottom: 4px;">
                                🔒 Cryptographic Ledger Verification Seal
                            </div>
                            <div>
                                This statement has been generated from DhanPay's immutable, append-only double-entry financial ledger.
                                Any manual alteration of amounts, references, or balances invalidates the cryptographic checksum below:
                            </div>
                            <span class="seal-hash">SHA-256: %s</span>
                            <div style="margin-top: 10px; font-size: 10px; color: #64748B;">
                                Issued by: %s | Contact: support@dhanpay.in | This is a computer-generated authenticated document and requires no physical signature.
                            </div>
                        </div>
                    </div>
                </body>
                </html>
                """,
                statement.statementReference(),
                statement.statementReference(),
                startDateStr, endDateStr,
                genDateStr,
                acc.currency(),
                escapeHtml(acc.fullName()),
                escapeHtml(acc.email()),
                escapeHtml(acc.status()),
                escapeHtml(acc.walletReference()),
                escapeHtml(acc.ledgerAccountNumber() != null ? acc.ledgerAccountNumber() : "GL-LIAB-INR-0001"),
                s.totalTransactions(),
                s.openingBalance().setScale(2, RoundingMode.HALF_UP),
                s.totalCredits().setScale(2, RoundingMode.HALF_UP),
                s.totalDebits().setScale(2, RoundingMode.HALF_UP),
                s.netMovement().setScale(2, RoundingMode.HALF_UP),
                s.closingBalance().setScale(2, RoundingMode.HALF_UP),
                rows.toString(),
                statement.digitalVerificationHash(),
                statement.issuer()
        );
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
