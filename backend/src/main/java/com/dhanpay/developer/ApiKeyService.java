package com.dhanpay.developer;

import com.dhanpay.dto.developer.ApiKeyCreatedResponseDto;
import com.dhanpay.dto.developer.ApiKeyItemDto;
import com.dhanpay.dto.developer.CreateApiKeyRequestDto;
import com.dhanpay.entity.User;
import com.dhanpay.exception.ResourceNotFoundException;
import com.dhanpay.repository.UserRepository;
import com.dhanpay.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

@Service
public class ApiKeyService {

    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final int SECRET_LENGTH = 32;

    private final ApiKeyRepository apiKeyRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public ApiKeyService(ApiKeyRepository apiKeyRepository, UserRepository userRepository) {
        this.apiKeyRepository = apiKeyRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public ApiKeyCreatedResponseDto createApiKey(UserPrincipal principal, CreateApiKeyRequestDto request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found"));

        ApiKeyEnvironment env = (request.environment() != null) ? request.environment() : ApiKeyEnvironment.LIVE;
        String prefix = (env == ApiKeyEnvironment.TEST) ? "dp_test_" : "dp_live_";

        StringBuilder randomPart = new StringBuilder(SECRET_LENGTH);
        for (int i = 0; i < SECRET_LENGTH; i++) {
            randomPart.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }

        String rawSecretKey = prefix + randomPart;
        String keyHash = sha256Hex(rawSecretKey);
        String displayPrefix = prefix + randomPart.substring(0, 4) + "...";

        Instant expiresAt = null;
        if (request.expiryDays() != null && request.expiryDays() > 0) {
            expiresAt = Instant.now().plus(request.expiryDays(), ChronoUnit.DAYS);
        }

        ApiKey apiKey = new ApiKey(user, request.name().trim(), displayPrefix, keyHash, env, expiresAt);
        ApiKey saved = apiKeyRepository.save(apiKey);

        return new ApiKeyCreatedResponseDto(
                saved.getId(),
                saved.getName(),
                rawSecretKey,
                saved.getKeyPrefix(),
                saved.getEnvironment(),
                saved.getCreatedAt(),
                saved.getExpiresAt(),
                "Store this API key safely. For security reasons, it will never be displayed again."
        );
    }

    @Transactional(readOnly = true)
    public List<ApiKeyItemDto> listMyKeys(UserPrincipal principal) {
        return apiKeyRepository.findByUserIdOrderByCreatedAtDesc(principal.getId())
                .stream()
                .map(this::toItemDto)
                .toList();
    }

    @Transactional
    public ApiKeyItemDto revokeKey(UserPrincipal principal, Long keyId) {
        ApiKey key = apiKeyRepository.findByIdAndUserId(keyId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("API_KEY_NOT_FOUND", "API key not found"));

        key.revoke();
        ApiKey saved = apiKeyRepository.save(key);
        return toItemDto(saved);
    }

    @Transactional
    public Optional<UserPrincipal> authenticateApiKey(String rawKey) {
        if (rawKey == null || (!rawKey.startsWith("dp_live_") && !rawKey.startsWith("dp_test_"))) {
            return Optional.empty();
        }

        String hash = sha256Hex(rawKey.trim());
        Optional<ApiKey> keyOpt = apiKeyRepository.findActiveByKeyHashWithUser(hash);

        if (keyOpt.isEmpty()) {
            return Optional.empty();
        }

        ApiKey key = keyOpt.get();
        if (!key.isActive()) {
            return Optional.empty();
        }

        key.recordUsage();
        apiKeyRepository.save(key);

        return Optional.of(UserPrincipal.from(key.getUser()));
    }

    public static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private ApiKeyItemDto toItemDto(ApiKey k) {
        return new ApiKeyItemDto(
                k.getId(),
                k.getName(),
                k.getKeyPrefix(),
                k.getEnvironment(),
                k.getStatus(),
                k.getCreatedAt(),
                k.getExpiresAt(),
                k.getLastUsedAt()
        );
    }
}
