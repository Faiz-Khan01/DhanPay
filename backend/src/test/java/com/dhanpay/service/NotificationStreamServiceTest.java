package com.dhanpay.service;

import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class NotificationStreamServiceTest {

    @Test
    void subscribeAndSendToUser_registersEmitterAndDispatchesSafely() {
        NotificationStreamService service = new NotificationStreamService();

        SseEmitter emitter = service.subscribe(42L);
        assertThat(emitter).isNotNull();
        assertThat(service.activeSubscriberCount(42L)).isEqualTo(1);

        assertThatCode(() -> service.sendToUser(
                42L,
                "TRANSFER_RECEIVED",
                "Incoming Transfer",
                "Received ₹500.00",
                Map.of("reference", "TXN-1")
        )).doesNotThrowAnyException();
    }
}
