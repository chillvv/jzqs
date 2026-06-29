import type { OperationSettingsResponse } from "../../shared/api/types";

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
