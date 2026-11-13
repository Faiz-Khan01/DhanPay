package com.dhanpay.repository;

import com.dhanpay.entity.EscrowHold;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EscrowHoldRepository extends JpaRepository<EscrowHold, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT e FROM EscrowHold e
            JOIN FETCH e.buyerWallet bw
            JOIN FETCH bw.user
            JOIN FETCH e.sellerWallet sw
            JOIN FETCH sw.user
            JOIN FETCH e.escrowLedgerAccount
            WHERE e.reference = :reference
            """)
    Optional<EscrowHold> findByReferenceWithLock(@Param("reference") String reference);

    @Query("""
            SELECT e FROM EscrowHold e
            JOIN FETCH e.buyerWallet bw
            JOIN FETCH bw.user
            JOIN FETCH e.sellerWallet sw
            JOIN FETCH sw.user
            WHERE bw.id = :walletId OR sw.id = :walletId
            ORDER BY e.createdAt DESC, e.id DESC
            """)
    List<EscrowHold> findAllForWallet(@Param("walletId") Long walletId);
}
