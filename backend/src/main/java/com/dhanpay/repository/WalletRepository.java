package com.dhanpay.repository;

import com.dhanpay.entity.Wallet;
import com.dhanpay.entity.WalletStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    Optional<Wallet> findByUserId(Long userId);

    Optional<Wallet> findByWalletReference(String walletReference);

    boolean existsByWalletReference(String walletReference);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdWithLock(@Param("id") Long id);

    /** Used in reconciliation tests to look up a wallet by the owner's email. */
    @Query("SELECT w FROM Wallet w WHERE w.user.email = :email")
    Optional<Wallet> findByUserEmail(@Param("email") String email);

    long countByStatus(WalletStatus status);

    @Query(
            value = """
                    SELECT w FROM Wallet w
                    JOIN FETCH w.user u
                    WHERE u.role = com.dhanpay.entity.Role.ROLE_CUSTOMER
                    AND (
                        :search IS NULL OR :search = ''
                        OR LOWER(u.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                        OR LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%'))
                        OR LOWER(w.walletReference) LIKE LOWER(CONCAT('%', :search, '%'))
                    )
                    ORDER BY u.createdAt DESC, w.id DESC
                    """,
            countQuery = """
                    SELECT COUNT(w) FROM Wallet w
                    WHERE w.user.role = com.dhanpay.entity.Role.ROLE_CUSTOMER
                    AND (
                        :search IS NULL OR :search = ''
                        OR LOWER(w.user.fullName) LIKE LOWER(CONCAT('%', :search, '%'))
                        OR LOWER(w.user.email) LIKE LOWER(CONCAT('%', :search, '%'))
                        OR LOWER(w.walletReference) LIKE LOWER(CONCAT('%', :search, '%'))
                    )
                    """
    )
    Page<Wallet> findCustomerWallets(@Param("search") String search, Pageable pageable);
}
