package com.jzqs.app.mobile;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class MiniappOrderCancellationPolicyTest {

    @Test
    void shouldAllowCustomerCancelBeforeElevenForTomorrowPendingDispatchOrder() {
        boolean allowed = MiniappCustomerCancelGuard.canCustomerCancel(
            LocalDateTime.of(2026, 5, 14, 22, 59),
            LocalDate.of(2026, 5, 15),
            "PENDING_DISPATCH"
        );

        assertTrue(allowed);
    }

    @Test
    void shouldAllowCustomerCancelAfterElevenPmForTomorrowOrder() {
        // 远程实现：23:00 是下单截止时间，不再是退款截止（避免用户前一天晚间被错误拦截）
        boolean allowed = MiniappCustomerCancelGuard.canCustomerCancel(
            LocalDateTime.of(2026, 5, 14, 23, 0),
            LocalDate.of(2026, 5, 15),
            "PENDING_DISPATCH"
        );

        assertTrue(allowed);
    }

    @Test
    void shouldAllowCustomerCancelWhenOrderAlreadyAssignedToRider() {
        // 后台排单/分配骑手是内部调度细节，不应该堵住用户在送餐前一天的自助秒退款
        boolean allowed = MiniappCustomerCancelGuard.canCustomerCancel(
            LocalDateTime.of(2026, 5, 14, 20, 0),
            LocalDate.of(2026, 5, 15),
            "DISPATCHING"
        );

        assertTrue(allowed);
    }

    @Test
    void shouldRejectCustomerCancelForDeliveredOrder() {
        boolean allowed = MiniappCustomerCancelGuard.canCustomerCancel(
            LocalDateTime.of(2026, 5, 14, 20, 0),
            LocalDate.of(2026, 5, 15),
            "DELIVERED"
        );

        assertFalse(allowed);
    }

    @Test
    void shouldRejectCustomerCancelForServingDay() {
        // 送餐当天不可秒退，需联系商家协商
        boolean allowed = MiniappCustomerCancelGuard.canCustomerCancel(
            LocalDateTime.of(2026, 5, 14, 20, 0),
            LocalDate.of(2026, 5, 14),
            "PENDING_DISPATCH"
        );

        assertFalse(allowed);
    }

    @Test
    void shouldAllowCustomerCancelForDayAfterTomorrow() {
        // 非明天但未到送餐日的订单同样可退（与换地址判定一致）
        boolean allowed = MiniappCustomerCancelGuard.canCustomerCancel(
            LocalDateTime.of(2026, 5, 14, 20, 0),
            LocalDate.of(2026, 5, 16),
            "PENDING_DISPATCH"
        );

        assertTrue(allowed);
    }
}
