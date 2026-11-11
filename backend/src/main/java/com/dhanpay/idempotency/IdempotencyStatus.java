package com.dhanpay.idempotency;

/**
 * Processing states of an idempotent client request.
 */
public enum IdempotencyStatus {
    PROCESSING,
    IN_PROGRESS,
    COMPLETED,
    FAILED
}
