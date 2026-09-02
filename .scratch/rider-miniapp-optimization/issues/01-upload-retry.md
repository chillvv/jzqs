# 01 — 送达回执首次上传失败：补重试与服务路由头

**What to build:** 骑手点「确认送达」提交回执时，图片上传（`POST /api/mobile/rider/uploads/receipt`）遇到
网络抖动/网关超时等瞬时故障时自动退避重试（最多 2 次），且上传请求带上与 `request` 一致的服务路由头，
而不是第一次失败就报错、订单留在待送达。

**Blocked by:** None — can start immediately

**Status:** done

**类型:** bug
**优先级:** high

## Acceptance criteria

- [x] `uploadFile` 请求头包含 `X-WX-SERVICE` / `X-Vm-Service`（来自 `resolveServiceHeaders`）与 `Authorization`
- [x] 网络失败/超时/5xx/「系统繁忙」时自动重试，最多 2 次，退避 500ms * retry
- [x] 401 与明确业务错误（4xx 非 OK code）不重试
- [x] 重试期间不重复弹错误 toast，最终失败才提示
- [x] 现有 `request-auth.test.js` 中「uploadFile 只保留 token 头」的断言更新为新行为，并补重试用例
