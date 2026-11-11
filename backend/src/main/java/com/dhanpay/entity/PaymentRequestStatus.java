package com.dhanpay.entity;

/**
 * Lifecycle state of a peer-to-peer payment request.
 */
public enum PaymentRequestStatus {
    PENDING,
    PAID,
    DECLINED,
    CANCELLED
}
