package com.jzqs.app.customer.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jzqs.app.customer.api.CustomerProfileCreateRequest;
import com.jzqs.app.customer.api.CustomerProfileCreateResponse;
import com.jzqs.app.customer.service.CustomerAssetService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Transactional
class CustomerAssetServiceTransactionTest {

    @Autowired
    private CustomerAssetService customerAssetService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateCustomerAndGrantInitialMealsWithinSingleTransaction() {
        CustomerProfileCreateResponse result = customerAssetService.createCustomerProfile(
            new CustomerProfileCreateRequest(
                "事务建档客户",
                "13900008888",
                "测试建档加餐",
                null,
                "高新区软件园 A 座",
                6,
                "建档初始加餐",
                30,
                null,
                null,
                null
            )
        );

        Long customerId = result.customerId();
        assertNotNull(customerId);
        assertEquals("CREATED", result.status());

        Map<String, Object> wallet = jdbcTemplate.queryForMap(
            "SELECT id, total_meals, reserved_meals, consumed_meals, expired_at FROM meal_wallets WHERE customer_id = ? AND active = TRUE",
            customerId
        );
        assertEquals(6, ((Number) wallet.get("total_meals")).intValue());
        assertEquals(0, ((Number) wallet.get("reserved_meals")).intValue());
        assertEquals(0, ((Number) wallet.get("consumed_meals")).intValue());
        assertNotNull(wallet.get("expired_at"));

        Long walletId = ((Number) wallet.get("id")).longValue();
        Map<String, Object> transaction = jdbcTemplate.queryForMap(
            "SELECT transaction_type, meal_delta, operator_name, remark FROM wallet_transactions WHERE wallet_id = ? ORDER BY id DESC LIMIT 1",
            walletId
        );
        assertEquals("GRANT", transaction.get("transaction_type"));
        assertEquals(6, ((Number) transaction.get("meal_delta")).intValue());
        // 无 admin 上下文时 currentOperator() 返回系统默认
        assertEquals("系统", transaction.get("operator_name"));
        assertEquals("建档初始加餐", transaction.get("remark"));
    }

    @Test
    void shouldExposePackageValidityFieldsInCustomerAssets() {
        CustomerProfileCreateResponse result = customerAssetService.createCustomerProfile(
            new CustomerProfileCreateRequest(
                "有效期客户",
                "13900007777",
                "有效期验证",
                null,
                "天府软件园 B 区",
                2,
                "有效期测试",
                15,
                null,
                null,
                null
            )
        );

        Long customerId = result.customerId();
        var page = customerAssetService.listAssets(null, null, null, null, null);
        var item = page.items().stream()
            .filter(customer -> customer.id() == customerId)
            .findFirst()
            .orElseThrow();

        assertNotNull(item.packageExpiredAt());
        assertTrue(item.remainingValidityDays() >= 14);
        assertEquals("LOW_BALANCE", item.packageAlertCode());
    }
}
