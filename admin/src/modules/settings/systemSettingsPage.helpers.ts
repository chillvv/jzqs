import type { DispatchAiJobLogResponse, OperationSettingsResponse } from "../../shared/api/types";

export type BannerConfigItem = {
  imageUrl: string;
  enabled: boolean;
};

const DEFAULT_BANNER_CONFIGS: BannerConfigItem[] = [
  {
    imageUrl: "../../assets/hero-new.jpg",
    enabled: true
  }
];

export const DEFAULT_ADMIN_BANNER_IMAGE = DEFAULT_BANNER_CONFIGS[0].imageUrl;

function cloneDefaultBannerConfigs() {
  return DEFAULT_BANNER_CONFIGS.map((item) => ({ ...item }));
}

export function normalizeBannerConfigs(rawBannerImages: string) {
  if (!rawBannerImages.trim()) {
    return cloneDefaultBannerConfigs();
  }

  try {
    const parsed = JSON.parse(rawBannerImages);
    if (!Array.isArray(parsed)) {
      return cloneDefaultBannerConfigs();
    }

    const banners = parsed
      .map((item): BannerConfigItem | null => {
        if (typeof item === "string") {
          const imageUrl = item.trim();
          return imageUrl
            ? {
              imageUrl,
              enabled: true
            }
            : null;
        }
        if (!item || typeof item !== "object") {
          return null;
        }
        const imageUrl = typeof item.imageUrl === "string"
          ? item.imageUrl.trim()
          : typeof item.url === "string"
            ? item.url.trim()
            : "";
        if (!imageUrl) {
          return null;
        }
        return {
          imageUrl,
          enabled: item.enabled !== false
        };
      })
      .filter((item): item is BannerConfigItem => Boolean(item));

    return banners.length > 0 ? banners : cloneDefaultBannerConfigs();
  } catch {
    return cloneDefaultBannerConfigs();
  }
}

export function countBannerImages(rawBannerImages: string) {
  return normalizeBannerConfigs(rawBannerImages).length;
}

export function countEnabledBannerImages(rawBannerImages: string) {
  return normalizeBannerConfigs(rawBannerImages).filter((item) => item.enabled).length;
}

export function serializeBannerConfigs(items: BannerConfigItem[]) {
  const normalized = items
    .map((item) => ({
      imageUrl: item.imageUrl.trim(),
      enabled: item.enabled
    }))
    .filter((item) => item.imageUrl.length > 0);

  return JSON.stringify(normalized.length > 0 ? normalized : cloneDefaultBannerConfigs());
}

export function countPageLinkedBannerImages(rawBannerImages: string) {
  return 0;
}

export function resolveBannerActionSummary(item: BannerConfigItem) {
  return "点击查看大图";
}

export function resolveAdminMediaUrl(value: string) {
  const normalized = value.trim();
  if (!normalized) {
    return "";
  }
  if (normalized.startsWith("/uploads/")) {
    return normalized;
  }
  if (
    /^https?:\/\//i.test(normalized)
    || normalized.startsWith("data:")
    || normalized.startsWith("blob:")
    || normalized.startsWith(".")
  ) {
    return normalized;
  }
  if (typeof window === "undefined") {
    return normalized;
  }
  const root = window.location.origin.replace(/\/+$/, "");
  const path = normalized.startsWith("/") ? normalized : `/${normalized}`;
  return `${root}${path}`;
}

export function handleAdminImageFallback(event: { currentTarget: HTMLImageElement }) {
  const element = event.currentTarget;
  if (element.dataset.fallbackApplied === "true") {
    return;
  }
  element.dataset.fallbackApplied = "true";
  element.src = resolveAdminMediaUrl(DEFAULT_ADMIN_BANNER_IMAGE);
}

export function buildCustomerFacingSettingHints(settings: OperationSettingsResponse) {
  const subscribeHint = settings.deliverySubscribeEnabled
    ? `午餐 ${settings.deliverySubscribeLunchTime}、晚餐 ${settings.deliverySubscribeDinnerTime}；命中设置时间后才会发送取餐提醒订阅消息。`
    : "取餐提醒订阅已关闭，顾客不会在午餐或晚餐时段收到订阅消息。";
  return {
    bannerHint: `顾客首页将展示 ${countEnabledBannerImages(settings.bannerImages || "")} 张启用中的轮播图，并按 ${Math.max(1, settings.bannerIntervalSeconds || 3)} 秒轮播。`,
    popupHint: settings.popupAnnouncementEnabled
      ? "已开启锁定公告，用户进入小程序后只能查看公告。"
      : "锁定公告已关闭，用户可正常使用小程序。",
    reminderHint: settings.mealReminderPopupEnabled
      ? `顾客上线后，会按当前餐包状态弹出一次用餐提醒，并可勾选本次状态不再提示。${subscribeHint}`
      : `已关闭顾客上线提醒弹窗，顾客进入小程序后不会主动看到餐包状态提醒。${subscribeHint}`
  };
}

export type DispatchAiLogMetadataSummary = {
  orderCount: number;
  aiAdjustedCount: number;
  clusterCount: number;
  runStatusCode: string;
  runStatusLabel: string;
  runStatusDescription: string;
  thinkingStatus?: string;
  currentPhase?: string;
  providerError?: string;
  strategyMode?: string;
  anchorAddress?: string;
  inputAddresses?: string[];
  summary?: string;
  confidence?: number;
};

export type DispatchAiLogMetadataItem = {
  orderId: number;
  suggestedSequence: number;
  aiAdjusted: boolean;
  ruleSequence: number;
  addressLabel: string;
  clusterName: string;
  buildingName: string;
  roadName: string;
  distanceBand: string;
  neighborCount: number;
  adjustmentReason: string;
};

export type DispatchAiLogMetadataDetail = DispatchAiLogMetadataSummary & {
  analysisSteps: DispatchAiThinkingStep[];
  groups: DispatchAiThinkingGroup[];
  finalOrderIds: number[];
  perOrderReasons: DispatchAiThinkingOrderReason[];
  items: DispatchAiLogMetadataItem[];
};

export type DispatchAiThinkingStep = {
  type: string;
  title: string;
  message: string;
};

export type DispatchAiThinkingGroup = {
  groupName: string;
  orderIds: number[];
};

export type DispatchAiThinkingOrderReason = {
  orderId: number;
  reason: string;
};

export type DispatchAiConversationMessage = {
  id: string;
  role: "system" | "assistant" | "error";
  kind: string;
  title: string;
  content: string;
};

export function summarizeDispatchAiLogMetadata(rawMetadataJson: string): DispatchAiLogMetadataSummary | null {
  const detail = parseDispatchAiLogMetadata(rawMetadataJson);
  if (!detail) {
    return null;
  }
  return {
    orderCount: detail.orderCount,
    aiAdjustedCount: detail.aiAdjustedCount,
    clusterCount: detail.clusterCount,
    runStatusCode: detail.runStatusCode,
    runStatusLabel: detail.runStatusLabel,
    runStatusDescription: detail.runStatusDescription,
    thinkingStatus: detail.thinkingStatus,
    currentPhase: detail.currentPhase,
    providerError: detail.providerError,
    strategyMode: detail.strategyMode,
    anchorAddress: detail.anchorAddress,
    inputAddresses: detail.inputAddresses,
    summary: detail.summary,
    confidence: detail.confidence
  };
}

export function parseDispatchAiLogMetadata(rawMetadataJson: string): DispatchAiLogMetadataDetail | null {
  const normalized = rawMetadataJson.trim();
  if (!normalized) {
    return null;
  }
  try {
    const parsed = JSON.parse(normalized);
    const items = Array.isArray(parsed)
      ? parsed
      : Array.isArray(parsed?.items)
        ? parsed.items
        : [];
    const hasProgressFields = typeof parsed?.thinkingStatus === "string" || typeof parsed?.currentPhase === "string";
    if (!items.length && !hasProgressFields) {
      return null;
    }
    const normalizedItems: DispatchAiLogMetadataItem[] = items.map((item: any) => ({
      orderId: Number(item?.orderId || 0),
      suggestedSequence: Number(item?.suggestedSequence || 0),
      aiAdjusted: item?.aiAdjusted === true,
      ruleSequence: Number(item?.ruleSequence || 0),
      addressLabel: typeof item?.addressLabel === "string" ? item.addressLabel : "",
      clusterName: typeof item?.clusterName === "string" ? item.clusterName : "",
      buildingName: typeof item?.buildingName === "string" ? item.buildingName : "",
      roadName: typeof item?.roadName === "string" ? item.roadName : "",
      distanceBand: typeof item?.distanceBand === "string" ? item.distanceBand : "",
      neighborCount: Number(item?.neighborCount || 0),
      adjustmentReason: typeof item?.adjustmentReason === "string" ? item.adjustmentReason : ""
    }));
    const analysisSteps: DispatchAiThinkingStep[] = Array.isArray(parsed?.analysisSteps)
      ? parsed.analysisSteps.map((step: any) => ({
        type: typeof step?.type === "string" ? step.type : "analysis",
        title: typeof step?.title === "string" ? step.title : "AI 思考",
        message: typeof step?.message === "string" ? step.message : ""
      })).filter((step: DispatchAiThinkingStep) => step.title || step.message)
      : [];
    const groups: DispatchAiThinkingGroup[] = Array.isArray(parsed?.groups)
      ? parsed.groups.map((group: any) => ({
        groupName: typeof group?.groupName === "string" ? group.groupName : "未命名分组",
        orderIds: Array.isArray(group?.orderIds) ? group.orderIds.map((item: unknown) => Number(item || 0)).filter(Boolean) : []
      }))
      : [];
    const finalOrderIds = Array.isArray(parsed?.finalOrderIds)
      ? parsed.finalOrderIds.map((item: unknown) => Number(item || 0)).filter(Boolean)
      : [];
    const perOrderReasons: DispatchAiThinkingOrderReason[] = Array.isArray(parsed?.perOrderReasons)
      ? parsed.perOrderReasons.map((reason: any) => ({
        orderId: Number(reason?.orderId || 0),
        reason: typeof reason?.reason === "string" ? reason.reason : ""
      })).filter((reason: DispatchAiThinkingOrderReason) => reason.orderId > 0)
      : [];
    return {
      orderCount: items.length,
      aiAdjustedCount: items.filter((item: any) => item?.aiAdjusted === true).length,
      clusterCount: new Set(items.map((item: any) => String(item?.clusterName || "").trim()).filter(Boolean)).size,
      runStatusCode: typeof parsed?.runStatusCode === "string" ? parsed.runStatusCode : "",
      runStatusLabel: typeof parsed?.runStatusLabel === "string" ? parsed.runStatusLabel : "",
      runStatusDescription: typeof parsed?.runStatusDescription === "string" ? parsed.runStatusDescription : "",
      thinkingStatus: typeof parsed?.thinkingStatus === "string" ? parsed.thinkingStatus : "",
      currentPhase: typeof parsed?.currentPhase === "string" ? parsed.currentPhase : "",
      providerError: typeof parsed?.providerError === "string" ? parsed.providerError : "",
      strategyMode: typeof parsed?.strategyMode === "string" ? parsed.strategyMode : "",
      anchorAddress: typeof parsed?.anchorAddress === "string" ? parsed.anchorAddress : "",
      inputAddresses: Array.isArray(parsed?.inputAddresses)
        ? parsed.inputAddresses.filter((item: unknown): item is string => typeof item === "string")
        : [],
      summary: typeof parsed?.summary === "string" ? parsed.summary : "",
      confidence: typeof parsed?.confidence === "number" ? parsed.confidence : Number(parsed?.confidence || 0),
      analysisSteps,
      groups,
      finalOrderIds,
      perOrderReasons,
      items: normalizedItems
    };
  } catch {
    return null;
  }
}

export function buildDispatchAiConversation(detail: DispatchAiLogMetadataDetail): DispatchAiConversationMessage[] {
  const messages: DispatchAiConversationMessage[] = [];
  messages.push({
    id: "system-context",
    role: "system",
    kind: "context",
    title: "任务上下文",
    content: `已读取 ${detail.orderCount || detail.inputAddresses?.length || 0} 个地址，策略为 ${detail.strategyMode === "FAR_TO_NEAR" ? "远到近" : "近到远"}，锚点为 ${detail.anchorAddress || "未设置"}。`
  });
  if (detail.analysisSteps.length > 0) {
    detail.analysisSteps.forEach((step, index) => {
      messages.push({
        id: `step-${index}`,
        role: "assistant",
        kind: step.type,
        title: step.title,
        content: step.message
      });
    });
  } else if (detail.currentPhase) {
    messages.push({
      id: "assistant-phase",
      role: "assistant",
      kind: "progress",
      title: "当前阶段",
      content: detail.currentPhase
    });
  }
  if (detail.providerError) {
    messages.push({
      id: "assistant-error",
      role: "error",
      kind: "error",
      title: "AI 错误",
      content: detail.providerError
    });
  } else if (detail.summary) {
    messages.push({
      id: "assistant-summary",
      role: "assistant",
      kind: "summary",
      title: "排线结论",
      content: detail.summary
    });
  }
  return messages;
}

export function resolveDispatchAiOrderReasonMap(detail: DispatchAiLogMetadataDetail) {
  const reasonMap = new Map<number, string>();
  detail.perOrderReasons.forEach((item) => {
    reasonMap.set(item.orderId, item.reason);
  });
  detail.items.forEach((item) => {
    if (!reasonMap.has(item.orderId) && item.adjustmentReason) {
      reasonMap.set(item.orderId, item.adjustmentReason);
    }
  });
  return reasonMap;
}

export function resolveDispatchRunTypeLabel(runType: string) {
  return runType === "TEST" ? "测试实验" : "真实运行";
}

export function isProductionDispatchLog(log: Pick<DispatchAiJobLogResponse, "runType">) {
  return log.runType !== "TEST";
}

export function pickLatestProductionDispatchLog(logs: DispatchAiJobLogResponse[]) {
  return logs.find((log) => isProductionDispatchLog(log)) || null;
}

export function resolveDispatchRunStatusLabel(statusCode: string, fallbackLabel: string) {
  switch ((statusCode || "").trim()) {
    case "AI_CONFIRMED_RULE":
    case "RULE_ONLY":
      return "AI 已复核";
    default:
      return fallbackLabel.trim();
  }
}

export function resolveDispatchRunStatusDescription(
  statusCode: string,
  fallbackDescription: string,
  aiAdjustedCount: number
) {
  const normalizedCode = (statusCode || "").trim();
  if ((normalizedCode === "AI_CONFIRMED_RULE" || normalizedCode === "RULE_ONLY") && aiAdjustedCount <= 0) {
    return "AI 修正阶段已执行，当前规则顺序已通过校验，无需额外调整。";
  }
  return fallbackDescription.trim();
}

export function resolveDispatchRouteStatusTone(statusCode: string) {
  switch (statusCode) {
    case "AI_SUCCESS":
    case "AI_CONFIRMED_RULE":
    case "RULE_ONLY":
      return "tag-green";
    case "NO_ORDERS":
    case "AI_REQUIRED_UNAVAILABLE":
    case "AI_SKIPPED_UNAVAILABLE":
    case "AI_SKIPPED_LOW_BALANCE":
    case "AI_FALLBACK_RULE":
      return "tag-orange";
    case "FAILED_INTERNAL":
      return "tag-red";
    default:
      return "tag-gray";
  }
}
