import { describe, expect, it } from "vitest";
import {
  buildVisibleCustomerAddresses,
  buildCustomerOverviewSummary,
  normalizeInitialMealsValue,
  resolveCustomerSpecialMark,
  resolvePrimaryCustomerAddress
} from "./customerAssetPage.helpers";

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
