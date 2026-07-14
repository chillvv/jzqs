package com.jzqs.app.mobile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

final class MiniappCustomerCancelGuard {
    private static final LocalTime DEFAULT_CUSTOMER_CANCEL_CUTOFF = LocalTime.of(23, 0);

    private MiniappCustomerCancelGuard() {
    }

    static boolean canCustomerCancel(LocalDateTime now, LocalDate serveDate, String orderStatus) {
        return canCustomerCancel(now, serveDate, orderStatus, DEFAULT_CUSTOMER_CANCEL_CUTOFF);
    }

    static boolean canCustomerCancel(LocalDateTime now, LocalDate serveDate, String orderStatus, LocalTime cutoffTime) {
        if (!"PENDING_DISPATCH".equals(orderStatus)) {
            return false;
        }
        if (!serveDate.equals(now.toLocalDate().plusDays(1))) {
            return false;
        }
        return now.toLocalTime().isBefore(cutoffTime);
    }
}
