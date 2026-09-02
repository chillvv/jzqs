package com.jzqs.app.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.jzqs.app.common.test.BaseDbIntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 回归测试：数据库唯一约束对"查后再插"并发重复插入的兜底能力（V20/V21 的核心价值）。
 *
 * 背景（8.25 起集中爆发）：
 * - meal_wallets 出现一个客户多个生效钱包（并发重复插入，无唯一约束兜底）→ V20 补生成列唯一索引
 * - delivery_receipts 出现一单多条回执 → V20 补唯一索引
 * - subscription_rules 出现一客户多行，移动端 selectOne 抛 TooManyResults → V21 补 UNIQUE(customer_id)
 * - @Idempotent 纯内存幂等重启失效 → V21 新增 idempotency_records + key_hash 唯一约束
 *
 * 测试方法：两个线程同时向同一目标插入，唯一约束保证恰好 1 条成功、1 条被数据库拒绝。
 */
class UniqueConstraintConcurrencyTest extends BaseDbIntegrationTest {

    /** 并发跑 2 个任务，返回成功执行的数量（被唯一约束拒绝的不计） */
    private int launchRace(Runnable task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch go = new CountDownLatch(1);
        AtomicInteger success = new AtomicInteger();
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            futures.add(pool.submit(() -> {
                ready.countDown();
                try {
                    go.await(5, TimeUnit.SECONDS);
                    task.run();
                    success.incrementAndGet();
                } catch (Exception ignored) {
                    // 唯一键冲突被数据库拒绝：符合预期
                }
            }));
        }
        ready.await(5, TimeUnit.SECONDS);
        go.countDown();
        for (Future<?> future : futures) {
            future.get(20, TimeUnit.SECONDS);
        }
        pool.shutdownNow();
        return success.get();
    }

    @Test
    @DisplayName("并发插入 meal_wallets：同一客户只能存在一个生效钱包（V20 生成列唯一索引）")
    void mealWalletsAllowOnlyOneActivePerCustomer() throws Exception {
        resetTables();

        int success = launchRace(() -> jdbc.update(
            "INSERT INTO meal_wallets (customer_id, total_meals, reserved_meals, consumed_meals, active) "
                + "VALUES (1, 10, 0, 0, 1)"
        ));

        assertThat(success)
            .as("两个线程并发为同一客户建生效钱包，唯一约束必须保证只有一个成功")
            .isEqualTo(1);
        Integer activeCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM meal_wallets WHERE customer_id = 1 AND active = 1", Integer.class);
        assertThat(activeCount).isEqualTo(1);
    }

    @Test
    @DisplayName("并发插入 subscription_rules：同一客户至多一条规则（V21 唯一约束，防 TooManyResults）")
    void subscriptionRulesAllowOnlyOnePerCustomer() throws Exception {
        resetTables();

        int success = launchRace(() -> jdbc.update(
            "INSERT INTO subscription_rules (customer_id, active, lunch_enabled, dinner_enabled) "
                + "VALUES (1, 1, 1, 0)"
        ));

        assertThat(success)
            .as("并发为同一客户插入订阅规则，唯一约束必须保证只有一个成功")
            .isEqualTo(1);
        Integer ruleCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM subscription_rules WHERE customer_id = 1", Integer.class);
        assertThat(ruleCount).isEqualTo(1);
    }

    @Test
    @DisplayName("并发写入 idempotency_records：同一 key_hash 至多一条（V21 幂等落库唯一约束）")
    void idempotencyRecordsAllowOnlyOnePerKeyHash() throws Exception {
        resetTables();

        int success = launchRace(() -> jdbc.update(
            "INSERT INTO idempotency_records (key_hash, status, expires_at) "
                + "VALUES ('concurrent-same-key-hash', 'PROCESSING', DATE_ADD(NOW(), INTERVAL 60 SECOND))"
        ));

        assertThat(success)
            .as("并发写入同一幂等键，key_hash 唯一约束必须保证只有一个成功（其余为重复提交）")
            .isEqualTo(1);
        Integer recordCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM idempotency_records WHERE key_hash = 'concurrent-same-key-hash'",
            Integer.class);
        assertThat(recordCount).isEqualTo(1);
    }

    @Test
    @DisplayName("并发插入 delivery_receipts：同一订单至多一条回执（V20 唯一约束）")
    void deliveryReceiptsAllowOnlyOnePerOrder() throws Exception {
        resetTables();

        // delivery_receipts.meal_slot_order_id 有外键（V25），先建父表链
        jdbc.update(
            "INSERT INTO customers (id, name, phone, source, active, customer_status, is_priority_customer) "
                + "VALUES (1, '测试客户', '13800138000', 'TEST', 1, 'ACTIVE', 0)"
        );
        jdbc.update(
            "INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) "
                + "VALUES (101, 1, '测试客户', '13800138000', '测试路1号', '老城区', 0)"
        );
        jdbc.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status) "
                + "VALUES (201, 1, '2026-08-30', 'ADMIN', 'ACTIVE')"
        );
        jdbc.update(
            "INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, status) "
                + "VALUES (301, 201, 'LUNCH', 'LUNCH', 1, 101, 'PENDING_DISPATCH')"
        );

        int success = launchRace(() -> jdbc.update(
            "INSERT INTO delivery_receipts (meal_slot_order_id, receipt_url, delivered_at, visible_to_customer) "
                + "VALUES (301, 'http://example.test/receipt.png', NOW(), 0)"
        ));

        assertThat(success)
            .as("并发为同一订单插入回执，唯一约束必须保证只有一个成功")
            .isEqualTo(1);
        Integer receiptCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM delivery_receipts WHERE meal_slot_order_id = 301", Integer.class);
        assertThat(receiptCount).isEqualTo(1);
    }
}
