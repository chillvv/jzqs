package com.jzqs.app.customer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.jzqs.app.customer.api.CustomerAddressActionResponse;
import com.jzqs.app.customer.api.CustomerAddressUpsertRequest;
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
                "高新区",
                true
            )
        );

        assertEquals(9913L, result.customerId());
        assertEquals("CREATED", result.status());

        Map<String, Object> row = jdbcTemplate.queryForMap(
            "SELECT contact_name, contact_phone, address_line, area_code, is_default FROM customer_addresses WHERE customer_id = 9913"
        );
        assertEquals("测试客户", row.get("contact_name"));
        assertEquals("13900009913", row.get("contact_phone"));
        assertEquals("高新区测试路 13 号", row.get("address_line"));
        assertEquals("高新区", row.get("area_code"));
        assertNotNull(row.get("is_default"));
    }
}
