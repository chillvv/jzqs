const assert = require('node:assert/strict');
const {
  resolveMediaUrl,
  isLocalTempFile,
  stripLocalhostOrigin
} = require('../utils/media-url');

const API = 'https://jzqs.top';

// 1) 相对路径拼接
assert.equal(
  resolveMediaUrl('/uploads/rider-receipts/1.jpg', API),
  'https://jzqs.top/uploads/rider-receipts/1.jpg'
);

// 2) 本地回环地址(/uploads/) 还原成相对路径再拼 baseUrl，修复历史脏数据
assert.equal(
  resolveMediaUrl('http://127.0.0.1:8081/uploads/rider-receipts/1.jpg', API),
  'https://jzqs.top/uploads/rider-receipts/1.jpg'
);
assert.equal(
  resolveMediaUrl('http://localhost/uploads/rider-receipts/2.jpg', API),
  'https://jzqs.top/uploads/rider-receipts/2.jpg'
);
assert.equal(
  resolveMediaUrl('https://127.0.0.1:8081/uploads/rider-receipts/3.jpg', API),
  'https://jzqs.top/uploads/rider-receipts/3.jpg'
);

// 3) 微信开发者工具本地临时文件：降级为空，真机不会一直 500
assert.equal(
  resolveMediaUrl('http://127.0.0.1:44523/__tmp__/I1i0FreQzE.jpg', API),
  ''
);
assert.equal(isLocalTempFile('http://127.0.0.1:44523/__tmp__/x.jpg'), true);
assert.equal(isLocalTempFile('wxfile://tmp_abc.jpg'), true);
assert.equal(isLocalTempFile('https://jzqs.top/uploads/x.jpg'), false);

// 4) 真机临时文件协议(wxfile://tmp_)：原样返回（真机可显示临时文件）
assert.equal(resolveMediaUrl('wxfile://tmp_abc.jpg', API), 'wxfile://tmp_abc.jpg');

// 5) 云存储/外链：原样返回
assert.equal(resolveMediaUrl('cloud://env-xxx/x.jpg', API), 'cloud://env-xxx/x.jpg');
assert.equal(resolveMediaUrl('https://cos.example.com/x.jpg', API), 'https://cos.example.com/x.jpg');

// 6) 空值兜底
assert.equal(resolveMediaUrl('', API), '');
assert.equal(resolveMediaUrl(null, API), '');

// 7) stripLocalhostOrigin 单元测试
assert.equal(stripLocalhostOrigin('http://127.0.0.1:8081/uploads/x.jpg'), '/uploads/x.jpg');
assert.equal(stripLocalhostOrigin('http://localhost:8081/api/mobile/x'), '/api/mobile/x');
assert.equal(stripLocalhostOrigin('https://jzqs.top/uploads/x.jpg'), null);

console.log('media-url tests passed');
