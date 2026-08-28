package com.jzqs.app.common.realtime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class RealtimeRoutingTest {

    @Test
    void shouldMatchAdminOnlyEventsForAdminSessions() {
        RealtimeViewer adminViewer = RealtimeViewer.admin(1L, "OWNER");
        RealtimeViewer riderViewer = RealtimeViewer.rider(2L, "骑手小李");

        RealtimeEvent event = RealtimeEvent.builder("dispatch.queue.changed")
            .audience("admin")
            .payload("orderId", 901L)
            .build();

        assertTrue(adminViewer.matches(event.audiences()));
        assertFalse(riderViewer.matches(event.audiences()));
    }

    @Test
    void shouldMatchNamedRiderEventsForSameRider() {
        RealtimeViewer riderViewer = RealtimeViewer.rider(2L, "骑手小李");
        RealtimeViewer otherRiderViewer = RealtimeViewer.rider(3L, "骑手小王");

        RealtimeEvent event = RealtimeEvent.builder("dispatch.assignment.changed")
            .audiences(Set.of("rider:all", "rider:name:骑手小李"))
            .payload("areaCode", "高新区")
            .build();

        assertTrue(riderViewer.matches(event.audiences()));
        assertTrue(otherRiderViewer.matches(Set.of("rider:all")));
        assertFalse(otherRiderViewer.matches(Set.of("rider:name:骑手小李")));
    }

    @Test
    void shouldMatchCustomerScopedEventsForSameCustomerOnly() {
        RealtimeViewer customerViewer = RealtimeViewer.customer(1001L);
        RealtimeViewer otherCustomerViewer = RealtimeViewer.customer(1002L);

        RealtimeEvent event = RealtimeEvent.builder("customer.order.changed")
            .audience("customer:id:1001")
            .payload("orderId", 501L)
            .build();

        assertTrue(customerViewer.matches(event.audiences()));
        assertFalse(otherCustomerViewer.matches(event.audiences()));
    }
}
