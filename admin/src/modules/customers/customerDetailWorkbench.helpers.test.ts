import { describe, expect, it } from "vitest";
import { buildCustomerActionLabels } from "./customerAssetPage.helpers";

describe("buildCustomerActionLabels", () => {
  it("returns detail, supplement, and deduct actions for list rows", () => {
    expect(buildCustomerActionLabels()).toEqual(["详情资料", "加餐", "扣餐"]);
  });
});
