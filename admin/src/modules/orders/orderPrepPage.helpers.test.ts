import { describe, expect, it } from "vitest";
import {
  buildOrderRemarkLabelItems,
  buildRemarkLabelBatchText,
  buildRemarkLabelText,
  buildMealPrepExportRows,
  buildOrderPrepView,
  buildOrderPrepSummary,
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
    merchantRemark: "",
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
  },
  {
    id: 2,
    customerName: "李女士",
    customerPhone: "13800000002",
    mealSummary: "晚餐 / 黑椒牛肉套餐",
    quantity: 1,
    userNote: "",
    merchantRemark: "",
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
  },
  {
    id: 3,
    customerName: "王先生",
    customerPhone: "13800000003",
    mealSummary: "午餐 / 香煎鸡胸肉套餐",
    quantity: 1,
    userNote: "",
    merchantRemark: "",
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
  }
];

describe("order prep helpers", () => {
  it("filters refund processing orders by display status", () => {
    const result = buildOrderPrepView(
      items,
      {
        keyword: "",
        mealPeriod: "DINNER",
        source: "ALL",
        status: "REFUND_PROCESSING",
        remark: "ALL"
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
    expect(resolveOrderStatusTone("REFUNDED")).toBe("red");
    // 已取消是弱化的中性结局，不该和退款一样刺眼
    expect(resolveOrderStatusTone("CANCELLED")).toBe("gray");
    expect(resolveOrderStatusTone("DELIVERED")).toBe("green");
    expect(resolveOrderStatusTone("DISPATCHING")).toBe("blue");
    expect(resolveOrderStatusTone("PENDING_DISPATCH")).toBe("orange");
  });

  it("builds excel export rows with chinese status labels", () => {
    expect(buildMealPrepExportRows(items)).toEqual([
      {
        "客户名称": "张先生",
        "联系电话": "13800000001",
        "用户备注": "少饭",
        "商家备注": "",
        "下单时间": "",
        "配送地址": "软件园A座",
        "餐数": 1,
        "餐次": "午餐",
        "订单来源": "小程序",
        "区域": "",
        "骑手": ""
      },
      {
        "客户名称": "李女士",
        "联系电话": "13800000002",
        "用户备注": "",
        "商家备注": "",
        "下单时间": "",
        "配送地址": "腾讯大厦B座",
        "餐数": 1,
        "餐次": "晚餐",
        "订单来源": "后台录入",
        "区域": "",
        "骑手": ""
      },
      {
        "客户名称": "王先生",
        "联系电话": "13800000003",
        "用户备注": "",
        "商家备注": "",
        "下单时间": "",
        "配送地址": "天府三街",
        "餐数": 1,
        "餐次": "午餐",
        "订单来源": "小程序",
        "区域": "",
        "骑手": ""
      }
    ]);
  });

  it("builds order prep summary with confirmation counters", () => {
    const summary = buildOrderPrepSummary(
      [
        {
          ...items[0],
          merchantRemark: "周卡"
        },
        {
          ...items[1],
          id: 4,
          merchantRemark: "周卡",
          userNote: "少饭"
        }
      ],
      [
        {
          id: 100,
          customerName: "张先生",
          mealPeriod: "LUNCH",
          quantity: 2,
          subscriptionRuleId: 9
        }
      ]
    );

    expect(summary.totalOrders).toBe(2);
    expect(summary.totalMeals).toBe(2);
    expect(summary.remarkedOrderCount).toBe(2);
    expect(summary.confirmationCount).toBe(1);
    expect(summary.lunchConfirmationCount).toBe(2);
    expect(summary.dinnerConfirmationCount).toBe(0);
  });

  it("builds single remark label text with merged notes", () => {
    const result = buildRemarkLabelText({
      orderId: 1,
      customerName: "张先生",
      customerPhone: "13800000001",
      deliveryAddress: "软件园A座",
      remarkLine: "用户备注：少饭；商家备注：辣椒分开"
    });

    expect(result).toBe([
      "张先生",
      "13800000001",
      "软件园A座",
      "用户备注：少饭；商家备注：辣椒分开"
    ].join("\n"));
  });

  it("builds remark label items from orders with either user or merchant remarks", () => {
    const result = buildOrderRemarkLabelItems([
      items[0],
      {
        ...items[1],
        displayStatus: "PENDING_DISPATCH",
        userNote: "",
        merchantRemark: "提前送"
      },
      items[2]
    ]);

    expect(result).toEqual([
      {
        orderId: 1,
        customerName: "张先生",
        customerPhone: "13800000001",
        deliveryAddress: "软件园A座",
        remarkLine: "用户备注：少饭"
      },
      {
        orderId: 2,
        customerName: "李女士",
        customerPhone: "13800000002",
        deliveryAddress: "腾讯大厦B座",
        remarkLine: "商家备注：提前送"
      }
    ]);
  });

  it("builds batch remark label text separated by blank lines", () => {
    const rows = buildRemarkLabelBatchText([
      {
        orderId: 1,
        customerName: "张先生",
        customerPhone: "13800000001",
        deliveryAddress: "软件园A座",
        remarkLine: "用户备注：少饭"
      },
      {
        orderId: 2,
        customerName: "李女士",
        customerPhone: "13800000002",
        deliveryAddress: "腾讯大厦B座",
        remarkLine: "商家备注：提前送"
      }
    ]);

    expect(rows).toBe([
      "张先生",
      "13800000001",
      "软件园A座",
      "用户备注：少饭",
      "",
      "李女士",
      "13800000002",
      "腾讯大厦B座",
      "商家备注：提前送"
    ].join("\n"));
  });
});
