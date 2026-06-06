const assert = require('node:assert/strict');

const { resolveMediaUrl } = require('../miniapp/utils/media-url');
const { resolveVisibleOrders } = require('../miniapp/utils/order-list');
const { resolveMediaUrl: resolveRiderMediaUrl } = require('../miniapp-rider/utils/media-url');

assert.equal(
  resolveMediaUrl('/uploads/rider-receipts/2026-06-06/demo.jpg', 'https://jzqs.top'),
  'https://jzqs.top/uploads/rider-receipts/2026-06-06/demo.jpg',
  '顾客端应把后端返回的相对图片路径补全为可访问地址'
);

assert.equal(
  resolveRiderMediaUrl('/uploads/rider-receipts/2026-06-06/demo.jpg', 'https://jzqs.top'),
  'https://jzqs.top/uploads/rider-receipts/2026-06-06/demo.jpg',
  '骑手端应把后端返回的相对图片路径补全为可访问地址'
);

assert.equal(
  resolveMediaUrl('https://cdn.example.com/demo.jpg', 'https://jzqs.top'),
  'https://cdn.example.com/demo.jpg',
  '绝对地址不应被重复拼接'
);

assert.deepEqual(
  resolveVisibleOrders(
    [{ id: 1, mealName: '午餐' }, { id: 2, mealName: '晚餐' }],
    2
  ).map((item) => item.id),
  [2],
  '从餐次流水进入订单页时应直达关联订单'
);

assert.deepEqual(
  resolveVisibleOrders(
    [{ id: 1, mealName: '午餐' }, { id: 2, mealName: '晚餐' }],
    99
  ),
  [],
  '当目标订单不存在时应返回空列表，交给页面显示明确提示'
);

console.log('PASS: 小程序图片地址已统一解析，餐次流水可直达关联订单');
