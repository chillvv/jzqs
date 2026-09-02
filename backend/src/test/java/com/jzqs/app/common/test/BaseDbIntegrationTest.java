package com.jzqs.app.common.test;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 后端集成测试基类：直连真实 MySQL 测试库（jzqs_test，schema 由全部 Flyway 迁移脚本构建，
 * 与生产一致）。通过环境变量可覆盖连接：TEST_DB_URL / TEST_DB_USER / TEST_DB_PASSWORD。
 *
 * 测试库初始化：backend/scripts/init-test-db.sh
 */
public abstract class BaseDbIntegrationTest {

    protected static JdbcTemplate jdbc;
    private static HikariDataSource dataSource;

    /** 每个测试类用到的表，先子后父（外键约束下 TRUNCATE 顺序） */
    protected static final List<String> TRUNCATE_TABLES = List.of(
        "dispatch_batch_items",
        "dispatch_assignments",
        "delivery_exceptions",
        "dispatch_reassignments",
        "rider_address_bindings",
        "meal_slot_orders",
        "daily_orders",
        "customer_addresses",
        "delivery_receipts",
        "order_notes",
        "aftersale_cases",
        "customer_delivery_subscriptions",
        "subscription_rules",
        "meal_wallets",
        "dispatch_batches",
        "dispatch_area_bindings",
        "rider_profiles",
        "customers",
        "idempotency_records"
    );

    @BeforeAll
    static void initDb() {
        if (dataSource != null) {
            return;
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(System.getenv().getOrDefault(
            "TEST_DB_URL",
            "jdbc:mysql://127.0.0.1:3306/jzqs_test?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
        ));
        config.setUsername(System.getenv().getOrDefault("TEST_DB_USER", "jzqs"));
        config.setPassword(System.getenv().getOrDefault("TEST_DB_PASSWORD", "jzqs_password_123"));
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        config.setPoolName("jzqs-test");
        config.setConnectionTimeout(10_000);
        dataSource = new HikariDataSource(config);
        jdbc = new JdbcTemplate(dataSource);
    }

    @AfterAll
    static void closeDb() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
    }

    /** 清空测试数据（保留 schema），供每个用例独立重置 */
    protected static void resetTables() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        for (String table : TRUNCATE_TABLES) {
            jdbc.execute("TRUNCATE TABLE " + table);
        }
        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
    }
}
