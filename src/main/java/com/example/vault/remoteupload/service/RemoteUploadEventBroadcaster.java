package com.example.vault.remoteupload.service;

import com.example.vault.remoteupload.dto.RemoteUploadEventDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Component
public class RemoteUploadEventBroadcaster {

    private static final long SSE_TIMEOUT_MS = 20 * 60 * 1000L;

    private final CopyOnWriteArrayList<Subscription> subscriptions = new CopyOnWriteArrayList<>();

    public SseEmitter subscribe(UUID sessionId) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);
        Subscription subscription = new Subscription(sessionId, emitter);
        subscriptions.add(subscription);

        emitter.onCompletion(() -> subscriptions.remove(subscription));
        emitter.onTimeout(() -> {
            subscriptions.remove(subscription);
            emitter.complete();
        });
        emitter.onError(error -> subscriptions.remove(subscription));

        return emitter;
    }

    public void publish(UUID sessionId, RemoteUploadEventDto event) {
        for (Subscription subscription : subscriptions) {
            if (!subscription.sessionId.equals(sessionId)) {
                continue;
            }
            try {
                subscription.emitter.send(SseEmitter.event()
                        .name(event.type())
                        .data(event));
            } catch (IOException e) {
                log.debug("Failed to send remote upload event to client for {}", sessionId, e);
                subscriptions.remove(subscription);
                subscription.emitter.completeWithError(e);
            }
        }
    }

    private record Subscription(UUID sessionId, SseEmitter emitter) {
    }
}
