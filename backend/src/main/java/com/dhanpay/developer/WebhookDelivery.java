package com.dhanpay.developer;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "webhook_deliveries")
public class WebhookDelivery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "webhook_endpoint_id", nullable = false)
    private WebhookEndpoint webhookEndpoint;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "signature", nullable = false, length = 128)
    private String signature;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private WebhookDeliveryStatus status;

    @Column(name = "error_message", length = 512)
    private String errorMessage;

    @Column(name = "dispatched_at", nullable = false, updatable = false)
    private Instant dispatchedAt;

    protected WebhookDelivery() {
    }

    public WebhookDelivery(
            WebhookEndpoint webhookEndpoint,
            String eventType,
            String eventId,
            String payload,
            String signature,
            Integer httpStatus,
            WebhookDeliveryStatus status,
            String errorMessage) {
        this.webhookEndpoint = Objects.requireNonNull(webhookEndpoint, "webhookEndpoint must not be null");
        this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
        this.eventId = Objects.requireNonNull(eventId, "eventId must not be null");
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.signature = Objects.requireNonNull(signature, "signature must not be null");
        this.httpStatus = httpStatus;
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.errorMessage = errorMessage;
    }

    @PrePersist
    void onCreate() {
        if (this.dispatchedAt == null) {
            this.dispatchedAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public WebhookEndpoint getWebhookEndpoint() {
        return webhookEndpoint;
    }

    public String getEventType() {
        return eventType;
    }

    public String getEventId() {
        return eventId;
    }

    public String getPayload() {
        return payload;
    }

    public String getSignature() {
        return signature;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public WebhookDeliveryStatus getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getDispatchedAt() {
        return dispatchedAt;
    }
}
