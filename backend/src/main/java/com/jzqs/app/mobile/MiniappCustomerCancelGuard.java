package com.jzqs.app.mobile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

final class MiniappCustomerCancelGuard {
    private MiniappCustomerCancelGuard() {
    }

    static boolean canCustomerCancel(LocalDateTime now, LocalDate serveDate, String orderStatus) {
        if (!"PENDING_DISPATCH".equals(orderStatus)) {
            return false;
        }
        if (!serveDate.equals(now.toLocalDate().plusDays(1))) {
            return false;
        }
        return now.toLocalTime().isBefore(LocalTime.of(23, 0));
    }
}
