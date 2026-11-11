package com.dhanpay.repository;

import com.dhanpay.entity.Transfer;
import com.dhanpay.entity.TransferStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

public interface TransferRepository extends JpaRepository<Transfer, Long> {

    Optional<Transfer> findByReference(String reference);

    boolean existsByReference(String reference);

    long countByStatus(TransferStatus status);

    @Query("SELECT COALESCE(SUM(t.amount), 0.0000) FROM Transfer t WHERE t.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") TransferStatus status);

    @Query(
            value = """
                    SELECT t FROM Transfer t
                    JOIN FETCH t.senderWallet sw
                    JOIN FETCH sw.user su
                    JOIN FETCH t.recipientWallet rw
                    JOIN FETCH rw.user ru
                    WHERE (
                        (:direction IS NULL AND (sw.id = :walletId OR rw.id = :walletId))
                        OR (:direction = 'DEBIT' AND sw.id = :walletId)
                        OR (:direction = 'CREDIT' AND rw.id = :walletId)
                    )
                    AND (:status IS NULL OR t.status = :status)
                    AND (
                        :search IS NULL OR :search = ''
                        OR LOWER(t.reference) LIKE LOWER(CONCAT('%', :search, '%'))
                        OR (sw.id = :walletId AND LOWER(rw.walletReference) LIKE LOWER(CONCAT('%', :search, '%')))
                        OR (rw.id = :walletId AND LOWER(sw.walletReference) LIKE LOWER(CONCAT('%', :search, '%')))
                    )
                    ORDER BY t.createdAt DESC, t.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(t) FROM Transfer t
                    WHERE (
                        (:direction IS NULL AND (t.senderWallet.id = :walletId OR t.recipientWallet.id = :walletId))
                        OR (:direction = 'DEBIT' AND t.senderWallet.id = :walletId)
                        OR (:direction = 'CREDIT' AND t.recipientWallet.id = :walletId)
                    )
                    AND (:status IS NULL OR t.status = :status)
                    AND (
                        :search IS NULL OR :search = ''
                        OR LOWER(t.reference) LIKE LOWER(CONCAT('%', :search, '%'))
                        OR (t.senderWallet.id = :walletId AND LOWER(t.recipientWallet.walletReference) LIKE LOWER(CONCAT('%', :search, '%')))
                        OR (t.recipientWallet.id = :walletId AND LOWER(t.senderWallet.walletReference) LIKE LOWER(CONCAT('%', :search, '%')))
                    )
                    """
    )
    Page<Transfer> findWalletHistory(
            @Param("walletId") Long walletId,
            @Param("direction") String direction,
            @Param("status") TransferStatus status,
            @Param("search") String search,
            Pageable pageable);

    @Query("""
            SELECT COALESCE(SUM(t.amount), 0.0000) FROM Transfer t
            WHERE t.senderWallet.id = :walletId
              AND t.status = :status
              AND t.createdAt >= :since
            """)
    BigDecimal sumOutgoingAmountSince(
            @Param("walletId") Long walletId,
            @Param("status") TransferStatus status,
            @Param("since") Instant since);
}
