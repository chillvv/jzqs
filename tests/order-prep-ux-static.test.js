const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const repoRoot = path.resolve(__dirname, '..');
const page = fs.readFileSync(path.join(repoRoot, 'admin', 'src', 'modules', 'orders', 'OrderPrepPage.tsx'), 'utf8');
const helpers = fs.readFileSync(path.join(repoRoot, 'admin', 'src', 'modules', 'orders', 'orderPrepPage.helpers.ts'), 'utf8');
const css = fs.readFileSync(path.join(repoRoot, 'admin', 'src', 'index.css'), 'utf8');

assert.match(page, /placeholder="请输入图片 URL"/, '回执 URL 输入框应使用明确的空白占位提示');
assert.doesNotMatch(page, /placeholder="https:\/\/"/, '回执 URL 输入框不应继续显示误导性的 https:// 占位文本');
assert.doesNotMatch(page, /dropdown-menu/, '订单页应去掉三点下拉菜单容器');
assert.doesNotMatch(page, /MoreHorizontal/, '订单页不应继续保留三点按钮图标');
assert.match(page, /查看详情/, '订单列表应保留查看详情入口');
assert.match(page, /编辑订单/, '订单详情应提供编辑入口');
assert.match(page, /售后处理/, '订单详情应提供售后入口');
assert.match(page, /删除订单/, '订单详情应提供删除入口');
assert.match(page, /复制手机号/, '订单详情应提供联系客户入口');
assert.doesNotMatch(page, /window\.confirm/, '订单页不应继续使用原生 confirm');
assert.match(page, /有备注/, '订单筛选应新增有无备注维度');
assert.match(page, /筛选出 \{view\.filteredItems\.length\} 条|筛选出 \{view\.totalItems\} 条/, '订单页应显示筛选结果数量');
assert.match(page, /未找到相关数据/, '订单搜索无结果时应展示更明确的空状态文案');
assert.match(page, /确认批量核销/, '批量核销应改为业务确认弹窗');
assert.match(page, /即将核销/, '批量核销确认弹窗应展示即将操作的订单数量');
assert.match(page, /订单详情 - /, '订单页应提供轻量版订单详情弹窗');
assert.match(page, /order-detail-modal__info/, '订单详情弹窗应提供信息区块');
assert.match(page, /order-detail-modal__actions/, '订单详情弹窗应提供右侧操作区块');
assert.match(helpers, /REFUNDED[\s\S]*return "red"|CANCELLED[\s\S]*return "red"/, '退款或取消状态应归入红色异常态');
assert.doesNotMatch(helpers, /return "gray"/, '订单状态标签应收敛为 4 类，不再保留灰色状态');
assert.match(css, /\.order-list-footer/, '订单列表底部应有独立的分页承载区域');

console.log('PASS: 订单运营中心交互与版式优化已落地');
