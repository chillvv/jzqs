package com.jzqs.app.mobile.api;

/**
 * 骑手更新回执请求
 * 用于更新已提交的回执（riderName 通过 URL 参数传递）
 */
public record RiderReceiptUpdateRequest(
    String receiptFileKey,
    String receiptNote,
    String deliveredAt
) {
}
