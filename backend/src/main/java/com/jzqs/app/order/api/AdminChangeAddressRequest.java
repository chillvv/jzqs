package com.jzqs.app.order.api;

/**
 * 商家后台修改订单配送地址的请求。
 * customerId 可缺省：缺省时由 orderId 反查订单所属客户，避免商家端需要额外维护客户映射。
 */
public record AdminChangeAddressRequest(long addressId, Long customerId) {
}
