package com.jzqs.app.common.realtime;

import java.util.Map;
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

    /**
     * 只推给「指定的那一个骑手」+ admin，<b>不含 rider:all</b>。
     *
     * 与 publishRiderEvent 的区别：后者会广播给所有在线骑手，只适合「队列有变动，大家
     * 都刷新一下」这类提示；而像「后台改了某一单的配送地址」这种只与当事骑手有关的提醒，
     * 广播会让无关骑手收到别人的改址弹窗，因此必须走本方法。
     */
    public void publishRiderDirectEvent(String eventType, String riderName, Map<String, Object> payload) {
        RealtimeEvent.Builder builder = RealtimeEvent.builder(eventType).audience("admin");
        if (hasText(riderName)) {
            String normalizedRiderName = riderName.trim();
            builder.audience("rider:name:" + normalizedRiderName).payload("riderName", normalizedRiderName);
        }
        if (payload != null) {
            payload.forEach(builder::payload);
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
