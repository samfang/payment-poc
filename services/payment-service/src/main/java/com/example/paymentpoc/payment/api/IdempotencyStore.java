package com.example.paymentpoc.payment.api;

import com.example.paymentpoc.shared.exception.DuplicateIdempotencyKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory idempotency store. Not durable — sufficient for POC and integration tests.
 * A real implementation would use a distributed cache or database with TTL.
 */
@Component
public class IdempotencyStore {

    private final ConcurrentHashMap<String, ResponseEntity<?>> store = new ConcurrentHashMap<>();

    /**
     * Returns the cached response for the given key if it exists.
     */
    public Optional<ResponseEntity<?>> get(String key) {
        return Optional.ofNullable(store.get(key));
    }

    /**
     * Stores the response for the given key.
     * Throws DuplicateIdempotencyKeyException if the key is already in-flight
     * (i.e., stored with a sentinel null value indicating processing has started).
     */
    public void store(String key, ResponseEntity<?> response) {
        store.put(key, response);
    }

    /**
     * Marks the key as in-flight by inserting it. Returns false if the key was already present.
     * Callers should call store() with the final response once processing completes.
     */
    public boolean tryAcquire(String key) {
        return store.putIfAbsent(key, ResponseEntity.status(409).build()) == null;
    }

    /**
     * Atomically checks for an existing entry, then marks the key as in-flight.
     * Returns the cached response if present, or empty if the key is new.
     * Throws DuplicateIdempotencyKeyException if a concurrent request is already processing.
     */
    public Optional<ResponseEntity<?>> getOrAcquire(String key) {
        ResponseEntity<?> existing = store.get(key);
        if (existing != null) {
            // 409 sentinel means another request is still in progress
            if (existing.getStatusCode().value() == 409 && existing.getBody() == null) {
                throw new DuplicateIdempotencyKeyException(key);
            }
            return Optional.of(existing);
        }
        // Try to atomically mark as in-flight
        ResponseEntity<?> sentinel = ResponseEntity.status(409).<Void>build();
        ResponseEntity<?> prev = store.putIfAbsent(key, sentinel);
        if (prev != null) {
            if (prev.getStatusCode().value() == 409 && prev.getBody() == null) {
                throw new DuplicateIdempotencyKeyException(key);
            }
            return Optional.of(prev);
        }
        return Optional.empty();
    }
}
