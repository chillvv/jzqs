const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');

const repoRoot = path.resolve(__dirname, '..');

function read(...segments) {
  return fs.readFileSync(path.join(repoRoot, ...segments), 'utf8');
}

const customerPage = read('admin', 'src', 'modules', 'customers', 'CustomerAssetPage.tsx');
const customerHelpers = read('admin', 'src', 'modules', 'customers', 'customerAssetPage.helpers.ts');
const settingsPage = read('admin', 'src', 'modules', 'settings', 'SystemSettingsPage.tsx');
const settingsHelper = read('admin', 'src', 'modules', 'settings', 'systemSettingsPage.helpers.ts');
const adminTypes = read('admin', 'src', 'shared', 'api', 'types.ts');
const adminHttp = read('admin', 'src', 'shared', 'api', 'http.ts');
const packageReminderRequest = read('backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'settings', 'api', 'PackageReminderSettingsUpdateRequest.java');
const settingsResponse = read('backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'settings', 'api', 'OperationSettingsResponse.java');
const settingsService = read('backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'settings', 'service', 'SettingsService.java');
const settingsServiceImpl = read('backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'settings', 'service', 'impl', 'SettingsServiceImpl.java');
const scheduler = read('backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'mobile', 'DeliverySubscribeScheduler.java');
const mobilePortalService = read('backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'mobile', 'MobilePortalServiceImpl.java');
const mobileHomeResponse = read('backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'mobile', 'api', 'MobileHomeResponse.java');
const homeJs = read('miniapp', 'pages', 'home', 'index.js');
const homeWxml = read('miniapp', 'pages', 'home', 'index.wxml');
const maintenancePage = read('admin', 'src', 'modules', 'maintenance', 'MaintenancePage.tsx');
const maintenanceHelpers = read('admin', 'src', 'modules', 'maintenance', 'maintenancePage.helpers.ts');
const maintenanceServiceImpl = read('backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'maintenance', 'DataCleanupService.java');
const deliveryServiceImpl = read('backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'delivery', 'service', 'impl', 'DeliveryServiceImpl.java');
const dispatchServiceImpl = read('backend', 'src', 'main', 'java', 'com', 'jzqs', 'app', 'dispatch', 'service', 'impl', 'DispatchServiceImpl.java');
const cleanupDeadLogsMigration = read('backend', 'src', 'main', 'resources', 'db', 'migration', 'V51__drop_dead_notification_logs.sql');
const cleanupDeadTablesMigration = read('backend', 'src', 'main', 'resources', 'db', 'migration', 'V52__drop_dead_geocode_and_track_tables.sql');

assert.match(
  customerHelpers,
  /export type CustomerRemainingValidityState = "ALL" \| "VALID" \| "EXPIRING_SOON" \| "EXPIRED" \| "NO_EXPIRY";/,
  '客户中心 helper 应新增剩余天数状态筛选类型'
);

assert.match(
  customerPage,
  /remainingValidityStateFilter/,
  '客户中心应新增剩余天数状态筛选状态'
);

assert.match(
  customerPage,
  /全部天数状态|有效中|即将到期|已过期|未设置/,
  '客户中心应提供剩余天数状态筛选选项'
);

assert.match(
  customerPage,
  /已过期|今日到期/,
  '客户中心剩余天数展示不应继续对过期客户显示“剩余 N 天”'
);

assert.match(
  customerPage,
  /shouldRenderPackageAlert\(item\.packageAlertLabel, item\.packageAlertCode\)/,
  '客户中心列表中已过期客户不应再重复显示提醒文案'
);

assert.match(
  customerPage,
  /shouldRenderPackageAlert\([\s\S]*detail\?\.wallet\?\.packageAlertCode \|\| activeItem\.packageAlertCode[\s\S]*\)/,
  '客户详情中已过期客户不应再重复显示当前提醒'
);

assert.match(
  settingsPage,
  /mealReminderPopupEnabled|deliverySubscribeEnabled|deliverySubscribeLunchTime|deliverySubscribeDinnerTime/,
  '系统设置页面应支持餐提醒弹窗以及午餐晚餐两套订阅时间配置'
);

assert.match(
  adminTypes,
  /mealReminderPopupEnabled: boolean;[\s\S]*deliverySubscribeEnabled: boolean;[\s\S]*deliverySubscribeLunchTime: string;[\s\S]*deliverySubscribeDinnerTime: string;/,
  '管理端设置类型应补齐午餐晚餐两套订阅时间字段'
);

assert.match(
  adminHttp,
  /mealReminderPopupEnabled[\s\S]*deliverySubscribeEnabled[\s\S]*deliverySubscribeLunchTime[\s\S]*deliverySubscribeDinnerTime/,
  '管理端设置请求应把午餐晚餐两套订阅时间一起提交给后端'
);

assert.match(
  packageReminderRequest,
  /boolean mealReminderPopupEnabled[\s\S]*boolean deliverySubscribeEnabled[\s\S]*String deliverySubscribeLunchTime[\s\S]*String deliverySubscribeDinnerTime/,
  '后端设置更新请求应支持午餐晚餐两套订阅时间字段'
);

assert.match(
  settingsResponse,
  /boolean mealReminderPopupEnabled[\s\S]*boolean deliverySubscribeEnabled[\s\S]*String deliverySubscribeLunchTime[\s\S]*String deliverySubscribeDinnerTime/,
  '后端设置响应应返回午餐晚餐两套订阅时间字段'
);

assert.match(
  settingsService,
  /updatePackageReminderSettings\([\s\S]*int packageExpiryReminderDays,[\s\S]*int packageLowBalanceThreshold,[\s\S]*boolean mealReminderPopupEnabled,[\s\S]*boolean deliverySubscribeEnabled,[\s\S]*String deliverySubscribeLunchTime,[\s\S]*String deliverySubscribeDinnerTime[\s\S]*\)/,
  'SettingsService 应扩展为午餐晚餐两套订阅时间更新能力'
);

assert.match(
  settingsServiceImpl,
  /meal_reminder_popup_enabled[\s\S]*delivery_subscribe_enabled[\s\S]*delivery_subscribe_lunch_time[\s\S]*delivery_subscribe_dinner_time/,
  'SettingsServiceImpl 应读写午餐晚餐两套订阅时间配置列'
);

assert.doesNotMatch(
  scheduler,
  /0 30 11 \* \* \?|0 0 17 \* \* \?/,
  '订阅通知调度不应继续硬编码午餐晚餐两个固定时间点'
);

assert.match(
  scheduler,
  /0 \* \* \* \* \?/,
  '订阅通知调度应改为按分钟巡检并读取后台配置时间'
);

assert.match(
  scheduler,
  /matchesConfiguredTriggerTime\(settings\.deliverySubscribeLunchTime\(\), now\)[\s\S]*sendScheduledDeliverySubscribeMessages\("LUNCH"\)/,
  '调度器应在午餐配置时间命中时才发送午餐订阅通知'
);

assert.match(
  scheduler,
  /matchesConfiguredTriggerTime\(settings\.deliverySubscribeDinnerTime\(\), now\)[\s\S]*sendScheduledDeliverySubscribeMessages\("DINNER"\)/,
  '调度器应在晚餐配置时间命中时才发送晚餐订阅通知'
);

assert.match(
  mobileHomeResponse,
  /boolean mealReminderPopupEnabled[\s\S]*String mealReminderTitle[\s\S]*String mealReminderMessage[\s\S]*String mealReminderKey/,
  '移动端首页响应应包含餐提醒弹窗配置与文案'
);

assert.match(
  homeJs,
  /showMealReminderPopup|mealReminderChecked|mealReminderKey/,
  '小程序首页应维护餐提醒弹窗与不再提示状态'
);

assert.match(
  homeWxml,
  /不再提示|meal-reminder-popup/,
  '小程序首页应提供餐提醒弹窗和不再提示勾选入口'
);

assert.match(
  settingsHelper,
  /顾客(?:每次)?上线后，会按当前餐包状态弹出一次用餐提醒，并可勾选本次状态不再提示。|顾客上线后会按当前餐包状态弹出一次用餐提醒，并可勾选本次状态不再提示。/,
  '系统设置提示应说明顾客上线后的单次餐包提醒语义'
);

assert.match(
  settingsHelper,
  /命中设置时间后才会发送取餐提醒订阅消息|按设置时间扫描发送取餐提醒订阅消息/,
  '系统设置提示应明确午晚餐订阅时间是真实发送触发时间'
);

assert.match(
  homeWxml,
  /餐包提醒|这个状态今天不再提醒|我知道了/,
  '首页弹窗文案应升级为更柔和统一的话术'
);

assert.match(
  mobilePortalService,
  /建议尽快联系商家续卡|建议优先安排近期餐食|建议提前联系商家续卡或补餐/,
  '后端应统一输出服务型提醒文案'
);

assert.match(
  maintenancePage,
  /订单历史|配送批次|回执记录|通知日志|区域调整记录|地址绑定|钱包流水/,
  '系统维护页应展示可配置的数据清理模块卡片'
);

assert.match(
  maintenancePage,
  /保留时长|自动清理|立即执行|最近结果/,
  '系统维护页应展示每个模块的可调规则和执行入口'
);

assert.match(
  maintenancePage,
  /规则设置/,
  '系统维护页应把清理规则收纳到规则设置入口中'
);

assert.match(
  maintenancePage,
  /订单历史|回执记录|钱包流水/,
  '系统维护页应只保留商家能理解的三类业务清理项'
);

assert.doesNotMatch(
  maintenancePage,
  /配送批次|区域调整记录|地址绑定|通知日志/,
  '系统维护页不应继续直接向商家暴露内部结构类清理项'
);

assert.match(
  maintenancePage,
  /保存后将用于后续自动清理和立即执行全部|修改后需先保存规则，再按新规则执行/,
  '系统维护页应明确保存规则会影响后续自动清理和整页手动执行'
);

assert.match(
  maintenancePage,
  /当前有未保存修改|规则已同步到服务器/,
  '系统维护页应明确提示规则是否已保存'
);

assert.match(
  maintenanceServiceImpl,
  /maintenance_cleanup_settings|retention_value|auto_enabled/,
  '后端清理服务应改为读取可配置清理规则'
);

assert.match(
  maintenanceServiceImpl,
  /BUSINESS_MODULE_KEYS[\s\S]*ORDER_HISTORY[\s\S]*RECEIPT_RECORD[\s\S]*WALLET_TRANSACTION/,
  '后端维护服务应只把订单历史、回执记录、钱包流水作为商家可见清理项'
);

assert.match(
  maintenanceServiceImpl,
  /manualCleanup\(\)[\s\S]*fetchBusinessConfiguredRules\(false\)/,
  '手动执行全部应只按商家可见的业务清理规则执行'
);

assert.match(
  maintenanceServiceImpl,
  /scheduledCleanup\(\)[\s\S]*fetchBusinessConfiguredRules\(true\)/,
  '定时清理也应只按商家可见且开启自动清理的业务规则执行'
);

assert.doesNotMatch(
  maintenanceServiceImpl,
  /NOTIFICATION_LOG|cleanupOldNotifications/,
  '后端维护清理服务不应继续保留通知日志这个已废弃模块'
);

assert.match(
  maintenanceServiceImpl,
  /MAINTENANCE_LOG_RETENTION_DAYS|pruneMaintenanceJobLogs/,
  '维护执行日志应具备真实保留周期与自动清理逻辑'
);

assert.doesNotMatch(
  deliveryServiceImpl,
  /INSERT INTO notification_logs/,
  '送达流程不应继续把未真实发送的通知写入 notification_logs'
);

assert.match(
  deliveryServiceImpl,
  /"notificationStatus", "SKIPPED"/,
  '送达流程未接入真实通知发送时应明确返回 SKIPPED'
);

assert.doesNotMatch(
  dispatchServiceImpl,
  /INSERT INTO notification_logs/,
  '配送确认流程不应继续把未真实发送的通知写入 notification_logs'
);

assert.match(
  dispatchServiceImpl,
  /"notificationStatus", "SKIPPED"/,
  '配送确认流程未接入真实通知发送时应明确返回 SKIPPED'
);

assert.match(
  cleanupDeadLogsMigration,
  /DROP TABLE IF EXISTS notification_logs/i,
  '已停写的 notification_logs 应通过新迁移从数据库中清理掉'
);

assert.match(
  cleanupDeadLogsMigration,
  /DELETE FROM maintenance_cleanup_settings[\s\S]*NOTIFICATION_LOG/i,
  '维护清理配置中也应移除通知日志这个无价值留痕模块'
);

assert.match(
  cleanupDeadTablesMigration,
  /DROP TABLE IF EXISTS address_geocode_cache/i,
  '未接入实际读写的地址地理编码缓存表应通过新迁移真实删库'
);

assert.match(
  cleanupDeadTablesMigration,
  /DROP TABLE IF EXISTS rider_delivery_tracks/i,
  '未接入实际业务的骑手轨迹表应通过新迁移真实删库'
);

assert.doesNotMatch(
  maintenanceHelpers,
  /NOTIFICATION_LOG_CLEANUP|通知日志/,
  '维护页日志文案映射中不应继续保留通知日志模块残留'
);

assert.match(
  dispatchServiceImpl,
  /DISPATCH_REASSIGNMENT_RETENTION_DAYS\s*=\s*30/,
  '区域调整留痕应限制为最近30天窗口，避免无限堆积'
);

assert.match(
  dispatchServiceImpl,
  /recentReassignments\(String serveDate\)[\s\S]*created_at >= \?/,
  '区域调整记录查询应只读取保留窗口内的数据'
);

assert.match(
  dispatchServiceImpl,
  /pruneOldDispatchReassignments\(\)[\s\S]*DELETE FROM dispatch_reassignments[\s\S]*created_at < \?/,
  '区域调整记录应在服务内具备真实清理旧数据的逻辑'
);

assert.match(
  dispatchServiceImpl,
  /assignOrder\(long orderId, String riderName, String areaCode\)[\s\S]*markDispatchExceptionResolved\(orderId, riderName\)/,
  '异常重新派单后应真正标记对应配送异常为已解决'
);

assert.match(
  dispatchServiceImpl,
  /confirmExceptionArea\(long mealSlotOrderId, String areaCode, String riderName, boolean rememberAddress, String updatedBy\)[\s\S]*markDispatchExceptionResolved\(mealSlotOrderId, riderName\)/,
  '异常确认区域后也应真正标记对应配送异常为已解决'
);

assert.match(
  dispatchServiceImpl,
  /DISPATCH_EXCEPTION_RETENTION_DAYS\s*=\s*30/,
  '已解决的配送异常应限制为最近30天留痕'
);

assert.match(
  dispatchServiceImpl,
  /markDispatchExceptionResolved\([\s\S]*UPDATE delivery_exceptions[\s\S]*resolved = TRUE[\s\S]*resolved_at = CURRENT_TIMESTAMP[\s\S]*resolved_by = \?/,
  '配送异常解决时应写入 resolved、resolved_at、resolved_by'
);

assert.match(
  dispatchServiceImpl,
  /pruneResolvedDispatchExceptions\(\)[\s\S]*DELETE FROM delivery_exceptions[\s\S]*resolved = TRUE[\s\S]*COALESCE\(resolved_at, created_at\) < \?/,
  '已解决的旧配送异常应具备真实清理逻辑'
);

assert.match(
  mobilePortalService,
  /DELIVERY_SUBSCRIPTION_RETENTION_DAYS\s*=\s*30/,
  '配送订阅状态记录应限制为最近30天窗口'
);

assert.match(
  mobilePortalService,
  /pruneOldDeliverySubscriptions\(\)[\s\S]*DELETE FROM customer_delivery_subscriptions[\s\S]*COALESCE\(sent_at, authorized_at\) < \?/,
  '配送订阅状态表应具备真实清理旧记录的逻辑'
);

console.log('PASS: 客户提醒筛选与系统设置提醒链路已收口');
