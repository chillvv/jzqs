package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class MobileHomePackageValidityTest {

    // 使用 983x 高 ID 段：@SpringBootTest 测试类共享同一随机库，
    // 低 ID（1/501/502 等）易与其他测试类撞车（曾出现 DuplicateKey）。
    private static final long CUSTOMER_ID = 9833L;
    private static final long ADDRESS_ID = 9833L;
    private static final long LEGACY_WALLET_ID = 9834L;
    private static final long ACTIVE_WALLET_ID = 9835L;

    @Autowired
    private MobilePortalService mobilePortalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void seedCustomerAndWallet() {
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE customer_id IN (9833, 9834)");
        jdbcTemplate.update("DELETE FROM meal_wallets WHERE customer_id IN (9833, 9834)");
        jdbcTemplate.update("DELETE FROM customers WHERE id IN (9833, 9834)");
        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, customer_status, source, active) VALUES (9833, '有效期顾客', '13900005555', 'FORMAL', 'ADMIN', TRUE)"
        );
        jdbcTemplate.update(
            "INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (9833, 9833, '有效期顾客', '13900005555', '高新区软件园 D 座', '高新区', TRUE)"
        );
        jdbcTemplate.update(
            "INSERT INTO meal_wallets (id, customer_id, total_meals, reserved_meals, consumed_meals, active, opened_at, expired_at, last_adjusted_at) VALUES (9833, 9833, 10, 0, 8, TRUE, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP)",
            LocalDateTime.now().plusDays(5)
        );
        jdbcTemplate.update(
            "UPDATE admin_settings SET package_expiry_reminder_days = 7, package_low_balance_threshold = 3 WHERE id = 1"
        );
    }

    @Test
    void shouldExposePackageValidityFieldsInCustomerHome() {
        var response = mobilePortalService.customerHome(CUSTOMER_ID);

        assertEquals("EXPIRING_SOON", response.packageAlertCode());
        assertTrue(response.packageExpiredAt() != null && !response.packageExpiredAt().isBlank());
        assertTrue(response.remainingValidityDays() <= 5);
    }

    /**
     * 回归：存在「已过期 / 已停用的大余额钱包」+「有效的小余额钱包」时，
     * customerHome 必须只展示有效钱包的余量。
     * 修复前 LEFT JOIN 不过滤 active/过期，会取到 id 最小的历史钱包（此处剩 8 餐），
     * 前端据此放行「午餐+晚餐」下单，但后端实际扣有效钱包（仅剩 1 餐），
     * 造成「午餐成功、晚餐提示余额不足、无预订成功界面」的线上症状。
     *
     * 注意：V24 的 uk_meal_wallets_active_customer 保证每客户至多一个 active 钱包，
     * 两个 active 的脏数据形态在约束下不可能存在，故历史钱包用 active = FALSE。
     */
    @Test
    void shouldShowOnlyValidWalletBalanceWhenExpiredActiveWalletExists() {
        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, customer_status, source, active) VALUES (9834, '多钱包口径顾客', '13900005002', 'FORMAL', 'ADMIN', TRUE)"
        );
        // 历史大余额钱包：已停用（active = FALSE）
        jdbcTemplate.update(
            "INSERT INTO meal_wallets (id, customer_id, total_meals, reserved_meals, consumed_meals, active, opened_at, expired_at, last_adjusted_at) VALUES (9834, 9834, 10, 0, 2, FALSE, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP)",
            LocalDateTime.now().minusDays(1)
        );
        // 当前唯一 active 钱包（未过期、余额仅 1 餐）
        jdbcTemplate.update(
            "INSERT INTO meal_wallets (id, customer_id, total_meals, reserved_meals, consumed_meals, active, opened_at, expired_at, last_adjusted_at) VALUES (9835, 9834, 5, 0, 4, TRUE, CURRENT_TIMESTAMP, ?, CURRENT_TIMESTAMP)",
            LocalDateTime.now().plusDays(30)
        );

        var response = mobilePortalService.customerHome(9834L);

        assertEquals(1, response.remainingMeals());
    }
}
