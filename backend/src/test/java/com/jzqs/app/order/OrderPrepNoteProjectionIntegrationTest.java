package com.jzqs.app.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.jzqs.app.common.api.PageResponse;
import com.jzqs.app.order.api.OrderPrepItemResponse;
import com.jzqs.app.order.service.OrderPrepService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class OrderPrepNoteProjectionIntegrationTest {

    private static final long CUSTOMER_ID = 9931L;
    private static final long ORDER_ID = 9301L;
    private static final long DAILY_ORDER_ID = 9301L;
    private static final long ADDRESS_ID = 9301L;

    @Autowired
    private OrderPrepService orderPrepService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareFixtures() {
        LocalDate serveDate = LocalDate.now().plusDays(4);

        jdbcTemplate.update("DELETE FROM order_notes WHERE meal_slot_order_id = ?", ORDER_ID);
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id = ?", ORDER_ID);
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id = ?", DAILY_ORDER_ID);
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id = ?", ADDRESS_ID);
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM daily_orders WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM customers WHERE id = ?", CUSTOMER_ID);

        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source, active) VALUES (?, '备注投影客户9931', '13900009331', 'BACKEND', TRUE)",
            CUSTOMER_ID
        );

        jdbcTemplate.update(
            """
                INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default)
                VALUES (?, ?, '备注投影客户9931', '13900009331', '测试备注投影地址', '高新区', TRUE)
                """,
            ADDRESS_ID,
            CUSTOMER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at)
                VALUES (?, ?, ?, 'BACKEND', 'PENDING_DISPATCH', FALSE, CURRENT_TIMESTAMP)
                """,
            DAILY_ORDER_ID,
            CUSTOMER_ID,
            serveDate
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, merchant_remark, status, source_type
                ) VALUES (
                    ?, ?, 'LUNCH', 'LUNCH', 1, ?, '-', '-', '', 'PENDING_DISPATCH', 'BACKEND'
                )
                """,
            ORDER_ID,
            DAILY_ORDER_ID,
            ADDRESS_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO order_notes (
                    meal_slot_order_id, customer_id, note_type, source_type, scope_type, content, effective_status, created_by
                ) VALUES
                    (?, ?, 'USER', 'CUSTOMER_PROFILE', 'SNAPSHOT', '长期少饭', 'ACTIVE', 'test'),
                    (?, ?, 'USER', 'CUSTOMER_ORDER_INPUT', 'SNAPSHOT', '本次不要辣', 'ACTIVE', 'test'),
                    (?, ?, 'MERCHANT', 'MERCHANT_PROFILE', 'SNAPSHOT', '重点关注', 'ACTIVE', 'test'),
                    (?, ?, 'MERCHANT', 'MERCHANT_ORDER_ONCE', 'ORDER_ONCE', '本餐送果蔬汁', 'ACTIVE', 'test')
                """,
            ORDER_ID,
            CUSTOMER_ID,
            ORDER_ID,
            CUSTOMER_ID,
            ORDER_ID,
            CUSTOMER_ID,
            ORDER_ID
            ,
            CUSTOMER_ID
        );
    }

    @Test
    void shouldProjectOrderNotesIntoPrepPage() {
        PageResponse<OrderPrepItemResponse> response = orderPrepService.prepPage(LocalDate.now().plusDays(4).toString());

        assertThat(response.items()).hasSize(1);
        OrderPrepItemResponse item = response.items().get(0);
        assertThat(item.userNote()).isEqualTo("长期少饭，本次不要辣");
        assertThat(item.merchantRemark()).isEqualTo("重点关注，本餐送果蔬汁");
    }

    @Test
    void shouldTreatOrderNotesAsHighAttentionEntriesInPrepPage() {
        PageResponse<OrderPrepItemResponse> response = orderPrepService.prepPage(LocalDate.now().plusDays(4).toString());

        assertThat(response.items()).hasSize(1);
        OrderPrepItemResponse item = response.items().get(0);
        assertThat(item.userNote()).isEqualTo("长期少饭，本次不要辣");
        assertThat(item.merchantRemark()).isEqualTo("重点关注，本餐送果蔬汁");
        assertThat(item.priorityCustomer()).isFalse();
    }

    @Test
    void shouldKeepOrderColumnRemarkAlongsideSnapshotNotes() {
        // 回归：订单列上的商家备注不能因为出现备注快照就被整列丢弃，
        // 也不能因为出现用户备注就消失——两者要合并展示。
        jdbcTemplate.update(
            "UPDATE meal_slot_orders SET user_note = '列上的用户备注', merchant_remark = '列上的商家备注' WHERE id = ?",
            ORDER_ID
        );

        PageResponse<OrderPrepItemResponse> response = orderPrepService.prepPage(LocalDate.now().plusDays(4).toString());

        OrderPrepItemResponse item = response.items().stream()
            .filter(candidate -> candidate.id() == ORDER_ID)
            .findFirst()
            .orElseThrow();
        assertThat(item.userNote()).isEqualTo("长期少饭，本次不要辣，列上的用户备注");
        assertThat(item.merchantRemark()).isEqualTo("重点关注，本餐送果蔬汁，列上的商家备注");
    }

    @Test
    void shouldNotDuplicateRemarkThatAppearsInBothSnapshotAndOrderColumn() {
        jdbcTemplate.update(
            "UPDATE meal_slot_orders SET user_note = '本次不要辣', merchant_remark = '本餐送果蔬汁' WHERE id = ?",
            ORDER_ID
        );

        PageResponse<OrderPrepItemResponse> response = orderPrepService.prepPage(LocalDate.now().plusDays(4).toString());

        OrderPrepItemResponse item = response.items().stream()
            .filter(candidate -> candidate.id() == ORDER_ID)
            .findFirst()
            .orElseThrow();
        assertThat(item.userNote()).isEqualTo("长期少饭，本次不要辣");
        assertThat(item.merchantRemark()).isEqualTo("重点关注，本餐送果蔬汁");
    }

    @Test
    void shouldIgnoreLegacySpecialTagOnlyOrdersWhenProjectingPrepPage() {
        LocalDate serveDate = LocalDate.now().plusDays(5);
        long orderId = 9302L;
        long dailyOrderId = 9302L;
        long addressId = 9302L;

        jdbcTemplate.update("DELETE FROM order_notes WHERE meal_slot_order_id = ?", orderId);
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id = ?", orderId);
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id = ?", dailyOrderId);
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id = ?", addressId);

        jdbcTemplate.update(
            """
                INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default)
                VALUES (?, ?, '备注投影客户9931', '13900009331', '测试 legacy 标签地址', '高新区', FALSE)
                """,
            addressId,
            CUSTOMER_ID
        );
        jdbcTemplate.update(
            """
                INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at)
                VALUES (?, ?, ?, 'BACKEND', 'PENDING_DISPATCH', FALSE, CURRENT_TIMESTAMP)
                """,
            dailyOrderId,
            CUSTOMER_ID,
            serveDate
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, merchant_remark, status, source_type, is_priority
                ) VALUES (
                    ?, ?, 'DINNER', 'DINNER', 1, ?, '', '', '', 'PENDING_DISPATCH', 'BACKEND', FALSE
                )
                """,
            orderId,
            dailyOrderId,
            addressId
        );

        PageResponse<OrderPrepItemResponse> response = orderPrepService.prepPage(serveDate.toString());

        assertThat(response.items()).extracting(OrderPrepItemResponse::id).contains(orderId);
        OrderPrepItemResponse item = response.items().stream()
            .filter(candidate -> candidate.id() == orderId)
            .findFirst()
            .orElseThrow();
        assertThat(item.userNote()).isBlank();
        assertThat(item.merchantRemark()).isBlank();
    }
}
