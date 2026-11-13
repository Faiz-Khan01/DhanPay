package com.dhanpay.service;

import com.dhanpay.dto.admin.AdminStatsDto;
import com.dhanpay.dto.admin.AdminTransferItemDto;
import com.dhanpay.dto.admin.AdminUserWalletDto;
import com.dhanpay.dto.admin.AuditLogItemDto;
import com.dhanpay.entity.AuditLog;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.Transfer;
import com.dhanpay.entity.TransferStatus;
import com.dhanpay.entity.User;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.exception.ConflictException;
import com.dhanpay.exception.ResourceNotFoundException;
import com.dhanpay.ledger.LedgerService;
import com.dhanpay.ledger.ReconciliationReportDto;
import com.dhanpay.ledger.ReconciliationService;
import com.dhanpay.repository.AuditLogRepository;
import com.dhanpay.repository.TransferRepository;
import com.dhanpay.repository.UserRepository;
import com.dhanpay.repository.WalletRepository;
import com.dhanpay.security.UserPrincipal;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Administrative operations service for KPI telemetry, wallet controls, audit logs, and ledger audits.
 */
@Service
public class AdminService {

    private static final int MONETARY_SCALE = 4;

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final TransferRepository transferRepository;
    private final AuditLogRepository auditLogRepository;
    private final AuditService auditService;
    private final LedgerService ledgerService;
    private final ReconciliationService reconciliationService;
    private final ObjectMapper objectMapper;
    private NotificationStreamService notificationStreamService;

    @Autowired(required = false)
    public void setNotificationStreamService(NotificationStreamService notificationStreamService) {
        this.notificationStreamService = notificationStreamService;
    }

    public AdminService(
            UserRepository userRepository,
            WalletRepository walletRepository,
            TransferRepository transferRepository,
            AuditLogRepository auditLogRepository,
            AuditService auditService,
            LedgerService ledgerService,
            ReconciliationService reconciliationService,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transferRepository = transferRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
        this.ledgerService = ledgerService;
        this.reconciliationService = reconciliationService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public AdminStatsDto getSystemStats() {
        long totalUsers = userRepository.count();
        long activeWallets = walletRepository.countByStatus(WalletStatus.ACTIVE);
        long frozenWallets = walletRepository.countByStatus(WalletStatus.FROZEN);

        BigDecimal volume = transferRepository.sumAmountByStatus(TransferStatus.SUCCESS);
        BigDecimal normalizedVolume = (volume == null ? BigDecimal.ZERO : volume)
                .setScale(MONETARY_SCALE, RoundingMode.HALF_EVEN);

        long successfulTransfers = transferRepository.countByStatus(TransferStatus.SUCCESS);
        long failedTransfers = transferRepository.countByStatus(TransferStatus.FAILED);

        return new AdminStatsDto(
                totalUsers,
                activeWallets,
                frozenWallets,
                normalizedVolume,
                successfulTransfers,
                failedTransfers
        );
    }

    @Transactional(readOnly = true)
    public Page<AdminUserWalletDto> getCustomers(int page, int size, String search) {
        Pageable pageable = buildPageable(page, size);
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();

        return walletRepository.findCustomerWallets(normalizedSearch, pageable)
                .map(AdminService::toAdminUserWalletDto);
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminUserWalletDto freezeWallet(
            Long walletId,
            String reason,
            UserPrincipal adminPrincipal,
            HttpServletRequest httpRequest) {
        if (reason == null || reason.isBlank()) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "FREEZE_REASON_REQUIRED", "A reason is required to freeze a wallet");
        }
        return transitionWalletStatus(
                walletId,
                WalletStatus.FROZEN,
                "WALLET_FREEZE",
                reason.trim(),
                adminPrincipal,
                httpRequest
        );
    }

    @Transactional(rollbackFor = Exception.class)
    public AdminUserWalletDto unfreezeWallet(
            Long walletId,
            String reason,
            UserPrincipal adminPrincipal,
            HttpServletRequest httpRequest) {
        String effectiveReason = (reason == null || reason.isBlank())
                ? "Administrative unfreeze approved"
                : reason.trim();
        return transitionWalletStatus(
                walletId,
                WalletStatus.ACTIVE,
                "WALLET_UNFREEZE",
                effectiveReason,
                adminPrincipal,
                httpRequest
        );
    }

    @Transactional(readOnly = true)
    public Page<AuditLogItemDto> getAuditLogs(int page, int size) {
        Pageable pageable = buildPageable(page, size);
        Page<AuditLog> logs = auditLogRepository.findAllByOrderByCreatedAtDescIdDesc(pageable);

        Set<Long> actorIds = logs.getContent().stream()
                .map(AuditLog::getActorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<Long, String> actorDisplayById = actorIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(actorIds).stream()
                .collect(Collectors.toMap(
                        User::getId,
                        u -> u.getFullName() + " (" + u.getEmail() + ")"
                ));

        return logs.map(entry -> {
            String actorDisplay = entry.getActorUserId() == null
                    ? "System"
                    : actorDisplayById.getOrDefault(entry.getActorUserId(), "User #" + entry.getActorUserId());
            return new AuditLogItemDto(
                    entry.getId(),
                    entry.getActorUserId(),
                    actorDisplay,
                    entry.getAction(),
                    entry.getResourceType(),
                    entry.getResourceId(),
                    entry.getDetails(),
                    entry.getIpAddress(),
                    entry.getCreatedAt()
            );
        });
    }

    @Transactional(readOnly = true)
    public Page<AdminTransferItemDto> getTransfers(int page, int size, TransferStatus status, String search) {
        Pageable pageable = buildPageable(page, size);
        String normalizedSearch = (search == null || search.isBlank()) ? null : search.trim();
        return transferRepository.findAdminTransfers(status, normalizedSearch, pageable)
                .map(AdminService::toAdminTransferItemDto);
    }

    @Transactional
    public AdminTransferItemDto reverseTransfer(
            String reference,
            String reason,
            UserPrincipal adminPrincipal,
            HttpServletRequest httpRequest) {
        Transfer transfer = transferRepository.findByReference(reference)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TRANSFER_NOT_FOUND",
                        "Transfer not found: " + reference
                ));

        if (transfer.getStatus() == TransferStatus.REVERSED) {
            throw new ConflictException(
                    "TRANSFER_ALREADY_REVERSED",
                    "Transfer " + reference + " has already been reversed"
            );
        }
        if (transfer.getStatus() != TransferStatus.SUCCESS) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "TRANSFER_NOT_REVERSIBLE",
                    "Only settled SUCCESS transfers can be reversed (current status: " + transfer.getStatus() + ")"
            );
        }

        Long senderId = transfer.getSenderWallet().getId();
        Long recipientId = transfer.getRecipientWallet().getId();
        Long firstLockId = Math.min(senderId, recipientId);
        Long secondLockId = Math.max(senderId, recipientId);

        Wallet firstLocked = lockWalletOrThrow(firstLockId);
        Wallet secondLocked = Objects.equals(firstLockId, secondLockId) ? firstLocked : lockWalletOrThrow(secondLockId);

        Wallet senderWallet = Objects.equals(senderId, firstLocked.getId()) ? firstLocked : secondLocked;
        Wallet recipientWallet = Objects.equals(recipientId, firstLocked.getId()) ? firstLocked : secondLocked;

        if (senderWallet.getStatus() == WalletStatus.CLOSED || recipientWallet.getStatus() == WalletStatus.CLOSED) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST,
                    "WALLET_CLOSED",
                    "Cannot reverse a transfer involving a closed wallet"
            );
        }

        BigDecimal amount = transfer.getAmount();
        if (recipientWallet.getBalance().compareTo(amount) < 0) {
            throw new BusinessException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "RECIPIENT_INSUFFICIENT_FUNDS",
                    "Recipient wallet " + recipientWallet.getWalletReference()
                            + " has insufficient balance (" + recipientWallet.getBalance()
                            + ") to reverse " + amount
            );
        }

        recipientWallet.setBalance(recipientWallet.getBalance().subtract(amount));
        senderWallet.setBalance(senderWallet.getBalance().add(amount));
        walletRepository.save(recipientWallet);
        walletRepository.save(senderWallet);

        transfer.setStatus(TransferStatus.REVERSED);
        Transfer savedTransfer = transferRepository.save(transfer);

        ledgerService.recordReversal(savedTransfer, senderWallet, recipientWallet, amount);

        String details = serializeAuditDetails(Map.of(
                "transferReference", savedTransfer.getReference(),
                "senderWallet", senderWallet.getWalletReference(),
                "recipientWallet", recipientWallet.getWalletReference(),
                "amount", amount.toPlainString(),
                "reason", reason.trim()
        ));
        auditService.logAction(
                adminPrincipal.getId(),
                "TRANSFER_REVERSED",
                "TRANSFER",
                savedTransfer.getReference(),
                details,
                httpRequest
        );

        if (notificationStreamService != null) {
            String formatted = amount.setScale(2, RoundingMode.HALF_EVEN).toPlainString();
            notificationStreamService.sendToUser(
                    senderWallet.getUser().getId(),
                    "TRANSFER_REVERSED",
                    "Transfer Reversed",
                    "₹" + formatted + " from " + savedTransfer.getReference() + " was restored to your wallet.",
                    Map.of("reference", savedTransfer.getReference(), "amount", amount)
            );
            notificationStreamService.sendToUser(
                    recipientWallet.getUser().getId(),
                    "TRANSFER_REVERSED",
                    "Transfer Reversed",
                    "₹" + formatted + " from " + savedTransfer.getReference() + " was reversed by admin.",
                    Map.of("reference", savedTransfer.getReference(), "amount", amount)
            );
        }

        return toAdminTransferItemDto(savedTransfer);
    }

    private Wallet lockWalletOrThrow(Long walletId) {
        return walletRepository.findByIdWithLock(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet not found: " + walletId));
    }

    public ReconciliationReportDto runReconciliation() {
        return reconciliationService.reconcileAllWallets();
    }

    private AdminUserWalletDto transitionWalletStatus(
            Long walletId,
            WalletStatus targetStatus,
            String auditAction,
            String reason,
            UserPrincipal adminPrincipal,
            HttpServletRequest httpRequest) {
        Wallet wallet = walletRepository.findByIdWithLock(walletId)
                .orElseThrow(() -> new ResourceNotFoundException("WALLET_NOT_FOUND", "Wallet not found: " + walletId));

        User owner = wallet.getUser();
        if (owner.getRole() == Role.ROLE_ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "SYSTEM_WALLET_PROTECTED", "System admin wallets cannot be frozen or modified");
        }
        if (wallet.getStatus() == WalletStatus.CLOSED) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "WALLET_CLOSED", "Closed wallets cannot change operational status");
        }
        if (wallet.getStatus() == targetStatus) {
            throw new ConflictException(
                    "WALLET_STATUS_UNCHANGED",
                    "Wallet " + wallet.getWalletReference() + " is already " + targetStatus
            );
        }

        WalletStatus previousStatus = wallet.getStatus();
        wallet.setStatus(targetStatus);
        Wallet saved = walletRepository.save(wallet);

        String details = serializeAuditDetails(Map.of(
                "walletReference", saved.getWalletReference(),
                "previousStatus", previousStatus.name(),
                "newStatus", targetStatus.name(),
                "reason", reason
        ));

        auditService.logAction(
                adminPrincipal.getId(),
                auditAction,
                "WALLET",
                saved.getWalletReference(),
                details,
                httpRequest
        );

        if (notificationStreamService != null) {
            notificationStreamService.sendToUser(
                    owner.getId(),
                    "WALLET_STATUS_CHANGED",
                    "Wallet Status Updated",
                    "Your wallet " + saved.getWalletReference() + " is now " + targetStatus.name() + ".",
                    Map.of("walletReference", saved.getWalletReference(), "status", targetStatus.name())
            );
        }

        return toAdminUserWalletDto(saved);
    }

    private String serializeAuditDetails(Map<String, String> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"reason\":\"" + payload.getOrDefault("reason", "") + "\"}";
        }
    }

    private static Pageable buildPageable(int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        return PageRequest.of(safePage, safeSize);
    }

    private static AdminTransferItemDto toAdminTransferItemDto(Transfer transfer) {
        Wallet sender = transfer.getSenderWallet();
        Wallet recipient = transfer.getRecipientWallet();
        return new AdminTransferItemDto(
                transfer.getId(),
                transfer.getReference(),
                sender.getWalletReference(),
                sender.getUser().getFullName(),
                recipient.getWalletReference(),
                recipient.getUser().getFullName(),
                transfer.getAmount(),
                transfer.getCurrency(),
                transfer.getStatus(),
                transfer.getDescription(),
                transfer.getFailureReason(),
                transfer.getCreatedAt(),
                transfer.getUpdatedAt()
        );
    }

    private static AdminUserWalletDto toAdminUserWalletDto(Wallet wallet) {
        User owner = wallet.getUser();
        return new AdminUserWalletDto(
                owner.getId(),
                owner.getFullName(),
                owner.getEmail(),
                owner.getStatus(),
                wallet.getId(),
                wallet.getWalletReference(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getStatus(),
                owner.getCreatedAt()
        );
    }
}
