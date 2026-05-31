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
    void shouldRejectCustomerCancelAfterElevenPm() {
        boolean allowed = MiniappCustomerCancelGuard.canCustomerCancel(
            LocalDateTime.of(2026, 5, 14, 23, 0),
            LocalDate.of(2026, 5, 15),
            "PENDING_DISPATCH"
        );

        assertFalse(allowed);
    }

    @Test
    void shouldRejectCustomerCancelForNonPendingDispatchOrder() {
        boolean allowed = MiniappCustomerCancelGuard.canCustomerCancel(
            LocalDateTime.of(2026, 5, 14, 20, 0),
            LocalDate.of(2026, 5, 15),
            "DISPATCHING"
        );

        assertFalse(allowed);
    }

    @Test
    void shouldRejectCustomerCancelForNonTomorrowOrder() {
        boolean allowed = MiniappCustomerCancelGuard.canCustomerCancel(
            LocalDateTime.of(2026, 5, 14, 20, 0),
            LocalDate.of(2026, 5, 16),
            "PENDING_DISPATCH"
        );

        assertFalse(allowed);
    }
}
