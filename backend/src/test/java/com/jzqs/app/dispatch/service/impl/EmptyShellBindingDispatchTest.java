package com.jzqs.app.dispatch.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.jzqs.app.common.realtime.RealtimeAudienceModule;
import com.jzqs.app.common.test.BaseDbIntegrationTest;
import com.jzqs.app.dispatch.api.DispatchPendingItemResponse;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 回归测试：空壳绑定记录（area_code=''、rider_profile_id=NULL）不得把订单从分单工作台"遮挡"掉。
 *
 * 背景（8.24 生产事故）：客户改地址到新地址时，reconcileDispatchArea 会写入一条
 * area_code=''、rider_profile_id=NULL 的"空壳"绑定。旧实现分单工作台用
 * LEFT JOIN rider_address_bindings + rab.id IS NULL 判断"是否已认领"，
 * 空壳记录让订单被误判为已认领、从工作台过滤掉 → 订单既不在工作台也没被分配（静默死锁）。
 *
 * 修复后的正确语义：只有 rider_profile_id 非空（真实记忆）的绑定才算"已认领"，
 * 空壳记录应让订单继续留在待派单列表。
 */
class EmptyShellBindingDispatchTest extends BaseDbIntegrationTest {

    private DispatchQueryModule query;
    private DispatchAssignmentModule assignment;

    @BeforeEach
    void setUp() {
        resetTables();
        // pendingItems 按 serve_date = 今天过滤，fixture 用今天
        LocalDate today = LocalDate.now();
        jdbc.update(
            "INSERT INTO customers (id, name, phone, source, active, customer_status, is_priority_customer) "
                + "VALUES (1, '测试客户', '13800138000', 'TEST', 1, 'ACTIVE', 0)"
        );
        jdbc.update(
            "INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) "
                + "VALUES (101, 1, '测试客户', '13800138000', '测试路1号', '老城区', 0), "
                + "       (102, 1, '测试客户', '13800138000', '新地址路2号', '', 0)"
        );
        jdbc.update(
            "INSERT INTO rider_profiles (id, rider_name, phone, employment_status, default_area_code, auth_status) "
                + "VALUES (5, '张三', '13900139000', 'ACTIVE', '老城区', 'ACTIVE')"
        );
        jdbc.update(
            "INSERT INTO dispatch_area_bindings (id, area_code, default_rider_profile_id, backup_rider_profile_id, updated_by) "
                + "VALUES (1, '老城区', 5, NULL, 'SYSTEM')"
        );
        jdbc.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status) VALUES (201, 1, ?, 'ADMIN', 'ACTIVE')",
            today
        );
        // 301：地址 102（空壳绑定）；302：地址 101（真实绑定 + 区域已绑定骑手）
        // 同客户同一天只允许一条 daily_orders（uk_daily_orders_customer_date），两个订单共用 201
        jdbc.update(
            "INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, status) "
                + "VALUES (301, 201, 'LUNCH', 'LUNCH', 1, 102, 'PENDING_DISPATCH'), "
                + "       (302, 201, 'LUNCH', 'LUNCH', 1, 101, 'PENDING_DISPATCH')"
        );
        // 空壳绑定（改址待确认，area_code=''、rider_profile_id=NULL）+ 真实绑定
        jdbc.update(
            "INSERT INTO rider_address_bindings (customer_id, address_id, meal_period, address_fingerprint, area_code, rider_profile_id, manually_confirmed, updated_reason) "
                + "VALUES (1, 102, 'LUNCH', '新地址路2号', '', NULL, 0, 'ADDRESS_CHANGED_PENDING_CONFIRM'), "
                + "       (1, 101, 'LUNCH', '测试路1号', '老城区', 5, 1, 'AREA_CONFIRMED')"
        );

        assignment = new DispatchAssignmentModule(jdbc, new DispatchBatchModule(jdbc), mock(RealtimeAudienceModule.class));
        query = new DispatchQueryModule(jdbc, assignment);
    }

    @Test
    @DisplayName("空壳绑定不遮挡：订单 301（空壳地址）必须出现在待派单列表")
    void emptyShellBindingMustNotHideOrder() {
        List<DispatchPendingItemResponse> pending = query.pendingItems("LUNCH", null);

        List<Long> orderIds = pending.stream().map(DispatchPendingItemResponse::orderId).toList();
        assertThat(orderIds)
            .as("空壳绑定记录不得把订单从分单工作台过滤掉（8.24 死锁根因回归）")
            .contains(301L);
    }

    @Test
    @DisplayName("真实绑定的订单被自动派单：订单 302 不在待派单列表，且已有 DISPATCHING 派单记录")
    void rememberedBindingOrderGetsAutoDispatched() {
        List<DispatchPendingItemResponse> pending = query.pendingItems("LUNCH", null);

        List<Long> orderIds = pending.stream().map(DispatchPendingItemResponse::orderId).toList();
        assertThat(orderIds)
            .as("有真实记忆的订单应被自动派单，不应留在待派单列表")
            .doesNotContain(302L);

        String assignmentStatus = jdbc.queryForObject(
            "SELECT status FROM dispatch_assignments WHERE meal_slot_order_id = 302",
            String.class
        );
        assertThat(assignmentStatus).isEqualTo("DISPATCHING");
    }

    @Test
    @DisplayName("空壳绑定订单不会被自动派单占用 dispatch_assignments（保持待商家确认）")
    void emptyShellBindingMustNotCreateAssignment() {
        query.pendingItems("LUNCH", null);

        Integer assignedCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id = 301",
            Integer.class
        );
        assertThat(assignedCount)
            .as("空壳绑定（area_code=''）不应触发自动派单写 dispatch_assignments")
            .isEqualTo(0);
    }

    @Test
    @DisplayName("跨餐期记忆不遮挡：地址只有 DINNER 记忆时，LUNCH 订单必须留在待派单列表（9.2 程梦雅事故回归）")
    void crossMealPeriodMemoryMustNotHideOrder() {
        // 地址 102 只有 DINNER 餐期的已确认记忆（无 LUNCH / 通用记忆）
        jdbc.update(
            "UPDATE rider_address_bindings SET meal_period = 'DINNER', area_code = '老城区', rider_profile_id = 5, "
                + "manually_confirmed = 1, updated_reason = 'AREA_CONFIRMED' "
                + "WHERE customer_id = 1 AND address_id = 102"
        );

        List<DispatchPendingItemResponse> pending = query.pendingItems("LUNCH", null);

        List<Long> orderIds = pending.stream().map(DispatchPendingItemResponse::orderId).toList();
        assertThat(orderIds)
            .as("DINNER 记忆不得充当 LUNCH 记忆：订单必须出现在工作台待派单列表，由商家手动归区")
            .contains(301L);

        Integer assignedCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM dispatch_assignments WHERE meal_slot_order_id = 301",
            Integer.class
        );
        assertThat(assignedCount)
            .as("跨餐期记忆不应触发自动派单")
            .isEqualTo(0);
    }
}
