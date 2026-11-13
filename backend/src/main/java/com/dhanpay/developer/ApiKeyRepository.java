package com.dhanpay.developer;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    @Query("SELECT k FROM ApiKey k JOIN FETCH k.user u WHERE k.keyHash = :keyHash AND k.status = 'ACTIVE'")
    Optional<ApiKey> findActiveByKeyHashWithUser(@Param("keyHash") String keyHash);

    List<ApiKey> findByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<ApiKey> findByIdAndUserId(Long id, Long userId);
}
