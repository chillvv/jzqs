const assert = require('node:assert/strict');
const {
  getReceiptDisplayState,
  mapReceiptRecord
} = require('../utils/receipt-display');

function getLocalISODate(now = new Date()) {
  const tzOffset = now.getTimezoneOffset() * 60000;
  return new Date(now.getTime() - tzOffset).toISOString().split('T')[0];
}

function shiftDate(days, base = new Date()) {
  const tzOffset = base.getTimezoneOffset() * 60000;
  return new Date(base.getTime() - tzOffset + days * 86400000).toISOString().split('T')[0];
}

// 用一个固定的"今天"作为测试锚点，所有测试用例都用相同日期，避免跨日导致不稳定。
const ANCHOR = new Date('2026-08-09T08:00:00');
const TODAY = getLocalISODate(ANCHOR);
const YESTERDAY = shiftDate(-1, ANCHOR);
const TOMORROW = shiftDate(1, ANCHOR);

// 用 mock now 控制的固定时间点。
const BEFORE_LUNCH_RELEASE = new Date('2026-08-09T10:30:00'); // 11:30 之前
const AT_LUNCH_RELEASE = new Date('2026-08-09T11:30:00'); // 11:30 整点
const AFTER_LUNCH_RELEASE = new Date('2026-08-09T11:44:00'); // 11:30 之后（复现截图 bug）
const BEFORE_DINNER_RELEASE = new Date('2026-08-09T15:00:00'); // 17:00 之前
const AFTER_DINNER_RELEASE = new Date('2026-08-09T18:00:00'); // 17:00 之后

// 1) 当天 + 骑手已上传回执 + 释放时间还没到：提示"图片将于 HH:MM 后可见"
assert.deepEqual(
  getReceiptDisplayState(
    {
      status: 'DELIVERED',
      mealPeriod: 'LUNCH',
      serveDate: TODAY,
      receiptUrl: '/uploads/rider-receipts/1.jpg',
      receiptVisible: false
    },
    BEFORE_LUNCH_RELEASE
  ),
  {
    canShowReceiptImage: false,
    receiptHint: '配送已完成，图片将于 11:30 后可见'
  }
);

// 2) 当天 + 晚餐 + 释放时间未到：使用 17:00
assert.deepEqual(
  getReceiptDisplayState(
    {
      status: 'DELIVERED',
      mealPeriod: 'DINNER',
      serveDate: TODAY,
      receiptUrl: '/uploads/rider-receipts/2.jpg',
      receiptVisible: false
    },
    BEFORE_DINNER_RELEASE
  ),
  {
    canShowReceiptImage: false,
    receiptHint: '配送已完成，图片将于 17:00 后可见'
  }
);

// 3) 当天 + 释放时间已过 + receiptVisible=true：直接展示图片
assert.deepEqual(
  getReceiptDisplayState(
    {
      status: 'DELIVERED',
      mealPeriod: 'LUNCH',
      serveDate: TODAY,
      receiptUrl: '/uploads/rider-receipts/3.jpg',
      receiptVisible: true
    },
    AFTER_LUNCH_RELEASE
  ),
  {
    canShowReceiptImage: true,
    receiptHint: ''
  }
);

// 4) 送达日已过 + 曾经上传过回执（被凌晨清理）：提示"仅当天可查看"
assert.deepEqual(
  getReceiptDisplayState(
    {
      status: 'DELIVERED',
      mealPeriod: 'LUNCH',
      serveDate: YESTERDAY,
      receiptUrl: '',
      receiptNote: '',
      receiptEverExisted: true,
      receiptVisible: false
    },
    AFTER_LUNCH_RELEASE
  ),
  {
    canShowReceiptImage: false,
    receiptHint: '回执照片仅送餐当天可查看'
  }
);

// 5) 送达日已过 + 从未上传过回执：维持默认空状态（不暴露任何"被清理"暗示）
assert.deepEqual(
  getReceiptDisplayState(
    {
      status: 'DELIVERED',
      mealPeriod: 'LUNCH',
      serveDate: YESTERDAY,
      receiptUrl: '',
      receiptNote: '',
      receiptEverExisted: false,
      receiptVisible: false
    },
    AFTER_LUNCH_RELEASE
  ),
  {
    canShowReceiptImage: false,
    receiptHint: ''
  }
);

// 6) 当天 + 骑手尚未提交回执：默认空状态（让 WXML fallback 提示"当前暂无可查看回执图片"）
assert.deepEqual(
  getReceiptDisplayState(
    {
      status: 'DELIVERED',
      mealPeriod: 'LUNCH',
      serveDate: TODAY,
      receiptUrl: '',
      receiptNote: '',
      receiptEverExisted: false,
      receiptVisible: false
    },
    BEFORE_LUNCH_RELEASE
  ),
  {
    canShowReceiptImage: false,
    receiptHint: ''
  }
);

// 7) 【关键回归】当天 + 释放时间已过 + 后端 receiptVisible 仍为 false：
//    兜底强制展示图片，不再误提示"将于 XX 后可见"。
//    这正是用户截图中的 bug 场景：11:44 看到 8.9 午餐的回执仍提示"11:30 后可见"。
assert.deepEqual(
  getReceiptDisplayState(
    {
      status: 'DELIVERED',
      mealPeriod: 'LUNCH',
      serveDate: TODAY,
      receiptUrl: '/uploads/rider-receipts/after-release.jpg',
      receiptNote: '骑手确认送达',
      receiptEverExisted: true,
      receiptVisible: false // 后端标记仍为 false，应由前端兜底
    },
    AFTER_LUNCH_RELEASE
  ),
  {
    canShowReceiptImage: true,
    receiptHint: ''
  }
);

// 8) 释放时间整点（11:30:00）也视为已过：与"之后"行为一致。
assert.deepEqual(
  getReceiptDisplayState(
    {
      status: 'DELIVERED',
      mealPeriod: 'LUNCH',
      serveDate: TODAY,
      receiptUrl: '/uploads/rider-receipts/at-release.jpg',
      receiptVisible: false
    },
    AT_LUNCH_RELEASE
  ),
  {
    canShowReceiptImage: true,
    receiptHint: ''
  }
);

// 9) 晚餐释放时间已过 + receiptVisible=false 兜底可见。
assert.deepEqual(
  getReceiptDisplayState(
    {
      status: 'DELIVERED',
      mealPeriod: 'DINNER',
      serveDate: TODAY,
      receiptUrl: '/uploads/rider-receipts/dinner-after.jpg',
      receiptVisible: false
    },
    AFTER_DINNER_RELEASE
  ),
  {
    canShowReceiptImage: true,
    receiptHint: ''
  }
);

// 10) mapReceiptRecord 集成测试：未到释放时间时 hint 应包含 11:30。
{
  const item = mapReceiptRecord(
    {
      id: 1,
      status: 'DELIVERED',
      serveDate: TODAY,
      mealPeriod: 'LUNCH',
      mealName: '轻食套餐',
      deliveryAddress: '高新区科技园A座8层',
      receiptUrl: '/uploads/rider-receipts/1.jpg',
      receiptVisible: false,
      receiptNote: '已放前台',
      deliveredAt: `${TODAY} 10:30:00`,
      source: 'BACKEND'
    },
    '',
    BEFORE_LUNCH_RELEASE
  );

  assert.equal(item.canShowReceiptImage, false);
  assert.match(item.receiptHint, /11:30/);
  assert.equal(item.sourceText, '后台代下单');
}

// 11) mapReceiptRecord 集成测试：过了释放时间即使用 receiptVisible=false 也走可见分支。
{
  const item = mapReceiptRecord(
    {
      id: 2,
      status: 'DELIVERED',
      serveDate: TODAY,
      mealPeriod: 'LUNCH',
      mealName: '轻食套餐',
      deliveryAddress: '吉首',
      receiptUrl: '/uploads/rider-receipts/900001.jpg',
      receiptVisible: false, // 后端未及时更新
      receiptNote: '骑手确认送达',
      deliveredAt: `${TODAY} 10:30:00`,
      source: 'SELF'
    },
    'https://example.com',
    AFTER_LUNCH_RELEASE
  );

  assert.equal(item.canShowReceiptImage, true);
  assert.equal(item.receiptHint, '');
  assert.equal(item.sourceText, '自主下单');
}

// 12) 【本地临时文件降级】开发者工具 __tmp__ 临时路径解析为空，
//     且基于解析后 URL 的显示状态应为「不可显示」，避免 src 为空的矛盾渲染。
{
  const item = mapReceiptRecord({
    id: 3,
    status: 'DELIVERED',
    serveDate: TODAY,
    mealPeriod: 'LUNCH',
    mealName: '轻食套餐',
    deliveryAddress: '吉首',
    receiptUrl: 'http://127.0.0.1:44523/__tmp__/I1i0FreQzE.jpg',
    receiptVisible: false,
    receiptNote: '骑手确认送达',
    deliveredAt: `${TODAY} 10:30:00`,
    source: 'SELF'
  }, 'https://jzqs.top', AFTER_LUNCH_RELEASE);

  assert.equal(item.receiptUrl, '');
  assert.equal(item.canShowReceiptImage, false);
}

console.log('receipt-display tests passed');
