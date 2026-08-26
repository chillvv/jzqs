package com.jzqs.app.mobile.api;

/**
 * 每晚用餐提醒订阅状态同步请求。
 * 前端每次进入下单页时用 wx.getSetting 实时查询微信侧订阅真实状态，并回传后端，
 * 用于纠正后端快照与微信侧真实状态的不一致（用户在微信设置里关闭/重新开启订阅时微信不会回调后端）。
 */
public record NightlySubscriptionSyncRequest(
    boolean enabled,
    String templateId
) {
}
