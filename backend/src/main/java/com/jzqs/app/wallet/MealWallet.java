package com.jzqs.app.wallet;
public class MealWallet {
    private final int totalMeals;
    private int consumedMeals;
    private MealWallet(int totalMeals) {
        this.totalMeals = totalMeals;
    }
    public static MealWallet open(int totalMeals) {
        return new MealWallet(totalMeals);
    }

    /**
     * 加餐/下单：直接消费餐次（立即扣）。取消/退款则反向 consume(-meals) 加回。
     */
    public void consume(int meals) {
        consumedMeals += meals;
    }
    public int availableMeals() {
        return totalMeals - consumedMeals;
    }
    public int consumedMeals() {
        return consumedMeals;
    }
}
