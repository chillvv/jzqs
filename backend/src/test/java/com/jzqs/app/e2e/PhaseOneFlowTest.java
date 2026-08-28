package com.jzqs.app.e2e;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.jzqs.app.wallet.MealWallet;
import org.junit.jupiter.api.Test;
class PhaseOneFlowTest {
    @Test
    void shouldConsumeMealsOnOrderThenRefundOnCancel() {
        MealWallet wallet = MealWallet.open(33);
        wallet.consume(2);
        assertEquals(31, wallet.availableMeals());
        assertEquals(2, wallet.consumedMeals());
        // 取消/退款反向加回
        wallet.consume(-2);
        assertEquals(33, wallet.availableMeals());
    }
}
