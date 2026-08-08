package com.jzqs.app.mobile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Set;

final class MiniappCustomerCancelGuard {
    private static final LocalTime DEFAULT_CUSTOMER_CANCEL_CUTOFF = LocalTime.of(23, 0);

    /**
     * 对顾客而言只要"还没送到"就属于可退范围。
     * 后台是否已经排单、是否已经分配骑手（DISPATCHING/DISPATCHED）都是内部调度细节，
     * 不应该堵住用户在送餐前一天的自助秒退款。
     */
    private static final Set<String> UNDELIVERED_STATUSES = Set.of(
        "PENDING_DISPATCH",
        "DISPATCHING",
        "DISPATCHED"
    );

    private MiniappCustomerCancelGuard() {
    }

    static boolean canCustomerCancel(LocalDateTime now, LocalDate serveDate, String orderStatus) {
        return canCustomerCancel(now, serveDate, orderStatus, DEFAULT_CUSTOMER_CANCEL_CUTOFF);
    }

    /**
     * 自助取消（秒退）的判定与「换地址」保持一致：只要送餐日还没到（即送餐前一天及更早）即可操作，
     * 送餐当天不可秒退、需联系商家协商。
     * 注意：23:00 的 selfOrderCutoffTime 是「下单截止」时间，被误用为退款截止是之前的逻辑缺陷，
     * 此处不再用它约束已下订单的退款，避免用户在前一天晚间被错误拦截。
     */
    static boolean canCustomerCancel(LocalDateTime now, LocalDate serveDate, String orderStatus, LocalTime cutoffTime) {
        if (!UNDELIVERED_STATUSES.contains(orderStatus)) {
            return false;
        }
        if (!serveDate.isAfter(now.toLocalDate())) {
            return false;
        }
        return true;
    }
}
