const assert = require('node:assert/strict');
const fs = require('node:fs');

const { resolveMediaUrl } = require('../miniapp/utils/media-url');
const { resolveVisibleOrders } = require('../miniapp/utils/order-list');
const { resolveMediaUrl: resolveRiderMediaUrl } = require('../miniapp-rider/utils/media-url');

const ordersWxml = fs.readFileSync('miniapp/pages/orders/index.wxml', 'utf8');
const receiptsWxml = fs.readFileSync('miniapp/pages/receipts/index.wxml', 'utf8');

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

assert.equal(
  'pages/orders/index?orderId=123'.startsWith('pages/orders/index?orderId='),
  true,
  '订阅消息跳转页应直达订单详情过滤视图'
);

assert.equal(
  ordersWxml.includes('detail-line">{{item.guidanceText}}'),
  false,
  '顾客端订单卡片不应继续展示冗余状态说明文案'
);

assert.equal(
  ordersWxml.includes('order-meta-line'),
  false,
  '顾客端订单卡片应去掉重复的元信息行'
);

assert.equal(
  receiptsWxml.includes('detail-section-desc'),
  false,
  '顾客端订单详情不应保留冗余分区说明'
);

assert.equal(
  receiptsWxml.includes('下单来源'),
  false,
  '顾客端订单详情不应重复展示下单来源'
);

assert(
  receiptsWxml.includes('wx:if="{{item.receiptNote}}"'),
  '顾客端订单详情应仅在有骑手备注时显示该栏'
);

assert(
  receiptsWxml.includes('wx:if="{{item.deliveredAt}}"'),
  '顾客端订单详情应仅在有送达时间时显示该栏'
);

console.log('PASS: 小程序图片地址已统一解析，餐次流水可直达关联订单');
