# [OPEN] miniapp-auth-image

## 用户症状
- 静默登录失败，进入未登录状态，日志指向 `auth.js:351`
- 选择照片失败，日志指向 `image.js:138`
- 环境：Windows 微信开发者工具，`mp`，基础库 `3.16.0`

## 已知现象
- 历史上图片来源顺序已做过一次兼容修复，但运行时仍失败
- 当前需要真实运行时证据，不能继续靠静态猜测

## 调试目标
1. 拿到静默登录请求的真实请求参数、响应状态、响应体和异常分支
2. 拿到 `chooseMedia` / `chooseImage` 的真实调用参数和失败 `errMsg`
3. 用证据判断两条问题链路是否独立，以及各自根因

## 假设
- H1: 静默登录接口实际返回了非 2xx 或业务失败码，但前端把细节抹平成“请求失败”
- H2: 静默登录请求参数缺失或本地 token / rider 信息状态异常，导致服务端拒绝
- H3: 图片选择在 Windows 开发者工具下对 `camera` 能力或 `chooseMedia` 支持异常，真实 `errMsg` 被统一抹平
- H4: 图片选择失败后回退 `chooseImage` 也失败，且失败原因与权限/平台限制有关
- H5: 两个问题互相独立，图片失败与登录态无关

## 计划
1. 只加日志，不改业务逻辑
2. 启动调试日志服务
3. 在 `auth.js` 和 `image.js` 添加最小插桩
4. 请用户复现一次
5. 用日志证据排除错误假设后再做最小修复

## 当前进度
- 调试服务已启动，环境文件：`.dbg/miniapp-auth-image.env`
- 调试地址：`http://127.0.0.1:7778/event`
- 已在 `miniapp-rider/utils/auth.js` 添加认证链路插桩
- 已在 `miniapp-rider/utils/image.js` 添加选图链路插桩
- 已做静态自检，当前无新增诊断错误

## 证据结论
- 选图真实报错为 `chooseMedia:fail api scope is not declared in the privacy agreement`
- 回退 `chooseImage` 后仍报同一类错误，说明不是 API 顺序问题，而是隐私声明链路缺失
- 静默登录真实请求打到 `https://jzqs.top/api/mobile/rider-auth/wx-login`，返回 `400`
- 仓库内 `miniapp-rider/tests/api-base.test.js` 与 `miniapp/tests/api-base.test.js` 都证明默认 API 基址应为 `http://127.0.0.1:8081`
- 因此静默登录问题根因是当前默认 API 基址漂移到线上域名，导致小程序没有命中仓库这版本地后端

## 已实施修复
- 将两个小程序的默认 `apiBaseUrl` 恢复为 `http://127.0.0.1:8081`
- 在 `miniapp-rider/utils/image.js` 前置调用 `wx.requirePrivacyAuthorize`
- 对“隐私协议未声明 / 用户未授权”场景返回明确错误文案，不再统一抛 `选择图片失败`
- 新增选图隐私报错回归测试，并通过现有选图测试与两个 `api-base` 测试
