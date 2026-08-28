package com.jzqs.app.order;

import static org.assertj.core.api.Assertions.assertThat;

import com.jzqs.app.order.api.OrderNoteCreateResponse;
import com.jzqs.app.order.api.OrderNoteCreateRequest;
import com.jzqs.app.order.api.OrderNotesResponse;
import com.jzqs.app.order.service.OrderPrepService;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
class OrderNotesServiceIntegrationTest {
    private static final long CUSTOMER_ID = 9922L;
    private static final String CUSTOMER_NAME = "订单备注客户9922";
    private static final String CUSTOMER_PHONE = "13900009222";

    @Autowired
    private OrderPrepService orderPrepService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void prepareOrderNoteFixtures() {
        LocalDate serveDate = LocalDate.now().plusDays(3);
        jdbcTemplate.update("DELETE FROM order_notes WHERE meal_slot_order_id = 9201");
        jdbcTemplate.update("DELETE FROM meal_slot_orders WHERE id = 9201");
        jdbcTemplate.update("DELETE FROM daily_orders WHERE id = 9201");
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE id = 9201");
        jdbcTemplate.update("DELETE FROM customer_addresses WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM daily_orders WHERE customer_id = ?", CUSTOMER_ID);
        jdbcTemplate.update("DELETE FROM customers WHERE id = ?", CUSTOMER_ID);

        jdbcTemplate.update(
            "INSERT INTO customers (id, name, phone, source, active) VALUES (?, ?, ?, 'BACKEND', TRUE)",
            CUSTOMER_ID,
            CUSTOMER_NAME,
            CUSTOMER_PHONE
        );

        jdbcTemplate.update(
            """
                INSERT INTO customer_addresses (id, customer_id, contact_name, contact_phone, address_line, area_code, is_default)
                VALUES (9201, ?, ?, ?, '测试备注地址A座', '高新区', TRUE)
                """
            ,
            CUSTOMER_ID,
            CUSTOMER_NAME,
            CUSTOMER_PHONE
        );
        jdbcTemplate.update(
            """
                INSERT INTO daily_orders (id, customer_id, serve_date, source, status, locked, created_at)
                VALUES (9201, ?, ?, 'BACKEND', 'PENDING_DISPATCH', FALSE, CURRENT_TIMESTAMP)
                """,
            CUSTOMER_ID,
            serveDate
        );
        jdbcTemplate.update(
            """
                INSERT INTO meal_slot_orders (
                    id, daily_order_id, meal_period, delivery_meal_period, quantity, address_id, note, user_note, merchant_remark, status, source_type
                ) VALUES (
                    9201, 9201, 'LUNCH', 'LUNCH', 1, 9201, '本次少饭', '本次少饭', '', 'PENDING_DISPATCH', 'BACKEND'
                )
                """
        );
        jdbcTemplate.update(
            """
                INSERT INTO order_notes (
                    meal_slot_order_id, customer_id, note_type, source_type, scope_type, content, effective_status, created_by
                ) VALUES (
                    9201, ?, 'USER', 'CUSTOMER_PROFILE', 'SNAPSHOT', '长期少饭', 'ACTIVE', 'test'
                )
                """
            ,
            CUSTOMER_ID
        );
    }

    @Test
    void shouldPersistAndQueryOneTimeMerchantOrderNotes() {
        OrderNoteCreateResponse result = orderPrepService.addOrderNote(
            9201L,
            new OrderNoteCreateRequest("MERCHANT", "ORDER_ONCE", "本餐送果蔬汁")
        );

        assertThat(result.orderId()).isEqualTo(9201L);
        assertThat(result.status()).isEqualTo("CREATED");

        OrderNotesResponse response = orderPrepService.orderNotes(9201L);
        assertThat(response.userNotes()).extracting(note -> note.content()).containsExactly("长期少饭");
        assertThat(response.merchantNotes()).extracting(note -> note.content()).containsExactly("本餐送果蔬汁");
        assertThat(response.merchantNotes()).extracting(note -> note.sourceType()).containsExactly("MERCHANT_ORDER_ONCE");
        assertThat(response.merchantNotes()).extracting(note -> note.scopeType()).containsExactly("ORDER_ONCE");
    }
}
