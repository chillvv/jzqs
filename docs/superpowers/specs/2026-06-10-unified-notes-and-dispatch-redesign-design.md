# Unified Notes and Dispatch Redesign Design

**Date:** 2026-06-10

## Goal

重构当前“客户备注 / 标签 / 默认备注 / 固定订餐备注 / 订单备注 / 骑手回单 / 后台调度”这套已经混杂的业务模型，统一为一套简单、可执行、可扩展的规则：

- 不再保留“标签”这个独立概念，全部统一为“备注”。
- 只保留两类备注：`用户备注`、`商家备注`。
- 商家备注支持三种生效范围：长期、限时、单餐。
- 客户中心只维护客户级规则；订单在生成时把规则固化成快照；调度页和骑手端只消费订单快照。
- 后台调度页第一眼能看到“骑手当前送到哪一单”，并能直接查看地址参照图与本次送达图。

## Scope

- 重构备注业务模型、数据库结构、接口结构和后台页面组织方式。
- 删除当前备注相关的混乱旧结构，不做兼容式保留。
- 允许清空旧备注相关数据，按新模型重新开始。
- 同步整理骑手回单图片、地址参照图和后台调度进度展示。
- 输出明确的实施顺序，保证可以分阶段落地。

## Primary Rules

### Core Note Types

- 系统中只存在两类备注：
  - `用户备注`
  - `商家备注`
- “标签”不再是独立业务对象。
- “周卡体验”“重点关注”“新客重点服务”“送果蔬汁”都统一视为备注。

### Scope Rules

- 用户备注支持两种形态：
  - 客户长期用户备注
  - 本次下单用户备注
- 商家备注支持三种生效范围：
  - 长期商家备注
  - 限时商家备注
  - 单餐商家备注

### Ownership Rules

- 客户中心只维护客户级备注规则：
  - 长期用户备注
  - 长期商家备注
  - 限时商家备注
- 订单页 / 调度页只维护订单级备注：
  - 本次下单用户备注
  - 单餐商家备注
- 固定订餐规则只保留固定订餐自身的默认用户备注，不再承载“周卡”“重点关注”“特殊标签”这类商家运营逻辑。

### Snapshot Rules

- 客户级规则是订单生成的输入源。
- 订单生成时，系统计算当时生效的备注，并写入订单备注快照。
- 订单生成之后，客户档案的后续修改不得反向影响历史订单。
- 调度页、骑手端、后台复核只读取订单快照，不再动态回查客户中心来拼装备注。

### Time-boxed Merchant Note Rules

- “周卡体验”不是标签，而是一条带开始时间和结束时间的限时商家备注。
- 限时商家备注只影响有效期内新生成的订单。
- 过期后不再参与新订单生成。
- 已经生成的历史订单仍保留当时的快照内容。

### Delivery Progress Rules

- 队列按派送顺序排序。
- 第一个未完成订单视为“当前送到的单”。
- 后台需要同时展示：
  - 当前送到第几单
  - 下一单是谁
  - 已完成数量
  - 剩余数量
  - 异常数量

## UX Design

### Customer Center

客户中心只负责客户级规则，不再承担订单级临时处理。

#### Customer Detail Layout

- `用户备注`
  - 长期用户备注
- `商家备注`
  - 长期商家备注
  - 限时商家备注
- `历史摘要`
  - 最近订单备注摘要，仅供查看，不在这里直接维护订单级备注

#### Merchant Note Quick Actions

- 加长期商家备注
- 加限时商家备注
- 编辑已有备注
- 失效 / 删除备注

#### Time-boxed Merchant Note Input

新增或编辑限时商家备注时，只需要输入：

- 备注内容
- 开始时间
- 结束时间

典型模板：

- 周卡体验
- 本周重点关注
- 本周加量
- 新客体验期

### Order / Dispatch Page

订单页和调度页只处理当前订单结果，不再承担客户级规则配置。

#### Order Card / Order Detail

每个订单固定显示：

- 客户基础信息
- 当前生效用户备注
- 当前生效商家备注
- 本单商家备注
- 骑手状态
- 地址参照图
- 本次送达图
- 回单说明

#### Order-level Quick Actions

- 添加本餐商家备注
- 查看参照图
- 查看本次送达图
- 标记异常
- 设为参照图

典型单餐商家备注模板：

- 本餐送果蔬汁
- 本餐加汤
- 电话联系后送
- 优先配送

### Dispatch Board

后台调度页拆成三栏结构：

- 左侧：骑手进度栏
- 中间：当前骑手队列
- 右侧：订单详情与图片区

#### Rider Progress Card

每个骑手卡片至少显示：

- 已送 `X / Y`
- 当前第几单
- 下一单客户与地址
- 异常数

#### Queue Column

按顺序显示：

- 已送达
- 当前配送中
- 待送

当前单需要明显高亮。

#### Detail Column

右侧详情展示：

- 用户备注
- 商家备注
- 参照图
- 本次送达图
- 回单说明
- 异常状态
- 快捷操作按钮

### Rider Side

骑手端只关注配送过程本身：

- 看自己的队列
- 看地址参照图
- 上传本次送达图
- 填写本次送达说明

## Data Model

本次允许直接清空旧备注相关数据，因此优先采用新模型，不做兼容式保留。

### New Table: customer_notes

客户级备注表，承载长期用户备注、长期商家备注、限时商家备注。

#### Fields

- `id`
- `customer_id`
- `note_type`
  - `USER`
  - `MERCHANT`
- `scope_type`
  - `LONG_TERM`
  - `TIME_BOXED`
- `content`
- `start_at`
- `end_at`
- `is_active`
- `display_order`
- `created_by`
- `updated_by`
- `created_at`
- `updated_at`

#### Constraints

- `customer_id` 建索引。
- `customer_id + note_type + scope_type + is_active` 建联合索引。
- `TIME_BOXED` 备注必须同时有 `start_at` 和 `end_at`。
- `LONG_TERM` 备注默认 `start_at` / `end_at` 为空。

### New Table: order_notes

订单级备注表，承载本次下单用户备注和单餐商家备注，也用于保存订单生成时的备注快照。

#### Fields

- `id`
- `meal_slot_order_id`
- `customer_id`
- `note_type`
  - `USER`
  - `MERCHANT`
- `source_type`
  - `CUSTOMER_PROFILE`
  - `CUSTOMER_ORDER_INPUT`
  - `MERCHANT_PROFILE`
  - `MERCHANT_TIME_BOXED`
  - `MERCHANT_ORDER_ONCE`
  - `SUBSCRIPTION_DEFAULT`
- `scope_type`
  - `SNAPSHOT`
  - `ORDER_ONCE`
- `content`
- `effective_status`
  - `ACTIVE`
  - `CANCELLED`
  - `EXPIRED`
- `created_by`
- `created_at`

#### Constraints

- `meal_slot_order_id` 建索引。
- `meal_slot_order_id + note_type` 建联合索引。
- 每条订单快照一经生成不允许覆盖式更新，只允许追加或取消单餐备注。

### Existing Table Adjustment: subscription_rules

固定订餐规则只保留固定订餐自身字段。

#### Keep

- 客户
- 时间范围
- 午晚餐开关
- 默认地址
- 默认用户备注
- 是否暂停

#### Remove From Business Meaning

- 不再承载：
  - 周卡
  - 重点关注
  - 商家限时运营逻辑
  - 特殊标签

### Existing Table Adjustment: meal_slot_orders

订单表不再承担备注语义主源，只保留必要的显示缓存或兼容字段，真实来源收口到 `order_notes`。

#### Target Direction

- `order_notes` 成为备注主表。
- `meal_slot_orders` 若保留 `note` / `user_note` / `admin_note`，也只作为过渡期兼容字段，不再作为最终规则源。

## Database Cleanup

由于本次允许按新业务重构且旧数据可以清空，建议直接废弃或删除以下旧结构：

- `customer_order_preferences`
- `customer_order_tags`

并同步废弃以下旧语义：

- `customers.remark` 不再承担“最近一次下单备注”的职责
- “标签”相关旧命名全部移除
- 订单里的 `special_tag` 语义废弃

## Backend Design

### New Responsibilities

- 统一管理客户级备注规则
- 统一管理订单级备注与订单快照
- 在下单和固定订餐生成订单时计算当前生效备注
- 为后台调度页提供骑手当前进度、当前单、下一单、异常数量
- 为后台详情页提供参照图与本次送达图双图区数据

### Note Aggregation Pipeline

订单生成时，按以下顺序读取并写入快照：

#### User Notes

- 客户长期用户备注
- 本次下单用户备注
- 固定订餐默认用户备注（如来源于固定订餐）

#### Merchant Notes

- 当前生效的长期商家备注
- 当前生效的限时商家备注
- 当前订单新增的单餐商家备注

### Snapshot Write Rule

- 每次生成订单时，把当时有效备注写入 `order_notes`
- 历史订单不做回溯重算
- 后台调度和骑手端只查 `order_notes`

### Delivery Progress Aggregation

新增或改造后台调度接口，按骑手返回：

- `completedCount`
- `currentOrderId`
- `currentSequenceNumber`
- `nextOrderId`
- `pendingCount`
- `exceptionCount`

当前单判定规则：

- 队列中第一个未完成订单即为当前单

### Image Responsibilities

- 地址参照图属于地址级资产
- 本次送达图属于订单级资产
- 后台详情接口同时返回：
  - 地址参照图
  - 当前订单最新送达图
  - 是否可一键设为参照图

## API Design

### Customer-level Notes

#### Query Customer Notes

- `GET /api/admin/customers/{customerId}/notes`
- 返回：
  - 长期用户备注
  - 长期商家备注
  - 限时商家备注

#### Save Customer Note

- `POST /api/admin/customers/{customerId}/notes`
- 参数：
  - `noteType`
  - `scopeType`
  - `content`
  - `startAt`
  - `endAt`

#### Update / Disable Customer Note

- `PUT /api/admin/customers/{customerId}/notes/{noteId}`
- `POST /api/admin/customers/{customerId}/notes/{noteId}/disable`

### Order-level Notes

#### Query Order Notes

- `GET /api/admin/orders/{orderId}/notes`

#### Add One-time Merchant Note

- `POST /api/admin/orders/{orderId}/notes`
- 参数：
  - `noteType = MERCHANT`
  - `scopeType = ORDER_ONCE`
  - `content`

### Snapshot Generation Hooks

- 小程序下单
- 后台代客录单
- 固定订餐自动生成订单

这些入口统一调用备注聚合器并写入 `order_notes`

### Dispatch Board

#### Rider Progress Overview

- `GET /api/admin/dispatch/riders/progress`
- 返回：
  - 每个骑手的完成数、当前单、下一单、异常数

#### Rider Queue Detail

- `GET /api/admin/dispatch/riders/{riderName}/queue-detail`
- 返回：
  - 队列顺序
  - 当前状态
  - 当前单备注快照
  - 地址参照图
  - 本次送达图

## Frontend Design

### Admin Customer Center

- 删除“默认标签”“默认商家备注”“默认用户备注”这种混合表述
- 统一改为：
  - 长期用户备注
  - 长期商家备注
  - 限时商家备注
- 限时商家备注支持按时间排序和当前生效状态显示

### Admin Dispatch Board

- 顶部或左侧展示骑手总览卡片
- 中间展示当前骑手队列
- 右侧展示当前单详情
- 详情区显示：
  - 用户备注列表
  - 商家备注列表
  - 地址参照图
  - 本次送达图
  - 回单说明
  - 追加本餐商家备注入口

### Rider Side

- 队列页继续显示当前配送队列
- 订单详情页继续支持上传回单
- 订单详情页明确展示地址参照图和本次送达图状态

## Data Flow

### Customer Note Maintenance

1. 运营在客户中心新增长期或限时备注。
2. 系统写入 `customer_notes`。
3. 这些备注只影响未来新生成的订单。

### Manual / Miniapp / Subscription Order Creation

1. 系统接收下单请求。
2. 读取客户长期用户备注。
3. 读取当前生效的长期商家备注和限时商家备注。
4. 读取本次下单用户备注。
5. 若为固定订餐，读取固定订餐默认用户备注。
6. 合并后写入 `order_notes` 快照。
7. 订单详情、调度页、骑手端统一使用该快照。

### One-time Merchant Action

1. 运营在订单详情点击“加本餐商家备注”。
2. 系统写入 `order_notes`，`scope_type = ORDER_ONCE`。
3. 调度页和骑手端即时显示。

### Dispatch Progress

1. 系统按骑手和顺序读取当日队列。
2. 找到第一个未完成订单。
3. 计算骑手当前单、下一单、完成数和异常数。
4. 后台调度页据此渲染进度卡片。

## Error Handling

- 限时商家备注若开始时间晚于结束时间，直接拒绝保存。
- 单餐商家备注只能挂在存在的订单上。
- 订单快照写入失败时，整笔订单创建失败，不能出现“有订单无备注快照”的半成功状态。
- 骑手回单图或参照图读取失败时，不影响队列主流程，但要明确提示。
- 调度进度接口若某骑手无当前单，返回空而不是报错。

## Performance Strategy

- `customer_notes` 与 `order_notes` 都使用针对查询路径的联合索引。
- 调度页概览接口按骑手聚合，不在总览列表中一次性返回全部图片。
- 订单详情按需返回图片与备注详情。
- 订单快照生成时一次写入，避免后续调度页重复做动态规则计算。

## Testing

- 后端：
  - 长期用户备注创建与查询
  - 限时商家备注时间校验
  - 周卡作为限时商家备注自动生效与自动失效
  - 下单时备注快照生成
  - 固定订餐生成订单时带入默认用户备注
  - 单餐商家备注追加成功
  - 历史订单不受客户档案变更影响
  - 骑手当前单计算正确
- 前端：
  - 客户中心备注录入与展示
  - 调度页骑手当前单高亮
  - 订单详情备注分区展示
  - 参照图 / 本次送达图同时展示

## Non-Goals

- 不做旧备注相关数据迁移。
- 不保留“标签”业务概念。
- 不为历史订单回填新规则。
- 不在本次重构里扩展复杂审批流程。

## Implementation Phases

### Phase 1: Data Model Reset

- 新建 `customer_notes`
- 新建 `order_notes`
- 废弃 `customer_order_preferences`
- 废弃 `customer_order_tags`
- 清理旧备注相关数据

### Phase 2: Backend Refactor

- 实现客户级备注接口
- 实现订单级备注接口
- 实现订单快照生成器
- 改造小程序下单、后台录单、固定订餐生成流程
- 改造调度进度聚合接口

### Phase 3: Admin UI Refactor

- 重构客户中心备注区域
- 重构调度页三栏布局
- 新增单餐商家备注入口
- 接入图片双图区

### Phase 4: Rider and Verification

- 骑手端接入新的订单备注快照读取
- 完成联调
- 清理旧字段的界面入口和文案

