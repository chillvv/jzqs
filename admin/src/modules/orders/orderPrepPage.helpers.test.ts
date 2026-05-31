import { describe, expect, it } from "vitest";
import {
  buildMealPrepExportRows,
  buildOrderPrepView,
  resolveOrderDisplayStatusLabel,
  resolveOrderStatusTone
} from "./orderPrepPage.helpers";
import type { OrderPrepItemResponse } from "../../shared/api/types";

const items: OrderPrepItemResponse[] = [
  {
    id: 1,
    customerName: "张先生",
    customerPhone: "13800000001",
    mealSummary: "午餐 / 香煎鸡胸肉套餐",
    quantity: 1,
    userNote: "少饭",
    adminNote: "",
    specialTag: "",
    deliveryAddress: "软件园A座",
    source: "MINIAPP",
    priorityCustomer: false,
    fixedSubscription: false,
    status: "PENDING_DISPATCH",
    displayStatus: "PENDING_DISPATCH",
    displayStatusLabel: "待配送",
    canAssign: true,
    canCancel: true,
    canReceipt: false,
    walletStatusLabel: "已占用"
  },
  {
    id: 2,
    customerName: "李女士",
    customerPhone: "13800000002",
    mealSummary: "晚餐 / 黑椒牛肉套餐",
    quantity: 1,
    userNote: "",
    adminNote: "退款申请中",
    specialTag: "",
    deliveryAddress: "腾讯大厦B座",
    source: "BACKEND",
    priorityCustomer: true,
    fixedSubscription: false,
    status: "PENDING_DISPATCH",
    displayStatus: "REFUND_PROCESSING",
    displayStatusLabel: "退款处理中",
    canAssign: true,
    canCancel: true,
    canReceipt: false,
    walletStatusLabel: "已占用"
  },
  {
    id: 3,
    customerName: "王先生",
    customerPhone: "13800000003",
    mealSummary: "午餐 / 香煎鸡胸肉套餐",
    quantity: 1,
    userNote: "",
    adminNote: "",
    specialTag: "",
    deliveryAddress: "天府三街",
    source: "MINIAPP",
    priorityCustomer: false,
    fixedSubscription: false,
    status: "DELIVERED",
    displayStatus: "DELIVERED",
    displayStatusLabel: "已完成",
    canAssign: false,
    canCancel: false,
    canReceipt: false,
    walletStatusLabel: "已核销"
  }
];

describe("order prep helpers", () => {
  it("filters refund processing orders by display status", () => {
    const result = buildOrderPrepView(
      items,
      {
        keyword: "",
        mealPeriod: "ALL",
        source: "ALL",
        status: "REFUND_PROCESSING"
      },
      1,
      20
    );

    expect(result.totalItems).toBe(1);
    expect(result.pageItems.map((item) => item.id)).toEqual([2]);
  });

  it("uses completed label for delivered orders", () => {
    expect(resolveOrderDisplayStatusLabel("DELIVERED")).toBe("已完成");
    expect(resolveOrderDisplayStatusLabel("REFUND_PROCESSING")).toBe("退款处理中");
  });

  it("maps tones for refund processing and gray finished states", () => {
    expect(resolveOrderStatusTone("REFUND_PROCESSING")).toBe("red");
    expect(resolveOrderStatusTone("REFUNDED")).toBe("gray");
    expect(resolveOrderStatusTone("CANCELLED")).toBe("gray");
    expect(resolveOrderStatusTone("DELIVERED")).toBe("green");
  });

  it("builds excel export rows with chinese status labels", () => {
    expect(buildMealPrepExportRows(items)).toEqual([
      {
        "订单ID": 1,
        "客户姓名": "张先生",
        "联系电话": "13800000001",
        "餐次": "午餐 / 香煎鸡胸肉套餐",
        "数量": 1,
        "配送地址": "软件园A座",
        "订单来源": "小程序",
        "用户备注": "少饭",
        "后台备注": "-",
        "特殊标签": "-",
        "订单状态": "待配送"
      },
      {
        "订单ID": 2,
        "客户姓名": "李女士",
        "联系电话": "13800000002",
        "餐次": "晚餐 / 黑椒牛肉套餐",
        "数量": 1,
        "配送地址": "腾讯大厦B座",
        "订单来源": "后台录入",
        "用户备注": "-",
        "后台备注": "退款申请中",
        "特殊标签": "-",
        "订单状态": "退款处理中"
      },
      {
        "订单ID": 3,
        "客户姓名": "王先生",
        "联系电话": "13800000003",
        "餐次": "午餐 / 香煎鸡胸肉套餐",
        "数量": 1,
        "配送地址": "天府三街",
        "订单来源": "小程序",
        "用户备注": "-",
        "后台备注": "-",
        "特殊标签": "-",
        "订单状态": "已完成"
      }
    ]);
  });
});
