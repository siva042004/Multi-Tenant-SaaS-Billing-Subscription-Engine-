package com.billing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Publishes subscription/payment lifecycle events to connected dashboard
 * clients via Server-Sent Events, keyed by tenant so each tenant only
 * receives its own billing updates in real time.
 */
@Component
public class SubscriptionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionEventPublisher.class);

    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emittersByTenant = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID tenantId) {
        SseEmitter emitter = new SseEmitter(0L); // no timeout; client reconnects on failure
        emittersByTenant.computeIfAbsent(tenantId, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(tenantId, emitter));
        emitter.onTimeout(() -> removeEmitter(tenantId, emitter));
        emitter.onError(e -> removeEmitter(tenantId, emitter));

        try {
            emitter.send(SseEmitter.event().name("CONNECTED").data("ok"));
        } catch (IOException e) {
            removeEmitter(tenantId, emitter);
        }
        return emitter;
    }

    public void publish(UUID tenantId, String eventType, UUID subscriptionId) {
        var emitters = emittersByTenant.get(tenantId);
        if (emitters == null) return;

        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                        .name(eventType)
                        .data(Map.of("subscriptionId", subscriptionId.toString(), "type", eventType)));
            } catch (IOException e) {
                removeEmitter(tenantId, emitter);
            }
        }
    }

    private void removeEmitter(UUID tenantId, SseEmitter emitter) {
        var list = emittersByTenant.get(tenantId);
        if (list != null) {
            list.remove(emitter);
        }
    }
}
