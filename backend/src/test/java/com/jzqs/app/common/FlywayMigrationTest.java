package com.jzqs.app.common;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

class FlywayMigrationTest {

    @Test
    void shouldApplyPhaseOneMigrationsAndSeedAdminData() throws Exception {
        String serverJdbcUrl = "jdbc:mysql://127.0.0.1:3306/?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";
        String databaseName = "jzqs_flyway_test";
        String jdbcUrl = "jdbc:mysql://127.0.0.1:3306/" + databaseName
            + "?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai";

        try (Connection connection = DriverManager.getConnection(serverJdbcUrl, "root", "root");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP DATABASE IF EXISTS " + databaseName);
            statement.execute(
                "CREATE DATABASE " + databaseName + " CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci"
            );
        }
        try {
            Flyway flyway = Flyway.configure()
                .locations("classpath:db/migration")
                .dataSource(jdbcUrl, "root", "root")
                .load();

            assertDoesNotThrow(flyway::migrate);

            try (Connection connection = DriverManager.getConnection(jdbcUrl, "root", "root");
                 Statement statement = connection.createStatement()) {

                try (ResultSet settingsCount = statement.executeQuery("SELECT COUNT(*) FROM admin_settings")) {
                    settingsCount.next();
                    assertEquals(1, settingsCount.getInt(1));
                }

                try (ResultSet adminSettingColumns = statement.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'admin_settings' " +
                        "AND COLUMN_NAME IN ('banner_images', 'popup_announcement_enabled', 'popup_announcement_content')"
                )) {
                    adminSettingColumns.next();
                    assertEquals(3, adminSettingColumns.getInt(1));
                }

                try (ResultSet adminSettingsSeed = statement.executeQuery(
                    "SELECT banner_images, popup_announcement_enabled, popup_announcement_content FROM admin_settings WHERE id = 1"
                )) {
                    adminSettingsSeed.next();
                    assertEquals("[\"../../assets/hero-new.jpg\"]", adminSettingsSeed.getString("banner_images"));
                    assertEquals(false, adminSettingsSeed.getBoolean("popup_announcement_enabled"));
                    assertEquals("", adminSettingsSeed.getString("popup_announcement_content"));
                }

                try (ResultSet customerCount = statement.executeQuery("SELECT COUNT(*) FROM customers")) {
                    customerCount.next();
                    assertEquals(3, customerCount.getInt(1));
                }

                try (ResultSet weeklyTableCount = statement.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN ('menu_weeks', 'menu_week_items')"
                )) {
                    weeklyTableCount.next();
                    assertEquals(2, weeklyTableCount.getInt(1));
                }

                try (ResultSet publishedWeekCount = statement.executeQuery(
                    "SELECT COUNT(*) FROM menu_weeks WHERE status = 'PUBLISHED'"
                )) {
                    publishedWeekCount.next();
                    assertEquals(1, publishedWeekCount.getInt(1));
                }

                try (ResultSet menuItemCount = statement.executeQuery("SELECT COUNT(*) FROM menu_week_items")) {
                    menuItemCount.next();
                    assertEquals(42, menuItemCount.getInt(1));
                }

                try (ResultSet newColumns = statement.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS " +
                        "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'menu_week_items' " +
                        "AND COLUMN_NAME IN ('dish_items_json', 'total_calories')"
                )) {
                    newColumns.next();
                    assertEquals(2, newColumns.getInt(1));
                }

                try (ResultSet dispatchCount = statement.executeQuery("SELECT COUNT(*) FROM dispatch_assignments")) {
                    dispatchCount.next();
                    assertEquals(2, dispatchCount.getInt(1));
                }

                try (ResultSet notificationCount = statement.executeQuery("SELECT COUNT(*) FROM notification_logs")) {
                    notificationCount.next();
                    assertEquals(1, notificationCount.getInt(1));
                }

                try (ResultSet migrationVersion = statement.executeQuery(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '11'"
                )) {
                    migrationVersion.next();
                    assertEquals(1, migrationVersion.getInt(1));
                }

                try (ResultSet upgradedCustomerColumns = statement.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() " +
                        "AND TABLE_NAME = 'customers' " +
                        "AND COLUMN_NAME IN ('customer_status', 'registered_at', 'first_paid_at', 'last_order_at', " +
                        "'last_login_at', 'source_channel', 'remark', 'current_openid', 'openid_updated_at', " +
                        "'is_priority_customer', 'priority_tag', 'priority_note')"
                )) {
                    upgradedCustomerColumns.next();
                    assertEquals(12, upgradedCustomerColumns.getInt(1));
                }

                try (ResultSet upgradedOrderColumns = statement.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() " +
                        "AND TABLE_NAME = 'meal_slot_orders' " +
                        "AND COLUMN_NAME IN ('user_note', 'admin_note', 'special_tag', 'is_priority', 'source_type', 'confirmed_from_subscription')"
                )) {
                    upgradedOrderColumns.next();
                    assertEquals(6, upgradedOrderColumns.getInt(1));
                }

                try (ResultSet newBusinessTables = statement.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN " +
                        "('subscription_confirmations', 'aftersale_cases', 'aftersale_actions', 'cost_entries', 'operating_metrics_daily')"
                )) {
                    newBusinessTables.next();
                    assertEquals(5, newBusinessTables.getInt(1));
                }

                try (ResultSet riderDispatchTables = statement.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME IN " +
                        "('rider_profiles', 'rider_address_bindings', 'dispatch_batches', 'dispatch_batch_items')"
                )) {
                    riderDispatchTables.next();
                    assertEquals(4, riderDispatchTables.getInt(1));
                }

                try (ResultSet deliveryReceiptColumns = statement.executeQuery(
                    "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA = DATABASE() " +
                        "AND TABLE_NAME = 'delivery_receipts' " +
                        "AND COLUMN_NAME IN ('visible_at', 'expires_at', 'visible_to_customer')"
                )) {
                    deliveryReceiptColumns.next();
                    assertEquals(3, deliveryReceiptColumns.getInt(1));
                }

                try (ResultSet migrationVersion = statement.executeQuery(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '12'"
                )) {
                    migrationVersion.next();
                    assertEquals(1, migrationVersion.getInt(1));
                }

                try (ResultSet migrationVersion = statement.executeQuery(
                    "SELECT COUNT(*) FROM flyway_schema_history WHERE version = '34'"
                )) {
                    migrationVersion.next();
                    assertEquals(1, migrationVersion.getInt(1));
                }
            }
        } finally {
            try (Connection connection = DriverManager.getConnection(serverJdbcUrl, "root", "root");
                 Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE IF EXISTS " + databaseName);
            }
        }
    }
}
