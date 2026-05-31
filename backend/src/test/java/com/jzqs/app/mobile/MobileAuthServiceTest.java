package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jzqs.app.common.error.BusinessException;
import java.util.Map;
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
        jdbcTemplate.update("DELETE FROM customers WHERE id > 3");
        jdbcTemplate.update(
            "UPDATE customers SET openid = NULL, session_key = NULL, current_openid = NULL, openid_updated_at = NULL, " +
                "source_channel = NULL, last_login_at = NULL, registered_at = NULL, customer_status = 'INTENTION'"
        );
    }

    @Test
    void shouldRequirePhoneAndNameForNewDevUser() {
        Map<String, Object> result = mobileAuthService.wxLogin("code-new-user");

        assertEquals("DEV_SIMULATION", result.get("authMode"));
        assertEquals(Boolean.FALSE, result.get("registered"));
        assertEquals(Boolean.TRUE, result.get("needPhoneAuth"));
        assertEquals(Boolean.FALSE, result.get("needName"));
        assertTrue(String.valueOf(result.get("openid")).startsWith("dev_"));
    }

    @Test
    void shouldCompleteLoginAfterBindingDevPhone() {
        Map<String, Object> bindResult = mobileAuthService.bindDevPhone("dev_code_a", "13600000011");

        assertEquals(Boolean.FALSE, bindResult.get("needPhoneAuth"));
        assertEquals(Boolean.FALSE, bindResult.get("needName"));
        assertEquals(Boolean.TRUE, bindResult.get("registered"));
        assertFalse(String.valueOf(bindResult.get("token")).isBlank());
    }

    @Test
    void shouldReturnTokenForExistingCustomerDevLogin() {
        mobileAuthService.bindDevPhone("dev_code_existing", "13600000013");

        Map<String, Object> loginResult = mobileAuthService.wxLogin("code_existing");

        assertEquals(Boolean.TRUE, loginResult.get("registered"));
        assertEquals(Boolean.FALSE, loginResult.get("needPhoneAuth"));
        assertEquals(Boolean.FALSE, loginResult.get("needName"));
        assertFalse(String.valueOf(loginResult.get("token")).isBlank());
        assertTrue(loginResult.get("customerId") instanceof Number);
    }

    @Test
    void shouldCreateIntentionCustomerAndRecordWxPhoneLoginFields() {
        Map<String, Object> bindResult = mobileAuthService.bindPhone("dev_bind_new", "13600000012", "林晓");

        assertEquals(Boolean.TRUE, bindResult.get("registered"));
        Long customerId = ((Number) bindResult.get("customerId")).longValue();
        Map<String, Object> customer = jdbcTemplate.queryForMap(
            """
                SELECT name, phone, customer_status, source_channel, current_openid,
                       registered_at, last_login_at, openid_updated_at
                FROM customers
                WHERE id = ?
                """,
            customerId
        );

        assertEquals("林晓", customer.get("name"));
        assertEquals("13600000012", customer.get("phone"));
        assertEquals("INTENTION", customer.get("customer_status"));
        assertEquals("MINIAPP_WX_PHONE", customer.get("source_channel"));
        assertEquals("dev_bind_new", customer.get("current_openid"));
        assertTrue(customer.get("registered_at") != null);
        assertTrue(customer.get("last_login_at") != null);
        assertTrue(customer.get("openid_updated_at") != null);
    }

    @Test
    void shouldMergeExistingCustomerByPhoneAndRefreshWxPhoneMarkers() {
        jdbcTemplate.update(
            "UPDATE customers SET name = '旧名字', phone = '13800000001', customer_status = 'FORMAL', current_openid = NULL, source_channel = NULL WHERE id = 1"
        );

        Map<String, Object> bindResult = mobileAuthService.bindPhone("dev_bind_existing", "13800000001", "张先生");

        assertEquals(Boolean.TRUE, bindResult.get("registered"));
        assertEquals(1L, ((Number) bindResult.get("customerId")).longValue());
        Map<String, Object> customer = jdbcTemplate.queryForMap(
            """
                SELECT name, customer_status, source_channel, current_openid, last_login_at, openid_updated_at
                FROM customers
                WHERE id = 1
                """
        );

        assertEquals("张先生", customer.get("name"));
        assertEquals("FORMAL", customer.get("customer_status"));
        assertEquals("MINIAPP_WX_PHONE", customer.get("source_channel"));
        assertEquals("dev_bind_existing", customer.get("current_openid"));
        assertTrue(customer.get("last_login_at") != null);
        assertTrue(customer.get("openid_updated_at") != null);
    }

    @Test
    void shouldCreateUnassignedRiderWhenBindingPhone() {
        Map<String, Object> result = mobileAuthService.bindRiderPhone("rider_dev_01", "13800000009", "骑手小李");

        assertEquals(Boolean.TRUE, result.get("registered"));
        assertEquals("UNASSIGNED", result.get("riderStatus"));
        assertEquals(Boolean.FALSE, result.get("workbenchEnabled"));
        assertEquals("骑手小李", result.get("riderName"));

        Map<String, Object> rider = jdbcTemplate.queryForMap(
            """
                SELECT rider_name, display_name, phone, current_openid, auth_status, default_area_code
                FROM rider_profiles
                WHERE phone = ?
                """,
            "13800000009"
        );

        assertEquals("骑手小李", rider.get("rider_name"));
        assertEquals("骑手小李", rider.get("display_name"));
        assertEquals("rider_dev_01", rider.get("current_openid"));
        assertEquals("UNASSIGNED", rider.get("auth_status"));
        assertEquals(null, rider.get("default_area_code"));
    }
}
