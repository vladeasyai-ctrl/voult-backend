package com.example.vault.importing.service;

import com.example.vault.importing.dto.ImportEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class ImportEventBroadcaster {

    private static final long SSE_TIMEOUT_MS = 5 * 60 * 1000L;

    private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(UUID importId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Subscription subscription = new Subscription(importId, emitter);
        subscriptions.add(subscription);

        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> {
            subscriptions.remove(subscription);
            emitter.complete();
        });
        emitter.onError(error -> subscriptions.remove(subscription));

        return emitter;
    }

    public void publish(UUID importId, ImportEventDto event) {
        for (Subscription subscription : subscriptions) {
            if (!subscription.importId.equals(importId)) {
                continue;
            }
            try {
                subscription.emitter.send(SseEmitter.event()
                        .name(event.type())
                        .data(event));
            } catch (IOException e) {
                log.debug("Failed to send import event to client for {}", importId, e);
                subscriptions.remove(subscription);
                subscription.emitter.completeWithError(e);
            }
        }
    }

    private record Subscription(UUID importId, SseEmitter emitter) {
    }
}
