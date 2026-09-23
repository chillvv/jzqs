import { describe, expect, it } from "vitest";
import {
  buildCreateRiderPayload,
  clampPercent,
  formatCostPerOrder,
  formatMonthlySalary,
  getActiveQueueLabel,
  groupBatchesByMealPeriod,
  hasDisplayValue,
  hasOrderAttention,
  mealPeriodLabel,
  normalizeDispatchOverview,
  parseMonthlySalaryInput,
  reorderDispatchAreaOrders,
  resolveRiderCostTone,
  riderStatusLabel,
  riderStatusTagClass,
  validateCreateRiderDraft,
  validateMonthlySalaryInput
} from "./dispatchCenterLayout.helpers";
import type { DispatchBatchResponse } from "../../shared/api/types";

function buildBatch(mealPeriod: string, batchId: number): DispatchBatchResponse {
  return {
    batchId,
    serveDate: "2026-05-14",
    mealPeriod,
    riderProfileId: 1,
    riderName: "张师傅",
    areaCode: "A",
    batchStatus: "DISPATCHING",
    totalCount: 10,
    deliveredCount: 3,
    currentSequence: 4,
    currentCustomerName: "张三",
    nextCustomerName: "李四"
  };
}

describe("dispatchCenterLayout helpers", () => {
  it("treats empty strings, blanks and dash as no display value", () => {
    expect(hasDisplayValue("")).toBe(false);
    expect(hasDisplayValue("   ")).toBe(false);
    expect(hasDisplayValue("-")).toBe(false);
    expect(hasDisplayValue("用户备注")).toBe(true);
  });

  it("shows attention only when user or merchant remark has value", () => {
    expect(hasOrderAttention({ userNote: "", merchantRemark: "" })).toBe(false);
    expect(hasOrderAttention({ userNote: " ", merchantRemark: "-" })).toBe(false);
    expect(hasOrderAttention({ userNote: "少饭", merchantRemark: "" })).toBe(true);
    expect(hasOrderAttention({ userNote: "", merchantRemark: "优先配送" })).toBe(true);
  });

  it("ignores receipt note and images when deciding attention", () => {
    expect(
      hasOrderAttention({
        userNote: "",
        merchantRemark: "",
        receiptNote: "已放前台",
        receiptUrl: "https://img.example.com/receipt.jpg"
      } as any)
    ).toBe(false);
  });

  it("returns plain-language phone guidance for invalid rider phone numbers", () => {
    expect(
      validateCreateRiderDraft({
        riderName: "王师傅",
        phone: "1380013800",
        enabled: true,
        monthlySalary: ""
      })
    ).toEqual({
      riderName: "",
      phone: "手机号少了一位哦，请输入 11 位手机号",
      monthlySalary: ""
    });
  });

  it("reorders area orders by final order ids", () => {
    const result = reorderDispatchAreaOrders(
      [
        { orderId: 1001, sequenceNumber: 1, customerName: "张三", deliveryAddress: "A", deliveryStatus: "PENDING", riderName: null, userNote: "", merchantRemark: "", referenceImageUrl: "", receiptUrl: "", receiptNote: "", deliveredAt: null, quantity: 1 },
        { orderId: 1002, sequenceNumber: 2, customerName: "李四", deliveryAddress: "B", deliveryStatus: "PENDING", riderName: null, userNote: "", merchantRemark: "", referenceImageUrl: "", receiptUrl: "", receiptNote: "", deliveredAt: null, quantity: 1 }
      ],
      [1002, 1001]
    );

    expect(result.map((item) => item.orderId)).toEqual([1002, 1001]);
  });

  it("translates meal periods to chinese labels", () => {
    expect(mealPeriodLabel("DINNER")).toBe("晚餐");
    expect(mealPeriodLabel("LUNCH")).toBe("午餐");
  });

  it("labels rider employment status with matching tag classes", () => {
    expect(riderStatusLabel("ACTIVE")).toBe("启用中");
    expect(riderStatusLabel("DISABLED")).toBe("已停用");
    expect(riderStatusTagClass("ACTIVE")).toBe("tag-green");
    expect(riderStatusTagClass("DISABLED")).toBe("tag-gray");
  });

  it("falls back to zeroed overview counters when the api returns nothing", () => {
    expect(normalizeDispatchOverview({})).toEqual({
      pendingCount: 0,
      dispatchingCount: 0,
      missingRiderAreaCount: 0,
      crossMealDeliveryOutCount: 0,
      crossMealDeliveryInCount: 0
    });
  });

  it("reports rider name and phone validation errors separately", () => {
    expect(validateCreateRiderDraft({ riderName: "", phone: "13800000001", enabled: true, monthlySalary: "" })).toEqual({
      riderName: "请填写骑手姓名",
      phone: "",
      monthlySalary: ""
    });
    expect(validateCreateRiderDraft({ riderName: "张", phone: "13800000001", enabled: true, monthlySalary: "" })).toEqual({
      riderName: "姓名需为2-20字",
      phone: "",
      monthlySalary: ""
    });
    expect(validateCreateRiderDraft({ riderName: "张三!", phone: "13800000001", enabled: true, monthlySalary: "" })).toEqual({
      riderName: "姓名仅支持中文、字母、数字和间隔号",
      phone: "",
      monthlySalary: ""
    });
  });

  it("trims rider draft fields and maps the enabled flag to employment status", () => {
    expect(buildCreateRiderPayload({ riderName: " 张三 ", phone: " 13800000001 ", enabled: true, monthlySalary: "" })).toEqual({
      riderName: "张三",
      displayName: "张三",
      phone: "13800000001",
      employmentStatus: "ACTIVE",
      monthlySalary: null
    });
    expect(buildCreateRiderPayload({ riderName: "李四", phone: "13800000002", enabled: false, monthlySalary: "" })).toEqual({
      riderName: "李四",
      displayName: "李四",
      phone: "13800000002",
      employmentStatus: "DISABLED",
      monthlySalary: null
    });
  });

  it("validates and parses the optional monthly salary used by rider monthly cost stats", () => {
    // 留空 = 未设置月薪，不参与成本计算，也不该拦住提交
    expect(validateMonthlySalaryInput("")).toBe("");
    expect(validateMonthlySalaryInput("   ")).toBe("");
    expect(parseMonthlySalaryInput("")).toBeNull();

    expect(validateMonthlySalaryInput("6000")).toBe("");
    expect(parseMonthlySalaryInput(" 6000.50 ")).toBe(6000.5);

    expect(validateMonthlySalaryInput("abc")).toBe("月薪需为数字");
    expect(validateMonthlySalaryInput("-1")).toBe("月薪不能为负数");

    expect(formatMonthlySalary(null)).toBe("未设置");
    expect(formatMonthlySalary(0)).toBe("未设置");
    expect(formatMonthlySalary(6000)).toBe("¥ 6000.00");
    expect(formatCostPerOrder(null)).toBe("--");
    expect(formatCostPerOrder(2.5)).toBe("¥ 2.50");

    expect(
      buildCreateRiderPayload({ riderName: "王五", phone: "13800000005", enabled: true, monthlySalary: "8000" })
    ).toEqual({
      riderName: "王五",
      displayName: "王五",
      phone: "13800000005",
      employmentStatus: "ACTIVE",
      monthlySalary: 8000
    });
  });

  it("keeps share bars inside 0-100 and classifies unit cost against the monthly baseline", () => {
    expect(clampPercent(42.3)).toBe(42.3);
    expect(clampPercent(-5)).toBe(0);
    expect(clampPercent(120)).toBe(100);
    expect(clampPercent(undefined)).toBe(0);

    // 没有单均成本（未设月薪 / 当月无单）的骑手不参与分档
    expect(resolveRiderCostTone(null, 5)).toBe("none");
    // 还没有整体基准（无人设月薪）时统一按「正常」显示，避免全部标红或标绿
    expect(resolveRiderCostTone(5, null)).toBe("mid");
    expect(resolveRiderCostTone(5, 0)).toBe("mid");

    const baseline = 10;
    expect(resolveRiderCostTone(7, baseline)).toBe("low");
    expect(resolveRiderCostTone(9.5, baseline)).toBe("mid");
    expect(resolveRiderCostTone(13, baseline)).toBe("high");
  });

  it("groups dispatch batches by meal period", () => {
    const groups = groupBatchesByMealPeriod([
      buildBatch("LUNCH", 1),
      buildBatch("DINNER", 2)
    ]);

    expect(groups.lunch.map((item) => item.batchId)).toEqual([1]);
    expect(groups.dinner.map((item) => item.batchId)).toEqual([2]);
  });

  it("describes the active queue with current and next customer", () => {
    expect(getActiveQueueLabel(buildBatch("LUNCH", 1))).toBe("张三 / 下一单 李四");
    expect(getActiveQueueLabel({ ...buildBatch("LUNCH", 1), currentCustomerName: null, nextCustomerName: null }))
      .toBe("待开始 / 下一单 无");
  });
});
