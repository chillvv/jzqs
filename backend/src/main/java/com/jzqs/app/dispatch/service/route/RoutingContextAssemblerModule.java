package com.jzqs.app.dispatch.service.route;

import java.util.List;

public interface RoutingContextAssemblerModule {

    String buildRoutingContext(String areaCode, CurrentTask task, String immediateCorrection);

    record CurrentTask(
        String scene,
        List<String> inputAddresses,
        List<Long> orderIds
    ) {
    }
}
