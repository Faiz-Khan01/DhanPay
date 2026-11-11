package com.dhanpay.service;

import com.dhanpay.entity.AuditLog;
import com.dhanpay.repository.AuditLogRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Transaction-participating audit trail logger persisting security and financial events to {@code audit_logs}.
 */
@Service
public class AuditService {

    private static final int MAX_IP_LENGTH = 45;

    private final AuditLogRepository auditLogRepository;

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Records an audit event within the active transaction (or starts a new transaction if none is active).
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLog logAction(
            Long actorUserId,
            String action,
            String resourceType,
            String resourceId,
            String details,
            String ipAddress) {
        AuditLog entry = new AuditLog(
                actorUserId,
                action,
                resourceType,
                resourceId,
                details,
                truncateIp(ipAddress)
        );
        return auditLogRepository.save(entry);
    }

    /**
     * Convenience overload extracting the client IP from an incoming {@link HttpServletRequest}.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public AuditLog logAction(
            Long actorUserId,
            String action,
            String resourceType,
            String resourceId,
            String details,
            HttpServletRequest request) {
        return logAction(actorUserId, action, resourceType, resourceId, details, extractClientIp(request));
    }

    private static String extractClientIp(HttpServletRequest request) {
        if (request == null) {
            return null;
        }
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwardedFor)) {
            int commaIdx = forwardedFor.indexOf(',');
            String firstIp = commaIdx > 0 ? forwardedFor.substring(0, commaIdx) : forwardedFor;
            return firstIp.trim();
        }
        return request.getRemoteAddr();
    }

    private static String truncateIp(String ipAddress) {
        if (ipAddress == null) {
            return null;
        }
        String trimmed = ipAddress.trim();
        return trimmed.length() > MAX_IP_LENGTH ? trimmed.substring(0, MAX_IP_LENGTH) : trimmed;
    }
}
