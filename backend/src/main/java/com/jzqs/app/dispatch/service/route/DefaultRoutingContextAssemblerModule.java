package com.jzqs.app.dispatch.service.route;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class DefaultRoutingContextAssemblerModule implements RoutingContextAssemblerModule {
    private final AreaMemoryModule areaMemoryModule;

    public DefaultRoutingContextAssemblerModule(AreaMemoryModule areaMemoryModule) {
        this.areaMemoryModule = areaMemoryModule;
    }

    @Override
    public String buildRoutingContext(String areaCode, CurrentTask task, String immediateCorrection) {
        List<AreaMemoryModule.AreaMemoryItem> memories = areaMemoryModule.loadRoutingMemory(areaCode, task.scene());
        String memorySummary = memories.isEmpty()
            ? "当前区域暂无长期记忆。"
            : memories.stream()
                .map(item -> "[" + item.memoryType() + "] " + item.summary())
                .collect(Collectors.joining("；"));
        String correctionSummary = safe(immediateCorrection).trim().isEmpty()
            ? "本次未补充文本纠偏。"
            : safe(immediateCorrection).trim();
        int addressCount = task.inputAddresses() == null ? 0 : task.inputAddresses().size();
        return """
            当前任务场景：%s
            当前区域：%s
            当前订单数：%d
            当前订单ID：%s
            区域长期记忆：%s
            本次即时纠偏：%s
            """.formatted(
            safe(task.scene()).trim(),
            safe(areaCode).trim(),
            addressCount,
            task.orderIds() == null ? List.of() : task.orderIds(),
            memorySummary,
            correctionSummary
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
