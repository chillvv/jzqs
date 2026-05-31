import assert from "node:assert/strict";
import {
  buildMealPrepExportRows,
  buildOrderPrepCompactSummary,
  buildOrderPrepDefaultTab,
  buildSubscriptionConfirmationPanelState,
  buildOrderPrepSummary,
  buildOrderPrepView,
  formatOrderNote,
  resolveOrderDisplayStatusLabel,
  resolveOrderSourceLabel,
  resolveOrderStatusTone
} from "../temp-test/modules/orders/orderPrepPage.helpers.js";

const items = [
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
    walletStatusLabel: "待扣餐"
  },
  {
    id: 2,
    customerName: "李女士",
    customerPhone: "13800000002",
    mealSummary: "晚餐 / 黑椒牛肉套餐",
    quantity: 2,
    userNote: "",
    adminNote: "送果蔬汁",
    specialTag: "补偿单",
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
    userNote: "多蔬菜",
    adminNote: "",
    specialTag: "",
    deliveryAddress: "天府三街",
    source: "MINIAPP",
    priorityCustomer: false,
    fixedSubscription: true,
    status: "DELIVERED",
    displayStatus: "DELIVERED",
    displayStatusLabel: "已完成",
    canAssign: false,
    canCancel: false,
    canReceipt: false,
    walletStatusLabel: "已核销"
  }
];

{
  const result = buildOrderPrepView(
    items,
    {
      keyword: "13800000002",
      mealPeriod: "DINNER",
      source: "BACKEND",
      status: "REFUND_PROCESSING"
    },
    1,
    10
  );

  assert.equal(result.totalItems, 1);
  assert.equal(result.totalPages, 1);
  assert.deepEqual(result.pageItems.map((item) => item.id), [2]);
}

{
  const result = buildOrderPrepView(
    items,
    {
      keyword: "",
      mealPeriod: "ALL",
      source: "ALL",
      status: "ALL"
    },
    2,
    2
  );

  assert.equal(result.totalItems, 3);
  assert.equal(result.totalPages, 2);
  assert.deepEqual(result.pageItems.map((item) => item.id), [3]);
}

assert.equal(formatOrderNote(""), "-");
assert.equal(formatOrderNote("  "), "-");
assert.equal(formatOrderNote(null), "-");
assert.equal(formatOrderNote("少饭"), "少饭");

assert.equal(resolveOrderSourceLabel(items[0]), "小程序");
assert.equal(resolveOrderSourceLabel(items[1]), "后台录入");
assert.equal(resolveOrderSourceLabel(items[2]), "固定订餐");
assert.equal(resolveOrderDisplayStatusLabel("DELIVERED"), "已完成");
assert.equal(resolveOrderDisplayStatusLabel("REFUND_PROCESSING"), "退款处理中");
assert.equal(resolveOrderStatusTone("REFUND_PROCESSING"), "red");
assert.equal(resolveOrderStatusTone("REFUNDED"), "gray");
assert.equal(resolveOrderStatusTone("CANCELLED"), "gray");

{
  const summary = buildOrderPrepSummary(items, [
    { id: 11, priority: true },
    { id: 12, priority: false }
  ], [
    { id: 21, priorityCustomer: true },
    { id: 22, priorityCustomer: false }
  ]);

  assert.deepEqual(summary, {
    totalOrders: 3,
    totalMeals: 4,
    lunchCount: 2,
    dinnerCount: 2,
    pendingDispatchCount: 2,
    priorityOrderCount: 1,
    confirmationCount: 2,
    priorityConfirmationCount: 1,
    specialOrderCount: 2,
    prioritySpecialCount: 1
  });
}

{
  const compact = buildOrderPrepCompactSummary({
    totalMeals: 105,
    lunchCount: 62,
    dinnerCount: 43,
    subscriptionCount: 12
  }, {
    confirmationCount: 5,
    totalMeals: 105,
    lunchCount: 62,
    dinnerCount: 43
  });

  assert.deepEqual(compact, [
    {
      label: "当前待出餐",
      value: "105 份",
      tone: "blue"
    },
    {
      label: "餐次结构",
      value: "62 / 43",
      tone: "orange"
    },
    {
      label: "待确认固定订餐",
      value: "5 份",
      tone: "red"
    }
  ]);
}

{
  assert.equal(buildOrderPrepDefaultTab(0), "ORDERS");
  assert.equal(buildOrderPrepDefaultTab(5), "CONFIRMATION");
}

{
  assert.deepEqual(buildSubscriptionConfirmationPanelState(0), {
    visible: false,
    expanded: false
  });
  assert.deepEqual(buildSubscriptionConfirmationPanelState(5), {
    visible: true,
    expanded: true
  });
}

{
  const rows = buildMealPrepExportRows(items);
  assert.deepEqual(rows, [
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
      "数量": 2,
      "配送地址": "腾讯大厦B座",
      "订单来源": "后台录入",
      "用户备注": "-",
      "后台备注": "送果蔬汁",
      "特殊标签": "补偿单",
      "订单状态": "退款处理中"
    },
    {
      "订单ID": 3,
      "客户姓名": "王先生",
      "联系电话": "13800000003",
      "餐次": "午餐 / 香煎鸡胸肉套餐",
      "数量": 1,
      "配送地址": "天府三街",
      "订单来源": "固定订餐",
      "用户备注": "多蔬菜",
      "后台备注": "-",
      "特殊标签": "-",
      "订单状态": "已完成"
    }
  ]);
}

console.log("order-prep helpers test: ok");
