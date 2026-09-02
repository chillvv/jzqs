# 02 — 导航按客户地址：顾客选点采集坐标 + 骑手精准导航

**What to build:** 骑手点「导航」时精准到达客户地址（而非写死坐标）。

**Blocked by:** None — can start immediately

**Status:** done

**类型:** bug
**优先级:** high

## 方案变更说明（最终）

- 方案一「后端地理编码（腾讯位置服务）」因第三方商业授权问题已移除。
- 方案二「骑手端选点」骑手每次导航都要手动搜索，体验差，老板升级为顾客选点。
- 方案三「web-view + map-nav.html 唤起 scheme」微信小程序 web-view 不允许唤起第三方 App scheme（ERR_UNKNOWN_URL_SCHEME）。
- 方案四「wx.openLocation + address（误以为 address 是终点）」实测 wx.openLocation 导航按 lat/lng、address 只是显示文本，无效。
- **最终方案（老板拍板「顾客选点」）**：顾客保存地址时 `wx.chooseLocation` 选点（微信内置地图，可搜索/拖动微调，免费无需 key）采集坐标随地址落库；骑手端订单带坐标，`wx.openLocation` 精准导航，骑手零操作。

## Acceptance criteria

- [x] 顾客端 `miniapp/pages/addresses/` 加「地图选点」：`wx.chooseLocation` 选点（已填地址自动复制到剪贴板，方便粘贴搜索；编辑时初始定位到原坐标方便微调）
- [x] 顾客保存地址时 `latitude`/`longitude` 随地址提交（后端 `V32` 字段 + `MobileAddress` 读写已就绪）
- [x] 骑手端订单 `RiderQueueItemResponse` 带 `latitude`/`longitude`（`RiderQueueSupport` JOIN customer_addresses 已就绪）
- [x] 骑手端 `map.service.navigate(order)`：订单有坐标 → `wx.openLocation` 精准导航；无坐标（旧地址）→ 复制地址 + toast 提示，不写死坐标
- [x] 骑手端测试：有坐标导航 / 无坐标复制提示 / 无地址

## 关键事实（供以后回顾）

- 微信小程序**没有「地址文本→坐标」原生 API**；`wx.openLocation` 导航按 lat/lng、address 只是显示文本。
- 微信小程序 web-view 不能唤起第三方 App scheme（ERR_UNKNOWN_URL_SCHEME）。
- 高德/腾讯地理编码都需商用授权。
- **`wx.chooseLocation`（顾客或骑手选点）是唯一免费合规的坐标来源**，且是微信内置地图，无需任何 key。

## 部署备注

- 后端 `V32__customer_address_coordinates.sql` 需随迁移部署（给 `customer_addresses` 加 `latitude`/`longitude`）。
- 顾客端 `miniapp`、骑手端 `miniapp-rider` 改动需重新上传发布。