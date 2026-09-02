package com.jzqs.app.dispatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.realtime.TransactionalRealtimePublisher;
import com.jzqs.app.dispatch.service.DispatchService;
import com.jzqs.app.dispatch.service.route.DispatchRouteAiRefineService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 区域删除一致性测试（软删除方案，V31）。
 *
 * 覆盖用户核心场景：
 *  1. 无订单区域 -> 物理删除，骑手解绑，地址绑定删除；
 *  2. 有已送达历史订单区域 -> 软删除（active=0），历史订单仍显示原区域名（不产生孤儿），骑手解绑；
 *  3. 有配送中订单区域 -> 删除被拦截，区域保持不变。
 */
@SpringBootTest
class DispatchAreaDeleteSoftTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DispatchService dispatchService;

    @MockBean
    private TransactionalRealtimePublisher realtimeEventPublisher;

    @MockBean
    private DispatchRouteAiRefineService dispatchRouteAiRefineService;

    @BeforeEach
    void resetFixtures() {
        jdbcTemplate.update("DELETE FROM dispatch_assignments WHERE meal_slot_order_id >= 920");
        jdbcTemplate.update("DELETE FROM dispatch_area_bindings WHERE area_code IN ('空区920', '历史区921', '配送区922')");
        jdbcTemplate.update("DELETE FROM rider_address_bindings WHERE customer_id >= 920");
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id >= 920");
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id >= 920");
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id >= 920");
        jdbcTemplate.update("DELETE FROM rider_profiles WHERE id >= 920");
        jdbcTemplate.update("DELETE FROM customers WHERE id >= 920");
    }

    @Test
    void deleteAreaWithoutOrdersShouldPhysicallyDeleteAndUnbindRider() {
        insertCustomer(920L, "删区测试客户920", "13800000920");
        insertAddress(920L, 920L, "空区920路1号", "空区920");
        insertRiderProfile(920L, "骑手空区920");
        insertAreaBinding("空区920", 920L);
        jdbcTemplate.update("UPDATE rider_profiles SET default_area_code = '空区920' WHERE id = 920");
        insertAddressBinding(920L, 920L, "空区920");

        var response = dispatchService.deleteArea("空区920");

        assertEquals("DELETED", response.status());
        // 区域行被物理删除
        assertEquals(0, countRows("dispatch_area_bindings", "area_code", "空区920"));
        // 骑手档案解绑归属区域
        assertNull(queryDefaultAreaCode(920L));
        // 地址-骑手绑定删除
        assertEquals(0, countRows("rider_address_bindings", "area_code", "空区920"));
    }

    @Test
    void deleteAreaWithDeliveredOrdersShouldSoftDeleteAndKeepHistory() {
        insertCustomer(921L, "删区测试客户921", "13800000921");
        insertAddress(921L, 921L, "历史区921路1号", "历史区921");
        insertDailyOrder(921L, 921L, "DELIVERED");
        insertMealSlotOrder(921L, 921L, "LUNCH", 921L, "DELIVERED");
        insertRiderProfile(921L, "骑手历史921");
        insertAreaBinding("历史区921", 921L);
        jdbcTemplate.update("UPDATE rider_profiles SET default_area_code = '历史区921' WHERE id = 921");
        insertAddressBinding(921L, 921L, "历史区921");
        insertAssignment(921L, "骑手历史921", 921L, "历史区921", "DELIVERED");

        var response = dispatchService.deleteArea("历史区921");

        assertEquals("DEACTIVATED", response.status());
        // 区域行保留但已停用
        assertEquals(0, queryActive("历史区921"));
        assertEquals(1, countRows("dispatch_area_bindings", "area_code", "历史区921"));
        // 骑手档案解绑归属区域
        assertNull(queryDefaultAreaCode(921L));
        // 历史订单仍指向原区域（不产生孤儿）
        assertEquals("历史区921", queryAssignmentAreaCode(921L));
        // 地址-骑手绑定删除
        assertEquals(0, countRows("rider_address_bindings", "area_code", "历史区921"));
        // 区域列表不再展示已停用区域
        var areas = dispatchService.areaBindings("LUNCH", null);
        assertTrue(areas.stream().noneMatch(a -> "历史区921".equals(a.areaCode())),
            "已停用区域不应出现在区域列表");
    }

    @Test
    void deleteAreaWithDispatchingOrderShouldBeBlocked() {
        insertCustomer(922L, "删区测试客户922", "13800000922");
        insertAddress(922L, 922L, "配送区922路1号", "配送区922");
        insertDailyOrder(922L, 922L, "DISPATCHING");
        insertMealSlotOrder(922L, 922L, "LUNCH", 922L, "DISPATCHING");
        insertRiderProfile(922L, "骑手配送922");
        insertAreaBinding("配送区922", 922L);
        insertAssignment(922L, "骑手配送922", 922L, "配送区922", "DISPATCHING");

        BusinessException ex = assertThrows(BusinessException.class,
            () -> dispatchService.deleteArea("配送区922"));

        assertEquals(ErrorCode.DISPATCH_AREA_HAS_ACTIVE_ORDERS, ex.getErrorCode());
        // 区域行保持不变（仍启用）
        assertEquals(1, queryActive("配送区922"));
        assertEquals(1, countRows("dispatch_area_bindings", "area_code", "配送区922"));
    }

    private void insertCustomer(long id, String name, String phone) {
        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source) VALUES (?, ?, ?, 'BACKEND')",
            id, name, phone
        );
    }

    private void insertAddress(long id, long customerId, String addressLine, String areaCode) {
        jdbcTemplate.update(
            """
                INSERT INTO customer_addresses (
                    id, customer_id, contact_name, contact_phone, address_line, area_code, is_default
                ) VALUES (?, ?, ?, ?, ?, ?, TRUE)
                """,
            id, customerId, "联系人" + id, "1380000" + id, addressLine, areaCode
        );
    }

    private void insertDailyOrder(long id, long customerId, String status) {
        jdbcTemplate.update(
            """
                INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at)
                VALUES (?, ?, ?, 'MINIAPP', ?, FALSE, CURRENT_TIMESTAMP)
                """,
            id, customerId, LocalDate.of(2026, 5, 18), status
        );
    }

    private void insertMealSlotOrder(long id, long dailyOrderId, String mealPeriod, long addressId, String status) {
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, status, source_type
                ) VALUES (?, ?, ?, ?, 1, ?, '-', ?, 'MINIAPP')
                """,
            id, dailyOrderId, mealPeriod, mealPeriod, addressId, status
        );
    }

    private void insertRiderProfile(long id, String riderName) {
        jdbcTemplate.update(
            """
                INSERT INTO rider_profiles (
                    id, rider_name, display_name, phone, current_openid, auth_status, employment_status,
                    default_area_code, assigned_by, first_login_at, last_login_at, created_at
                ) VALUES (
                    ?, ?, ?, ?, ?, 'ACTIVE', 'ACTIVE',
                    NULL, '老板', TIMESTAMP '2026-05-15 08:00:00', TIMESTAMP '2026-05-15 11:30:00', CURRENT_TIMESTAMP
                )
                """,
            id, riderName, riderName, "1380000" + id, "rider_openid_" + id
        );
    }

    private void insertAreaBinding(String areaCode, Long defaultRiderProfileId) {
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_area_bindings (
                    area_code, keywords, default_rider_profile_id, backup_rider_profile_id, updated_by, updated_at, active
                ) VALUES (?, ?, ?, NULL, '老板', CURRENT_TIMESTAMP, 1)
                """,
            areaCode, areaCode, defaultRiderProfileId
        );
    }

    private void insertAssignment(long orderId, String riderName, Long riderProfileId, String areaCode, String status) {
        jdbcTemplate.update(
            """
                INSERT INTO dispatch_assignments (
                    meal_slot_order_id, rider_name, rider_profile_id, area_code, status, sequence_number, created_at
                ) VALUES (?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP)
                """,
            orderId, riderName, riderProfileId, areaCode, status
        );
    }

    private void insertAddressBinding(long customerId, long addressId, String areaCode) {
        jdbcTemplate.update(
            """
                INSERT INTO rider_address_bindings (
                    customer_id, address_id, meal_period, address_fingerprint, area_code,
                    rider_profile_id, manually_confirmed, updated_reason, updated_at
                ) VALUES (?, ?, 'LUNCH', ?, ?, NULL, TRUE, 'TEST', CURRENT_TIMESTAMP)
                """,
            customerId, addressId, areaCode, areaCode
        );
    }

    private int countRows(String tableName, String columnName, String value) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM " + tableName + " WHERE " + columnName + " = ?",
            Integer.class,
            value
        );
        return count == null ? 0 : count;
    }

    private Integer queryActive(String areaCode) {
        return jdbcTemplate.queryForObject(
            "SELECT active FROM dispatch_area_bindings WHERE area_code = ?",
            Integer.class,
            areaCode
        );
    }

    private String queryDefaultAreaCode(long riderId) {
        return jdbcTemplate.queryForObject(
            "SELECT default_area_code FROM rider_profiles WHERE id = ?",
            String.class,
            riderId
        );
    }

    private String queryAssignmentAreaCode(long orderId) {
        return jdbcTemplate.queryForObject(
            "SELECT area_code FROM dispatch_assignments WHERE meal_slot_order_id = ?",
            String.class,
            orderId
        );
    }
}
