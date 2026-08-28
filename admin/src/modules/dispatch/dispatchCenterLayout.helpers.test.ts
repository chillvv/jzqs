import { describe, expect, it } from "vitest";
import { hasDisplayValue, hasOrderAttention, reorderDispatchAreaOrders, validateCreateRiderDraft } from "./dispatchCenterLayout.helpers";

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
        areaCode: ""
      })
    ).toEqual({
      riderName: "",
      phone: "手机号少了一位哦，请输入 11 位手机号"
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
});
