package com.jzqs.app.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class AuthServiceTest {

    @Autowired
    private AuthService authService;

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
    void shouldLoginExistingCustomerByPhoneWithoutChangingName() {
        jdbcTemplate.update(
            """
            UPDATE customers
            SET name = '后台原名', phone = '13800000001', openid = NULL, current_openid = NULL,
                session_key = NULL, profile_completed = TRUE, active = TRUE
            WHERE id = 1
            """
        );

        AuthBindPhoneResponse response = authService.loginCustomerByPhone("13800000001", "dev_phone_login");

        assertEquals(1L, response.userId());
        assertEquals("customer", response.userType());
        assertFalse(response.token().isBlank());

        Map<String, Object> customer = jdbcTemplate.queryForMap(
            "SELECT name, current_openid, last_login_at FROM customers WHERE id = 1"
        );
        assertEquals("后台原名", customer.get("name"));
        assertEquals("dev_phone_login", customer.get("current_openid"));
        assertNotNull(customer.get("last_login_at"));
    }
}
