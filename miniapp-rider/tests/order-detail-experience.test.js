const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const detailJs = fs.readFileSync(path.join(__dirname, '..', 'pages', 'order-detail', 'index.js'), 'utf8');
const detailWxml = fs.readFileSync(path.join(__dirname, '..', 'pages', 'order-detail', 'index.wxml'), 'utf8');
const detailWxss = fs.readFileSync(path.join(__dirname, '..', 'pages', 'order-detail', 'index.wxss'), 'utf8');

test('order detail supports receipt and exception flow', () => {
  assert.match(detailJs, /enterEditReceiptMode/);
  assert.match(detailJs, /reportException/);
  assert.match(detailJs, /handleSubmitReceipt/);
  assert.match(detailJs, /handleUndoDelivery/);
  assert.match(detailWxml, /送达凭证/);
  assert.match(detailWxml, /确认送达/);
  assert.match(detailWxml, /上报配送异常/);
  assert.match(detailWxml, /编辑回执/);
  assert.match(detailWxml, /撤回送达/);
  assert.match(detailWxml, /上传照片或填写说明，至少提交一项/);
});

test('order detail shows backend merchant remark in merchant note column', () => {
  // 后端骑手接口返回 merchantRemark；页面必须把它映射到「商家嘱咐」栏，
  // 否则商家备注在骑手端永远显示不出来。
  assert.match(
    detailJs,
    /merchantNote:\s*normalizeOptionalText\(order\.merchantNote \|\| order\.adminNote \|\| order\.merchantRemark\)/
  );
  assert.match(detailWxml, /商家嘱咐/);
  // 长备注（逗号拼接多条）必须换行，不能横向溢出
  assert.match(detailWxss, /word-break:\s*break-all/);
});

test('order detail renders notes as a dedicated highlight card', () => {
  // 任何备注（用户/商家）都必须展示：独立「订单备注」卡片 + 警示配色，
  // 不能再埋在配送地址卡片里让骑手看不见。
  assert.match(detailWxml, /订单备注/);
  assert.match(detailWxml, /order-note-card/);
  assert.match(detailWxml, /wx:if="\{\{order\.customerNote \|\| order\.merchantNote\}\}"/);
  assert.match(detailWxml, /用户备注/);
  // 备注值可长按复制（骑手核对/转述用）
  assert.match(detailWxml, /class="order-note-value" user-select/);
  // 警示底色样式存在
  assert.match(detailWxss, /\.order-note-card\s*\{/);
  // 空备注（含后端 "-" 占位）必须归一化为空串，否则会渲染出空卡片
  assert.match(detailJs, /trimmed === '-' \? '' : trimmed/);
});
