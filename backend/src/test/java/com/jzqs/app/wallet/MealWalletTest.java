package com.jzqs.app.wallet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
class MealWalletTest {
    @Test
    void reserveShouldReduceAvailableMeals() {
        MealWallet wallet = MealWallet.open(33);
        wallet.reserve(2);
        assertEquals(31, wallet.availableMeals());
        assertEquals(2, wallet.reservedMeals());
    }
    @Test
    void consumeReservedShouldMoveReservedToConsumed() {
        MealWallet wallet = MealWallet.open(33);
        wallet.reserve(2);
        wallet.consumeReserved(2);
        assertEquals(31, wallet.availableMeals());
        assertEquals(0, wallet.reservedMeals());
        assertEquals(2, wallet.consumedMeals());
    }
    @Test
    void reserveShouldFailWhenBalanceNotEnough() {
        MealWallet wallet = MealWallet.open(1);
        assertThrows(IllegalStateException.class, () -> wallet.reserve(2));
    }
}
