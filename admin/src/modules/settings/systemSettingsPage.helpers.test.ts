import { describe, expect, it } from "vitest";
import {
  buildCustomerFacingSettingHints,
  countBannerImages,
  countPageLinkedBannerImages,
  normalizeBannerConfigs,
  parseDispatchAiLogMetadata,
  pickLatestProductionDispatchLog,
  resolveAdminMediaUrl,
  resolveBannerActionSummary,
  resolveDispatchRunStatusDescription,
  resolveDispatchRunStatusLabel,
  resolveDispatchRunTypeLabel,
  summarizeDispatchAiLogMetadata
} from "./systemSettingsPage.helpers";

describe("normalizeBannerConfigs", () => {
  it("returns default banner when backend value is empty", () => {
    expect(normalizeBannerConfigs("")).toEqual([
      {
        imageUrl: "../../assets/hero-new.jpg",
        enabled: true
      }
    ]);
  });

  it("falls back to default banner when backend value is invalid json", () => {
    expect(normalizeBannerConfigs("not-json")[0]?.imageUrl).toBe("../../assets/hero-new.jpg");
  });

  it("normalizes legacy banner json into image and enabled fields only", () => {
    expect(normalizeBannerConfigs('[{"imageUrl":"/banner-a.jpg","actionType":"MINI_PROGRAM_PAGE","actionTarget":"pages/order/index"}]')).toEqual([
      {
        imageUrl: "/banner-a.jpg",
        enabled: true
      }
    ]);
  });
});

describe("countBannerImages", () => {
  it("counts banners from valid json and stays safe on invalid json", () => {
    expect(countBannerImages('["/banner-a.jpg", "/banner-b.jpg"]')).toBe(2);
    expect(countBannerImages("{")).toBe(1);
  });
});

describe("countPageLinkedBannerImages", () => {
  it("always returns 0 because banners no longer support page links", () => {
    expect(
      countPageLinkedBannerImages('[{"imageUrl":"/banner-a.jpg","actionType":"MINI_PROGRAM_PAGE","actionTarget":"/pages/order/index"},{"imageUrl":"/banner-b.jpg","actionType":"PREVIEW_IMAGE","actionTarget":""}]')
    ).toBe(0);
  });
});

describe("resolveBannerActionSummary", () => {
  it("always returns preview copy", () => {
    expect(resolveBannerActionSummary({
      imageUrl: "/banner-a.jpg",
      enabled: true
    })).toBe("点击查看大图");
  });
});

describe("resolveAdminMediaUrl", () => {
  it("keeps upload paths relative in browser so dev proxy can forward them", () => {
    const originalWindow = globalThis.window;
    Object.defineProperty(globalThis, "window", {
      value: {
        location: {
          origin: "http://localhost:5173"
        }
      },
      configurable: true
    });

    expect(resolveAdminMediaUrl("/uploads/settings-banners/2026-06-12/banner-a.jpg")).toBe("/uploads/settings-banners/2026-06-12/banner-a.jpg");

    Object.defineProperty(globalThis, "window", {
      value: originalWindow,
      configurable: true
    });
  });
});

describe("buildCustomerFacingSettingHints", () => {
  it("explains popup impact on customers", () => {
    expect(
      buildCustomerFacingSettingHints({
        orderingEnabled: true,
        orderingStatusLabel: "开启",
        holidayNoticeTitle: "店休",
        holidayNoticeDesc: "明日恢复",
        emergencyActionLabel: "恢复",
        bannerImages: '["/banner.jpg"]',
        bannerIntervalSeconds: 5,
        popupAnnouncementEnabled: true,
        popupAnnouncementContent: "节日安排"
      } as any).popupHint
    ).toContain("只能查看公告");
  });
});

describe("summarizeDispatchAiLogMetadata", () => {
  it("supports both legacy array payloads and new object payloads", () => {
    expect(
      summarizeDispatchAiLogMetadata('[{"orderId":1,"suggestedSequence":1,"aiAdjusted":false},{"orderId":2,"suggestedSequence":2,"aiAdjusted":true}]')
    ).toEqual(expect.objectContaining({
      orderCount: 2,
      aiAdjustedCount: 1,
      clusterCount: 0,
      runStatusCode: "",
      runStatusLabel: "",
      runStatusDescription: ""
    }));

    expect(
      summarizeDispatchAiLogMetadata('{"runStatusCode":"AI_SUCCESS","runStatusLabel":"AI 已修正","runStatusDescription":"已聚合同片区","items":[{"orderId":3,"suggestedSequence":1,"aiAdjusted":true,"clusterName":"A栋"},{"orderId":4,"suggestedSequence":2,"aiAdjusted":true,"clusterName":"A栋"},{"orderId":5,"suggestedSequence":3,"aiAdjusted":false,"clusterName":"B栋"}]}')
    ).toEqual(expect.objectContaining({
      orderCount: 3,
      aiAdjustedCount: 2,
      clusterCount: 2,
      runStatusCode: "AI_SUCCESS",
      runStatusLabel: "AI 已修正",
      runStatusDescription: "已聚合同片区"
    }));
  });
});

describe("dispatch route helpers", () => {
  it("parses detail payload and keeps latest production log semantics", () => {
    expect(
      parseDispatchAiLogMetadata('{"runStatusCode":"AI_SUCCESS","runStatusLabel":"AI 已修正","runStatusDescription":"已聚合同片区","items":[{"orderId":3,"suggestedSequence":1,"aiAdjusted":true,"clusterName":"A栋","addressLabel":"A栋101"}]}')
    ).toEqual(expect.objectContaining({
      orderCount: 1,
      aiAdjustedCount: 1,
      clusterCount: 1,
      items: [
        expect.objectContaining({
          orderId: 3,
          suggestedSequence: 1,
          addressLabel: "A栋101"
        })
      ]
    }));

    expect(
      pickLatestProductionDispatchLog([
        { id: 1, runType: "TEST" },
        { id: 2, runType: "PRODUCTION" }
      ] as any)
    ).toEqual(expect.objectContaining({ id: 2 }));

    expect(resolveDispatchRunTypeLabel("TEST")).toBe("测试实验");
    expect(resolveDispatchRunTypeLabel("PRODUCTION")).toBe("真实运行");
  });

  it("relabels ai-confirmed rule results into product wording", () => {
    expect(resolveDispatchRunStatusLabel("AI_CONFIRMED_RULE", "AI 已参与")).toBe("AI 已复核");
    expect(resolveDispatchRunStatusLabel("RULE_ONLY", "仅规则输出")).toBe("AI 已复核");
    expect(resolveDispatchRunStatusDescription("AI_CONFIRMED_RULE", "旧文案", 0)).toContain("AI 修正阶段已执行");
    expect(resolveDispatchRunStatusDescription("AI_SUCCESS", "AI 已修正 2 单", 2)).toBe("AI 已修正 2 单");
  });
});
