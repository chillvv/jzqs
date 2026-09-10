# 04 — 骑手端错误文案区分「网络不通」与「网关/服务端错误」

**What to build:** 骑手端把 `wx.request` 的网络层失败、请求超时、HTTP 5xx（含网关 502/504）与业务错误分开提示，
不再把「服务端/网关问题」统一说成「无法连接服务器，请检查网络或联系管理员」，避免误导骑手与运维。

**Blocked by:** None

**Status:** todo

**类型:** bug
**优先级:** medium

## 背景与证据

截图（2026-09-09 骑手登录页）：输入手机号后提示「无法连接服务器，请检查网络或联系管理员」。

代码路径：

- `miniapp-rider/pages/login/index.js:92-97`：只要 `error.message` 含 **「无法连接」或「请求失败」**，
  就统一显示成「无法连接服务器，请检查网络或联系管理员」
- `miniapp-rider/utils/request.js:95`：`const errorMsg = body.message || '请求失败'`
  → **任何非 2xx 且没有 `message` 字段的响应都会命中「请求失败」**，其中就包括 Caddy 返回的 502

实测证据（同时段）：

- 后端 11:26 有 `上传文件处理失败: Failed to parse multipart servlet request`
- 网关访问日志记录到 `POST /api/mobile/rider/uploads/receipt` **耗时 92.7 秒后返回 502**
- `Caddyfile` 中 `/api/*` 的 `response_header_timeout 60s`，超时即 502

也就是说该提示**并不能证明骑手手机没网**，它同样代表「请求到达了网关但没拿到正常业务响应」，
这次排查因此被误导了一段时间。

## Acceptance criteria

- [ ] `fail`（网络层失败）→「网络异常，请检查网络后重试」
- [ ] `timeout` →「请求超时，请稍后重试」
- [ ] HTTP 5xx（尤其 502/504）→「服务暂时不可用，请稍后重试」，且与网络问题区分开
- [ ] 401 仍走登录过期流程；业务错误仍展示后端 `message`
- [ ] 补充/更新 `miniapp-rider` 相关单测（沿用现有 `request-auth.test.js` 的写法）
- [ ] 文案改动需重新上传小程序版本后生效，上线前在开发者工具验证 502 场景

## 备注

同一处理也应覆盖 `uploadFile`（回执上传）分支，该分支目前文案同样不区分 5xx 与网络失败。
