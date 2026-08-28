export function resolveOrderingTone(enabled) {
    return enabled ? "green" : "gray";
}
export function buildOperationRiskSummary(settings) {
    return {
        primaryHint: settings.orderingEnabled
            ? "当前仍可接单，若实际休息请先关停通道。"
            : "当前已停止接单，恢复营业前记得重新打开通道。",
        secondaryHint: settings.holidayNoticeTitle && settings.holidayNoticeDesc
            ? "公告已配置，记得核对恢复营业时间和小程序展示文案。"
            : "当前尚未配置公告，前台用户可能不知道为什么不能下单。",
        tone: settings.orderingEnabled ? "warning" : "info"
    };
}
