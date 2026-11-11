package com.dhanpay.idempotency;

/**
 * Result state returned by {@link IdempotencyService#lockOrRetrieve}.
 */
public record IdempotencyState(
        IdempotencyStatus status,
        String cachedResponsePayload
) {
    public boolean isCompleted() {
        return status == IdempotencyStatus.COMPLETED;
    }
}
