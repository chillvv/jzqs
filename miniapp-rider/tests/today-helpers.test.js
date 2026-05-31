const test = require('node:test');
const assert = require('node:assert/strict');
const {
  batchStatusLabel,
  buildTodayCards
} = require('../utils/today-helpers');

test('batchStatusLabel maps backend status to Chinese labels', () => {
  assert.equal(batchStatusLabel('READY'), '待开始');
  assert.equal(batchStatusLabel('IN_PROGRESS'), '配送中');
  assert.equal(batchStatusLabel('PARTIALLY_DONE'), '部分完成');
  assert.equal(batchStatusLabel('FINISHED'), '已完成');
  assert.equal(batchStatusLabel('ABNORMAL'), '异常');
  assert.equal(batchStatusLabel('UNKNOWN'), 'UNKNOWN');
  assert.equal(batchStatusLabel(), '待开始');
});

test('buildTodayCards normalizes meal label and default customer names', () => {
  const cards = buildTodayCards({
    lunchBatch: {
      batchId: 1,
      mealPeriod: 'LUNCH',
      batchStatus: 'IN_PROGRESS',
      currentCustomerName: '',
      nextCustomerName: null
    },
    dinnerBatch: {
      batchId: 2,
      mealPeriod: 'DINNER',
      batchStatus: 'READY',
      currentCustomerName: '张三',
      nextCustomerName: '李四'
    }
  });

  assert.equal(cards.length, 2);
  assert.deepEqual(cards[0], {
    batchId: 1,
    mealPeriod: 'LUNCH',
    batchStatus: 'IN_PROGRESS',
    currentCustomerName: '待开始',
    nextCustomerName: '无',
    mealLabel: '午餐',
    batchStatusLabel: '配送中'
  });
  assert.deepEqual(cards[1], {
    batchId: 2,
    mealPeriod: 'DINNER',
    batchStatus: 'READY',
    currentCustomerName: '张三',
    nextCustomerName: '李四',
    mealLabel: '晚餐',
    batchStatusLabel: '待开始'
  });
});

console.log('today helpers test: ok');
