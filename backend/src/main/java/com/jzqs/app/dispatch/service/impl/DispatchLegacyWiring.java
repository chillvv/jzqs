package com.jzqs.app.dispatch.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.realtime.RealtimeAudienceModule;
import com.jzqs.app.common.realtime.TransactionalRealtimePublisher;
import com.jzqs.app.dispatch.service.route.AreaMemoryModule;
import com.jzqs.app.dispatch.service.route.DefaultRoutingContextAssemblerModule;
import com.jzqs.app.dispatch.service.route.DispatchRouteAiRefineService;
import com.jzqs.app.dispatch.service.route.DispatchRouteFeatureExtractor;
import com.jzqs.app.dispatch.service.route.DispatchRouteScoringService;
import com.jzqs.app.dispatch.service.route.JdbcDispatchAiJobLogModule;
import com.jzqs.app.dispatch.service.route.JdbcAreaMemoryModule;
import com.jzqs.app.dispatch.service.route.RoutingContextAssemblerModule;
import org.springframework.jdbc.core.JdbcTemplate;

final class DispatchLegacyWiring {
    private DispatchLegacyWiring() {
    }

    static Modules create(JdbcTemplate jdbcTemplate, TransactionalRealtimePublisher realtimeEventPublisher) {
        DispatchBatchModule batchModule = new DispatchBatchModule(jdbcTemplate);
        RealtimeAudienceModule realtimeAudienceModule = new RealtimeAudienceModule(realtimeEventPublisher);
        DispatchAssignmentModule assignmentModule = new DispatchAssignmentModule(
            jdbcTemplate,
            batchModule,
            realtimeAudienceModule
        );
        DispatchQueryModule queryModule = new DispatchQueryModule(jdbcTemplate, assignmentModule);
        ObjectMapper objectMapper = new ObjectMapper();
        DispatchRouteFeatureExtractor routeFeatureExtractor = new DispatchRouteFeatureExtractor();
        DispatchRouteAiRefineService routeAiRefineService = new DispatchRouteAiRefineService(objectMapper);
        AreaMemoryModule areaMemoryModule = new JdbcAreaMemoryModule(jdbcTemplate, objectMapper);
        RoutingContextAssemblerModule routingContextAssemblerModule = new DefaultRoutingContextAssemblerModule(areaMemoryModule);
        return new Modules(
            new DispatchRouteScoringService(),
            batchModule,
            new DispatchRiderAdminModule(jdbcTemplate),
            new DispatchAreaAdminModule(jdbcTemplate),
            assignmentModule,
            new DispatchNotificationModule(jdbcTemplate),
            queryModule,
            new DispatchRouteLabModule(
                jdbcTemplate,
                objectMapper,
                new JdbcDispatchAiJobLogModule(jdbcTemplate, objectMapper),
                routeFeatureExtractor,
                routeAiRefineService
            ),
            new DispatchRouteSuggestionModule(
                jdbcTemplate,
                routeFeatureExtractor,
                routeAiRefineService
            ),
            new DispatchAreaCorrectionModule(
                jdbcTemplate,
                objectMapper,
                routeFeatureExtractor,
                routeAiRefineService,
                areaMemoryModule,
                routingContextAssemblerModule
            )
        );
    }

    record Modules(
        DispatchRouteScoringService dispatchRouteScoringService,
        DispatchBatchModule dispatchBatchModule,
        DispatchRiderAdminModule dispatchRiderAdminModule,
        DispatchAreaAdminModule dispatchAreaAdminModule,
        DispatchAssignmentModule dispatchAssignmentModule,
        DispatchNotificationModule dispatchNotificationModule,
        DispatchQueryModule dispatchQueryModule,
        DispatchRouteLabModule dispatchRouteLabModule,
        DispatchRouteSuggestionModule dispatchRouteSuggestionModule,
        DispatchAreaCorrectionModule dispatchAreaCorrectionModule
    ) {
    }
}
