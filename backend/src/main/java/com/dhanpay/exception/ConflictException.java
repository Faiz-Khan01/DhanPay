package com.dhanpay.exception;

import org.springframework.http.HttpStatus;

/**
 * Exception thrown when an operation violates a uniqueness or state conflict rule (HTTP 409).
 */
public class ConflictException extends BusinessException {

    public ConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
