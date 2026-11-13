package com.dhanpay.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Manages active Server-Sent Events (SSE) subscriptions keyed by authenticated user ID.
 */
@Service
public class NotificationStreamService {

    private static final Logger log = LoggerFactory.getLogger(NotificationStreamService.class);
    private static final long STREAM_TIMEOUT_MILLIS = 30 * 60 * 1000L;

    private final ConcurrentHashMap<Long, CopyOnWriteArrayList<SseEmitter>> emittersByUserId =
            new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(STREAM_TIMEOUT_MILLIS);
        CopyOnWriteArrayList<SseEmitter> list = emittersByUserId.computeIfAbsent(
                userId, id -> new CopyOnWriteArrayList<>()
        );
        list.add(emitter);

        Runnable cleanup = () -> removeEmitter(userId, emitter);
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(ex -> cleanup.run());

        try {
            emitter.send(SseEmitter.event()
                    .name("connected")
                    .data(Map.of("userId", userId, "connectedAt", Instant.now().toString()), MediaType.APPLICATION_JSON));
        } catch (IOException ex) {
            cleanup.run();
        }
        return emitter;
    }

    public void sendToUser(Long userId, String eventType, String title, String message, Map<String, Object> metadata) {
        if (userId == null) {
            return;
        }
        List<SseEmitter> list = emittersByUserId.get(userId);
        if (list == null || list.isEmpty()) {
            return;
        }

        Map<String, Object> payload = Map.of(
                "type", eventType,
                "title", title,
                "message", message,
                "timestamp", Instant.now().toString(),
                "data", metadata == null ? Map.of() : metadata
        );

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventType)
                        .data(payload, MediaType.APPLICATION_JSON));
            } catch (IOException ex) {
                log.debug("Removing stale SSE emitter for user {}: {}", userId, ex.getMessage());
                removeEmitter(userId, emitter);
            }
        }
    }

    int activeSubscriberCount(Long userId) {
        List<SseEmitter> list = emittersByUserId.get(userId);
        return list == null ? 0 : list.size();
    }

    private void removeEmitter(Long userId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> list = emittersByUserId.get(userId);
        if (list != null) {
            list.remove(emitter);
            if (list.isEmpty()) {
                emittersByUserId.remove(userId, list);
            }
        }
    }
}
