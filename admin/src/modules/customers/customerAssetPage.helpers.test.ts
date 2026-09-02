import { describe, expect, it } from "vitest";
import {
  buildCustomerAssetStats,
  buildCustomerPortfolioSummary,
  buildVisibleCustomerAddresses,
  buildCustomerOverviewSummary,
  filterCustomerAssets,
  normalizeInitialMealsValue,
  resolveCustomerOrderModeLabel,
  resolveCustomerSpecialMark,
  resolveCustomerStatusLabel,
  resolvePrimaryCustomerAddress
} from "./customerAssetPage.helpers";
import type { CustomerAssetResponse } from "../../shared/api/types";

const assetItems: CustomerAssetResponse[] = [
  {
    id: 1,
    name: "张先生",
    phone: "13800000001",
    customerStatus: "INTENTION",
    totalMeals: 0,
    remainingMeals: 0,
    hasOpenedCard: false,
    fixedSubscriptionEnabled: false,
    priorityCustomer: false,
    priorityTag: null,
    merchantRemark: "准备开卡",
    openedAt: null,
    packageExpiredAt: null,
    remainingValidityDays: 0,
    packageAlertCode: "",
    packageAlertLabel: "",
    lastOrderAt: null,
    registeredAt: "2026-05-01 08:00:00",
    status: "ACTIVE"
  },
  {
    id: 2,
    name: "李女士",
    phone: "13800000002",
    customerStatus: "FORMAL",
    totalMeals: 33,
    remainingMeals: 12,
    hasOpenedCard: true,
    fixedSubscriptionEnabled: true,
    priorityCustomer: true,
    priorityTag: "新开卡优先",
    merchantRemark: "重点关注",
    openedAt: "2026-05-01 08:00:00",
    packageExpiredAt: "2026-06-30",
    remainingValidityDays: 30,
    packageAlertCode: "NORMAL",
    packageAlertLabel: "正常",
    lastOrderAt: "2026-05-13 11:30:00",
    registeredAt: "2026-05-01 08:00:00",
    status: "ACTIVE"
  },
  {
    id: 3,
    name: "王女士",
    phone: "13800000003",
    customerStatus: "DORMANT",
    totalMeals: 7,
    remainingMeals: 0,
    hasOpenedCard: true,
    fixedSubscriptionEnabled: false,
    priorityCustomer: false,
    priorityTag: null,
    merchantRemark: "很久未下单",
    openedAt: "2025-10-01 08:00:00",
    packageExpiredAt: "2025-12-31",
    remainingValidityDays: -100,
    packageAlertCode: "EXPIRED",
    packageAlertLabel: "已过期",
    lastOrderAt: "2025-12-01 18:00:00",
    registeredAt: "2025-10-01 08:00:00",
    status: "EXHAUSTED"
  },
  {
    id: 4,
    name: "陈先生",
    phone: "13800000004",
    customerStatus: "FORMAL",
    totalMeals: 22,
    remainingMeals: 2,
    hasOpenedCard: true,
    fixedSubscriptionEnabled: false,
    priorityCustomer: false,
    priorityTag: null,
    merchantRemark: "普通客户",
    openedAt: "2026-04-01 08:00:00",
    packageExpiredAt: "2026-05-20",
    remainingValidityDays: 5,
    packageAlertCode: "EXPIRING_SOON",
    packageAlertLabel: "即将过期",
    lastOrderAt: "2026-05-12 12:00:00",
    registeredAt: "2026-04-01 08:00:00",
    status: "ACTIVE"
  }
];

describe("buildCustomerOverviewSummary", () => {
  it("returns only formal and fixed-subscription metrics", () => {
    const result = buildCustomerOverviewSummary(
      {
        formalCount: 12,
        fixedSubscriptionCount: 5
      }
    );

    expect(result).toEqual([
      { label: "正式用户", value: "12 人", tone: "slate" },
      { label: "固定订餐", value: "5 人", tone: "slate" }
    ]);
  });
});

describe("resolveCustomerSpecialMark", () => {
  it("returns trimmed remark text as the only special mark source", () => {
    expect(resolveCustomerSpecialMark("  需要单独联系  ")).toBe("需要单独联系");
    expect(resolveCustomerSpecialMark("")).toBeNull();
    expect(resolveCustomerSpecialMark("   ")).toBeNull();
    expect(resolveCustomerSpecialMark(null)).toBeNull();
  });
});

describe("normalizeInitialMealsValue", () => {
  it("keeps an empty string empty so the input can be cleared before typing", () => {
    expect(normalizeInitialMealsValue("")).toBe("");
  });

  it("falls back to zero only when the field is truly missing", () => {
    expect(normalizeInitialMealsValue(undefined)).toBe("0");
    expect(normalizeInitialMealsValue(null)).toBe("0");
  });

  it("preserves entered numeric strings", () => {
    expect(normalizeInitialMealsValue("12")).toBe("12");
  });
});

describe("resolvePrimaryCustomerAddress", () => {
  it("returns default address first", () => {
    expect(resolvePrimaryCustomerAddress([
      { id: 2, contactName: "后门", contactPhone: "139", addressLine: "B座", isDefault: false },
      { id: 1, contactName: "前台", contactPhone: "138", addressLine: "A座", isDefault: true }
    ])?.id).toBe(1);
  });

  it("falls back to the first address when no default exists", () => {
    expect(resolvePrimaryCustomerAddress([
      { id: 2, contactName: "后门", contactPhone: "139", addressLine: "B座", isDefault: false }
    ])?.id).toBe(2);
  });
});

describe("buildVisibleCustomerAddresses", () => {
  it("returns all addresses in original order", () => {
    expect(buildVisibleCustomerAddresses([
      { id: 1, contactName: "前台", contactPhone: "138", addressLine: "A座", isDefault: true },
      { id: 2, contactName: "后门", contactPhone: "139", addressLine: "B座", isDefault: false }
    ]).map((item) => item.id)).toEqual([1, 2]);
  });

  it("returns empty array for missing or empty address lists", () => {
    expect(buildVisibleCustomerAddresses(null)).toEqual([]);
    expect(buildVisibleCustomerAddresses(undefined)).toEqual([]);
    expect(buildVisibleCustomerAddresses([])).toEqual([]);
  });
});

describe("buildCustomerAssetStats", () => {
  it("counts formal, dormant and fixed-subscription customers", () => {
    expect(buildCustomerAssetStats(assetItems)).toEqual({
      formalCount: 2,
      dormantCount: 1,
      fixedSubscriptionCount: 1
    });
  });
});

describe("filterCustomerAssets", () => {
  it("combines keyword, status, balance and order-mode filters", () => {
    const filtered = filterCustomerAssets(assetItems, {
      keyword: "13800000002",
      customerStatus: "FORMAL",
      balanceState: "HAS_BALANCE",
      orderMode: "SUBSCRIPTION",
      remainingValidityState: "ALL"
    });

    expect(filtered.map((item) => item.id)).toEqual([2]);
  });

  it("keeps only opened cards with at most three remaining meals for low balance", () => {
    const filtered = filterCustomerAssets(assetItems, {
      keyword: "",
      customerStatus: "ALL",
      balanceState: "LOW_BALANCE",
      orderMode: "ALL",
      remainingValidityState: "ALL"
    });

    expect(filtered.map((item) => item.id)).toEqual([4]);
  });

  it("matches keyword against merchant remark for no-balance normal orders", () => {
    const filtered = filterCustomerAssets(assetItems, {
      keyword: "未下单",
      customerStatus: "ALL",
      balanceState: "NO_BALANCE",
      orderMode: "NORMAL",
      remainingValidityState: "ALL"
    });

    expect(filtered.map((item) => item.id)).toEqual([3]);
  });
});

describe("buildCustomerPortfolioSummary", () => {
  it("counts low-balance, exhausted, vip and recently active customers", () => {
    expect(buildCustomerPortfolioSummary(assetItems)).toEqual({
      lowBalanceCount: 1,
      exhaustedCount: 1,
      vipCount: 1,
      recentActiveCount: 2
    });
  });
});

describe("resolveCustomerOrderModeLabel", () => {
  it("labels fixed-subscription customers separately from normal ordering", () => {
    expect(resolveCustomerOrderModeLabel(assetItems[0])).toBe("普通下单");
    expect(resolveCustomerOrderModeLabel(assetItems[1])).toBe("固定订餐");
  });
});

describe("resolveCustomerStatusLabel", () => {
  it("maps known statuses and falls back to formal for anything else", () => {
    expect(resolveCustomerStatusLabel("FORMAL")).toBe("正式客户");
    expect(resolveCustomerStatusLabel("DORMANT")).toBe("沉睡客户");
    // 旧版"意向客户"文案已下线：未知状态统一回落为正式客户
    expect(resolveCustomerStatusLabel("INTENTION")).toBe("正式客户");
  });
});
