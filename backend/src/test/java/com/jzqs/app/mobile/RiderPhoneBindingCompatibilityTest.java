package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class RiderPhoneBindingCompatibilityTest {

    @Autowired
    private MobileAuthService mobileAuthService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetRiderFixtures() {
        jdbcTemplate.update("DELETE FROM rider_profiles WHERE id >= 930");
        jdbcTemplate.update(
            """
                INSERT INTO rider_profiles (
                    id, rider_name, display_name, phone, employment_status, auth_status,
                    default_area_code, created_at
                ) VALUES (
                    930, '骑手老周', '老周', '13800000930', 'ACTIVE', 'ACTIVE', '高新区', CURRENT_TIMESTAMP
                )
                """
        );
    }

    @Test
    void shouldBindPhoneToExistingRiderInsteadOfCreatingNewProfile() {
        Map<String, Object> state = mobileAuthService.bindRiderPhone("rider_openid_930", "13800000930", "老周");

        assertEquals(true, state.get("registered"));
        assertEquals("ACTIVE", state.get("riderStatus"));
        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rider_profiles WHERE phone = '13800000930'",
                Integer.class
            )
        );
        assertEquals(
            "rider_openid_930",
            jdbcTemplate.queryForObject(
                "SELECT current_openid FROM rider_profiles WHERE id = 930",
                String.class
            )
        );
    }

    @Test
    void shouldReturnNotFoundWhenPhoneDoesNotMatchAnyBackendCreatedRider() {
        Map<String, Object> state = mobileAuthService.bindRiderPhone("rider_openid_931", "13800000931", "新骑手");

        assertEquals(false, state.get("registered"));
        assertEquals("NOT_FOUND", state.get("riderStatus"));
        assertEquals(
            0,
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM rider_profiles WHERE phone = '13800000931'",
                Integer.class
            )
        );
    }
}
