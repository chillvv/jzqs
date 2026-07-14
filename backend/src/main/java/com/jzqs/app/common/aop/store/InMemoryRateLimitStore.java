package com.jzqs.app.common.aop.store;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

@Component
public class InMemoryRateLimitStore {
    private final ConcurrentHashMap<String, Deque<Long>> requests = new ConcurrentHashMap<>();

    public boolean allow(String key, int maxRequests, int windowSeconds) {
        long now = Instant.now().toEpochMilli();
        long windowStart = now - windowSeconds * 1000L;
        Deque<Long> timeline = requests.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        synchronized (timeline) {
            while (!timeline.isEmpty() && timeline.peekFirst() < windowStart) {
                timeline.pollFirst();
            }
            if (timeline.size() >= maxRequests) {
                return false;
            }
            timeline.addLast(now);
            return true;
        }
    }

    public void clear() {
        requests.clear();
    }
}
