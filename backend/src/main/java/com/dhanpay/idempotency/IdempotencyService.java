package com.dhanpay.idempotency;

import com.dhanpay.entity.Transfer;
import com.dhanpay.entity.User;
import com.dhanpay.exception.ConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

/**
 * Database-backed idempotency service preventing duplicate transaction processing and race conditions.
 */
@Service
public class IdempotencyService {

    private static final long DEFAULT_TTL_HOURS = 24L;
    private static final int MONETARY_SCALE = 4;

    private final IdempotencyRecordRepository idempotencyRecordRepository;

    public IdempotencyService(IdempotencyRecordRepository idempotencyRecordRepository) {
        this.idempotencyRecordRepository = idempotencyRecordRepository;
    }

    /**
     * Computes deterministic SHA-256 fingerprint for transfer request payload.
     */
    public String computeFingerprint(String recipientWalletReference, BigDecimal amount, String description) {
        Objects.requireNonNull(recipientWalletReference, "recipientWalletReference must not be null");
        Objects.requireNonNull(amount, "amount must not be null");

        String normalizedAmount = amount.setScale(MONETARY_SCALE, RoundingMode.UNNECESSARY).toPlainString();
        String normalizedDesc = description == null ? "" : description.trim();
        String raw = recipientWalletReference.trim() + "|" + normalizedAmount + "|" + normalizedDesc;

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 message digest unavailable", e);
        }
    }

    /**
     * Atomically locks or retrieves the request state in an isolated transaction.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public IdempotencyState lockOrRetrieve(User user, String idempotencyKey, String fingerprint) {
        Objects.requireNonNull(user, "user must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
        Objects.requireNonNull(fingerprint, "fingerprint must not be null");

        Optional<IdempotencyRecord> existingOpt = idempotencyRecordRepository
                .findByUserIdAndIdempotencyKey(user.getId(), idempotencyKey);

        if (existingOpt.isPresent()) {
            IdempotencyRecord record = existingOpt.get();

            if (!record.getRequestFingerprint().equalsIgnoreCase(fingerprint)) {
                throw new ConflictException(
                        "IDEMPOTENCY_MISMATCH",
                        "Payload fingerprint does not match original request for key: " + idempotencyKey
                );
            }

            if (record.getProcessingStatus() == IdempotencyStatus.COMPLETED) {
                return new IdempotencyState(IdempotencyStatus.COMPLETED, record.getResponsePayload());
            }

            if (record.getProcessingStatus() == IdempotencyStatus.PROCESSING
                    || record.getProcessingStatus() == IdempotencyStatus.IN_PROGRESS) {
                throw new ConflictException(
                        "CONCURRENT_REQUEST",
                        "A transfer with idempotency key '" + idempotencyKey + "' is already processing"
                );
            }

            // Retry on previously failed attempt
            record.setProcessingStatus(IdempotencyStatus.PROCESSING);
            record.setExpiresAt(Instant.now().plus(DEFAULT_TTL_HOURS, ChronoUnit.HOURS));
            idempotencyRecordRepository.save(record);
            return new IdempotencyState(IdempotencyStatus.PROCESSING, null);
        }

        try {
            Instant expiresAt = Instant.now().plus(DEFAULT_TTL_HOURS, ChronoUnit.HOURS);
            IdempotencyRecord record = new IdempotencyRecord(
                    user,
                    idempotencyKey,
                    fingerprint,
                    IdempotencyStatus.PROCESSING,
                    expiresAt
            );
            idempotencyRecordRepository.saveAndFlush(record);
            return new IdempotencyState(IdempotencyStatus.PROCESSING, null);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException(
                    "CONCURRENT_REQUEST",
                    "Concurrent duplicate request detected for idempotency key: " + idempotencyKey
            );
        }
    }

    /**
     * Marks the idempotency record as COMPLETED and caches the serialized response payload.
     * Participates in the caller's active transfer transaction so the FK reference to the
     * newly inserted {@link Transfer} commits atomically with balances and ledger entries.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void markSuccess(Long userId, String idempotencyKey, Transfer transfer, String responsePayload) {
        IdempotencyRecord record = idempotencyRecordRepository
                .findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .orElseThrow(() -> new IllegalStateException("Idempotency record not found for user and key"));

        record.setTransfer(transfer);
        record.setResponsePayload(responsePayload);
        record.setProcessingStatus(IdempotencyStatus.COMPLETED);
        idempotencyRecordRepository.save(record);
    }

    /**
     * Marks the idempotency record as FAILED or cleans it up after validation failure.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long userId, String idempotencyKey) {
        idempotencyRecordRepository.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .ifPresent(record -> {
                    record.setProcessingStatus(IdempotencyStatus.FAILED);
                    idempotencyRecordRepository.save(record);
                });
    }
}
