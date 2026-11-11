package com.dhanpay.repository;

import com.dhanpay.entity.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Append-only repository for {@link AuditLog} entries (no update/delete operations exposed).
 */
public interface AuditLogRepository extends Repository<AuditLog, Long> {

    AuditLog save(AuditLog auditLog);

    Optional<AuditLog> findById(Long id);

    List<AuditLog> findByActorUserIdOrderByCreatedAtDesc(Long actorUserId);

    Page<AuditLog> findAllByOrderByCreatedAtDescIdDesc(Pageable pageable);
}
