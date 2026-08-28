package com.jzqs.app.dispatch.service.route;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jzqs.app.common.error.BusinessException;
import java.net.http.HttpClient;
import java.util.List;
import org.junit.jupiter.api.Test;

class DispatchRouteAiRefineServiceTest {

    private final DispatchRouteAiRefineService service = new DispatchRouteAiRefineService(new ObjectMapper(), HttpClient.newHttpClient());
    private final List<DispatchRoutePoint> points = List.of(
        new DispatchRoutePoint(1L, "光谷软件园 A 栋", 0.0d, 0.0d, "光谷软件园", "A栋", "软件园路", List.of("光谷软件园", "A栋"), 0.0d, 0),
        new DispatchRoutePoint(2L, "光谷软件园 B 栋", 0.0d, 0.0d, "光谷软件园", "B栋", "软件园路", List.of("光谷软件园", "B栋"), 0.0d, 0),
        new DispatchRoutePoint(3L, "五环天地", 0.0d, 0.0d, "五环天地", "", "", List.of("五环天地"), 0.0d, 0)
    );

    @Test
    void shouldRejectPlanningResultWhenOrdersAreMissing() {
        BusinessException exception = assertThrows(
            BusinessException.class,
            () -> service.validatePlanningResult(
                """
                    {
                      "success": true,
                      "summary": "缺单",
                      "analysisSteps": [{"type":"grouping","title":"识别分组","message":"已识别 2 组"}],
                      "groups": [{"groupName":"软件园","orderIds":[1,2]}],
                      "finalOrderIds": [1,2],
                      "perOrderReasons": [{"orderId":1,"reason":"同片区"}],
                      "confidence": 0.88
                    }
                    """,
                points
            )
        );

        assertTrue(exception.getMessage().contains("订单集合不完整"));
    }

    @Test
    void shouldAcceptValidPlanningResult() {
        DispatchRouteAiPlanningResult result = service.validatePlanningResult(
            """
                {
                  "success": true,
                  "summary": "先处理软件园，再回收到五环天地周边",
                  "analysisSteps": [
                    {"type":"context_read","title":"读取区域上下文","message":"当前以光谷软件园片区为主"},
                    {"type":"sequencing","title":"生成最终顺序","message":"先聚合同片区，再处理回程点位"}
                  ],
                  "groups": [
                    {"groupName":"光谷软件园片区","orderIds":[1,2]},
                    {"groupName":"五环天地周边","orderIds":[3]}
                  ],
                  "finalOrderIds": [1,2,3],
                  "perOrderReasons": [
                    {"orderId":1,"reason":"与 2 同片区，适合作为起始段"},
                    {"orderId":2,"reason":"紧接 1 处理，减少跨片区跳转"},
                    {"orderId":3,"reason":"作为回程段收尾"}
                  ],
                  "confidence": 0.91
                }
                """,
            points
        );

        assertEquals(List.of(1L, 2L, 3L), result.finalOrderIds());
        assertEquals("先处理软件园，再回收到五环天地周边", result.summary());
        assertEquals(2, result.analysisSteps().size());
        assertEquals(3, result.perOrderReasons().size());
    }

    @Test
    void shouldAcceptStringOrderIdsFromModel() {
        DispatchRouteAiPlanningResult result = service.validatePlanningResult(
            """
                {
                  "success": true,
                  "summary": "模型使用了字符串编号",
                  "analysisSteps": [
                    {"type":"sequencing","title":"生成顺序","message":"已完成"}
                  ],
                  "groups": [],
                  "finalOrderIds": ["1", "2", "3"],
                  "perOrderReasons": [],
                  "confidence": 0.9
                }
                """,
            points
        );

        assertEquals(List.of(1L, 2L, 3L), result.finalOrderIds());
    }
}
