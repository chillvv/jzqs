import type { OperationSettingsResponse } from "../../shared/api/types";

const DEFAULT_BANNER_IMAGES = ["../../assets/hero-new.jpg"];

export function resolveOrderingTone(enabled: boolean) {
  return enabled ? "green" : "gray";
}

export function buildOperationRiskSummary(settings: OperationSettingsResponse) {
  return {
    primaryHint: settings.orderingEnabled
      ? "接单通道已开启，停业前请先关停。"
      : "接单通道已关闭，恢复营业前记得重新开启。",
    secondaryHint: settings.holidayNoticeTitle && settings.holidayNoticeDesc
      ? "前台公告已配置，恢复前核对展示时间和文案。"
      : "当前未配置前台公告，临时停单时用户侧不会收到说明。",
    tone: settings.orderingEnabled ? "warning" : "info"
  };
}

export function normalizeBannerImages(rawBannerImages: string) {
  if (!rawBannerImages.trim()) {
    return DEFAULT_BANNER_IMAGES;
  }

  try {
    const parsed = JSON.parse(rawBannerImages);
    if (!Array.isArray(parsed)) {
      return DEFAULT_BANNER_IMAGES;
    }

    const images = parsed.filter((item): item is string => typeof item === "string" && item.trim().length > 0);
    return images.length > 0 ? images : DEFAULT_BANNER_IMAGES;
  } catch {
    return DEFAULT_BANNER_IMAGES;
  }
}

export function countBannerImages(rawBannerImages: string) {
  return normalizeBannerImages(rawBannerImages).length;
}

export function buildCustomerFacingSettingHints(settings: OperationSettingsResponse) {
  return {
    orderHint: settings.orderingEnabled
      ? "顾客端会显示正常预订入口。"
      : "顾客端会显示暂停接单说明。",
    bannerHint: `顾客首页将展示 ${countBannerImages(settings.bannerImages || "")} 张轮播图。`,
    popupHint: settings.popupAnnouncementEnabled
      ? "仅已登录顾客会看到登录后弹窗公告。"
      : "顾客端不会出现登录后弹窗公告。"
  };
}
