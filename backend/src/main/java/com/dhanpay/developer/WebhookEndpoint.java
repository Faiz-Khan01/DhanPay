package com.dhanpay.developer;

import com.dhanpay.entity.User;
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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "webhook_endpoints")
public class WebhookEndpoint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "url", nullable = false, length = 512)
    private String url;

    @Column(name = "secret_key", nullable = false, length = 64)
    private String secretKey;

    @Column(name = "description", length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private WebhookStatus status;

    @Column(name = "events", nullable = false, length = 512)
    private String events;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected WebhookEndpoint() {
    }

    public WebhookEndpoint(
            User user,
            String url,
            String secretKey,
            String description,
            String events) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.url = Objects.requireNonNull(url, "url must not be null");
        this.secretKey = Objects.requireNonNull(secretKey, "secretKey must not be null");
        this.description = description;
        this.events = (events != null && !events.isBlank()) ? events : "all";
        this.status = WebhookStatus.ACTIVE;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = WebhookStatus.ACTIVE;
        }
        if (this.events == null || this.events.isBlank()) {
            this.events = "all";
        }
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }

    public boolean isSubscribedTo(String eventType) {
        if (this.status != WebhookStatus.ACTIVE) return false;
        if ("all".equalsIgnoreCase(this.events)) return true;
        for (String ev : this.events.split(",")) {
            if (ev.trim().equalsIgnoreCase(eventType)) return true;
        }
        return false;
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public WebhookStatus getStatus() {
        return status;
    }

    public void setStatus(WebhookStatus status) {
        this.status = status;
    }

    public String getEvents() {
        return events;
    }

    public void setEvents(String events) {
        this.events = events;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
