# 废弃代码清理清单 (Dead Code Inventory)
> 生成时间：2026-08-13  |  范围：admin/src、admin/scripts、cloudfunctions、backend/src、miniapp、miniapp-rider
> 已排除 node_modules / dist / target / uploads（依赖与构建产物，非一手代码）。
## 一、已清理（已删除）
### 1.1 未被引用的导出函数/类型（跨文件零引用，仅定义处出现）— 共 31 处

**admin/src/modules/aftersales/aftersalePage.helpers.ts**
- L121  `function` resolveAftersaleStatusLabel `resolveAftersaleStatusLabel`

**admin/src/modules/customers/customerAssetPage.helpers.ts**
- L118  `function` extractCustomerNoteGroups `extractCustomerNoteGroups`
- L129  `function` formatCustomerNoteSchedule `formatCustomerNoteSchedule`

**admin/src/modules/dispatch/dispatchCenterLayout.helpers.ts**
- L19  `type` NewRiderFieldErrors `NewRiderFieldErrors`
- L57  `function` normalizeMealPeriodTab `normalizeMealPeriodTab`

**admin/src/shared/api/http.ts**
- L247  `function` updateOrderMerchantRemark `updateOrderMerchantRemark`
- L383  `function` fetchMenuSchedules `fetchMenuSchedules`
- L427  `function` publishMenuWeek `publishMenuWeek`
- L432  `function` fetchDispatchBoard `fetchDispatchBoard`
- L457  `function` fetchDispatchBatches `fetchDispatchBatches`
- L466  `function` fetchDispatchExceptions `fetchDispatchExceptions`
- L488  `function` fetchPendingRiders `fetchPendingRiders`
- L525  `function` fetchDispatchRiderAuthBinding `fetchDispatchRiderAuthBinding`
- L530  `function` takeoverDispatchRiderAuth `takeoverDispatchRiderAuth`
- L540  `function` unbindDispatchRiderAuth `unbindDispatchRiderAuth`
- L570  `function` removeDispatchAreaBinding `removeDispatchAreaBinding`
- L709  `function` getDispatchAreaRouteSuggestion `getDispatchAreaRouteSuggestion`
- L748  `function` saveDispatchAreaRouteSuggestionFeedback `saveDispatchAreaRouteSuggestionFeedback`
- L759  `function` fetchDispatchReassignments `fetchDispatchReassignments`
- L765  `function` reassignDispatchWork `reassignDispatchWork`
- L910  `function` autoAssignDispatch `autoAssignDispatch`
- L915  `function` resolveDispatchException `resolveDispatchException`
- L923  `function` confirmDispatchExceptionArea `confirmDispatchExceptionArea`
- L938  `function` notifyDispatch `notifyDispatch`
- L943  `function` updateOrderingToggle `updateOrderingToggle`
- L950  `function` updateHolidayNotice `updateHolidayNotice`
- L999  `function` pauseOrderingWithNotice `pauseOrderingWithNotice`
- L1059  `function` consumeOrders `consumeOrders`
- L1193  `function` createMenuSchedule `createMenuSchedule`
- L1205  `function` updateMenuSchedule `updateMenuSchedule`
- L1217  `function` disableMenuSchedule `disableMenuSchedule`

### 1.2 一次性数据迁移/临时脚本（全仓库零引用）— 3 个文件
- admin/scripts/rewrite_areas.mjs （已删除）
- admin/scripts/rewrite_dispatch_areas.mjs （已删除）
- admin/scripts/rewrite_dispatch_home.mjs （已删除）

## 二、发现但保留（需人工确认后再处理）
### 2.1 @Deprecated 标记的方法/接口/端点 — 8 处（仍存在调用方，删除会破坏编译/线上接口）
- backend/src/main/java/com/jzqs/app/common/util/JwtUtils.java:116  [@deprecated]  `* @deprecated 使用 parseToken(String token) 替代`
- backend/src/main/java/com/jzqs/app/common/util/JwtUtils.java:118  [@deprecated]  `@Deprecated`
- backend/src/main/java/com/jzqs/app/mobile/MobileAuthService.java:54  [@deprecated]  `* @deprecated 使用 riderRegister 或 riderPhoneLogin 替代`
- backend/src/main/java/com/jzqs/app/mobile/MobileAuthService.java:56  [@deprecated]  `@Deprecated`
- backend/src/main/java/com/jzqs/app/mobile/MobileAuthServiceImpl.java:1056  [@deprecated]  `@Deprecated`
- backend/src/main/java/com/jzqs/app/mobile/api/RiderController.java:91  [@deprecated]  `* @deprecated 使用 register 或 phoneLogin 替代`
- backend/src/main/java/com/jzqs/app/mobile/api/RiderController.java:93  [@deprecated]  `@Deprecated`
- backend/src/main/java/com/jzqs/app/mobile/api/RiderController.java:95  [废弃]  `@Operation(summary = "骑手登录（已废弃）", description = "请使用 /register 或 /phone-login")`

  - `JwtUtils.parseCustomerId` 仍被 `MobileCustomerController.java:314` 调用。
  - `riderMixedLogin` 仍被已废弃但存活的公开端点 `RiderController.login` (`RiderController.java:100`) 调用。

### 2.2 遗留兼容类（Legacy / Compatibility）— 命名含 legacy，疑似旧实现占位，但由框架注册，删除会移除线上端点
- backend/.../dispatch/service/impl/DispatchLegacyWiring.java （被 DispatchServiceImpl 引用，存活）
- backend/.../order/api/LegacySpecialOrderResponse.java （全源码仅定义处出现，疑似废弃 DTO）
- backend/.../order/api/OrderPrepLegacyCompatibilityController.java （全源码仅定义处出现，疑似废弃 Controller，但仍是 Spring 注册端点）

### 2.3 miniapp / miniapp-rider 的 tests/ 目录测试脚本 — 未被任何运行器/CI 引用（无 package.json、无 import、无 CI 步骤）
- 建议确认无自定义测试入口后再清理。保留以免误删可能有用的测试。

## 三、方法论与限制
- 注释掉的代码：仅采用可靠的单行 `//` 注释 + 同行 `/* */` 检测（已剔除误报）。多行 `/* */` 块注释扫描因状态机易在正则/字符串中的 `/*` 处卡死而废弃；本次扫描未发现真实注释死代码（12 处疑似均为说明性注释）。
- 未引用导出：基于标识符全仓库出现次数（排除定义文件）判定；已对 31 处逐一 grep 复核，确认零外部引用后才删除。
- Java：未做全量死代码分析（需 IDE/SpotBugs）；仅标记 @Deprecated 并核查调用方。
- 未检测：文件内局部未使用变量/函数、动态调用（字符串/反射）、以及被新实现替代但未删除的旧逻辑（需结合业务确认）。
