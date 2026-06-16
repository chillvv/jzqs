const fs = require('node:fs');
const path = require('node:path');
const assert = require('node:assert/strict');

const repoRoot = path.resolve(__dirname, '..');

const adminHttp = fs.readFileSync(path.join(repoRoot, 'admin', 'src', 'shared', 'api', 'http.ts'), 'utf8');
const adminTypes = fs.readFileSync(path.join(repoRoot, 'admin', 'src', 'shared', 'api', 'types.ts'), 'utf8');
const dashboardHelpers = fs.readFileSync(path.join(repoRoot, 'admin', 'src', 'modules', 'dashboard', 'dashboardPage.helpers.ts'), 'utf8');
const analysisPage = fs.readFileSync(path.join(repoRoot, 'admin', 'src', 'modules', 'analysis', 'OperationsAnalysisPage.tsx'), 'utf8');
const analysisHelpers = fs.readFileSync(path.join(repoRoot, 'admin', 'src', 'modules', 'analysis', 'operationsAnalysisPage.helpers.ts'), 'utf8');
const orderPrepController = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'order', 'api', 'OrderPrepController.java'), 'utf8');
const orderPrepService = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'order', 'service', 'OrderPrepService.java'), 'utf8');
const orderPrepServiceImpl = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'order', 'service', 'impl', 'OrderPrepServiceImpl.java'), 'utf8');
const orderPrepStats = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'order', 'api', 'OrderPrepStatsResponse.java'), 'utf8');
const dashboardResponse = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'dashboard', 'api', 'DashboardOverviewResponse.java'), 'utf8');
const dashboardService = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'dashboard', 'service', 'impl', 'DashboardServiceImpl.java'), 'utf8');
const analysisResponse = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'analysis', 'api', 'AnalysisOverviewResponse.java'), 'utf8');
const analysisService = fs.readFileSync(path.join(repoRoot, 'backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'analysis', 'service', 'impl', 'OperationsAnalysisServiceImpl.java'), 'utf8');

assert.doesNotMatch(adminHttp, /fetchSpecialOrders|\/special-orders/, '前端 API 不应继续暴露特殊单接口');
assert.doesNotMatch(adminTypes, /SpecialOrderItem|specialOrderCount|specialOrdersToday/, '前端类型不应继续保留特殊单字段');
assert.doesNotMatch(dashboardHelpers, /specialOrdersToday|特殊备注订单/, 'Dashboard 不应继续展示特殊单统计');
assert.doesNotMatch(analysisPage, /specialOrders|特殊单/, '经营分析页不应继续展示特殊单');
assert.doesNotMatch(analysisHelpers, /specialOrderRate|specialOrders/, '经营分析 helper 不应继续保留特殊单占比');
assert.doesNotMatch(orderPrepController, /special-orders|SpecialOrderItem|specialOrders\(/, '订单准备控制器不应继续暴露特殊单接口');
assert.doesNotMatch(orderPrepService, /SpecialOrderItem|specialOrders\(/, '订单准备服务接口不应继续保留特殊单方法');
assert.doesNotMatch(orderPrepServiceImpl, /SpecialOrderItem|specialOrderCount|specialOrders\(|toSpecialOrderItem/, '订单准备服务实现不应继续保留特殊单逻辑');
assert.doesNotMatch(orderPrepStats, /specialOrderCount/, '订单统计响应不应继续保留特殊单字段');
assert.doesNotMatch(dashboardResponse, /specialOrdersToday/, 'Dashboard 响应不应继续保留特殊单字段');
assert.doesNotMatch(dashboardService, /specialOrdersToday|含备注需跟进/, 'Dashboard 服务不应继续构造特殊单统计');
assert.doesNotMatch(analysisResponse, /specialOrders/, '经营分析响应不应继续保留特殊单字段');
assert.doesNotMatch(analysisService, /specialOrders/, '经营分析服务不应继续构造特殊单统计');

console.log('PASS: 特殊单链路已从前后端与看板移除');
