package com.jzqs.app.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.jzqs.app.customer.api.CustomerAddressActionResponse;
import com.jzqs.app.customer.api.CustomerAddressUpsertRequest;
import com.jzqs.app.customer.api.CustomerProfileUpdateRequest;
import com.jzqs.app.customer.service.CustomerAssetService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class CustomerAssetServiceIntegrationTest {

    @Autowired
    private CustomerAssetService customerAssetService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE customer_id = 9913");
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE wallet_id IN (SELECT id FROM meal_wallets WHERE customer_id = 9913)");
        jdbcTemplate.update("DELETE FROM meal_wallets WHERE customer_id = 9913");
        jdbcTemplate.update("DELETE FROM customers WHERE id = 9913");
        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source, active) VALUES (9913, '测试客户', '13900009913', 'BACKEND', TRUE)"
        );
    }

    @Test
    void shouldCreateCustomerAddressForActiveCustomer() {
        CustomerAddressActionResponse result = customerAssetService.createCustomerAddress(
            9913L,
            new CustomerAddressUpsertRequest(
                "测试联系人",
                "13900009913",
                "高新区测试路 13 号",
                null,
                "高新区",
                true,
                new java.math.BigDecimal("30.545420"),
                new java.math.BigDecimal("104.062500")
            )
        );

        assertEquals(9913L, result.customerId());
        assertEquals("CREATED", result.status());

        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT contact_name, contact_phone, address_line, area_code, is_default, latitude, longitude FROM customer_addresses WHERE customer_id = 9913"
        );
        assertEquals("测试客户", row.get("contact_name"));
        assertEquals("13900009913", row.get("contact_phone"));
        assertEquals("高新区测试路 13 号", row.get("address_line"));
        assertEquals("高新区", row.get("area_code"));
        assertNotNull(row.get("is_default"));
        // 未定位地址已被禁止：新地址必须带坐标落库
        assertNotNull(row.get("latitude"));
        assertNotNull(row.get("longitude"));
    }

    @Test
    void shouldRejectCustomerAddressWithoutCoordinates() {
        org.junit.jupiter.api.Assertions.assertThrows(
            com.jzqs.app.common.error.BusinessException.class,
            () -> customerAssetService.createCustomerAddress(
                9913L,
                new CustomerAddressUpsertRequest(
                    "测试联系人", "13900009913", "高新区测试路 13 号", null, "高新区", true, null, null
                )
            )
        );
    }

    @Test
    void shouldAttachSubscriptionRuleToNewlyCreatedAddress() {
        // 地址重置场景：订阅规则存在但 default_address_id 为空；
        // 后台补录地址后规则必须自动回挂到新地址，固定订餐不再"没有地址"。
        jdbcTemplate.update(
            "INSERT INTO subscription_rules (customer_id, active, paused, week_days, lunch_enabled, dinner_enabled, start_date, end_date, default_address_id, created_at, updated_at) "
                + "VALUES (9913, TRUE, FALSE, '1,2,3,4,5', TRUE, FALSE, '2026-09-01', '2027-09-01', NULL, NOW(), NOW())"
        );
        try {
            CustomerAddressActionResponse result = customerAssetService.createCustomerAddress(
                9913L,
                new CustomerAddressUpsertRequest(
                    "测试联系人", "13900009913", "高新区测试路 13 号", null, "高新区", true,
                    new java.math.BigDecimal("30.545420"), new java.math.BigDecimal("104.062500")
                )
            );
            assertEquals("CREATED", result.status());

            Long ruleAddressId = jdbcTemplate.queryForObject(
                "SELECT default_address_id FROM subscription_rules WHERE customer_id = 9913",
                Long.class
            );
            assertNotNull(ruleAddressId);
            assertEquals(result.addressId(), ruleAddressId);
        } finally {
            jdbcTemplate.update("DELETE FROM subscription_rules WHERE customer_id = 9913");
        }
    }

    @Test
    void shouldClearMerchantRemarkWhenUpdatingProfileWithBlankRemark() {
        // 商家先给客户写过永久备注，之后想清空
        jdbcTemplate.update("UPDATE customers SET merchant_remark = '重点客户' WHERE id = 9913");

        customerAssetService.updateCustomerProfile(9913L, new CustomerProfileUpdateRequest(
            null, null, "", null, null, null, null, null, null, null, null
        ));

        String remark = jdbcTemplate.queryForObject(
            "SELECT merchant_remark FROM customers WHERE id = 9913",
            String.class
        );
        assertNull(remark);
    }

    @Test
    void shouldUpdateMerchantRemarkWhenUpdatingProfileWithNewValue() {
        jdbcTemplate.update("UPDATE customers SET merchant_remark = '旧备注' WHERE id = 9913");

        customerAssetService.updateCustomerProfile(9913L, new CustomerProfileUpdateRequest(
            null, null, "新备注", null, null, null, null, null, null, null, null
        ));

        String remark = jdbcTemplate.queryForObject(
            "SELECT merchant_remark FROM customers WHERE id = 9913",
            String.class
        );
        assertEquals("新备注", remark);
    }
}
