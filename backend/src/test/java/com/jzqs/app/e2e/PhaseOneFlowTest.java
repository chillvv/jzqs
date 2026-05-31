package com.jzqs.app.e2e;
import static org.junit.jupiter.api.Assertions.assertEquals;
import com.jzqs.app.wallet.MealWallet;
import org.junit.jupiter.api.Test;
class PhaseOneFlowTest {
    @Test
    void shouldReserveThenConsumeMealsAcrossCoreFlow() {
        MealWallet wallet = MealWallet.open(33);
        wallet.reserve(2);
        wallet.consumeReserved(2);
        assertEquals(31, wallet.availableMeals());
        assertEquals(2, wallet.consumedMeals());
    }
}
