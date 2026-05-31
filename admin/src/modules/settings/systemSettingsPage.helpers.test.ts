import { describe, expect, it } from "vitest";
import {
  buildCustomerFacingSettingHints,
  buildOperationRiskSummary,
  countBannerImages,
  normalizeBannerImages,
  resolveOrderingTone
} from "./systemSettingsPage.helpers";

describe("resolveOrderingTone", () => {
  it("maps ordering state to tag tone", () => {
    expect(resolveOrderingTone(true)).toBe("green");
    expect(resolveOrderingTone(false)).toBe("gray");
  });
});

describe("buildOperationRiskSummary", () => {
  it("returns concise warning copy when ordering is enabled", () => {
    expect(
      buildOperationRiskSummary({
        orderingEnabled: true,
        orderingStatusLabel: "通道开启中",
        holidayNoticeTitle: "",
        holidayNoticeDesc: "",
        emergencyActionLabel: "立即暂停接单"
      })
    ).toEqual({
      primaryHint: "接单通道已开启，停业前请先关停。",
      secondaryHint: "当前未配置前台公告，临时停单时用户侧不会收到说明。",
      tone: "warning"
    });
  });

  it("returns recovery guidance when ordering is disabled and notice exists", () => {
    expect(
      buildOperationRiskSummary({
        orderingEnabled: false,
        orderingStatusLabel: "通道已关闭",
        holidayNoticeTitle: "店休公告",
        holidayNoticeDesc: "明日恢复",
        emergencyActionLabel: "恢复接单"
      })
    ).toEqual({
      primaryHint: "接单通道已关闭，恢复营业前记得重新开启。",
      secondaryHint: "前台公告已配置，恢复前核对展示时间和文案。",
      tone: "info"
    });
  });
});

describe("normalizeBannerImages", () => {
  it("returns default banner when backend value is empty", () => {
    expect(normalizeBannerImages("")).toEqual(["../../assets/hero-new.jpg"]);
  });

  it("falls back to default banner when backend value is invalid json", () => {
    expect(normalizeBannerImages("not-json")).toEqual(["../../assets/hero-new.jpg"]);
  });

  it("filters non-string items from parsed json array", () => {
    expect(normalizeBannerImages('["/banner-a.jpg", 1, "", null, "/banner-b.jpg"]')).toEqual([
      "/banner-a.jpg",
      "/banner-b.jpg"
    ]);
  });
});

describe("countBannerImages", () => {
  it("counts banners from valid json and stays safe on invalid json", () => {
    expect(countBannerImages('["/banner-a.jpg", "/banner-b.jpg"]')).toBe(2);
    expect(countBannerImages("{")).toBe(1);
  });
});

describe("buildCustomerFacingSettingHints", () => {
  it("explains impact on customers", () => {
    expect(
      buildCustomerFacingSettingHints({
        orderingEnabled: false,
        orderingStatusLabel: "暂停接单",
        holidayNoticeTitle: "店休",
        holidayNoticeDesc: "明日恢复",
        emergencyActionLabel: "恢复",
        bannerImages: '["/banner.jpg"]',
        popupAnnouncementEnabled: true,
        popupAnnouncementContent: "节日安排"
      } as any).popupHint
    ).toContain("仅已登录顾客");
  });
});
