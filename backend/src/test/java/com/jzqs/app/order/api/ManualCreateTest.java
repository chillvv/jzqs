package com.jzqs.app.order.api;

import com.jzqs.app.common.util.JwtClaims;
import com.jzqs.app.common.util.JwtUtils;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class ManualCreateTest {
    private static final long ADDRESS_ID = 9101L;
    private static final long WALLET_ID = 9201L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String adminAuthHeader;
    private LocalDate dinnerServeDate;
    private LocalDate mergeServeDate;

    @BeforeEach
    void resetManualCreateOrders() {
        adminAuthHeader = "Bearer " + JwtUtils.generateToken(JwtClaims.admin(7L, "OWNER", "运营A"));
        dinnerServeDate = LocalDate.now().plusDays(10);
        mergeServeDate = LocalDate.now().plusDays(11);
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE wallet_id = ?", WALLET_ID);
        jdbcTemplate.update("DELETE FROM meal_wallets WHERE id = ?", WALLET_ID);
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id = ?", ADDRESS_ID);
        // V1 种子中客户 id 从 382 开始，id=1 不存在，需自建（V25 外键要求父行存在）
        jdbcTemplate.update(
            """
                INSERT INTO customers (id, name, phone, source, active, profile_completed, customer_status)
                VALUES (1, '张先生', '13800000001', 'BACKEND', 1, 1, 'FORMAL')
                ON DUPLICATE KEY UPDATE name = VALUES(name), phone = VALUES(phone), active = 1, customer_status = 'FORMAL'
                """
        );
        jdbcTemplate.update(
            """
                INSERT INTO customer_addresses (
                    id, customer_id, contact_name, contact_phone, address_line, area_code, is_default
                ) VALUES (?, 1, '张先生', '13800000001', '测试地址', '高新区', TRUE)
                """,
            ADDRESS_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_wallets (
                    id, customer_id, package_plan_id, total_meals, reserved_meals, consumed_meals, active
                ) VALUES (?, 1, NULL, 33, 0, 0, TRUE)
                """,
            WALLET_ID
        );
        jdbcTemplate.update(
            "DELETE FROM meal_slot_orders WHERE daily_order_id IN (SELECT id FROM daily_orders WHERE customer_id = 1 AND serve_date IN (?, ?))",
            dinnerServeDate,
            mergeServeDate
        );
        jdbcTemplate.update(
            "DELETE FROM daily_orders WHERE customer_id = 1 AND serve_date IN (?, ?)",
            dinnerServeDate,
            mergeServeDate
        );
    }

    @Test
    public void shouldCreateDinnerManualOrderFromMealPeriod() throws Exception {
        String payload = """
            {
                "customerId": 1,
                "addressId": %d,
                "mealPeriod": "DINNER",
                "merchantRemark": "-",
                "deliveryAddress": "测试地址",
                "source": "BACKEND",
                "quantity": 1,
                "serveDate": "%s"
            }
            """.formatted(ADDRESS_ID, dinnerServeDate);

        mockMvc.perform(post("/api/admin/orders/manual-create")
                .header("Authorization", adminAuthHeader)
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
            .andDo(print())
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING_DISPATCH"));

        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM meal_slot_orders mso
                    JOIN daily_orders do ON do.id = mso.daily_order_id
                    WHERE do.customer_id = 1
                      AND do.serve_date = ?
                      AND mso.meal_period = 'DINNER'
                      AND mso.delivery_meal_period = 'DINNER'
                      AND mso.status = 'PENDING_DISPATCH'
                    """,
                Integer.class,
                dinnerServeDate
            )
        );
        assertEquals(
            "测试地址",
            jdbcTemplate.queryForObject(
                """
                    SELECT ca.address_line
                    FROM meal_slot_orders mso
                    JOIN daily_orders do ON do.id = mso.daily_order_id
                    JOIN customer_addresses ca ON ca.id = mso.address_id
                    WHERE do.customer_id = 1
                      AND do.serve_date = ?
                      AND mso.meal_period = 'DINNER'
                    ORDER BY mso.id DESC
                    LIMIT 1
                    """,
                String.class,
                dinnerServeDate
            )
        );
    }

    @Test
    public void shouldMergeManualOrderQuantityForSameCustomerAddressAndMealPeriod() throws Exception {
        String firstPayload = """
            {
                "customerId": 1,
                "addressId": %d,
                "mealPeriod": "LUNCH",
                "merchantRemark": "再加一餐",
                "deliveryAddress": "测试地址",
                "source": "BACKEND",
                "quantity": 1,
                "serveDate": "%s"
            }
            """.formatted(ADDRESS_ID, mergeServeDate);
        String secondPayload = """
            {
                "customerId": 1,
                "addressId": %d,
                "mealPeriod": "LUNCH",
                "merchantRemark": "多菜",
                "deliveryAddress": "测试地址",
                "source": "BACKEND",
                "quantity": 1,
                "serveDate": "%s"
            }
            """.formatted(ADDRESS_ID, mergeServeDate);

        mockMvc.perform(post("/api/admin/orders/manual-create")
                .header("Authorization", adminAuthHeader)
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType(MediaType.APPLICATION_JSON)
                .content(firstPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("PENDING_DISPATCH"));

        mockMvc.perform(post("/api/admin/orders/manual-create")
                .header("Authorization", adminAuthHeader)
                .requestAttr("userId", 7L)
                .requestAttr("userType", "admin")
                .requestAttr("adminDisplayName", "运营A")
                .contentType(MediaType.APPLICATION_JSON)
                .content(secondPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("MERGED"));

        assertEquals(
            1,
            jdbcTemplate.queryForObject(
                """
                    SELECT COUNT(*)
                    FROM meal_slot_orders mso
                    JOIN daily_orders do ON do.id = mso.daily_order_id
                    WHERE do.customer_id = 1
                      AND do.serve_date = ?
                      AND mso.meal_period = 'LUNCH'
                      AND mso.delivery_meal_period = 'LUNCH'
                      AND mso.status = 'PENDING_DISPATCH'
                    """,
                Integer.class,
                mergeServeDate
            )
        );
        assertEquals(
            2,
            jdbcTemplate.queryForObject(
                """
                    SELECT mso.quantity
                    FROM meal_slot_orders mso
                    JOIN daily_orders do ON do.id = mso.daily_order_id
                    WHERE do.customer_id = 1
                      AND do.serve_date = ?
                      AND mso.meal_period = 'LUNCH'
                    ORDER BY mso.id DESC
                    LIMIT 1
                    """,
                Integer.class,
                mergeServeDate
            )
        );
        assertEquals(
            "再加一餐，多菜",
            jdbcTemplate.queryForObject(
                """
                    SELECT mso.merchant_remark
                    FROM meal_slot_orders mso
                    JOIN daily_orders do ON do.id = mso.daily_order_id
                    WHERE do.customer_id = 1
                      AND do.serve_date = ?
                      AND mso.meal_period = 'LUNCH'
                    ORDER BY mso.id DESC
                    LIMIT 1
                    """,
                String.class,
                mergeServeDate
            )
        );
    }
}
