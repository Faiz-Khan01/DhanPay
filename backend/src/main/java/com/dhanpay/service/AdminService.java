package com.dhanpay.service;

import com.dhanpay.dto.admin.AdminStatsDto;
import com.dhanpay.dto.admin.AdminUserWalletDto;
import com.dhanpay.dto.admin.AuditLogItemDto;
import com.dhanpay.entity.AuditLog;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.TransferStatus;
import com.dhanpay.entity.User;
import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import com.dhanpay.exception.BusinessException;
import com.dhanpay.exception.ConflictException;
import com.dhanpay.exception.ResourceNotFoundException;
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
    private final ReconciliationService reconciliationService;
    private final ObjectMapper objectMapper;

    public AdminService(
            UserRepository userRepository,
            WalletRepository walletRepository,
            TransferRepository transferRepository,
            AuditLogRepository auditLogRepository,
            AuditService auditService,
            ReconciliationService reconciliationService,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.transferRepository = transferRepository;
        this.auditLogRepository = auditLogRepository;
        this.auditService = auditService;
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
