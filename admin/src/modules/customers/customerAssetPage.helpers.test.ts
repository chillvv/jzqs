import { describe, expect, it } from "vitest";
import { buildCustomerOverviewSummary, resolveCustomerSpecialMark } from "./customerAssetPage.helpers";

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
