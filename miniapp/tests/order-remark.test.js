const assert = require('node:assert/strict');
const {
  DEFAULT_PRESET_NOTES,
  normalizeHistoryRemarkSuggestions,
  addHistoryRemark,
  composeRemark,
  resolveInitialRemark
} = require('../utils/order-remark');

assert.deepEqual(
  DEFAULT_PRESET_NOTES,
  []
);

assert.deepEqual(
  normalizeHistoryRemarkSuggestions([' 少饭 ', '少饭，多蔬菜', '', '少饭，多蔬菜', '不要洋葱']),
  ['少饭', '少饭，多蔬菜', '不要洋葱']
);

const storageState = {};
const storage = {
  getStorageSync(key) {
    return storageState[key];
  },
  setStorageSync(key, value) {
    storageState[key] = value;
  }
};

addHistoryRemark('少饭', { customerId: 101, storage });
addHistoryRemark('不要辣', { customerId: 101, storage });
addHistoryRemark('少饭', { customerId: 101, storage });
addHistoryRemark('多蔬菜', { customerId: 101, storage });
addHistoryRemark('加汤', { customerId: 101, storage });
addHistoryRemark('多饭', { customerId: 101, storage });
addHistoryRemark('不要香菜', { customerId: 101, storage });
addHistoryRemark('晚点送', { customerId: 202, storage });

assert.deepEqual(
  normalizeHistoryRemarkSuggestions(undefined, { customerId: 101, storage }),
  ['不要香菜', '多饭', '加汤', '多蔬菜', '少饭']
);

assert.deepEqual(
  normalizeHistoryRemarkSuggestions(undefined, { customerId: 202, storage }),
  ['晚点送']
);

assert.equal(
  composeRemark([], '不要洋葱'),
  '不要洋葱'
);

assert.equal(
  composeRemark([], '  '),
  ''
);

assert.equal(
  resolveInitialRemark('少饭', '多蔬菜'),
  '少饭'
);

assert.equal(
  resolveInitialRemark('', '少饭，多蔬菜'),
  '少饭，多蔬菜'
);

assert.equal(
  resolveInitialRemark('', ''),
  ''
);

console.log('order-remark tests passed');
