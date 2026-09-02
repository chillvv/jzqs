package com.jzqs.app.dispatch.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import com.jzqs.app.common.realtime.RealtimeAudienceModule;
import com.jzqs.app.common.test.BaseDbIntegrationTest;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 回归测试：订单真实状态（meal_slot_orders.status）与派单状态（dispatch_assignments.status）
 * 的一致性 —— 8.26 事故（938c78b）：已终态订单（DELIVERED/CANCELLED）被"更换骑手"捞入并
 * 报"订单状态已变更，无法派单"；V24 迁移把存量脏数据对齐。
 */
class DispatchTerminalStatusTest extends BaseDbIntegrationTest {

    private DispatchAssignmentModule assignment;

    @BeforeEach
    void setUp() {
        resetTables();
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        jdbc.update(
            "INSERT INTO customers (id, name, phone, source, active, customer_status, is_priority_customer) "
                + "VALUES (1, '测试客户', '13800138000', 'TEST', 1, 'ACTIVE', 0)"
        );
        jdbc.update(
            "INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default) "
                + "VALUES (101, 1, '测试客户', '13800138000', '测试路1号', '老城区', 0)"
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
            tomorrow
        );

        assignment = new DispatchAssignmentModule(jdbc, new DispatchBatchModule(jdbc), mock(RealtimeAudienceModule.class));
    }

    @Test
    @DisplayName("终态订单（DELIVERED）被派单必须被拒绝，不得回退为派送中")
    void deliveredOrderCannotBeReDispatched() {
        jdbc.update(
            "INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, status) "
                + "VALUES (301, 201, 'LUNCH', 'LUNCH', 1, 101, 'DELIVERED')"
        );
        // 脏数据：订单已终态但派单记录仍是活跃态（V24 修复前的形态）
        jdbc.update(
            "INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, rider_profile_id, area_code, status, sequence_number) "
                + "VALUES (1, 301, '张三', 5, '老城区', 'DISPATCHING', 1)"
        );

        assertThatThrownBy(() -> assignment.assignOrder(301, "张三", "老城区"))
            .as("已送达订单不可被重新派单（8.26 事故回归）")
            .isInstanceOf(BusinessException.class)
            .extracting(ex -> ((BusinessException) ex).getErrorCode())
            .isEqualTo(ErrorCode.ORDER_STATUS_INVALID);

        // 订单状态不得被非法回退
        String orderStatus = jdbc.queryForObject("SELECT status FROM meal_slot_orders WHERE id = 301", String.class);
        assertThat(orderStatus).isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("V24 对齐：终态订单的派单记录同步为 DELIVERED")
    void v24AlignmentSyncsTerminalStatus() {
        jdbc.update(
            "INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, status) "
                + "VALUES (301, 201, 'LUNCH', 'LUNCH', 1, 101, 'DELIVERED')"
        );
        jdbc.update(
            "INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, rider_profile_id, area_code, status, sequence_number) "
                + "VALUES (1, 301, '张三', 5, '老城区', 'DISPATCHING', 1)"
        );

        // 与 V24 迁移第 1 步相同的对齐 SQL
        jdbc.update(
            """
                UPDATE dispatch_assignments da
                JOIN meal_slot_orders mso ON mso.id = da.meal_slot_order_id
                SET da.status = 'DELIVERED'
                WHERE mso.status = 'DELIVERED'
                  AND da.status <> 'DELIVERED'
                """
        );

        String assignmentStatus = jdbc.queryForObject(
            "SELECT status FROM dispatch_assignments WHERE meal_slot_order_id = 301",
            String.class
        );
        assertThat(assignmentStatus)
            .as("V24 对齐后派单记录必须与订单终态一致")
            .isEqualTo("DELIVERED");
    }

    @Test
    @DisplayName("待派单订单可正常派单：meal_slot_orders 与 dispatch_assignments 均变 DISPATCHING")
    void pendingOrderCanBeDispatched() {
        jdbc.update(
            "INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, status) "
                + "VALUES (302, 201, 'LUNCH', 'LUNCH', 1, 101, 'PENDING_DISPATCH')"
        );

        assignment.assignOrder(302, "张三", "老城区");

        String orderStatus = jdbc.queryForObject("SELECT status FROM meal_slot_orders WHERE id = 302", String.class);
        String assignmentStatus = jdbc.queryForObject(
            "SELECT status FROM dispatch_assignments WHERE meal_slot_order_id = 302", String.class
        );
        assertThat(orderStatus).isEqualTo("DISPATCHING");
        assertThat(assignmentStatus).isEqualTo("DISPATCHING");
    }
}
