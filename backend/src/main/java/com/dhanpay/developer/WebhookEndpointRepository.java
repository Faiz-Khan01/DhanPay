package com.dhanpay.developer;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, Long> {

    List<WebhookEndpoint> findByUserIdOrderByCreatedAtDesc(Long userId);

    List<WebhookEndpoint> findByUserIdAndStatus(Long userId, WebhookStatus status);

    Optional<WebhookEndpoint> findByIdAndUserId(Long id, Long userId);
}
