import { describe, expect, it } from "vitest";
import {
  buildAftersaleResolveFormState,
  buildAftersaleTabs,
  resolveAftersaleAvailableActions,
  resolveAftersaleCompactStatusLabel,
  resolveAftersaleTone
} from "./aftersalePage.helpers";

describe("aftersale helpers", () => {
  it("builds the default status tabs for the aftersale center", () => {
    expect(buildAftersaleTabs(4, 2, 5, 1)).toEqual([
      { key: "PENDING", label: "待处理", count: 4 },
      { key: "PROCESSING", label: "处理中", count: 2 },
      { key: "COMPLETED", label: "已完成", count: 5 },
      { key: "REJECTED", label: "已驳回", count: 1 }
    ]);
  });

  it("marks completed refunds as green", () => {
    expect(resolveAftersaleTone("COMPLETED", "REFUND")).toBe("green");
  });

  it("shows refund and reject actions for pending refund cases", () => {
    expect(resolveAftersaleAvailableActions("REFUND", "PENDING")).toEqual([
      "REFUND_TO_WALLET",
      "REJECT"
    ]);
  });

  it("shows compensation actions for compensation cases", () => {
    expect(resolveAftersaleAvailableActions("COMPENSATION", "PENDING")).toEqual([
      "COMPENSATE_MEALS",
      "REGISTER_ONLY",
      "REJECT"
    ]);
  });

  it("defaults refund cases to refund-to-wallet resolution", () => {
    expect(buildAftersaleResolveFormState("REFUND")).toEqual({
      action: "REFUND_TO_WALLET",
      walletDelta: 1,
      adminRemark: "",
      refundBlocking: false
    });
  });

  it("defaults compensation cases to compensate-meals resolution", () => {
    expect(buildAftersaleResolveFormState("COMPENSATION")).toEqual({
      action: "COMPENSATE_MEALS",
      walletDelta: 1,
      adminRemark: "",
      refundBlocking: false
    });
  });

  it("uses a clearer rejected status label for compact customer-facing handoff", () => {
    expect(resolveAftersaleCompactStatusLabel("REJECTED")).toBe("未通过");
  });
});
