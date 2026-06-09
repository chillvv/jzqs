# [OPEN] WeChat Phone No Response Debug

## Session

- Session ID: `wechat-phone-noresponse`
- Started: 2026-06-07
- Status: OPEN

## Symptom

- 用户反馈：无论是否勾选协议，点击绿色微信登录后只提示“已同意协议，请再次点击微信登录”或表现为没有任何反应。
- 期望行为：首次点击绿色按钮弹出协议弹窗；同意后再次点击绿色按钮，应稳定触发微信手机号授权，并在页面或日志中能观察到明确后续行为。

## Hypotheses

1. 协议同意状态在点击勾选时被提前写成已同意，导致页面状态和真实交互不一致。
2. `open-type="getPhoneNumber"` 按钮点击后，微信组件事件根本没有进入 `bindgetphonenumber` 回调。
3. `bindtap="prepareWechatLogin"` 在按钮点击阶段被提前中断，导致 `getPhoneNumber` 组件链路没有继续执行。
4. 微信运行环境没有给当前按钮触发手机号能力，导致事件失败但前端缺少足够的运行时证据。
5. 页面确实收到了事件，但在 `startWechatLoginFlow` / `doLoginWithCode` 之前被忙碌态或条件分支吞掉。

## Evidence Plan

- 在顾客端与骑手端登录页记录：点击绿色按钮、打开协议弹窗、点击同意并继续、点击勾选、`prepareWechatLogin` 进入、`getPhoneNumber` 回调进入、`startWechatLoginFlow` 分支结果、`doLoginWithCode` 入口。
- 让用户按固定步骤复现一次，再根据运行时日志判断具体卡点。

## Runtime Evidence

- 调试服务已启动，`http://127.0.0.1:7777/health` 返回 `status=ok`。
- 顾客端复现日志显示：
  - `prepareWechatLogin:entry` 与 `prepareWechatLogin:success` 都出现，说明绿色按钮点击后前置隐私授权流程已进入并成功完成。
  - `getPhoneNumber` 回调确实进入，但 `errMsg` 为 `getPhoneNumber:fail no permission`，同时 `hasCode=false`、`codeLength=0`。
  - `startWechatLoginFlow` 也进入了，但因为微信没有返回 `code`，后续不会进入 `doLoginWithCode`。
- 协议弹窗相关日志显示：
  - 复现过程中存在 `agreementAccepted=true`、`agreementSheetChecked=true` 的历史状态，证明确实有旧持久化值影响当前页面展示。
  - 用户重新切换勾选后，日志显示曾出现 `nextChecked=false` 再 `nextChecked=true`，说明 UI 上“已同意”异常与旧存储污染有关，不是单纯点击没生效。
- 用户补充控制台日志：`[wxapplib] backgroundfetch privacy fail {"errno":4,"errMsg":"private_getBackgroundFetchData:fail ... internal error"}`。
  - 该日志来自微信运行时的 `backgroundfetch` 隐私接口，不是当前登录页代码主动调用的 `getPhoneNumber` 链路。
  - 现有前端埋点已证明手机号按钮事件与 `getPhoneNumber` 回调均已进入，因此这条 `backgroundfetch` 错误不是当前手机号登录失效的主阻塞点，更像微信运行时噪音或其他能力的内部错误。

## Hypothesis Status

1. 协议同意状态在点击勾选时被提前写成已同意，导致页面状态和真实交互不一致。 -> Confirmed
2. `open-type="getPhoneNumber"` 按钮点击后，微信组件事件根本没有进入 `bindgetphonenumber` 回调。 -> Rejected
3. `bindtap="prepareWechatLogin"` 在按钮点击阶段被提前中断，导致 `getPhoneNumber` 组件链路没有继续执行。 -> Rejected
4. 微信运行环境没有给当前按钮触发手机号能力，导致事件失败但前端缺少足够的运行时证据。 -> Confirmed
5. 页面确实收到了事件，但在 `startWechatLoginFlow` / `doLoginWithCode` 之前被忙碌态或条件分支吞掉。 -> Rejected

## Fix Applied

- 协议持久化键升级到 `v2`，避免沿用旧错误勾选状态。
- 勾选协议时只更新弹窗内的临时勾选态，不再提前写死正式同意状态。
- `getPhoneNumber:fail no permission` 现在直接展示准确提示，不再误导用户“再次点击微信登录”。
