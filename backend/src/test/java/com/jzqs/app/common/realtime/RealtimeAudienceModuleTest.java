package com.jzqs.app.common.realtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

class RealtimeAudienceModuleTest {

    @Test
    void publishDispatchEventShouldIncludeDispatchAudiencesAndPayload() {
        TransactionalRealtimePublisher publisher = Mockito.mock(TransactionalRealtimePublisher.class);
        RealtimeAudienceModule module = new RealtimeAudienceModule(publisher);

        module.publishDispatchEvent("dispatch.assignment.changed", "高新区", "骑手小李", 901L);

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(publisher).publish(eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getValue();

        assertEquals("dispatch.assignment.changed", event.eventType());
        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertTrue(event.audiences().contains("rider:name:骑手小李"));
        assertEquals("高新区", event.payload().get("areaCode"));
        assertEquals("骑手小李", event.payload().get("riderName"));
        assertEquals(901L, ((Number) event.payload().get("orderId")).longValue());
    }

    @Test
    void publishSystemEventShouldIncludeAllTopLevelAudiences() {
        TransactionalRealtimePublisher publisher = Mockito.mock(TransactionalRealtimePublisher.class);
        RealtimeAudienceModule module = new RealtimeAudienceModule(publisher);

        module.publishSystemEvent("system.home.changed");

        ArgumentCaptor<RealtimeEvent> eventCaptor = ArgumentCaptor.forClass(RealtimeEvent.class);
        verify(publisher).publish(eventCaptor.capture());
        RealtimeEvent event = eventCaptor.getValue();

        assertEquals("system.home.changed", event.eventType());
        assertTrue(event.audiences().contains("admin"));
        assertTrue(event.audiences().contains("rider:all"));
        assertTrue(event.audiences().contains("customer:all"));
    }
}
