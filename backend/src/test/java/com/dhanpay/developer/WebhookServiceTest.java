package com.dhanpay.developer;

import com.dhanpay.dto.developer.CreateWebhookRequestDto;
import com.dhanpay.dto.developer.WebhookEndpointDto;
import com.dhanpay.entity.Role;
import com.dhanpay.entity.User;
import com.dhanpay.entity.UserStatus;
import com.dhanpay.repository.UserRepository;
import com.dhanpay.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {

    @Mock
    private WebhookEndpointRepository webhookEndpointRepository;

    @Mock
    private WebhookDeliveryRepository webhookDeliveryRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private WebhookService webhookService;

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("FinTech Partner", "partner@example.com", "h", Role.ROLE_CUSTOMER, UserStatus.ACTIVE);
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    void createWebhook_generatesWhsecSecret() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(webhookEndpointRepository.save(any(WebhookEndpoint.class))).thenAnswer(inv -> {
            WebhookEndpoint ep = inv.getArgument(0);
            ReflectionTestUtils.setField(ep, "id", 101L);
            return ep;
        });

        WebhookEndpointDto res = webhookService.createWebhook(
                UserPrincipal.from(user),
                new CreateWebhookRequestDto("https://api.partner.com/dhanpay-webhook", "Primary ERP listener", "transfer.completed,escrow.released")
        );

        assertThat(res.id()).isEqualTo(101L);
        assertThat(res.url()).isEqualTo("https://api.partner.com/dhanpay-webhook");
        assertThat(res.secretKey()).startsWith("whsec_");
        assertThat(res.status()).isEqualTo(WebhookStatus.ACTIVE);
        assertThat(res.events()).isEqualTo("transfer.completed,escrow.released");
    }

    @Test
    void computeHmacSha256_producesConsistentDeterministicSignature() {
        String data = "1794500000.{\"id\":\"evt_123\",\"event\":\"transfer.completed\"}";
        String secret = "whsec_supersecuresecretkey123456789";

        String sig1 = WebhookService.computeHmacSha256(data, secret);
        String sig2 = WebhookService.computeHmacSha256(data, secret);

        assertThat(sig1).isNotEmpty();
        assertThat(sig1).isEqualTo(sig2);
        assertThat(sig1).matches("^[a-f0-9]{64}$");
    }

    @Test
    void deleteWebhook_removesEndpoint() {
        WebhookEndpoint endpoint = new WebhookEndpoint(user, "https://api.partner.com/webhook", "whsec_abc", "Test", "all");
        ReflectionTestUtils.setField(endpoint, "id", 50L);

        when(webhookEndpointRepository.findByIdAndUserId(50L, 1L)).thenReturn(Optional.of(endpoint));

        webhookService.deleteWebhook(UserPrincipal.from(user), 50L);

        verify(webhookEndpointRepository).delete(endpoint);
    }
}
