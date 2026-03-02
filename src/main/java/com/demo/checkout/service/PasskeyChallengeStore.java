package com.demo.checkout.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class PasskeyChallengeStore {

    private record Entry(
            String challengeJson,
            UUID authSessionId,
            UUID userId,
            String type,
            Instant expiresAt
    ) {}

    private final Map<UUID, Entry> store = new ConcurrentHashMap<>();

    public void put(UUID authSessionId, UUID userId, String type, String challengeJson, Instant expiresAt) {
        store.put(authSessionId, new Entry(challengeJson, authSessionId, userId, type, expiresAt));
    }

    public Optional<String> getAndConsume(UUID authSessionId, UUID expectedUserId, String expectedType) {
        Entry entry = store.remove(authSessionId);
        if (entry == null) {
            return Optional.empty();
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            log.warn("[passkey] challenge expired for authSessionId={}", authSessionId);
            return Optional.empty();
        }
        if (!entry.userId().equals(expectedUserId)) {
            log.warn("[passkey] challenge user mismatch authSessionId={} expectedUserId={} actualUserId={}",
                    authSessionId, expectedUserId, entry.userId());
            return Optional.empty();
        }
        if (!entry.type().equals(expectedType)) {
            log.warn("[passkey] challenge type mismatch authSessionId={} expectedType={} actualType={}",
                    authSessionId, expectedType, entry.type());
            return Optional.empty();
        }
        return Optional.of(entry.challengeJson());
    }
}
