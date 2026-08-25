package com.jzqs.app.common.aop.store;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * DB 流水幂等存储：以 idempotency_records 的 key_hash 唯一约束作为防重复提交的最终防线，
 * 替代原内存实现（重启即失效、多实例不共享、无流水可查）。
 * 占用/到期时间统一用数据库 NOW(3) 计算，避免应用与数据库时钟漂移。
 */
@Component
public class DbIdempotencyStore {
    private static final Logger log = LoggerFactory.getLogger(DbIdempotencyStore.class);

    private final JdbcTemplate jdbcTemplate;

    public DbIdempotencyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * 尝试占用幂等键：插入流水，冲突时仅当占用已过期才接管
     * （防止执行进程崩溃后留下永不过期的 PROCESSING 记录把操作锁死）。
     */
    public boolean acquire(String key, int ttlSeconds) {
        String keyHash = sha256Hex(key);
        int ttl = Math.max(1, ttlSeconds);
        try {
            return jdbcTemplate.update(
                "INSERT INTO idempotency_records (key_hash, status, expires_at) "
                    + "VALUES (?, 'PROCESSING', DATE_ADD(NOW(3), INTERVAL ? SECOND))",
                keyHash, ttl
            ) > 0;
        } catch (DuplicateKeyException ex) {
            return jdbcTemplate.update(
                "UPDATE idempotency_records SET status = 'PROCESSING', expires_at = DATE_ADD(NOW(3), INTERVAL ? SECOND) "
                    + "WHERE key_hash = ? AND expires_at <= NOW(3)",
                ttl, keyHash
            ) > 0;
        }
    }

    /** 执行成功：保留流水至 TTL 后自动过期，期间相同的重复提交继续被拒绝。失败仅告警，不影响业务结果。 */
    public void markSucceeded(String key, int ttlSeconds) {
        try {
            jdbcTemplate.update(
                "UPDATE idempotency_records SET status = 'SUCCEEDED', expires_at = DATE_ADD(NOW(3), INTERVAL ? SECOND) "
                    + "WHERE key_hash = ?",
                Math.max(1, ttlSeconds), sha256Hex(key)
            );
        } catch (Exception ex) {
            log.warn("标记幂等流水成功状态失败（记录将按原到期时间自动过期）: {}", ex.getMessage());
        }
    }

    /** 执行失败：删除流水放行重试。失败仅告警，记录会按到期时间自动放行。 */
    public void release(String key) {
        try {
            jdbcTemplate.update("DELETE FROM idempotency_records WHERE key_hash = ?", sha256Hex(key));
        } catch (Exception ex) {
            log.warn("释放幂等流水失败（记录将按到期时间自动放行）: {}", ex.getMessage());
        }
    }

    /** 定期清理已过期的幂等流水，避免表无限增长。 */
    @Scheduled(fixedDelay = 300_000L)
    public void purgeExpired() {
        try {
            jdbcTemplate.update("DELETE FROM idempotency_records WHERE expires_at <= NOW(3)");
        } catch (Exception ex) {
            log.warn("清理过期幂等流水失败: {}", ex.getMessage());
        }
    }

    private String sha256Hex(String key) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(key.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 unavailable", ex);
        }
    }
}
