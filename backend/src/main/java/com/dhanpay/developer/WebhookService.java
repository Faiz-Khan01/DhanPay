package com.dhanpay.developer;

import com.dhanpay.dto.developer.CreateWebhookRequestDto;
import com.dhanpay.dto.developer.WebhookDeliveryDto;
import com.dhanpay.dto.developer.WebhookEndpointDto;
import com.dhanpay.dto.developer.WebhookTestResponseDto;
import com.dhanpay.entity.User;
import com.dhanpay.exception.ResourceNotFoundException;
import com.dhanpay.repository.UserRepository;
import com.dhanpay.security.UserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);
    private static final String HMAC_SHA256 = "HmacSHA256";
    private static final String ALPHABET = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private final WebhookEndpointRepository webhookEndpointRepository;
    private final WebhookDeliveryRepository webhookDeliveryRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final SecureRandom secureRandom = new SecureRandom();

    public WebhookService(
            WebhookEndpointRepository webhookEndpointRepository,
            WebhookDeliveryRepository webhookDeliveryRepository,
            UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.webhookEndpointRepository = webhookEndpointRepository;
        this.webhookDeliveryRepository = webhookDeliveryRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public WebhookEndpointDto createWebhook(UserPrincipal principal, CreateWebhookRequestDto request) {
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("USER_NOT_FOUND", "User not found"));

        StringBuilder sb = new StringBuilder("whsec_");
        for (int i = 0; i < 32; i++) {
            sb.append(ALPHABET.charAt(secureRandom.nextInt(ALPHABET.length())));
        }
        String secretKey = sb.toString();

        WebhookEndpoint endpoint = new WebhookEndpoint(
                user,
                request.url().trim(),
                secretKey,
                request.description(),
                request.events()
        );

        WebhookEndpoint saved = webhookEndpointRepository.save(endpoint);
        return toDto(saved);
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointDto> listMyWebhooks(UserPrincipal principal) {
        return webhookEndpointRepository.findByUserIdOrderByCreatedAtDesc(principal.getId())
                .stream()
                .map(this::toDto)
                .toList();
    }

    @Transactional
    public void deleteWebhook(UserPrincipal principal, Long id) {
        WebhookEndpoint endpoint = webhookEndpointRepository.findByIdAndUserId(id, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WEBHOOK_NOT_FOUND", "Webhook endpoint not found"));
        webhookEndpointRepository.delete(endpoint);
    }

    @Transactional(readOnly = true)
    public List<WebhookDeliveryDto> listDeliveries(UserPrincipal principal, Long webhookId) {
        WebhookEndpoint endpoint = webhookEndpointRepository.findByIdAndUserId(webhookId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WEBHOOK_NOT_FOUND", "Webhook endpoint not found"));

        return webhookDeliveryRepository.findTop20ByWebhookEndpointIdOrderByDispatchedAtDesc(endpoint.getId())
                .stream()
                .map(this::toDeliveryDto)
                .toList();
    }

    @Transactional
    public WebhookTestResponseDto testWebhook(UserPrincipal principal, Long webhookId) {
        WebhookEndpoint endpoint = webhookEndpointRepository.findByIdAndUserId(webhookId, principal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("WEBHOOK_NOT_FOUND", "Webhook endpoint not found"));

        String eventId = "evt_test_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        long timestamp = Instant.now().getEpochSecond();

        Map<String, Object> payloadMap = Map.of(
                "id", eventId,
                "event", "test.ping",
                "timestamp", timestamp,
                "data", Map.of(
                        "message", "DhanPay signed webhook verification ping",
                        "endpointId", endpoint.getId(),
                        "url", endpoint.getUrl()
                )
        );

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payloadMap);
        } catch (Exception e) {
            jsonPayload = "{\"id\":\"" + eventId + "\",\"event\":\"test.ping\"}";
        }

        String signature = computeHmacSha256(timestamp + "." + jsonPayload, endpoint.getSecretKey());
        String headerSignature = "t=" + timestamp + ",v1=" + signature;

        Integer httpStatus = null;
        WebhookDeliveryStatus status = WebhookDeliveryStatus.FAILED;
        String errorMsg = null;

        try {
            httpStatus = dispatchHttpPost(endpoint.getUrl(), jsonPayload, headerSignature);
            if (httpStatus != null && httpStatus >= 200 && httpStatus < 300) {
                status = WebhookDeliveryStatus.SUCCESS;
            } else {
                errorMsg = "Endpoint responded with HTTP " + httpStatus;
            }
        } catch (Exception ex) {
            errorMsg = ex.getMessage();
            log.warn("Webhook test delivery failed for endpoint {}: {}", endpoint.getId(), ex.getMessage());
        }

        WebhookDelivery delivery = new WebhookDelivery(
                endpoint,
                "test.ping",
                eventId,
                jsonPayload,
                headerSignature,
                httpStatus,
                status,
                errorMsg
        );
        webhookDeliveryRepository.save(delivery);

        return new WebhookTestResponseDto(
                status == WebhookDeliveryStatus.SUCCESS,
                httpStatus,
                headerSignature,
                jsonPayload,
                errorMsg
        );
    }

    protected int dispatchHttpPost(String targetUrl, String jsonPayload, String signature) throws Exception {
        URL url = URI.create(targetUrl).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "DhanPay-Webhooks/1.0");
        conn.setRequestProperty("X-DhanPay-Signature", signature);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonPayload.getBytes(StandardCharsets.UTF_8));
        }

        return conn.getResponseCode();
    }

    public static String computeHmacSha256(String data, String key) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            SecretKeySpec secretKeySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), HMAC_SHA256);
            mac.init(secretKeySpec);
            byte[] hmacBytes = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to calculate HMAC-SHA256", e);
        }
    }

    private WebhookEndpointDto toDto(WebhookEndpoint e) {
        return new WebhookEndpointDto(
                e.getId(),
                e.getUrl(),
                e.getSecretKey(),
                e.getDescription(),
                e.getStatus(),
                e.getEvents(),
                e.getCreatedAt(),
                e.getUpdatedAt()
        );
    }

    private WebhookDeliveryDto toDeliveryDto(WebhookDelivery d) {
        return new WebhookDeliveryDto(
                d.getId(),
                d.getEventType(),
                d.getEventId(),
                d.getHttpStatus(),
                d.getStatus(),
                d.getErrorMessage(),
                d.getDispatchedAt()
        );
    }
}
