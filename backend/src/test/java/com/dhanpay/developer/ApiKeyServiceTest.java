package com.dhanpay.developer;

import com.dhanpay.dto.developer.ApiKeyCreatedResponseDto;
import com.dhanpay.dto.developer.ApiKeyItemDto;
import com.dhanpay.dto.developer.CreateApiKeyRequestDto;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.User;
import com.dhanpay.entity.UserStatus;
import com.dhanpay.repository.UserRepository;
import com.dhanpay.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ApiKeyServiceTest {

    @Mock
    private ApiKeyRepository apiKeyRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ApiKeyService apiKeyService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("Merchant Co", "merchant@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    void createApiKey_generatesLiveSecretAndMaskedPrefix() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> {
            ApiKey k = inv.getArgument(0);
            ReflectionTestUtils.setField(k, "id", 99L);
            return k;
        });

        ApiKeyCreatedResponseDto res = apiKeyService.createApiKey(
                UserPrincipal.from(user),
                new CreateApiKeyRequestDto("E-Commerce Backend", ApiKeyEnvironment.LIVE, 90)
        );

        assertThat(res.id()).isEqualTo(99L);
        assertThat(res.secretKey()).startsWith("dp_live_");
        assertThat(res.secretKey().length()).isEqualTo(8 + 32); // dp_live_ + 32 chars
        assertThat(res.keyPrefix()).startsWith("dp_live_");
        assertThat(res.keyPrefix()).endsWith("...");
        assertThat(res.environment()).isEqualTo(ApiKeyEnvironment.LIVE);
        assertThat(res.warning()).contains("never be displayed again");
    }

    @Test
    void authenticateApiKey_succeedsWithValidSecret() {
        String rawKey = "dp_live_samplevalidkey1234567890abcdef";
        String hash = ApiKeyService.sha256Hex(rawKey);

        ApiKey apiKey = new ApiKey(user, "Test Key", "dp_live_samp...", hash, ApiKeyEnvironment.LIVE, null);
        when(apiKeyRepository.findActiveByKeyHashWithUser(hash)).thenReturn(Optional.of(apiKey));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<UserPrincipal> principalOpt = apiKeyService.authenticateApiKey(rawKey);

        assertThat(principalOpt).isPresent();
        assertThat(principalOpt.get().getId()).isEqualTo(1L);
        assertThat(apiKey.getLastUsedAt()).isNotNull();
    }

    @Test
    void authenticateApiKey_failsWithInvalidSecretOrRevokedKey() {
        String invalidKey = "dp_live_nonexistentkey1234567890abc";
        String hash = ApiKeyService.sha256Hex(invalidKey);

        when(apiKeyRepository.findActiveByKeyHashWithUser(hash)).thenReturn(Optional.empty());

        Optional<UserPrincipal> principalOpt = apiKeyService.authenticateApiKey(invalidKey);
        assertThat(principalOpt).isEmpty();

        // Also test non-dp_ format
        assertThat(apiKeyService.authenticateApiKey("random_string_xyz")).isEmpty();
    }

    @Test
    void revokeKey_marksKeyRevoked() {
        ApiKey apiKey = new ApiKey(user, "Old Key", "dp_live_old...", "hash123", ApiKeyEnvironment.LIVE, null);
        ReflectionTestUtils.setField(apiKey, "id", 55L);

        when(apiKeyRepository.findByIdAndUserId(55L, 1L)).thenReturn(Optional.of(apiKey));
        when(apiKeyRepository.save(any(ApiKey.class))).thenAnswer(inv -> inv.getArgument(0));

        ApiKeyItemDto result = apiKeyService.revokeKey(UserPrincipal.from(user), 55L);

        assertThat(result.status()).isEqualTo(ApiKeyStatus.REVOKED);
    }
}
