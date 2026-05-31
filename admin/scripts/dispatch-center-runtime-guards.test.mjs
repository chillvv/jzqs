import assert from "node:assert/strict";
import fs from "node:fs";
import path from "node:path";

const routerPath = path.resolve("d:/Code/jzqs/admin/src/app/router.tsx");
const layoutPath = path.resolve("d:/Code/jzqs/admin/src/modules/dispatch/DispatchCenterLayout.tsx");
const homePath = path.resolve("d:/Code/jzqs/admin/src/modules/dispatch/DispatchHomePage.tsx");
const areasPath = path.resolve("d:/Code/jzqs/admin/src/modules/dispatch/DispatchAreasPage.tsx");
const ridersPath = path.resolve("d:/Code/jzqs/admin/src/modules/dispatch/DispatchRidersPage.tsx");
const dashboardPath = path.resolve("d:/Code/jzqs/admin/src/modules/dashboard/DashboardPage.tsx");
const helpersPath = path.resolve("d:/Code/jzqs/admin/src/modules/dispatch/dispatchCenterLayout.helpers.ts");
const httpPath = path.resolve("d:/Code/jzqs/admin/src/shared/api/http.ts");
const typesPath = path.resolve("d:/Code/jzqs/admin/src/shared/api/types.ts");

const routerContent = fs.readFileSync(routerPath, "utf8");
const layoutContent = fs.readFileSync(layoutPath, "utf8");
const homeContent = fs.readFileSync(homePath, "utf8");
const areasContent = fs.readFileSync(areasPath, "utf8");
const ridersContent = fs.readFileSync(ridersPath, "utf8");
const dashboardContent = fs.readFileSync(dashboardPath, "utf8");
const helpersContent = fs.readFileSync(helpersPath, "utf8");
const httpContent = fs.readFileSync(httpPath, "utf8");
const typesContent = fs.readFileSync(typesPath, "utf8");

// Router checks
assert.match(routerContent, /path:\s*"dispatch"/);
assert.match(routerContent, /DispatchCenterLayout/);
assert.match(routerContent, /path:\s*"areas"/);
assert.match(routerContent, /path:\s*"riders"/);

// Layout checks
assert.match(layoutContent, /骑手配送中心/);
assert.match(layoutContent, /Outlet/);
assert.match(layoutContent, /配送工作台/);
assert.match(layoutContent, /区域管理/);
assert.match(layoutContent, /骑手管理/);

// Workbench checks
assert.match(homeContent, /待分配订单/);
assert.match(homeContent, /mealPeriodLabel/);
assert.match(homeContent, /selectedPendingIds/);
assert.match(homeContent, /fetchDispatchPendingItems/);
assert.match(homeContent, /handleSingleAssign/);
assert.match(homeContent, /input type="checkbox"/);
assert.match(homeContent, /successCount/);
assert.match(homeContent, /failureCount/);
assert.match(homeContent, /failures/);
assert.doesNotMatch(homeContent, /智能派单/);
assert.doesNotMatch(homeContent, /suggestedAreaCode/);

// Areas checks
assert.match(areasContent, /新增区域/);
assert.match(areasContent, /区域详情/);
assert.match(areasContent, /更换骑手/);
assert.match(areasContent, /mealPeriodLabel/);
assert.match(areasContent, /移出/);
assert.match(areasContent, /draggable/);
assert.match(areasContent, /deleteBlockedState/);
assert.match(areasContent, /删除受阻|暂不能删除/);
assert.match(areasContent, /reorderAreaOrders/);
assert.match(areasContent, /moveOrderToArea/);
assert.match(areasContent, /GripVertical/);

// API typing checks
assert.match(typesContent, /export type DispatchAreaDeleteBlockedResponse = \{/);
assert.match(typesContent, /export type DispatchAreaBlockingOrder = \{/);
assert.match(typesContent, /export type DispatchPendingItemResponse = \{/);
assert.match(typesContent, /export type DispatchAreaOrderItemResponse = \{/);
assert.match(typesContent, /currentRiderName: string \| null;/);
assert.match(typesContent, /orders: DispatchAreaOrderItemResponse\[];/);
assert.match(typesContent, /dispatchId: number;/);
assert.match(typesContent, /orderId: number;/);
assert.match(httpContent, /export class DispatchAreaDeleteBlockedError extends Error/);
assert.match(httpContent, /error\.response\?\.data\?\.code === "DISPATCH_AREA_HAS_ACTIVE_ORDERS"/);
assert.match(httpContent, /export async function fetchDispatchPendingItems\(mealPeriod:/);
assert.match(httpContent, /export async function fetchDispatchOverview\(mealPeriod:/);
assert.match(httpContent, /export async function reorderAreaOrders/);
assert.match(httpContent, /export async function moveOrderToArea/);
assert.match(httpContent, /export async function batchAssignDispatchPendingOrders/);

// Shared helper labels
assert.match(helpersContent, /return value === "DINNER" \? "晚餐" : "午餐"/);

// Dashboard hooks must not appear after early returns
const dashboardFirstMemoIndex = dashboardContent.indexOf("const heroMetrics = useMemo");
const dashboardErrorReturnIndex = dashboardContent.indexOf("if (error)");
const dashboardLoadingReturnIndex = dashboardContent.indexOf("if (!data)");
assert.ok(dashboardFirstMemoIndex > -1);
assert.ok(dashboardErrorReturnIndex === -1 || dashboardFirstMemoIndex < dashboardErrorReturnIndex);
assert.ok(dashboardLoadingReturnIndex === -1 || dashboardFirstMemoIndex < dashboardLoadingReturnIndex);

// Riders checks
assert.match(ridersContent, /新增骑手/);
assert.match(ridersContent, /手机号/);
assert.match(ridersContent, /888888/);
assert.doesNotMatch(ridersContent, /微信|openid|接管|解绑/);

// Old pages must not exist
assert.throws(() => fs.readFileSync(path.resolve("d:/Code/jzqs/admin/src/modules/dispatch/DispatchCreatePage.tsx")));
assert.throws(() => fs.readFileSync(path.resolve("d:/Code/jzqs/admin/src/modules/dispatch/DispatchReassignmentsPage.tsx")));
assert.throws(() => fs.readFileSync(path.resolve("d:/Code/jzqs/admin/src/modules/dispatch/DispatchCenterPage.tsx")));

console.log("dispatch center runtime guards: ok");
