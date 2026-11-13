package com.dhanpay.controller;

import com.dhanpay.security.UserPrincipal;
import com.dhanpay.service.NotificationStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Server-Sent Events (SSE) endpoint for real-time wallet, transfer, escrow, and security notifications.
 */
@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notifications", description = "Real-time Server-Sent Events stream for live wallet updates")
public class NotificationController {

    private final NotificationStreamService notificationStreamService;

    public NotificationController(NotificationStreamService notificationStreamService) {
        this.notificationStreamService = notificationStreamService;
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Subscribe to live notifications", description = "Opens an authenticated SSE stream for incoming transfers, payment requests, escrow changes, and wallet status events")
    public SseEmitter streamNotifications(
            @Parameter(hidden = true) @AuthenticationPrincipal UserPrincipal principal) {
        return notificationStreamService.subscribe(principal.getId());
    }
}
