package com.dhanpay.service;

import com.dhanpay.dto.recurring.CreateRecurringTransferRequestDto;
import com.dhanpay.dto.recurring.RecurringTransferItemDto;
import com.dhanpay.dto.transfer.TransferRequestDto;
import com.dhanpay.entity.RecurringFrequency;
import com.dhanpay.entity.RecurringStatus;
import com.dhanpay.entity.RecurringTransfer;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.exception.ResourceNotFoundException;
import com.dhanpay.repository.RecurringTransferRepository;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Service managing automated standing order recurring transfer schedules and background execution.
 */
@Service
public class RecurringTransferService {

    private static final Logger log = LoggerFactory.getLogger(RecurringTransferService.class);
    private static final int MONETARY_SCALE = 4;

    private final RecurringTransferRepository recurringTransferRepository;
    private final WalletRepository walletRepository;
    private final TransferService transferService;
    private final AuditService auditService;
    private NotificationStreamService notificationStreamService;

    public RecurringTransferService(
            RecurringTransferRepository recurringTransferRepository,
            WalletRepository walletRepository,
            TransferService transferService,
            AuditService auditService) {
        this.recurringTransferRepository = recurringTransferRepository;
        this.walletRepository = walletRepository;
        this.transferService = transferService;
        this.auditService = auditService;
    }

    @Autowired(required = false)
    public void setNotificationStreamService(NotificationStreamService notificationStreamService) {
        this.notificationStreamService = notificationStreamService;
    }

    @Transactional(readOnly = true)
    public List<RecurringTransferItemDto> listMyRecurring(UserPrincipal principal) {
        Wallet wallet = requireCallerWallet(principal.getId());
        return recurringTransferRepository.findAllInvolvingWallet(wallet.getId()).stream()
                .map(r -> toDto(r, wallet.getId()))
                .toList();
    }

    @Transactional
    public RecurringTransferItemDto createRule(
            UserPrincipal principal,
            CreateRecurringTransferRequestDto request,
            HttpServletRequest httpRequest) {
        Wallet senderWallet = requireCallerWallet(principal.getId());
        if (senderWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "WALLET_INACTIVE", "Your wallet is currently " + senderWallet.getStatus());
        }

        String recipientRef = request.recipientWalletReference().trim().toUpperCase();
        Wallet recipientWallet = walletRepository.findByWalletReference(recipientRef)
                .orElseThrow(() -> new ResourceNotFoundException("RECIPIENT_NOT_FOUND", "Recipient wallet not found: " + recipientRef));

        if (Objects.equals(senderWallet.getId(), recipientWallet.getId())) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "SELF_TRANSFER_PROHIBITED", "Cannot set recurring transfers to yourself");
        }
        if (recipientWallet.getStatus() != WalletStatus.ACTIVE) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "RECIPIENT_INACTIVE", "Recipient wallet is currently inactive");
        }

        LocalDate startDate = request.startDate() != null ? request.startDate() : LocalDate.now(ZoneOffset.UTC);
        if (request.endDate() != null && request.endDate().isBefore(startDate)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "INVALID_DATE_RANGE", "End date cannot be earlier than start date");
        }

        Instant nextExecution = startDate.atStartOfDay(ZoneOffset.UTC).toInstant();
        if (nextExecution.isBefore(Instant.now())) {
            nextExecution = Instant.now();
        }

        String reference = "REC-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
        BigDecimal amount = request.amount().setScale(MONETARY_SCALE, RoundingMode.HALF_EVEN);
        String description = (request.description() == null || request.description().isBlank())
                ? "Recurring " + request.frequency() + " Transfer"
                : request.description().trim();

        RecurringTransfer rule = new RecurringTransfer(
                reference,
                senderWallet,
                recipientWallet,
                amount,
                senderWallet.getCurrency(),
                request.frequency(),
                description,
                startDate,
                request.endDate(),
                nextExecution,
                request.maxExecutions()
        );

        RecurringTransfer saved = recurringTransferRepository.save(rule);
        auditService.logAction(principal.getId(), "RECURRING_RULE_CREATED", "RECURRING", reference,
                "{\"frequency\":\"" + request.frequency() + "\",\"amount\":\"" + amount + "\"}", httpRequest);

        return toDto(saved, senderWallet.getId());
    }

    @Transactional
    public RecurringTransferItemDto pauseRule(UserPrincipal principal, String reference) {
        RecurringTransfer rule = requireRuleOwnedBy(principal.getId(), reference);
        rule.setStatus(RecurringStatus.PAUSED);
        return toDto(recurringTransferRepository.save(rule), rule.getSenderWallet().getId());
    }

    @Transactional
    public RecurringTransferItemDto resumeRule(UserPrincipal principal, String reference) {
        RecurringTransfer rule = requireRuleOwnedBy(principal.getId(), reference);
        rule.setStatus(RecurringStatus.ACTIVE);
        if (rule.getNextExecutionAt().isBefore(Instant.now())) {
            rule.setNextExecutionAt(Instant.now());
        }
        rule.resetFailureCount();
        return toDto(recurringTransferRepository.save(rule), rule.getSenderWallet().getId());
    }

    @Transactional
    public RecurringTransferItemDto cancelRule(UserPrincipal principal, String reference) {
        RecurringTransfer rule = requireRuleOwnedBy(principal.getId(), reference);
        rule.setStatus(RecurringStatus.CANCELLED);
        return toDto(recurringTransferRepository.save(rule), rule.getSenderWallet().getId());
    }

    @Transactional
    public int executeDueRules() {
        List<RecurringTransfer> due = recurringTransferRepository.findDueTransfers(RecurringStatus.ACTIVE, Instant.now());
        if (due.isEmpty()) {
            return 0;
        }

        int processed = 0;
        for (RecurringTransfer rule : due) {
            String idempotencyKey = "REC-" + rule.getReference() + "-" + (rule.getTotalExecutions() + 1);
            try {
                TransferRequestDto transferReq = new TransferRequestDto(
                        rule.getRecipientWallet().getWalletReference(),
                        rule.getAmount(),
                        rule.getDescription()
                );

                transferService.executeTransfer(
                        UserPrincipal.from(rule.getSenderWallet().getUser()),
                        idempotencyKey,
                        transferReq,
                        null
                );

                rule.incrementTotalExecutions();
                rule.setLastExecutedAt(Instant.now());
                rule.resetFailureCount();
                rule.setNextExecutionAt(computeNextExecution(rule.getNextExecutionAt(), rule.getFrequency()));

                if (rule.getMaxExecutions() != null && rule.getTotalExecutions() >= rule.getMaxExecutions()) {
                    rule.setStatus(RecurringStatus.COMPLETED);
                } else if (rule.getEndDate() != null && LocalDate.now(ZoneOffset.UTC).isAfter(rule.getEndDate())) {
                    rule.setStatus(RecurringStatus.COMPLETED);
                }

                recurringTransferRepository.save(rule);
                processed++;

                if (notificationStreamService != null) {
                    notificationStreamService.sendToUser(
                            rule.getSenderWallet().getUser().getId(),
                            "RECURRING_EXECUTED",
                            "Standing Order Executed",
                            "Recurring payment of ₹" + rule.getAmount().setScale(2, RoundingMode.HALF_EVEN)
                                    + " was sent to " + rule.getRecipientWallet().getUser().getFullName(),
                            Map.of("reference", rule.getReference(), "amount", rule.getAmount())
                    );
                }
            } catch (Exception ex) {
                log.warn("Recurring execution failed for {}: {}", rule.getReference(), ex.getMessage());
                rule.recordFailure(ex.getMessage());
                if (rule.getFailureCount() >= 3) {
                    rule.setStatus(RecurringStatus.PAUSED);
                    log.error("Recurring rule {} auto-paused after 3 consecutive failures", rule.getReference());
                } else {
                    rule.setNextExecutionAt(Instant.now().plusSeconds(3600)); // Retry in 1 hour
                }
                recurringTransferRepository.save(rule);
            }
        }
        return processed;
    }

    private static Instant computeNextExecution(Instant from, RecurringFrequency freq) {
        ZonedDateTime zdt = from.atZone(ZoneOffset.UTC);
        return switch (freq) {
            case DAILY -> zdt.plusDays(1).toInstant();
            case WEEKLY -> zdt.plusWeeks(1).toInstant();
            case MONTHLY -> zdt.plusMonths(1).toInstant();
        };
    }

    private Wallet requireCallerWallet(Long userId) {
        return walletRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet not found for authenticated user"));
    }

    private RecurringTransfer requireRuleOwnedBy(Long userId, String reference) {
        RecurringTransfer rule = recurringTransferRepository.findByReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException("RECURRING_NOT_FOUND", "Standing order not found: " + reference));
        if (!Objects.equals(rule.getSenderWallet().getUser().getId(), userId)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "You do not own this standing order");
        }
        return rule;
    }

    private static RecurringTransferItemDto toDto(RecurringTransfer r, Long viewerWalletId) {
        String role = Objects.equals(r.getSenderWallet().getId(), viewerWalletId) ? "SENDER" : "RECIPIENT";
        return new RecurringTransferItemDto(
                r.getReference(),
                role,
                r.getSenderWallet().getWalletReference(),
                r.getSenderWallet().getUser().getFullName(),
                r.getRecipientWallet().getWalletReference(),
                r.getRecipientWallet().getUser().getFullName(),
                r.getAmount(),
                r.getCurrency(),
                r.getFrequency(),
                r.getDescription(),
                r.getStatus(),
                r.getStartDate(),
                r.getEndDate(),
                r.getNextExecutionAt(),
                r.getLastExecutedAt(),
                r.getTotalExecutions(),
                r.getMaxExecutions(),
                r.getFailureCount(),
                r.getLastFailureReason(),
                r.getCreatedAt()
        );
    }
}
