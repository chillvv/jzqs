package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 骑手队列刷新的回归测试（2026-09-09/09-10 生产事故）。
 *
 * <p>事故根因：读路径物化里的
 * {@code UPDATE dispatch_batch_items ... WHERE batch_id = ? AND item_status = 'CURRENT'}
 * 是非唯一前缀的范围更新，InnoDB 会对整批索引区间加 X 记录锁 + 间隙锁。多个并发刷新
 * 即使改的是**不同订单**，也会争同一段锁区间并成环死锁，骑手表现为“刷新不出来数据”。
 *
 * <p>本测试覆盖两点：
 * <ol>
 *   <li>{@link #refreshShouldNotLockOtherOrdersInSameBatch()}：刷新不得锁住同批次的其他订单
 *       —— 这一条在旧实现上是确定性失败的（范围更新会锁住整批）；</li>
 *   <li>{@link #steadyStateRefreshShouldNotWriteAnyRow()}：稳态刷新不得产生任何 UPDATE
 *       —— 旧实现每次刷新都重写整批明细、批头与派单行。</li>
 * </ol>
 */
@SpringBootTest
class RiderQueueConcurrentRefreshTest {
    private static final long CUSTOMER_ID = 9861L;
    private static final long ADDRESS_ID = 9861L;
    private static final long DAILY_ORDER_ID = 9861L;
    private static final long BATCH_ID = 9861L;
    private static final long RIDER_PROFILE_ID = 9861L;
    private static final long ITEM_ID_BASE = 986100L;
    private static final int ITEM_COUNT = 12;

    @Autowired
    private RiderQueueSupport riderQueueSupport;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DataSource dataSource;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private String serveDate;

    @BeforeEach
    void resetSeedData() {
        serveDate = LocalDate.now().toString();
        jdbcTemplate.update("DELETE FROM dispatch_batch_items WHERE batch_id = ?", BATCH_ID);
        jdbcTemplate.update("DELETE FROM dispatch_assignments WHERE rider_profile_id = ?", RIDER_PROFILE_ID);
        jdbcTemplate.update("DELETE FROM dispatch_batches WHERE id = ?", BATCH_ID);
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id >= ? AND id < ?", ITEM_ID_BASE, ITEM_ID_BASE + ITEM_COUNT);
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id = ?", DAILY_ORDER_ID);
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id = ?", ADDRESS_ID);
        jdbcTemplate.update("DELETE FROM customers WHERE id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM rider_profiles WHERE id = ?", RIDER_PROFILE_ID);

        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source, active, customer_status) VALUES (?, '并发刷新客户9861', '13900009861', 'MINIAPP', TRUE, 'FORMAL')",
            CUSTOMER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO customer_addresses (
                    id, customer_id, contact_name, contact_phone, address_line, area_code, is_default
                ) VALUES (
                    ?, ?, '并发刷新客户9861', '13900009861', '高新区并发路1号', '高新区', TRUE
                )
                """,
            ADDRESS_ID,
            CUSTOMER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at)
                VALUES (?, ?, CURRENT_DATE, 'MINIAPP', 'DISPATCHING', FALSE, CURRENT_TIMESTAMP)
                """,
            DAILY_ORDER_ID,
            CUSTOMER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO rider_profiles (
                    id, rider_name, display_name, phone, auth_status, employment_status, default_area_code, display_order, created_at
                ) VALUES (
                    ?, '并发刷新骑手', '并发刷新骑手', '13800009861', 'ACTIVE', 'ACTIVE', '高新区', 1, CURRENT_TIMESTAMP
                )
                """,
            RIDER_PROFILE_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_batches (
                    id, serve_date, meal_period, rider_profile_id, area_code, batch_status, total_count, delivered_count, current_sequence
                ) VALUES (
                    ?, CURRENT_DATE, 'LUNCH', ?, '高新区', 'IN_PROGRESS', ?, 0, 1
                )
                """,
            BATCH_ID,
            RIDER_PROFILE_ID,
            ITEM_COUNT
        );

        for (int index = 0; index < ITEM_COUNT; index++) {
            long orderId = ITEM_ID_BASE + index;
            // 第 1 单是队列当前项，其余待配送；派单侧状态与物化结果保持一致，
            // 这样“稳态”下刷新不应再写任何一行。
            String itemStatus = index == 0 ? "CURRENT" : "PENDING";
            jdbcTemplate.update(
                """
                    INSERT INTO meal_slot_orders (
                        id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, status, source_type
                    ) VALUES (?, ?, 'LUNCH', 'LUNCH', 1, ?, '-', '-', 'DISPATCHING', 'MINIAPP')
                    """,
                orderId,
                DAILY_ORDER_ID,
                ADDRESS_ID
            );
            jdbcTemplate.update(
                """
                    INSERT INTO dispatch_assignments (
                        meal_slot_order_id, rider_name, area_code, status, rider_profile_id, sequence_number
                    ) VALUES (?, '并发刷新骑手', '高新区', 'DISPATCHING', ?, ?)
                    """,
                orderId,
                RIDER_PROFILE_ID,
                index + 1
            );
            jdbcTemplate.update(
                """
                    INSERT INTO dispatch_batch_items (
                        id, batch_id, meal_slot_order_id, current_sequence, suggested_sequence, item_status, manually_adjusted
                    ) VALUES (?, ?, ?, ?, ?, ?, FALSE)
                    """,
                orderId,
                BATCH_ID,
                orderId,
                index + 1,
                index + 1,
                itemStatus
            );
        }
    }

    /**
     * 刷新骑手队列时，同批次里“另一个订单”的写入不应被挡住。
     *
     * <p>旧实现执行 {@code WHERE batch_id = ? AND item_status = 'CURRENT'} 的范围更新，
     * 会在整批索引区间上加锁；本用例用一个独立连接去更新同批次的另一条明细，
     * 若被挡住（innodb_lock_wait_timeout = 1s）即失败。
     */
    @Test
    void refreshShouldNotLockOtherOrdersInSameBatch() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // 预热到稳态（旧实现在这里就会写下整批锁）
            riderQueueSupport.riderQueue(RIDER_PROFILE_ID, serveDate);
            long otherOrderItemId = ITEM_ID_BASE + 5;

            assertEquals(
                "OK",
                probeUpdateFromSeparateConnection(otherOrderItemId),
                "刷新不应锁住同批次的其他订单：旧实现的 WHERE batch_id = ? 范围更新会锁住整批明细"
            );
        });
    }

    /**
     * 稳态下反复刷新不应产生任何写入。
     *
     * <p>用会话级 {@code Handler_update} 计数：事务模板把连接绑定到当前线程，
     * 计数只反映本次刷新自己发出的 UPDATE，不受同库其他会话干扰。
     */
    @Test
    void steadyStateRefreshShouldNotWriteAnyRow() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            riderQueueSupport.riderQueue(RIDER_PROFILE_ID, serveDate);
            long before = sessionHandlerUpdates();
            for (int round = 0; round < 5; round++) {
                riderQueueSupport.riderQueue(RIDER_PROFILE_ID, serveDate);
            }
            assertEquals(
                0,
                sessionHandlerUpdates() - before,
                "稳态刷新不应再产生 UPDATE（旧实现每次刷新都重写整批明细 + 批头 + 派单行）"
            );
        });
    }

    /**
     * 并发刷新同一批次的冒烟用例：不得抛异常，且结果状态自洽（恰好一个 CURRENT 且为序号最小的项）。
     */
    @Test
    void concurrentRefreshShouldKeepQueueStateConsistent() throws Exception {
        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch startGate = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        try {
            for (int threadIndex = 0; threadIndex < threads; threadIndex++) {
                futures.add(pool.submit(() -> {
                    startGate.await();
                    for (int round = 0; round < 3; round++) {
                        riderQueueSupport.riderQueue(RIDER_PROFILE_ID, serveDate);
                    }
                    return null;
                }));
            }
            startGate.countDown();
            for (Future<?> future : futures) {
                future.get(60, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dispatch_batch_items WHERE batch_id = ? AND item_status = 'CURRENT'",
            Integer.class,
            BATCH_ID
        ));
        assertEquals(1, jdbcTemplate.queryForObject(
            "SELECT current_sequence FROM dispatch_batch_items WHERE batch_id = ? AND item_status = 'CURRENT'",
            Integer.class,
            BATCH_ID
        ));
        assertEquals(ITEM_COUNT - 1, jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM dispatch_batch_items WHERE batch_id = ? AND item_status = 'PENDING'",
            Integer.class,
            BATCH_ID
        ));
        assertEquals(ITEM_COUNT, jdbcTemplate.queryForObject(
            "SELECT total_count FROM dispatch_batches WHERE id = ?",
            Integer.class,
            BATCH_ID
        ));
    }

    /** 用一条独立连接更新同批次里另一条明细，返回 OK 或被锁等待的具体原因。 */
    private String probeUpdateFromSeparateConnection(long itemId) {
        ExecutorService pool = Executors.newSingleThreadExecutor();
        try {
            Future<String> future = pool.submit(() -> {
                try (Connection connection = dataSource.getConnection()) {
                    try (Statement statement = connection.createStatement()) {
                        statement.execute("SET SESSION innodb_lock_wait_timeout = 1");
                    }
                    try (PreparedStatement statement = connection.prepareStatement(
                        "UPDATE dispatch_batch_items SET manually_adjusted = TRUE WHERE id = ?")) {
                        statement.setLong(1, itemId);
                        statement.executeUpdate();
                    }
                    return "OK";
                } catch (SQLException ex) {
                    return "BLOCKED: " + ex.getMessage();
                }
            });
            return future.get(20, TimeUnit.SECONDS);
        } catch (Exception ex) {
            return "PROBE_ERROR: " + ex.getMessage();
        } finally {
            pool.shutdownNow();
        }
    }

    private long sessionHandlerUpdates() {
        Long value = jdbcTemplate.queryForObject(
            "SHOW SESSION STATUS LIKE 'Handler_update'",
            (rs, rowNum) -> rs.getLong(2)
        );
        return value == null ? 0L : value;
    }
}
