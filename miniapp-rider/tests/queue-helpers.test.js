const test = require('node:test');
const assert = require('node:assert/strict');
const { moveQueueItem, canMoveQueueItem } = require('../utils/queue-helpers');

test('moveQueueItem moves current item to target index', () => {
  const list = [
    { id: 1, currentSequence: 1 },
    { id: 2, currentSequence: 2 },
    { id: 3, currentSequence: 3 }
  ];
  const result = moveQueueItem(list, 0, 2);
  assert.deepEqual(result.map((item) => item.id), [2, 3, 1]);
  assert.deepEqual(result.map((item) => item.currentSequence), [1, 2, 3]);
});

test('canMoveQueueItem blocks moving pending item ahead of delivered item', () => {
  const list = [
    { id: 1, itemStatus: 'DELIVERED', currentSequence: 1 },
    { id: 2, itemStatus: 'CURRENT', currentSequence: 2 },
    { id: 3, itemStatus: 'PENDING', currentSequence: 3 }
  ];

  assert.equal(canMoveQueueItem(list, 1, -1), false);
});

test('moveQueueItem keeps delivered items locked in place', () => {
  const list = [
    { id: 1, itemStatus: 'DELIVERED', currentSequence: 1 },
    { id: 2, itemStatus: 'CURRENT', currentSequence: 2 },
    { id: 3, itemStatus: 'PENDING', currentSequence: 3 }
  ];

  const result = moveQueueItem(list, 1, 0);
  assert.deepEqual(result.map((item) => item.id), [1, 2, 3]);
  assert.deepEqual(result.map((item) => item.currentSequence), [1, 2, 3]);
});

console.log('queue helpers test: ok');
