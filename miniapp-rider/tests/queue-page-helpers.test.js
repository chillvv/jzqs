const test = require('node:test');
const assert = require('node:assert/strict');
const {
  hasQueueOrderChanged,
  buildReceiptSubmitState,
  getQueueFocusItems
} = require('../utils/queue-page-helpers');

test('hasQueueOrderChanged returns false when batch item order is unchanged', () => {
  const previousItems = [
    { batchItemId: 11 },
    { batchItemId: 12 }
  ];
  const nextItems = [
    { batchItemId: 11 },
    { batchItemId: 12 }
  ];

  assert.equal(hasQueueOrderChanged(previousItems, nextItems), false);
});

test('hasQueueOrderChanged returns true when batch item order changes', () => {
  const previousItems = [
    { batchItemId: 11 },
    { batchItemId: 12 }
  ];
  const nextItems = [
    { batchItemId: 12 },
    { batchItemId: 11 }
  ];

  assert.equal(hasQueueOrderChanged(previousItems, nextItems), true);
});

test('buildReceiptSubmitState asks rider to upload receipt image first', () => {
  const state = buildReceiptSubmitState({
    currentItem: { mealSlotOrderId: 101 },
    receiptTempFilePath: '',
    submittingTaskId: null
  });

  assert.deepEqual(state, {
    disabled: true,
    loading: false,
    buttonText: '先上传送达图',
    helperText: '请先拍照或选择图片，再提交送达。'
  });
});

test('buildReceiptSubmitState shows submitting feedback for current task', () => {
  const state = buildReceiptSubmitState({
    currentItem: { mealSlotOrderId: 101 },
    receiptTempFilePath: 'temp/receipt.jpg',
    submittingTaskId: 101
  });

  assert.deepEqual(state, {
    disabled: true,
    loading: true,
    buttonText: '提交中...',
    helperText: '正在上传送达图片并提交回执，请稍候。'
  });
});

test('buildReceiptSubmitState enables submit after receipt image selected', () => {
  const state = buildReceiptSubmitState({
    currentItem: { mealSlotOrderId: 101 },
    receiptTempFilePath: 'temp/receipt.jpg',
    submittingTaskId: null
  });

  assert.deepEqual(state, {
    disabled: false,
    loading: false,
    buttonText: '确认送达',
    helperText: '已选送达图片，可以提交本单回执。'
  });
});

test('getQueueFocusItems skips deferred items when finding current and next task', () => {
  const result = getQueueFocusItems([
    { batchItemId: 1, currentSequence: 1, itemStatus: 'DEFERRED' },
    { batchItemId: 2, currentSequence: 2, itemStatus: 'PENDING' },
    { batchItemId: 3, currentSequence: 3, itemStatus: 'PENDING' }
  ]);

  assert.equal(result.currentItem.batchItemId, 2);
  assert.equal(result.nextItem.batchItemId, 3);
});

test('getQueueFocusItems ignores delivered items entirely', () => {
  const result = getQueueFocusItems([
    { batchItemId: 1, currentSequence: 1, itemStatus: 'DELIVERED' },
    { batchItemId: 2, currentSequence: 2, itemStatus: 'PENDING' },
    { batchItemId: 3, currentSequence: 3, itemStatus: 'DEFERRED' }
  ]);

  assert.equal(result.currentItem.batchItemId, 2);
  assert.equal(result.nextItem, null);
});

console.log('queue page helpers test: ok');
