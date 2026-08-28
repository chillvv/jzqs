package com.jzqs.app.mobile.api;

/**
 * 顾客钱包剩余餐数快照（下单前实时校验用，口径与 customerHome/activeWalletId 一致：
 * 只计「id 最小的未过期 active 钱包」）。
 */
public record MobileWalletBalanceResponse(int remainingMeals) {
}
