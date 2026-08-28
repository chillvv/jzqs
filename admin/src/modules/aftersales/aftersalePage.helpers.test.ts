import { describe, expect, it } from "vitest";
import {
  buildAftersaleView,
  buildAftersaleResolveFormState,
  buildAftersaleTabs,
  resolveAftersaleAvailableActions,
  resolveAftersaleCompactStatusLabel,
  resolveSettlementSummary,
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
      settledLossMeals: 0,
      giftZeroMealCount: 0,
      giftVeggieJuiceCount: 0,
      adminRemark: "",
      refundBlocking: false
    });
  });

  it("defaults compensation cases to compensate-meals resolution", () => {
    expect(buildAftersaleResolveFormState("COMPENSATION")).toEqual({
      action: "COMPENSATE_MEALS",
      walletDelta: 1,
      settledLossMeals: 0,
      giftZeroMealCount: 0,
      giftVeggieJuiceCount: 0,
      adminRemark: "",
      refundBlocking: false
    });
  });

  it("uses a clearer rejected status label for compact customer-facing handoff", () => {
    expect(resolveAftersaleCompactStatusLabel("REJECTED")).toBe("未通过");
  });

  it("hides auto refund records when the flag is enabled", () => {
    const items = [
      {
        id: 1,
        sourceCategory: "AUTO_REFUND",
        status: "COMPLETED",
        type: "REFUND",
        customerName: "张三",
        customerPhone: "13800000001",
        reasonText: "自动退款",
        reasonCode: "AUTO",
        issueParamSummary: "",
        orderId: 1
      },
      {
        id: 2,
        sourceCategory: "NORMAL",
        status: "PENDING",
        type: "COMPENSATION",
        customerName: "李四",
        customerPhone: "13800000002",
        reasonText: "漏餐",
        reasonCode: "LOSS",
        issueParamSummary: "午餐",
        orderId: 2
      }
    ] as any;

    expect(buildAftersaleView(items, {
      status: "ALL",
      type: "ALL",
      keyword: "",
      hideAutoRefund: true
    })).toHaveLength(1);
  });

  it("shows settlement action labels for compensation gifts", () => {
    expect(resolveSettlementSummary({
      resolutionAction: "REGISTER_ONLY",
      walletDelta: 0,
      giftZeroMealCount: 1,
      giftVeggieJuiceCount: 1
    } as any)).toBe("仅登记 / 补零餐 1 / 果蔬汁 1");
  });
});
