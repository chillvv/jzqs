package com.jzqs.app.packageplan;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.jzqs.app.packageplan.api.GrantPackageResponse;
import com.jzqs.app.packageplan.service.PackageGrantService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class PackageGrantServiceIntegrationTest {

    @Autowired
    private PackageGrantService packageGrantService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM wallet_transactions WHERE wallet_id IN (SELECT id FROM meal_wallets WHERE customer_id = 9921)");
        jdbcTemplate.update("DELETE FROM meal_wallets WHERE customer_id = 9921");
        jdbcTemplate.update("DELETE FROM customers WHERE id = 9921");
        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source, active) VALUES (9921, '开卡测试客户', '13900009921', 'BACKEND', TRUE)"
        );
        // V20 迁移已加 uk_meal_wallets_active_customer 唯一键：同客户只能有 1 个活跃钱包。
        // 因此 seed 一个活跃 + 一个已停用钱包，验证 grant 后仍只有一个活跃。
        jdbcTemplate.update(
            "INSERT INTO meal_wallets (id, customer_id, package_plan_id, total_meals, reserved_meals, consumed_meals, active) VALUES (19921, 9921, 1, 10, 0, 0, TRUE)"
        );
        jdbcTemplate.update(
            "INSERT INTO meal_wallets (id, customer_id, package_plan_id, total_meals, reserved_meals, consumed_meals, active) VALUES (19922, 9921, 3, 0, 0, 0, FALSE)"
        );
    }

    @Test
    void shouldDeactivateDuplicateActiveWalletsWhenGrantingPackage() {
        GrantPackageResponse result = packageGrantService.grantPackage(9921L, "MONTH_33", 33, "后台客服", 7L);

        assertEquals(9921L, result.customerId());
        assertEquals("MONTH_33", result.packageCode());
        assertEquals(33, result.remainingMeals());

        Integer activeWalletCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM meal_wallets WHERE customer_id = 9921 AND active = TRUE",
            Integer.class
        );
        assertEquals(1, activeWalletCount);

        Map<String, Object> activeWallet = jdbcTemplate.queryForMap(
            "SELECT package_plan_id, total_meals, reserved_meals, consumed_meals FROM meal_wallets WHERE customer_id = 9921 AND active = TRUE"
        );
        assertEquals(1L, ((Number) activeWallet.get("package_plan_id")).longValue());
        assertEquals(33, ((Number) activeWallet.get("total_meals")).intValue());
        assertEquals(0, ((Number) activeWallet.get("reserved_meals")).intValue());
        assertEquals(0, ((Number) activeWallet.get("consumed_meals")).intValue());
    }
}
