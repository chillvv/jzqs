package com.jzqs.app.order.api;

public record OrderDirectRefundRequest(
    String reasonCode,
    String reasonText,
    String operatorName
) {
    public String reasonCodeOrDefault() {
        return reasonCode == null || reasonCode.isBlank() ? "ADMIN_DIRECT_REFUND" : reasonCode;
    }

    public String reasonTextOrDefault() {
        return reasonText == null || reasonText.isBlank() ? "商家后台直接退款" : reasonText;
    }

    public String operatorNameOrDefault() {
        return operatorName == null || operatorName.isBlank() ? "后台客服" : operatorName;
    }
}
