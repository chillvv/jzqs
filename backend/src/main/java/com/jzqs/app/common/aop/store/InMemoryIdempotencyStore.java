package com.jzqs.app.common.aop.store;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryIdempotencyStore {
    private final ConcurrentHashMap<String, Long> holders = new ConcurrentHashMap<>();

    public boolean acquire(String key, int ttlSeconds) {
        long expiresAt = Instant.now().toEpochMilli() + ttlSeconds * 1000L;
        while (true) {
            Long current = holders.get(key);
            long now = Instant.now().toEpochMilli();
            if (current != null && current > now) {
                return false;
            }
            if (current == null) {
                if (holders.putIfAbsent(key, expiresAt) == null) {
                    return true;
                }
                continue;
            }
            if (holders.replace(key, current, expiresAt)) {
                return true;
            }
        }
    }

    public void release(String key) {
        holders.remove(key);
    }

    public void clear() {
        holders.clear();
    }
}
