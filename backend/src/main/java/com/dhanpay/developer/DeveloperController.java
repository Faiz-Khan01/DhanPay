package com.dhanpay.developer;

import com.dhanpay.dto.developer.ApiKeyCreatedResponseDto;
import com.dhanpay.dto.developer.ApiKeyItemDto;
import com.dhanpay.dto.developer.CreateApiKeyRequestDto;
import com.dhanpay.dto.developer.CreateWebhookRequestDto;
import com.dhanpay.dto.developer.WebhookDeliveryDto;
import com.dhanpay.dto.developer.WebhookEndpointDto;
import com.dhanpay.dto.developer.WebhookTestResponseDto;
import com.dhanpay.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/developer")
@Tag(name = "Developer & Merchant Platform", description = "API keys and signed webhook subscriptions management")
public class DeveloperController {

    private final ApiKeyService apiKeyService;
    private final WebhookService webhookService;

    public DeveloperController(ApiKeyService apiKeyService, WebhookService webhookService) {
        this.apiKeyService = apiKeyService;
        this.webhookService = webhookService;
    }

    // --- API KEYS ---

    @PostMapping("/keys")
    @Operation(summary = "Generate new API key", description = "Generates a cryptographically secure API key. Secret is returned once.")
    public ResponseEntity<ApiKeyCreatedResponseDto> createApiKey(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateApiKeyRequestDto request) {
        ApiKeyCreatedResponseDto res = apiKeyService.createApiKey(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping("/keys")
    @Operation(summary = "List merchant API keys", description = "Returns active and revoked API keys with masked prefixes")
    public ResponseEntity<List<ApiKeyItemDto>> listKeys(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(apiKeyService.listMyKeys(principal));
    }

    @DeleteMapping("/keys/{id}")
    @Operation(summary = "Revoke API key", description = "Permanently disables an API key")
    public ResponseEntity<ApiKeyItemDto> revokeKey(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(apiKeyService.revokeKey(principal, id));
    }

    // --- WEBHOOKS ---

    @PostMapping("/webhooks")
    @Operation(summary = "Register webhook endpoint", description = "Registers a destination URL with an HMAC-SHA256 signing secret")
    public ResponseEntity<WebhookEndpointDto> createWebhook(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,
            @Valid @RequestBody CreateWebhookRequestDto request) {
        WebhookEndpointDto res = webhookService.createWebhook(principal, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(res);
    }

    @GetMapping("/webhooks")
    @Operation(summary = "List registered webhooks", description = "Returns all webhook configurations for authenticated user")
    public ResponseEntity<List<WebhookEndpointDto>> listWebhooks(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(webhookService.listMyWebhooks(principal));
    }

    @DeleteMapping("/webhooks/{id}")
    @Operation(summary = "Delete webhook endpoint")
    public ResponseEntity<Void> deleteWebhook(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        webhookService.deleteWebhook(principal, id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/webhooks/{id}/deliveries")
    @Operation(summary = "Get webhook delivery log", description = "Returns the 20 most recent dispatch attempts and HTTP status codes")
    public ResponseEntity<List<WebhookDeliveryDto>> listDeliveries(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(webhookService.listDeliveries(principal, id));
    }

    @PostMapping("/webhooks/{id}/test")
    @Operation(summary = "Dispatch test webhook ping", description = "Sends a signed HMAC-SHA256 ping to verify the receiving endpoint")
    public ResponseEntity<WebhookTestResponseDto> testWebhook(
            @Parameter(hidden = true)
            @AuthenticationPrincipal UserPrincipal principal,
            @PathVariable Long id) {
        return ResponseEntity.ok(webhookService.testWebhook(principal, id));
    }
}
