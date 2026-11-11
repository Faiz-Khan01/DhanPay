package com.dhanpay.entity;

/**
 * Lifecycle state of a P2P wallet transfer.
 */
public enum TransferStatus {
    INITIATED,
    SUCCESS,
    FAILED,
    REVERSED
}
