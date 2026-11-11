package com.dhanpay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Strongly-typed JWT configuration bound from {@code dhanpay.jwt.*}.
 */
@ConfigurationProperties(prefix = "dhanpay.jwt")
public class JwtProperties {

    private String secret;
    private long expirationMs = 86400000L;
    private String issuer = "dhanpay-engine";

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
