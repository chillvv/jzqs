package com.jzqs.app.dispatch.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.realtime.RealtimeAudienceModule;
import com.jzqs.app.common.test.BaseDbIntegrationTest;
import com.jzqs.app.dispatch.api.DispatchAreaBlockingOrderResponse;
import com.jzqs.app.dispatch.api.DispatchAreaOrderItemResponse;
import com.jzqs.app.dispatch.api.DispatchPendingItemResponse;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 回归测试：分单端（分单工作台 / 区域队列 / 区域删除拦截）展示的配送地址必须带上门牌号。
 *
 * 背景：V33 把客户地址拆成 address_line（定位地址，如「武汉理工大学南湖校区」）与
 * door_number（门牌号 / 哪个门，如「东门」），骑手端与顾客端一直按
 * 「address_line + 门牌号」拼接展示。但派单中心各查询只取 address_line，
 * 导致商家分单时只能看到学校名、看不到具体是哪个门，无法正确归区。
 *
 * 正确语义：分单端与骑手端拼接口径一致；门牌号为空时不得留下多余空格。
 */
class DispatchAddressDoorNumberTest extends BaseDbIntegrationTest {

    private static final String CAMPUS = "武汉理工大学南湖校区";
    private static final String AREA_CODE = "南湖校区";

    private DispatchQueryModule query;
    private DispatchAreaAdminModule areaAdmin;

    @BeforeEach
    void setUp() {
        resetTables();
        LocalDate today = LocalDate.now();
        jdbc.update(
            "INSERT INTO customers (id, name, phone, source, active, customer_status, is_priority_customer) "
                + "VALUES (1, '测试客户', '13800138000', 'TEST', 1, 'ACTIVE', 0)"
        );
        // 101 有门牌号（东门）；102 无门牌号（仅定位地址）
        jdbc.update(
            "INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, door_number, area_code, is_default) "
                + "VALUES (101, 1, '测试客户', '13800138000', ?, '东门', ?, 0), "
                + "       (102, 1, '测试客户', '13800138000', ?, NULL, ?, 0)",
            CAMPUS, AREA_CODE, CAMPUS, AREA_CODE
        );
        jdbc.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status) VALUES (201, 1, ?, 'ADMIN', 'ACTIVE')",
            today
        );
        jdbc.update(
            "INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, status) "
                + "VALUES (301, 201, 'LUNCH', 'LUNCH', 1, 101, 'PENDING_DISPATCH'), "
                + "       (302, 201, 'LUNCH', 'LUNCH', 1, 102, 'PENDING_DISPATCH')"
        );

        DispatchAssignmentModule assignment =
            new DispatchAssignmentModule(jdbc, new DispatchBatchModule(jdbc), mock(RealtimeAudienceModule.class));
        query = new DispatchQueryModule(jdbc, assignment);
        areaAdmin = new DispatchAreaAdminModule(jdbc, assignment);
    }

    @Test
    @DisplayName("分单工作台待分配列表：地址必须带门牌号，商家才能区分同一学校的不同门")
    void pendingItemsMustShowDoorNumber() {
        Map<Long, String> addressByOrderId = query.pendingItems("LUNCH", null).stream()
            .collect(Collectors.toMap(DispatchPendingItemResponse::orderId, DispatchPendingItemResponse::deliveryAddress));

        assertThat(addressByOrderId.get(301L))
            .as("有门牌号的地址必须拼接展示（南湖校区 + 东门）")
            .isEqualTo(CAMPUS + " 东门");
        assertThat(addressByOrderId.get(302L))
            .as("门牌号为空时只展示定位地址，且不得留下多余空格")
            .isEqualTo(CAMPUS);
    }

    @Test
    @DisplayName("区域队列订单：地址同样必须带门牌号（区域详情与骑手进度共用该查询）")
    void areaOrdersMustShowDoorNumber() {
        insertAssignments();

        Map<Long, String> addressByOrderId = query.areaBindings("LUNCH", LocalDate.now().toString()).stream()
            .filter(binding -> AREA_CODE.equals(binding.areaCode()))
            .flatMap(binding -> binding.orders().stream())
            .collect(Collectors.toMap(
                DispatchAreaOrderItemResponse::orderId,
                DispatchAreaOrderItemResponse::deliveryAddress,
                (left, right) -> left
            ));

        assertThat(addressByOrderId.get(301L)).isEqualTo(CAMPUS + " 东门");
        assertThat(addressByOrderId.get(302L)).isEqualTo(CAMPUS);
    }

    @Test
    @DisplayName("区域删除拦截列表：地址带门牌号，商家能看懂订单挡在哪个门")
    void areaDeleteBlockingOrdersMustShowDoorNumber() {
        insertAssignments();

        assertThatThrownBy(() -> areaAdmin.deleteArea(AREA_CODE))
            .isInstanceOf(BusinessException.class)
            .satisfies(error -> {
                Object data = ((BusinessException) error).getData();
                assertThat(data).isInstanceOf(Map.class);
                @SuppressWarnings("unchecked")
                List<DispatchAreaBlockingOrderResponse> orders =
                    (List<DispatchAreaBlockingOrderResponse>) ((Map<String, Object>) data).get("orders");
                assertThat(orders).extracting(DispatchAreaBlockingOrderResponse::deliveryAddress)
                    .contains(CAMPUS + " 东门");
            });
    }

    private void insertAssignments() {
        jdbc.update(
            "INSERT INTO dispatch_area_bindings (id, area_code, default_rider_profile_id, backup_rider_profile_id, updated_by) "
                + "VALUES (1, ?, NULL, NULL, 'SYSTEM')",
            AREA_CODE
        );
        jdbc.update(
            "INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, area_code, status, sequence_number) "
                + "VALUES (401, 301, '张三', ?, 'DISPATCHING', 1), "
                + "       (402, 302, '张三', ?, 'DISPATCHING', 2)",
            AREA_CODE, AREA_CODE
        );
    }
}
