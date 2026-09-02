# 骑手小程序三项优化 · 进度文档

> 2026-09-01 由用户提出三项问题；上一会话被中断且未落地任何代码改动（git status 无 miniapp-rider 改动），
> 本文档接管进度，防止再次丢失。

## 问题描述（用户原话归纳）

1. **送达回执第一次上传失败**：点击「确认送达」（立即送达）后订单仍停留在待送达，需要重新上传一遍。
2. **导航不按客户地址**：点击导航后定位的不是该客户的地址。
3. **订单详情备注不显示**：订单有任何备注时应展示出来，现在没有显示。

## 诊断结论（已核实代码）

### 问题1：第一次上传失败
- `miniapp-rider/utils/request.js` 的 `uploadFile`：
  - **没有重试**（`request` 有 2 次退避重试，上传一次失败即报错）；
  - **没有带服务路由头**（`X-WX-SERVICE` / `X-Vm-Service`，`request` 有；走 TCB 网关路由时会导致上传 404）。
- `Caddyfile`：`jzqs.top /api/*` 的 `read_timeout 30s`，小于小程序端 `wx.uploadFile` 的 60s 超时，
  弱网上传大图（≤5MB）易被网关掐断（同仓库 jango.com.cn 的 `/api/*` 已用 180s，属同类问题的既有修法）。
- 后端 `RiderReceiptStorageSupport.uploadRiderReceipt` 无首次失败因素（目录自动创建、5MB 限制、magic bytes 探测均正常）。

### 问题2：导航不按客户地址
- `miniapp-rider/services/map.service.js` 写死兜底坐标 `FALLBACK_CITY`（长沙市中心 28.2282,112.9388），
  `wx.openLocation` 永远定位到长沙市中心，而不是客户地址。
- 全链路无坐标数据：`customer_addresses` 表无经纬度字段；客户小程序不采集坐标；项目无腾讯地图 key。
- 结论：小程序端 `wx.openLocation` 必须要坐标，地址文本→坐标必须走地理编码（腾讯位置服务 WebService）。
  key 放后端环境变量（`TENCENT_MAP_KEY`，可选），由后端代理转发，避免小程序端配域名白名单和暴露 key。

### 问题3：订单详情备注不显示
- 后端 `/api/rider/orders/{id}` 返回 `note`（用户备注）与 `merchantRemark`（商家备注），投影逻辑（order-note-merge）正确。
- 前端 `pages/order-detail/index.js` 映射正确（`customerNote` / `merchantNote`）。
- 备注 WXML 埋在「配送地址」卡片内部的 `inline-remarks` 小块里，非常不显眼，骑手基本看不到；
  且未配置地图 key 时无任何提示。改为独立「订单备注」卡片置顶展示。

## 修复方案

| # | 修复 | 文件 | 状态 |
|---|------|------|------|
| 1a | uploadFile 补服务路由头 + 瞬时错误退避重试（最多2次） | `miniapp-rider/utils/request.js` | done |
| 1b | jzqs.top `/api/*` read_timeout 30s→120s | `Caddyfile` | done |
| 2a | ~~后端地理编码代理~~（已废弃：第三方商业授权问题，`MapGeocodeSupport` 已删） | — | 废弃 |
| 2b | 顾客选点 + 骑手导航：顾客保存地址时 `wx.chooseLocation` 选点落库，骑手端 `wx.openLocation` 直接用订单坐标精准导航 | `miniapp/pages/addresses/`、`miniapp-rider/services/map.service.js`、后端 V32/MobileAddress | done |
| 3 | 订单详情独立「订单备注」卡片（用户备注/商家嘱咐，醒目样式） | `pages/order-detail/index.wxml/.wxss/.js` | done |
| 4 | 骑手端测试补充与更新 | `miniapp-rider/tests/*` | done |

## 验证记录

- 骑手端全量测试：`node --test tests/*.test.js` → **36/36 通过**（含新增 map-service 3 条、
  uploadFile 重试/401 2 条、备注卡片 1 条，及更新后的服务路由头断言）。
- 后端：`mvn compile` 通过；`MapGeocodeSupportTest`（key 未配置 / 解析成功+缓存 / 失败不缓存）**3/3 通过**。
- **2026-09-01 会话中断后复核**（本会话）：重跑 `node --test "tests/*.test.js"` → **36/36 通过**；
  重跑 `mvn -q test -Dtest=MapGeocodeSupportTest` → 退出码 0（**3/3 通过**）；
  复核 `map.service.js` 分层导航与 `order-detail/index.wxml` 备注卡片代码均在位。三项修复确认落地，无遗留开发事项。
- 实施文件清单：
  - `miniapp-rider/utils/request.js`（uploadFile 重试 + 服务头）
  - `miniapp-rider/services/map.service.js`（分层导航）
  - `miniapp-rider/pages/order-detail/index.wxml|.wxss|.js`（独立备注卡片 + “-” 归一化）
  - `miniapp-rider/tests/request-auth.test.js|map-service.test.js|order-detail-experience.test.js`
  - `backend .../mobile/MapGeocodeSupport.java`、`mobile/api/RiderGeocodeResponse.java`、`RiderController.java`、
    `application.yml`、`MapGeocodeSupportTest.java`
  - `Caddyfile`（/api/* read_timeout 120s）、`.env.example`（TENCENT_MAP_KEY 说明）

## 方案变更（2026-09-01 五次收尾，最终方案）

> 问题2 的方案经历五次变更，最终由老板拍板「顾客选点」：

- **方案一（已废弃）**：后端调腾讯位置服务地理编码，因**第三方商业授权问题已移除**。
- **方案二（已废弃）**：骑手端选点（每次导航手动搜）—— 老板嫌骑手每次多操作，升级为顾客选点。
- **方案三（已废弃）**：骑手端 web-view + `map-nav.html` 中转页唤起地图 App scheme —— 微信小程序 web-view 不允许唤起第三方 App scheme（`ERR_UNKNOWN_URL_SCHEME`）。
- **方案四（已废弃）**：`wx.openLocation` + address（误以为 address 是导航终点）—— 实测 `wx.openLocation` 导航按 lat/lng 坐标、address 只是显示文本，无效。
- **最终方案（老板拍板「顾客选点」）**：顾客保存地址时 `wx.chooseLocation` 选点（微信内置地图，可搜索/拖动微调，免费无需 key）采集坐标随地址落库；骑手端订单带坐标，`wx.openLocation` 精准导航，**骑手零操作**。
- **关键事实（跨项目通用，已记 MEMORY.md）**：
  ① 微信小程序**没有「地址文本→坐标」的原生 API**；`wx.openLocation` 导航按坐标、address 只是显示
  ② 微信小程序 web-view 不能唤起第三方 App scheme（ERR_UNKNOWN_URL_SCHEME）
  ③ 高德/腾讯地理编码都需商用授权
  → **`wx.chooseLocation` 选点是唯一免费合规的坐标来源**（微信内置地图，无需 key）
- **本轮落地**：顾客端 `miniapp/pages/addresses/` 加「地图选点」（复制地址 + 选点 + 微调 + 坐标回填）；骑手端 `map.service.js` 改回「订单坐标 → `wx.openLocation` 精准导航，无坐标才复制提示」；后端 V32/MobileAddress/RiderQueue 已就绪不动
- **验证**：骑手端 `node --test "tests/*.test.js"` → **36/36 通过**；顾客端 **33/33 通过**。

## 待人工事项（部署侧）

1. **后端 `V32__customer_address_coordinates.sql` 迁移需随部署执行**（给 `customer_addresses` 加 `latitude`/`longitude`，顾客选点落库用）。
2. **Caddyfile 改动需在服务器 `docker compose restart caddy`（或 reload）生效**（上轮加的 `read_timeout 30s→120s`，问题 1 弱网上传）。
3. 顾客端 `miniapp`、骑手端 `miniapp-rider` 改动需重新上传发布（或开发者工具预览验证）。
