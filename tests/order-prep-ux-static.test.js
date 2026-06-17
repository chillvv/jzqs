const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const repoRoot = path.resolve(__dirname, '..');
const page = fs.readFileSync(path.join(repoRoot, 'admin', 'src', 'modules', 'orders', 'OrderPrepPage.tsx'), 'utf8');
const helpers = fs.readFileSync(path.join(repoRoot, 'admin', 'src', 'modules', 'orders', 'orderPrepPage.helpers.ts'), 'utf8');
const css = fs.readFileSync(path.join(repoRoot, 'admin', 'src', 'index.css'), 'utf8');
const http = fs.readFileSync(path.join(repoRoot, 'admin', 'src', 'shared', 'api', 'http.ts'), 'utf8');
const controller = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'order', 'api', 'OrderPrepController.java'), 'utf8');
const service = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'order', 'service', 'OrderPrepService.java'), 'utf8');

assert.match(page, /type="file"/, '订单回执应改为文件上传而不是手填 URL');
assert.doesNotMatch(page, /placeholder="https:\/\/"|placeholder="请输入图片 URL"/, '订单回执弹窗不应继续出现 URL 输入占位提示');
assert.doesNotMatch(page, /dropdown-menu/, '订单页应去掉三点下拉菜单容器');
assert.doesNotMatch(page, /MoreHorizontal/, '订单页不应继续保留三点按钮图标');
assert.match(page, /查看详情/, '订单列表应保留查看详情入口');
assert.match(page, /编辑订单/, '订单详情应提供编辑入口');
assert.match(page, /售后处理/, '订单详情应提供售后入口');
assert.match(page, /删除订单/, '订单详情应提供删除入口');
assert.doesNotMatch(page, /复制手机号/, '订单详情应移除复制手机号入口');
assert.doesNotMatch(page, /window\.confirm/, '订单页不应继续使用原生 confirm');
assert.doesNotMatch(page, /全部餐次/, '订单页不应继续提供全部餐次视图');
assert.doesNotMatch(page, /mealPeriodFilter !== "ALL"|mealPeriod: OrderPrepMealPeriodFilter = "ALL"|useState<OrderPrepMealPeriodFilter>\("ALL"\)/, '订单页餐次状态不应再保留 ALL');
assert.match(page, /order-meal-toggle/, '订单页应改为午餐晚餐切换按钮');
assert.match(page, /有备注/, '订单筛选应新增有无备注维度');
assert.match(page, /筛选出 \{view\.filteredItems\.length\} 条|筛选出 \{view\.totalItems\} 条/, '订单页应显示筛选结果数量');
assert.match(page, /未找到相关数据/, '订单搜索无结果时应展示更明确的空状态文案');
assert.doesNotMatch(page, /批量核销扣餐/, '订单页应移除批量核销扣餐按钮');
assert.doesNotMatch(page, /确认批量核销/, '订单页应移除批量核销确认弹窗');
assert.doesNotMatch(page, /特殊单 \{specialOrders\.length\}|特殊单明细|fetchSpecialOrders|SpecialOrderItem|specialOrders/, '订单页不应继续保留特殊单状态、接口或弹窗');
assert.match(page, /订单详情 - /, '订单页应提供轻量版订单详情弹窗');
assert.doesNotMatch(page, /isOrderOverviewOpen|openOrderOverview|buildOrderPrepOverview|<LayoutGrid/, '订单页应直接移除订单摘要入口与弹窗逻辑');
assert.doesNotMatch(page, /activeOverviewMetric|order-overview__metric-row|tag-outline/, '订单页不应继续保留订单摘要旧筛选残留');
assert.doesNotMatch(helpers, /buildOrderPrepOverview|OrderPrepOverviewSection|OrderPrepOverviewDetailItem/, '订单摘要 helper 应整体删除');
assert.match(page, /order-detail-view__grid/, '订单详情弹窗应使用新的卡片分区布局');
assert.match(page, /order-meal-panel/, '订单页应把餐次与餐数拆成独立块展示');
assert.match(page, /order-detail-image-grid/, '订单详情应直接展示参照图与回执图');
assert.match(page, /capture="environment"/, '移动端上传回执应支持直接调起后置相机');
assert.doesNotMatch(page, /回执图片 URL|请输入图片 URL/, '订单回执弹窗不应继续要求手填图片 URL');
assert.match(page, /删除回执/, '订单详情应提供删除回执入口，补齐回执增删改查');
assert.match(http, /deleteDeliveryReceipt/, '后台接口层应提供删除回执方法');
assert.match(controller, /@PostMapping\("\/\{orderId\}\/receipt\/delete"\)/, '后台控制器应暴露删除回执接口');
assert.match(service, /deleteDeliveryReceipt\(long orderId\)/, '订单服务接口应定义删除回执能力');
assert.ok(
  page.indexOf('录入代客订单') < page.indexOf('导入固定订餐'),
  '录入代客订单按钮应排在导入固定订餐左侧'
);
assert.match(helpers, /REFUNDED[\s\S]*return "red"|CANCELLED[\s\S]*return "red"/, '退款或取消状态应归入红色异常态');
assert.doesNotMatch(helpers, /return "gray"/, '订单状态标签应收敛为 4 类，不再保留灰色状态');
assert.match(css, /\.order-list-footer/, '订单列表底部应有独立的分页承载区域');

console.log('PASS: 订单运营中心交互与版式优化已落地');
