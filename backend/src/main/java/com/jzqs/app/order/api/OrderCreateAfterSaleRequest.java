package com.jzqs.app.order.api;

public record OrderCreateAfterSaleRequest(
    String type,
    String reasonCode,
    String reasonText,
    String issueParamSummary,
    String remark,
    String operatorName
) {
    public String typeOrDefault() {
        return type == null || type.isBlank() ? "COMPENSATION" : type;
    }

    public String reasonCodeOrDefault() {
        return reasonCode == null || reasonCode.isBlank() ? "ADMIN_DIRECT" : reasonCode;
    }

    public String reasonTextOrDefault() {
        return reasonText == null ? "" : reasonText;
    }

    public String issueParamSummaryOrDefault() {
        return issueParamSummary == null ? "" : issueParamSummary;
    }

    public String remarkOrDefault() {
        return remark == null ? "" : remark;
    }

    public String operatorNameOrDefault() {
        return operatorName == null || operatorName.isBlank() ? "后台客服" : operatorName;
    }
}
