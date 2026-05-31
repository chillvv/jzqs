const assert = require('node:assert/strict');
const {
  getReceiptDisplayState,
  mapReceiptRecord
} = require('../utils/receipt-display');

assert.deepEqual(
  getReceiptDisplayState({
    status: 'DELIVERED',
    mealPeriod: 'LUNCH',
    receiptUrl: '/uploads/rider-receipts/1.jpg',
    receiptVisible: false
  }),
  {
    canShowReceiptImage: false,
    receiptHint: '配送已完成，图片将于 11:30 后可见'
  }
);

assert.deepEqual(
  getReceiptDisplayState({
    status: 'DELIVERED',
    mealPeriod: 'DINNER',
    receiptUrl: '/uploads/rider-receipts/2.jpg',
    receiptVisible: true
  }),
  {
    canShowReceiptImage: true,
    receiptHint: ''
  }
);

{
  const item = mapReceiptRecord({
    id: 1,
    status: 'DELIVERED',
    serveDate: '2026-05-15',
    mealPeriod: 'LUNCH',
    mealName: '轻食套餐',
    deliveryAddress: '高新区科技园A座8层',
    receiptUrl: '/uploads/rider-receipts/1.jpg',
    receiptVisible: false,
    receiptNote: '已放前台',
    deliveredAt: '2026-05-15 10:30:00',
    source: 'BACKEND'
  });

  assert.equal(item.canShowReceiptImage, false);
  assert.match(item.receiptHint, /11:30/);
  assert.equal(item.sourceText, '后台代下单');
}

console.log('receipt-display tests passed');
