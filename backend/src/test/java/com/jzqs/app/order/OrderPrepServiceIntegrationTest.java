package com.jzqs.app.order;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jzqs.app.order.api.ManualCreateCustomerSearchResponse;
import com.jzqs.app.order.api.OrderProfileUpdateRequest;
import com.jzqs.app.order.service.OrderPrepService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class OrderPrepServiceIntegrationTest {
    private static final long PROFILE_CUSTOMER_ID = 9910L;

    @Autowired
    private OrderPrepService orderPrepService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareOrderForAddressChange() {
        LocalDate serveDate = LocalDate.now().plusDays(2);
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE customer_id IN (9901, 9902, 9903)");
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE wallet_id IN (SELECT id FROM meal_wallets WHERE customer_id IN (9901, 9902, 9903))");
        jdbcTemplate.update("DELETE FROM meal_wallets WHERE customer_id IN (9901, 9902, 9903)");
        jdbcTemplate.update("DELETE FROM customers WHERE id IN (9901, 9902, 9903)");
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE daily_order_id IN (SELECT id FROM daily_orders WHERE customer_id = ?)", PROFILE_CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM daily_orders WHERE customer_id = ?", PROFILE_CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE customer_id = ?", PROFILE_CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM customers WHERE id = ?", PROFILE_CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id = 9101");
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id = 9101");
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id IN (9101, 9102)");

        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source, active) VALUES (?, '测试改址客户', '13900009910', 'BACKEND', TRUE)",
            PROFILE_CUSTOMER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default)
                VALUES
                  (9101, ?, '测试改址客户', '13900009910', '测试旧地址A座', '高新区', TRUE),
                  (9102, ?, '测试改址客户', '13900009910', '测试新地址B座', '高新区', FALSE)
                """
            ,
            PROFILE_CUSTOMER_ID,
            PROFILE_CUSTOMER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at)
                VALUES (9101, ?, ?, 'BACKEND', 'PENDING_DISPATCH', FALSE, CURRENT_TIMESTAMP)
                """,
            PROFILE_CUSTOMER_ID,
            serveDate
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, merchant_remark, status, source_type
                ) VALUES (
                    9101, 9101, 'LUNCH', 'LUNCH', 1, 9101, '少饭', '少饭', '少饭', 'PENDING_DISPATCH', 'BACKEND'
                )
                """
        );
    }

    @Test
    void shouldSearchManualCreateCustomersBySingleCharacterName() {
        insertManualSearchCustomer(9901L, "阿木", "99887766554", "测试单字地址A", 12);

        List<ManualCreateCustomerSearchResponse> result = orderPrepService.searchManualCreateCustomers("阿");

        assertFalse(result.isEmpty());
        assertEquals(9901L, result.get(0).customerId());
        assertEquals("阿木", result.get(0).customerName());
    }

    @Test
    void shouldPrioritizeNameMatchesForShortNumericKeywordInManualCustomerSearch() {
        // 用 9901 手机号的唯一后缀做关键字，避免与 V1 种子大量客户（id 更大且按 id DESC 排前）冲突
        insertManualSearchCustomer(9901L, "1号客户", "99887766554", "测试数字姓名地址", 9);
        insertManualSearchCustomer(9902L, "普通客户", "18812345678", "测试手机号地址", 6);

        List<ManualCreateCustomerSearchResponse> result = orderPrepService.searchManualCreateCustomers("6554");

        assertFalse(result.isEmpty());
        int nameMatchIndex = indexOfCustomer(result, 9901L);
        assertTrue(nameMatchIndex >= 0);
        assertEquals("1号客户", result.get(nameMatchIndex).customerName());
    }

    @Test
    void shouldSumRemainingMealsAcrossActiveWalletsWhenManualCustomerSearchFindsDuplicateWallets() {
        // 用不与 V1 种子冲突的手机号（V1 中有客户 912 吴慧 15356987071）
        insertManualSearchCustomerWithoutAddress(9903L, "重复钱包客户", "18800009903");
        // V20 唯一键 uk_meal_wallets_active_customer 禁止同客户多个活跃钱包：
        // 因此 seed 1 个活跃(100) + 1 个已停用(0) 钱包，验证求和只计活跃钱包
        jdbcTemplate.update(
            "INSERT INTO meal_wallets (id, customer_id, package_plan_id, total_meals, reserved_meals, consumed_meals, active) VALUES (?, ?, NULL, ?, 0, 0, TRUE)",
            19903L,
            9903L,
            100
        );
        jdbcTemplate.update(
            "INSERT INTO meal_wallets (id, customer_id, package_plan_id, total_meals, reserved_meals, consumed_meals, active) VALUES (?, ?, NULL, ?, 0, 0, FALSE)",
            19904L,
            9903L,
            0
        );

        List<ManualCreateCustomerSearchResponse> result = orderPrepService.searchManualCreateCustomers("18800009903");

        assertFalse(result.isEmpty());
        assertEquals(9903L, result.get(0).customerId());
        assertEquals(100, result.get(0).remainingMeals());
        assertEquals(0, result.get(0).addresses().size());
    }

    @Test
    void shouldUpdateOrderAddressWhenAdminChangesDeliveryAddress() {
        orderPrepService.updateOrderProfile(9101L, new OrderProfileUpdateRequest(
            null,
            null,
            null,
            9102L,
            "客服联系后改地址",
            null,
            null
        ));

        Long currentAddressId = jdbcTemplate.queryForObject(
            "SELECT address_id FROM meal_slot_orders WHERE id = 9101",
            Long.class
        );
        assertEquals(9102L, currentAddressId);

        Map<String, Object> orderRow = jdbcTemplate.queryForMap(
            "SELECT merchant_remark FROM meal_slot_orders WHERE id = 9101"
        );
        assertEquals("客服联系后改地址", orderRow.get("merchant_remark"));
    }

    private int indexOfCustomer(List<ManualCreateCustomerSearchResponse> result, long customerId) {
        for (int i = 0; i < result.size(); i++) {
            if (result.get(i).customerId() == customerId) {
                return i;
            }
        }
        return -1;
    }

    private void insertManualSearchCustomer(long customerId, String name, String phone, String addressLine, int totalMeals) {
        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source, active) VALUES (?, ?, ?, 'BACKEND', TRUE)",
            customerId,
            name,
            phone
        );
        jdbcTemplate.update(
            "INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) VALUES (?, ?, ?, ?, ?, '测试区域', TRUE)",
            customerId,
            customerId,
            name,
            phone,
            addressLine
        );
        jdbcTemplate.update(
            "INSERT INTO meal_wallets (id, customer_id, package_plan_id, total_meals, reserved_meals, consumed_meals, active) VALUES (?, ?, NULL, ?, 0, 0, TRUE)",
            customerId,
            customerId,
            totalMeals
        );
    }

    private void insertManualSearchCustomerWithoutAddress(long customerId, String name, String phone) {
        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source, active) VALUES (?, ?, ?, 'BACKEND', TRUE)",
            customerId,
            name,
            phone
        );
    }
}
