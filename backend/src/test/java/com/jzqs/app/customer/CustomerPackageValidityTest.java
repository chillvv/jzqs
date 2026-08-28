package com.jzqs.app.customer;

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
class CustomerPackageValidityTest {

    @Autowired
    private CustomerAssetService customerAssetService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldPersistWalletExpiryAndExposeAlertFields() {
        CustomerProfileCreateResponse result = customerAssetService.createCustomerProfile(
            new CustomerProfileCreateRequest(
                "套餐有效期客户",
                "13900006666",
                "有效期建档",
                null,
                "高新区天府软件园 C 座",
                2,
                "建档初始加餐",
                15,
                null,
                null,
                null
            )
        );

        long customerId = result.customerId();
        Map<String, Object> wallet = jdbcTemplate.queryForMap(
            "SELECT expired_at, total_meals FROM meal_wallets WHERE customer_id = ? AND active = TRUE",
            customerId
        );
        assertNotNull(wallet.get("expired_at"));
        assertEquals(2, ((Number) wallet.get("total_meals")).intValue());

        var page = customerAssetService.listAssets(null, null, null, null, null);
        var item = page.items().stream()
            .filter(customer -> customer.id() == customerId)
            .findFirst()
            .orElseThrow();

        assertEquals("LOW_BALANCE", item.packageAlertCode());
        assertNotNull(item.packageExpiredAt());
        assertTrue(item.remainingValidityDays() >= 14);
    }
}
