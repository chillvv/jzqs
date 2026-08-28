package com.jzqs.app.wallet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;
class MealWalletTest {
    @Test
    void consumeShouldReduceAvailableMeals() {
        MealWallet wallet = MealWallet.open(33);
        wallet.consume(2);
        assertEquals(31, wallet.availableMeals());
        assertEquals(2, wallet.consumedMeals());
    }
    @Test
    void refundShouldRestoreAvailableMeals() {
        MealWallet wallet = MealWallet.open(33);
        wallet.consume(2);
        wallet.consume(-2);
        assertEquals(33, wallet.availableMeals());
        assertEquals(0, wallet.consumedMeals());
    }
}
