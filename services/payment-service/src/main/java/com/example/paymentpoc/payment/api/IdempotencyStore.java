package com.example.paymentpoc.payment.api;

import com.example.paymentpoc.shared.exception.DuplicateIdempotencyKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory idempotency store. Not durable — sufficient for POC and integration tests.
 *
 * Keys are scoped per owner: two users can share the same Idempotency-Key string without
 * interference. Within the same owner, a replay must present the same body hash; a
 * different hash returns 409 (conflicting request).
 */
@Component
public class IdempotencyStore {

    private record Entry(String bodyHash, ResponseEntity<?> response) {
        boolean isInFlight() { return response == null; }
    }

    private final ConcurrentHashMap<String, Entry> store = new ConcurrentHashMap<>();

    private static String storeKey(String ownerId, String idempotencyKey) {
        return ownerId + ":" + idempotencyKey;
    }

    /**
     * Checks for a cached response. Returns:
     * - The cached response if the same owner + same key + same body hash (replay).
     * - Empty optional if the key is new (and atomically marks it in-flight).
     * Throws DuplicateIdempotencyKeyException if:
     * - The key is in-flight (concurrent request still processing), or
     * - The key was used before but with a different body hash.
     */
    public Optional<ResponseEntity<?>> getOrAcquire(String ownerId, String idempotencyKey, String bodyHash) {
        String key = storeKey(ownerId, idempotencyKey);

        Entry existing = store.get(key);
        if (existing != null) {
            if (existing.isInFlight() || !existing.bodyHash().equals(bodyHash)) {
                throw new DuplicateIdempotencyKeyException(idempotencyKey);
            }
            return Optional.of(existing.response());
        }

        Entry sentinel = new Entry(bodyHash, null);
        Entry prev = store.putIfAbsent(key, sentinel);
        if (prev != null) {
            if (prev.isInFlight() || !prev.bodyHash().equals(bodyHash)) {
                throw new DuplicateIdempotencyKeyException(idempotencyKey);
            }
            return Optional.of(prev.response());
        }

        return Optional.empty();
    }

    /** Commits the final response for a completed request. */
    public void store(String ownerId, String idempotencyKey, String bodyHash, ResponseEntity<?> response) {
        store.put(storeKey(ownerId, idempotencyKey), new Entry(bodyHash, response));
    }

    /** Removes the in-flight sentinel when a request fails, allowing retries. */
    public void release(String ownerId, String idempotencyKey) {
        store.remove(storeKey(ownerId, idempotencyKey));
    }
}
