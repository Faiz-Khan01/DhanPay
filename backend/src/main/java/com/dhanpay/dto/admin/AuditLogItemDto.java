package com.dhanpay.dto.admin;

import java.time.Instant;

/**
 * Audit trail item enriched with actor identity for admin inspection.
 */
public record AuditLogItemDto(
        Long id,
        Long actorUserId,
        String actorDisplay,
        String action,
        String resourceType,
        String resourceId,
        String details,
        String ipAddress,
        Instant createdAt
) {
}
