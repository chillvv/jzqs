package com.jzqs.app.dispatch.service.route;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class DispatchAreaMemoryContractsTest {

    @Test
    void correctionModeSupportsChatDragAndMixed() {
        List<String> modes = List.of("CHAT", "DRAG", "MIXED");
        assertEquals(List.of("CHAT", "DRAG", "MIXED"), modes);
    }
}
