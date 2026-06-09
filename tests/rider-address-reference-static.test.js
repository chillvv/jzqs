const fs = require('fs');
const assert = require('assert');

const queueResponse = fs.readFileSync(
  'backend/src/main/java/com/jzqs/app/mobile/api/RiderQueueItemResponse.java',
  'utf8'
);
const riderController = fs.readFileSync(
  'backend/src/main/java/com/jzqs/app/mobile/api/RiderController.java',
  'utf8'
);
const portalService = fs.readFileSync(
  'backend/src/main/java/com/jzqs/app/mobile/MobilePortalServiceImpl.java',
  'utf8'
);
const queueJs = fs.readFileSync('miniapp-rider/pages/queue/index.js', 'utf8');
const queueWxml = fs.readFileSync('miniapp-rider/pages/queue/index.wxml', 'utf8');
const detailJs = fs.readFileSync('miniapp-rider/pages/order-detail/index.js', 'utf8');
const detailWxml = fs.readFileSync('miniapp-rider/pages/order-detail/index.wxml', 'utf8');
const taskService = fs.readFileSync('miniapp-rider/services/task.service.js', 'utf8');
const queueWxss = fs.readFileSync('miniapp-rider/pages/queue/index.wxss', 'utf8');
const migrationPath = 'backend/src/main/resources/db/migration/V37__add_address_reference_images.sql';

assert(queueResponse.includes('long addressId'), 'queue response must expose addressId');
assert(riderController.includes('/address-reference'), 'rider controller must expose address reference endpoints');
assert(fs.existsSync(migrationPath), 'migration must create address_reference_images');

const migration = fs.readFileSync(migrationPath, 'utf8');
assert(migration.includes('address_reference_images'), 'migration must create address_reference_images');
assert(
  portalService.includes('saveAddressReferenceImageIfAbsent'),
  'portal service must auto-seed first receipt image'
);
assert(queueJs.includes('batchReferenceMode'), 'queue page must support batch reference mode');
assert(queueWxml.includes('批量参考图'), 'queue page must render batch reference entry');
assert(
  taskService.includes('saveBatchAddressReferenceImage'),
  'task service must support batch address reference api'
);
assert(
  detailJs.includes('handleViewReferenceImage'),
  'order detail must load reference image on demand'
);
assert(detailWxml.includes('查看参考图'), 'order detail must render view reference image button');
assert(
  detailJs.includes('showReferenceSheet'),
  'order detail must manage reference image inside an internal sheet'
);
assert(
  detailWxml.includes('reference-sheet'),
  'order detail must render an internal reference image sheet'
);
assert.equal(
  detailWxml.includes('reference-inline-actions'),
  false,
  'order detail must not mix reference image actions into outer receipt area'
);
assert.equal(
  detailWxml.includes('送达后请及时上传回执照片，顾客端会同步看到送达结果。'),
  false,
  'order detail should remove verbose proof instructions'
);
assert(
  detailWxml.includes('暂无参考图'),
  'order detail should show an empty reference image hint when unavailable'
);
assert.equal(
  detailWxml.includes('异常与节奏处理'),
  false,
  'order detail should simplify exception section copy'
);
assert.equal(
  queueWxml.includes('只在你点击时才更新参考图'),
  false,
  'queue page should remove redundant batch reference helper copy'
);
assert(
  queueWxml.includes('batch-action-safe-spacer'),
  'queue page must reserve safe space for the floating batch action bar'
);
assert(
  queueWxss.includes('bottom: calc(130rpx + env(safe-area-inset-bottom) + 24rpx)'),
  'queue page must lift floating batch actions above the custom tab bar'
);
