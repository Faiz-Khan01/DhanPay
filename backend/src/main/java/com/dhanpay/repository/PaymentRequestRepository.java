package com.dhanpay.repository;

import com.dhanpay.entity.PaymentRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface PaymentRequestRepository extends JpaRepository<PaymentRequest, Long> {

    @Query("""
            SELECT pr FROM PaymentRequest pr
            JOIN FETCH pr.requesterWallet rw
            JOIN FETCH rw.user ru
            JOIN FETCH pr.payerWallet pw
            JOIN FETCH pw.user pu
            LEFT JOIN FETCH pr.transfer t
            WHERE pr.reference = :reference
            """)
    Optional<PaymentRequest> findByReferenceWithDetails(@Param("reference") String reference);

    @Query("""
            SELECT pr FROM PaymentRequest pr
            JOIN FETCH pr.requesterWallet rw
            JOIN FETCH rw.user ru
            JOIN FETCH pr.payerWallet pw
            JOIN FETCH pw.user pu
            LEFT JOIN FETCH pr.transfer t
            WHERE rw.id = :walletId OR pw.id = :walletId
            ORDER BY pr.createdAt DESC
            """)
    List<PaymentRequest> findAllInvolvingWallet(@Param("walletId") Long walletId);

    boolean existsByReference(String reference);
}
