package com.jzqs.app.common.aop.store;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 测试用内存幂等存储：模拟 DbIdempotencyStore 的真实幂等语义，
 * 避免 @WebMvcTest 下 mock 返回 false 导致所有幂等接口被误判为重复提交(409)。
 */
public class TestIdempotencyStore extends DbIdempotencyStore {
    private final Map<String, Long> held = new ConcurrentHashMap<>();

    public TestIdempotencyStore() {
        super(null);
    }

    @Override
    public boolean acquire(String key, int ttlSeconds) {
        return held.putIfAbsent(key, System.currentTimeMillis()) == null;
    }

    @Override
    public void markSucceeded(String key, int ttlSeconds) {
    }

    @Override
    public void release(String key) {
        held.remove(key);
    }
}
