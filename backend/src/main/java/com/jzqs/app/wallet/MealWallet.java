package com.jzqs.app.wallet;
public class MealWallet {
    private final int totalMeals;
    private int reservedMeals;
    private int consumedMeals;
    private MealWallet(int totalMeals) {
        this.totalMeals = totalMeals;
    }
    public static MealWallet open(int totalMeals) {
        return new MealWallet(totalMeals);
    }
    public void reserve(int meals) {
        if (meals <= 0 || meals > availableMeals()) {
            throw new IllegalStateException("wallet balance not enough");
        }
        reservedMeals += meals;
    }
    public void release(int meals) {
        if (meals <= 0 || meals > reservedMeals) {
            throw new IllegalStateException("reserved meals not enough");
        }
        reservedMeals -= meals;
    }
    public void consumeReserved(int meals) {
        if (meals <= 0 || meals > reservedMeals) {
            throw new IllegalStateException("reserved meals not enough");
        }
        reservedMeals -= meals;
        consumedMeals += meals;
    }
    public int availableMeals() {
        return totalMeals - reservedMeals - consumedMeals;
    }
    public int reservedMeals() {
        return reservedMeals;
    }
    public int consumedMeals() {
        return consumedMeals;
    }
}
