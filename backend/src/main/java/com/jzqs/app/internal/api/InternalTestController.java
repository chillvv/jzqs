package com.jzqs.app.internal.api;

import com.jzqs.app.common.api.ApiResponse;
import com.jzqs.app.mobile.NightlyReminderModule;
import com.jzqs.app.common.error.BusinessException;
import com.jzqs.app.common.error.ErrorCode;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 内部测试接口（需用 X-Internal-Token 鉴权，路径前缀 /api/internal/）。
 * 仅用于开发/联调阶段给指定客户手动触发订阅消息，不要在生产常规流程中使用。
 */
@RestController
@RequestMapping("/api/internal/test")
public class InternalTestController {

    private final NightlyReminderModule nightlyReminderModule;

    public InternalTestController(NightlyReminderModule nightlyReminderModule) {
        this.nightlyReminderModule = nightlyReminderModule;
    }

    public record NightlyTestRequest(long customerId) {
    }

    public record NightlyTestResponse(String page, String message) {
    }

    /**
     * 给指定客户触发一次「每晚用餐提醒（餐数）」测试下发，复用生产群发逻辑与内容。
     */
    @PostMapping("/nightly-reminder/send")
    public ApiResponse<NightlyTestResponse> sendNightlyReminder(@RequestBody NightlyTestRequest request) {
        if (request.customerId() <= 0) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR, "customerId 无效");
        }
        String page = nightlyReminderModule.sendTestMessage(request.customerId());
        return ApiResponse.success(new NightlyTestResponse(page, "已触发发送"));
    }
}
