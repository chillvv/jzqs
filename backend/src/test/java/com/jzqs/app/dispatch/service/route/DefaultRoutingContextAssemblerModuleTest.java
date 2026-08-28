package com.jzqs.app.dispatch.service.route;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class DefaultRoutingContextAssemblerModuleTest {

    @Test
    void contextContainsAreaMemoryAndImmediateCorrection() {
        AreaMemoryModule areaMemoryModule = new AreaMemoryModule() {
            @Override
            public List<AreaMemoryItem> loadRoutingMemory(String areaCode, String scene) {
                return List.of(new AreaMemoryItem(
                    1L,
                    "A01",
                    "ROUTE_PREFERENCE",
                    "午餐先写字楼",
                    "A 区午餐高峰先写字楼后住宅",
                    "ALL",
                    1,
                    "ACTIVE",
                    List.of(11L)
                ));
            }

            @Override
            public long recordCorrection(RecordCorrectionCommand command) {
                return 0L;
            }

            @Override
            public MergeMemoryResult mergeMemory(long correctionId) {
                return null;
            }
        };

        RoutingContextAssemblerModule module = new DefaultRoutingContextAssemblerModule(areaMemoryModule);
        String context = module.buildRoutingContext(
            "A01",
            new RoutingContextAssemblerModule.CurrentTask(
                "LUNCH",
                List.of("光谷大道 1 号", "软件园一路 2 号"),
                List.of(1001L, 1002L)
            ),
            "这个地址虽然近，但要最后送"
        );

        assertTrue(context.contains("A 区午餐高峰先写字楼后住宅"));
        assertTrue(context.contains("这个地址虽然近，但要最后送"));
        assertTrue(context.contains("1001"));
    }
}
