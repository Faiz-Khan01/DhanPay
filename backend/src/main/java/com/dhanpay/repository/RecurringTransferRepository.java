package com.dhanpay.repository;

import com.dhanpay.entity.RecurringStatus;
import com.dhanpay.entity.RecurringTransfer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RecurringTransferRepository extends JpaRepository<RecurringTransfer, Long> {

    Optional<RecurringTransfer> findByReference(String reference);

    @Query("""
            SELECT r FROM RecurringTransfer r
            JOIN FETCH r.senderWallet sw
            JOIN FETCH sw.user
            JOIN FETCH r.recipientWallet rw
            JOIN FETCH rw.user
            WHERE r.status = :status
              AND r.nextExecutionAt <= :now
            ORDER BY r.nextExecutionAt ASC
            """)
    List<RecurringTransfer> findDueTransfers(
            @Param("status") RecurringStatus status,
            @Param("now") Instant now
    );

    @Query("""
            SELECT r FROM RecurringTransfer r
            JOIN FETCH r.senderWallet sw
            JOIN FETCH sw.user
            JOIN FETCH r.recipientWallet rw
            JOIN FETCH rw.user
            WHERE sw.id = :walletId OR rw.id = :walletId
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<RecurringTransfer> findAllInvolvingWallet(@Param("walletId") Long walletId);
}
