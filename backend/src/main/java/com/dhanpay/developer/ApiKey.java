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
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "api_keys")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "key_prefix", nullable = false, length = 16)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, unique = true, length = 64)
    private String keyHash;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "environment", nullable = false, length = 10)
    private ApiKeyEnvironment environment;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private ApiKeyStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "last_used_at")
    private Instant lastUsedAt;

    protected ApiKey() {
    }

    public ApiKey(
            User user,
            String name,
            String keyPrefix,
            String keyHash,
            ApiKeyEnvironment environment,
            Instant expiresAt) {
        this.user = Objects.requireNonNull(user, "user must not be null");
        this.name = Objects.requireNonNull(name, "name must not be null");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix must not be null");
        this.keyHash = Objects.requireNonNull(keyHash, "keyHash must not be null");
        this.environment = environment != null ? environment : ApiKeyEnvironment.LIVE;
        this.status = ApiKeyStatus.ACTIVE;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.status == null) {
            this.status = ApiKeyStatus.ACTIVE;
        }
        if (this.environment == null) {
            this.environment = ApiKeyEnvironment.LIVE;
        }
    }

    public boolean isActive() {
        if (this.status != ApiKeyStatus.ACTIVE) {
            return false;
        }
        return this.expiresAt == null || Instant.now().isBefore(this.expiresAt);
    }

    public void revoke() {
        this.status = ApiKeyStatus.REVOKED;
    }

    public void recordUsage() {
        this.lastUsedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public String getName() {
        return name;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public String getKeyHash() {
        return keyHash;
    }

    public ApiKeyEnvironment getEnvironment() {
        return environment;
    }

    public ApiKeyStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getLastUsedAt() {
        return lastUsedAt;
    }
}
