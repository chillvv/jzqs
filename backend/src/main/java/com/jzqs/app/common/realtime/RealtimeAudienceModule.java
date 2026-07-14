package com.jzqs.app.common.realtime;

import org.springframework.stereotype.Component;

@Component
public class RealtimeAudienceModule {
    private final TransactionalRealtimePublisher realtimeEventPublisher;

    public RealtimeAudienceModule(TransactionalRealtimePublisher realtimeEventPublisher) {
        this.realtimeEventPublisher = realtimeEventPublisher;
    }

    public void publishSystemEvent(String eventType) {
        realtimeEventPublisher.publish(
            RealtimeEvent.builder(eventType)
                .audience("admin")
                .audience("rider:all")
                .audience("customer:all")
                .build()
        );
    }

    public void publishDispatchEvent(String eventType, String areaCode, String riderName, Object orderId) {
        RealtimeEvent.Builder builder = RealtimeEvent.builder(eventType)
            .audience("admin")
            .audience("rider:all");
        if (hasText(areaCode)) {
            builder.payload("areaCode", areaCode.trim());
        }
        if (hasText(riderName)) {
            String normalizedRiderName = riderName.trim();
            builder.audience("rider:name:" + normalizedRiderName).payload("riderName", normalizedRiderName);
        }
        if (orderId != null) {
            builder.payload("orderId", orderId);
        }
        realtimeEventPublisher.publish(builder.build());
    }

    public void publishRiderEvent(String eventType, String riderName, Object orderId) {
        RealtimeEvent.Builder builder = RealtimeEvent.builder(eventType)
            .audience("admin")
            .audience("rider:all");
        if (hasText(riderName)) {
            String normalizedRiderName = riderName.trim();
            builder.audience("rider:name:" + normalizedRiderName).payload("riderName", normalizedRiderName);
        }
        if (orderId != null) {
            builder.payload("orderId", orderId);
        }
        realtimeEventPublisher.publish(builder.build());
    }

    public void publishCustomerEvent(String eventType, long customerId, Object orderId) {
        RealtimeEvent.Builder builder = RealtimeEvent.builder(eventType)
            .audience("admin")
            .audience("customer:id:" + customerId)
            .payload("customerId", customerId);
        if (orderId != null) {
            builder.payload("orderId", orderId);
        }
        realtimeEventPublisher.publish(builder.build());
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
