package com.dhanpay.exception;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.Map;

/**
 * Standardized RFC 7807-compatible API error payload.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        Instant timestamp,
        int status,
        String error,
        String code,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
    public static ApiErrorResponse of(int status, String error, String code, String message, String path) {
        return new ApiErrorResponse(Instant.now(), status, error, code, message, path, null);
    }

    public static ApiErrorResponse ofValidation(
            int status,
            String error,
            String code,
            String message,
            String path,
            Map<String, String> fieldErrors) {
        return new ApiErrorResponse(Instant.now(), status, error, code, message, path, fieldErrors);
    }
}
