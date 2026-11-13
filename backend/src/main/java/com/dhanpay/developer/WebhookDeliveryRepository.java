package com.dhanpay.developer;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface WebhookDeliveryRepository extends JpaRepository<WebhookDelivery, Long> {

    List<WebhookDelivery> findTop20ByWebhookEndpointIdOrderByDispatchedAtDesc(Long webhookEndpointId);

    Page<WebhookDelivery> findByWebhookEndpointIdOrderByDispatchedAtDesc(Long webhookEndpointId, Pageable pageable);
}
