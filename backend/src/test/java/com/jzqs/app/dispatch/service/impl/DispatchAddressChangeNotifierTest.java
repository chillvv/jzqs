package com.jzqs.app.dispatch.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.jzqs.app.common.realtime.RealtimeAudienceModule;
import com.jzqs.app.common.test.BaseDbIntegrationTest;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 回归测试：后台改地址必须把「哪一单变成了什么地址」定点推给当事骑手。
 *
 * 背景（2026-09 商家反馈）：改址会静默撤销/改写订单的派单快照，骑手端只做沉默刷新，
 * 骑手按旧地址送错单。为保证不广播打扰无关骑手，通知必须是「定向」的
 * （audience 只含该骑手 + admin），本测试用 mock 校验调用参数与 payload。
 */
class DispatchAddressChangeNotifierTest extends BaseDbIntegrationTest {

    private static final String CAMPUS = "武汉理工大学南湖校区";

    private RealtimeAudienceModule realtimeAudienceModule;
    private DispatchAddressChangeNotifier notifier;

    @BeforeEach
    void setUp() {
        resetTables();
        jdbc.update(
            "INSERT INTO customers (id, name, phone, source, active, customer_status, is_priority_customer) "
                + "VALUES (1, '张三', '13800138000', 'TEST', 1, 'ACTIVE', 0)"
        );
        jdbc.update(
            "INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, door_number, area_code, is_default) "
                + "VALUES (101, 1, '张三', '13800138000', ?, '东门', '南湖校区', 1), "
                + "       (102, 1, '张三', '13800138000', '新地址路2号', NULL, '', 0)",
            CAMPUS
        );
        jdbc.update(
            "INSERT INTO daily_orders (id, customer_id, serve_date, source, status) VALUES (201, 1, ?, 'ADMIN', 'ACTIVE')",
            LocalDate.now()
        );
        jdbc.update(
            "INSERT INTO meal_slot_orders (id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, status) "
                + "VALUES (301, 201, 'LUNCH', 'LUNCH', 1, 101, 'DISPATCHING'), "
                + "       (302, 201, 'DINNER', 'DINNER', 1, 101, 'DISPATCHING'), "
                + "       (303, 201, 'DINNER', 'DINNER', 1, 101, 'PENDING_DISPATCH'), "
                + "       (304, 201, 'LUNCH', 'LUNCH', 1, 101, 'DELIVERED')"
        );
        jdbc.update(
            "INSERT INTO dispatch_assignments (id, meal_slot_order_id, rider_name, area_code, status, sequence_number) "
                + "VALUES (401, 301, '骑手小李', '南湖校区', 'DISPATCHING', 1), "
                + "       (402, 302, '骑手小王', '南湖校区', 'DISPATCHING', 1), "
                + "       (404, 304, '骑手老周', '南湖校区', 'DELIVERED', 1)"
        );

        realtimeAudienceModule = mock(RealtimeAudienceModule.class);
        notifier = new DispatchAddressChangeNotifier(jdbc, realtimeAudienceModule);
    }

    @Test
    @DisplayName("订单改址：定向推给原骑手，payload 带新地址（含门牌号）与提醒文案")
    void orderAddressChangedNotifiesCurrentRider() {
        String notifiedRider = notifier.notifyOrderAddressChanged(
            301L,
            DispatchAddressChangeNotifier.SOURCE_ADMIN_CHANGED_ADDRESS
        );

        assertThat(notifiedRider)
            .as("应通知改址前承接该单的骑手")
            .isEqualTo("骑手小李");

        Map<String, Object> payload = captureSinglePayload("骑手小李");
        assertThat(payload)
            .containsEntry("orderId", 301L)
            .containsEntry("customerName", "张三")
            .containsEntry("addressText", CAMPUS + " 东门")
            .containsEntry("source", DispatchAddressChangeNotifier.SOURCE_ADMIN_CHANGED_ADDRESS);
        assertThat(String.valueOf(payload.get("noticeText")))
            .as("提醒文案必须让骑手知道是「谁」的地址变成了「什么」")
            .contains("张三")
            .contains(CAMPUS + " 东门");
    }

    @Test
    @DisplayName("未派单 / 已送达的订单改址：不产生骑手提醒（避免噪音）")
    void orderAddressChangedSkipsUnassignedAndFinishedOrders() {
        assertThat(notifier.notifyOrderAddressChanged(
            303L, DispatchAddressChangeNotifier.SOURCE_ADMIN_CHANGED_ADDRESS))
            .as("未派单的订单没有需要通知的骑手")
            .isNull();
        assertThat(notifier.notifyOrderAddressChanged(
            304L, DispatchAddressChangeNotifier.SOURCE_ADMIN_CHANGED_ADDRESS))
            .as("已送达订单的骑手不需要再知道地址变化")
            .isNull();

        verifyNoInteractions(realtimeAudienceModule);
    }

    @Test
    @DisplayName("地址簿改址：地址文本真的变了，才逐个通知受影响订单的骑手")
    void customerAddressChangedNotifiesEveryAffectedRider() {
        String previousAddressText = notifier.readAddressText(101L);
        jdbc.update(
            "UPDATE customer_addresses SET address_line = ?, door_number = ? WHERE id = 101",
            CAMPUS,
            "南门"
        );

        int notifiedCount = notifier.notifyCustomerAddressChanged(
            101L,
            previousAddressText,
            DispatchAddressChangeNotifier.SOURCE_ADDRESS_BOOK_UPDATED
        );

        assertThat(notifiedCount)
            .as("只通知「还没送完且已派单」的订单：301、302 两单（303 未派单、304 已送达）")
            .isEqualTo(2);

        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(realtimeAudienceModule, times(2)).publishRiderDirectEvent(
            eq(DispatchAddressChangeNotifier.EVENT_ADDRESS_CHANGED),
            any(),
            captor.capture()
        );
        assertThat(captor.getAllValues()).hasSize(2)
            .allSatisfy(payload -> assertThat(payload).containsEntry("addressText", CAMPUS + " 南门"));
        assertThat(captor.getAllValues())
            .extracting(payload -> payload.get("orderId"))
            .containsExactlyInAnyOrder(301L, 302L);
    }

    @Test
    @DisplayName("地址簿只改了联系人电话：地址文本没变，不打扰骑手")
    void customerAddressChangedSkippedWhenAddressTextUnchanged() {
        String previousAddressText = notifier.readAddressText(101L);
        jdbc.update("UPDATE customer_addresses SET contact_phone = ? WHERE id = 101", "13900139000");

        int notifiedCount = notifier.notifyCustomerAddressChanged(
            101L,
            previousAddressText,
            DispatchAddressChangeNotifier.SOURCE_ADDRESS_BOOK_UPDATED
        );

        assertThat(notifiedCount).isZero();
        verify(realtimeAudienceModule, never()).publishRiderDirectEvent(any(), any(), any());
    }

    @Test
    @DisplayName("地址对外文本：拼门牌号，无门牌号时不留多余空格")
    void readAddressTextJoinsDoorNumber() {
        assertThat(notifier.readAddressText(101L)).isEqualTo(CAMPUS + " 东门");
        assertThat(notifier.readAddressText(102L)).isEqualTo("新地址路2号");
    }

    private Map<String, Object> captureSinglePayload(String riderName) {
        ArgumentCaptor<Map<String, Object>> captor = mapCaptor();
        verify(realtimeAudienceModule).publishRiderDirectEvent(
            eq(DispatchAddressChangeNotifier.EVENT_ADDRESS_CHANGED),
            eq(riderName),
            captor.capture()
        );
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<Map<String, Object>> mapCaptor() {
        return (ArgumentCaptor<Map<String, Object>>) (ArgumentCaptor<?>) ArgumentCaptor.forClass(Map.class);
    }
}
