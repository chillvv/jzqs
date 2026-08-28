package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.mobile.api.MobileAuthStateResponse;
import com.jzqs.app.mobile.api.RiderAuthStateResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class MobileAuthServiceTest {

    @Autowired
    private MobileAuthService mobileAuthService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetAuthData() {
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE wallet_id IN (SELECT id FROM meal_wallets WHERE customer_id > 3)");
        jdbcTemplate.update("DELETE FROM meal_wallets WHERE customer_id > 3");
        jdbcTemplate.update("DELETE FROM customers WHERE id > 3");
        jdbcTemplate.update("DELETE FROM dispatch_assignments WHERE rider_profile_id IS NOT NULL");
        jdbcTemplate.update("DELETE FROM rider_profiles");
        jdbcTemplate.update(
            """
                INSERT INTO customers (id, name, phone, customer_status, source, active)
                VALUES
                    (1, '张先生', '13800000001', 'FORMAL', 'MINIAPP', TRUE),
                    (2, '李女士', '13900000002', 'FORMAL', 'MINIAPP', TRUE),
                    (3, '王总', '13700000003', 'FORMAL', 'BACKEND', TRUE)
                ON DUPLICATE KEY UPDATE
                    name = VALUES(name),
                    phone = VALUES(phone),
                    customer_status = VALUES(customer_status),
                    source = VALUES(source),
                    active = VALUES(active)
                """
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_wallets (id, customer_id, package_plan_id, total_meals, reserved_meals, consumed_meals, active)
                VALUES
                    (1, 1, 1, 33, 1, 20, TRUE),
                    (2, 2, 2, 7, 1, 5, TRUE),
                    (3, 3, 1, 33, 1, 20, TRUE)
                ON DUPLICATE KEY UPDATE
                    customer_id = VALUES(customer_id),
                    package_plan_id = VALUES(package_plan_id),
                    total_meals = VALUES(total_meals),
                    reserved_meals = VALUES(reserved_meals),
                    consumed_meals = VALUES(consumed_meals),
                    active = VALUES(active)
                """
        );
        jdbcTemplate.update(
            "UPDATE customers SET openid = NULL, session_key = NULL, current_openid = NULL, openid_updated_at = NULL, " +
                "source_channel = NULL, last_login_at = NULL, registered_at = NULL, customer_status = 'INTENTION' WHERE id <= 3"
        );
    }

    @Test
    void shouldRequirePhoneAndNameForNewDevUser() {
        MobileAuthStateResponse result = mobileAuthService.wxLogin("code-new-user");

        assertEquals("DEV_SIMULATION", result.authMode());
        assertFalse(result.registered());
        assertTrue(result.needPhoneAuth());
        assertFalse(result.needName());
        assertTrue(result.openid().startsWith("dev_"));
    }

    @Test
    void shouldCompleteLoginAfterBindingDevPhone() {
        MobileAuthStateResponse bindResult = mobileAuthService.bindDevPhone("dev_code_a", "13600000011");

        assertFalse(bindResult.needPhoneAuth());
        // 绑定手机时新客户 name 为占位符（微信用户-xxx），仍需填写名字
        assertTrue(bindResult.needName());
        assertTrue(bindResult.registered());
        assertFalse(bindResult.token().isBlank());

        Long customerId = bindResult.customerId();
        assertEquals("FORMAL", jdbcTemplate.queryForObject("SELECT customer_status FROM customers WHERE id = ?", String.class, customerId));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT total_meals FROM meal_wallets WHERE customer_id = ? AND active = TRUE", Integer.class, customerId));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT reserved_meals FROM meal_wallets WHERE customer_id = ? AND active = TRUE", Integer.class, customerId));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT consumed_meals FROM meal_wallets WHERE customer_id = ? AND active = TRUE", Integer.class, customerId));
    }

    @Test
    void shouldReturnTokenForExistingCustomerDevLogin() {
        mobileAuthService.bindDevPhone("dev_code_existing", "13600000013");

        MobileAuthStateResponse loginResult = mobileAuthService.wxLogin("code_existing");

        assertTrue(loginResult.registered());
        assertFalse(loginResult.needPhoneAuth());
        // dev 绑定创建的客户 name 为占位符，仍需填写名字
        assertTrue(loginResult.needName());
        assertFalse(loginResult.token().isBlank());
        assertTrue(loginResult.customerId() instanceof Number);
    }

    @Test
    void shouldCreateFormalCustomerWithZeroMealsAndRecordWxPhoneLoginFields() {
        MobileAuthStateResponse bindResult = mobileAuthService.bindPhone("dev_bind_new", "13600000012", "林晓");

        assertTrue(bindResult.registered());
        Long customerId = bindResult.customerId();
        assertEquals("林晓", jdbcTemplate.queryForObject("SELECT name FROM customers WHERE id = ?", String.class, customerId));
        assertEquals("13600000012", jdbcTemplate.queryForObject("SELECT phone FROM customers WHERE id = ?", String.class, customerId));
        assertEquals("FORMAL", jdbcTemplate.queryForObject("SELECT customer_status FROM customers WHERE id = ?", String.class, customerId));
        assertEquals("MINIAPP_WX_PHONE", jdbcTemplate.queryForObject("SELECT source_channel FROM customers WHERE id = ?", String.class, customerId));
        assertEquals("dev_bind_new", jdbcTemplate.queryForObject("SELECT current_openid FROM customers WHERE id = ?", String.class, customerId));
        assertTrue(jdbcTemplate.queryForObject("SELECT registered_at FROM customers WHERE id = ?", Object.class, customerId) != null);
        assertTrue(jdbcTemplate.queryForObject("SELECT last_login_at FROM customers WHERE id = ?", Object.class, customerId) != null);
        assertTrue(jdbcTemplate.queryForObject("SELECT openid_updated_at FROM customers WHERE id = ?", Object.class, customerId) != null);
        assertEquals(0, jdbcTemplate.queryForObject("SELECT total_meals FROM meal_wallets WHERE customer_id = ? AND active = TRUE", Integer.class, customerId));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT reserved_meals FROM meal_wallets WHERE customer_id = ? AND active = TRUE", Integer.class, customerId));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT consumed_meals FROM meal_wallets WHERE customer_id = ? AND active = TRUE", Integer.class, customerId));
    }

    @Test
    void shouldMergeExistingCustomerByPhoneAndRefreshWxPhoneMarkers() {
        jdbcTemplate.update(
            """
                UPDATE customers
                SET name = '旧名字',
                    phone = '13800000001',
                    customer_status = 'FORMAL',
                    openid = 'dev_bind_existing',
                    current_openid = 'dev_bind_existing',
                    source_channel = NULL
                WHERE id = 1
                """
        );

        MobileAuthStateResponse bindResult = mobileAuthService.bindPhone("dev_bind_existing", "13800000001", "张先生");

        assertTrue(bindResult.registered());
        assertEquals(1L, bindResult.customerId());
        assertEquals("张先生", jdbcTemplate.queryForObject("SELECT name FROM customers WHERE id = 1", String.class));
        assertEquals("FORMAL", jdbcTemplate.queryForObject("SELECT customer_status FROM customers WHERE id = 1", String.class));
        assertEquals("MINIAPP_WX_PHONE", jdbcTemplate.queryForObject("SELECT source_channel FROM customers WHERE id = 1", String.class));
        assertEquals("dev_bind_existing", jdbcTemplate.queryForObject("SELECT current_openid FROM customers WHERE id = 1", String.class));
        assertTrue(jdbcTemplate.queryForObject("SELECT last_login_at FROM customers WHERE id = 1", Object.class) != null);
        assertTrue(jdbcTemplate.queryForObject("SELECT openid_updated_at FROM customers WHERE id = 1", Object.class) != null);
    }

    @Test
    void shouldCreateUnassignedRiderWhenBindingPhone() {
        jdbcTemplate.update(
            """
                INSERT INTO rider_profiles (
                    id, rider_name, display_name, phone, auth_status, employment_status, created_at
                ) VALUES (
                    9910, '骑手小李', '骑手小李', '13800000009', 'UNASSIGNED', 'ACTIVE', CURRENT_TIMESTAMP
                )
                """
        );

        RiderAuthStateResponse result = mobileAuthService.bindRiderPhone("rider_dev_01", "13800000009", "骑手小李");

        assertTrue(result.registered());
        assertEquals("UNASSIGNED", result.riderStatus());
        assertFalse(result.workbenchEnabled());
        assertEquals("骑手小李", result.riderName());
        assertEquals("骑手小李", jdbcTemplate.queryForObject("SELECT rider_name FROM rider_profiles WHERE phone = ?", String.class, "13800000009"));
        assertEquals("骑手小李", jdbcTemplate.queryForObject("SELECT display_name FROM rider_profiles WHERE phone = ?", String.class, "13800000009"));
        assertEquals("rider_dev_01", jdbcTemplate.queryForObject("SELECT current_openid FROM rider_profiles WHERE phone = ?", String.class, "13800000009"));
        assertEquals("UNASSIGNED", jdbcTemplate.queryForObject("SELECT auth_status FROM rider_profiles WHERE phone = ?", String.class, "13800000009"));
        assertEquals(null, jdbcTemplate.queryForObject("SELECT default_area_code FROM rider_profiles WHERE phone = ?", String.class, "13800000009"));
    }
}
