package com.dhanpay.repository;

import com.dhanpay.entity.Beneficiary;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BeneficiaryRepository extends JpaRepository<Beneficiary, Long> {

    @Query("""
            SELECT b FROM Beneficiary b
            JOIN FETCH b.targetWallet w
            JOIN FETCH w.user u
            WHERE b.ownerUser.id = :ownerUserId
            ORDER BY b.createdAt DESC
            """)
    List<Beneficiary> findAllByOwnerWithTarget(@Param("ownerUserId") Long ownerUserId);

    Optional<Beneficiary> findByIdAndOwnerUserId(Long id, Long ownerUserId);

    boolean existsByOwnerUserIdAndTargetWalletId(Long ownerUserId, Long targetWalletId);
}
