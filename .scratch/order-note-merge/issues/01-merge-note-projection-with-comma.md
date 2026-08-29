# 01 — 三端备注投影改为「列 ∪ 快照」去重 + 逗号拼接

**What to build:** 后台订单中心、派单中心、骑手端看到的「用户备注」「商家备注」，
都要把该订单的长期备注和一次性备注全部列出来，用逗号隔开；订单上原有的列值备注也一并带上，
不再因为出现用户备注而把商家备注顶掉。

**Blocked by:** None — can start immediately

**Status:** done

**类型:** bug
**优先级:** high

## Acceptance criteria

- [ ] `OrderQueryRepository.resolveProjectedUserNote` / `resolveProjectedMerchantRemark`
      改为「快照行优先 + 列值兜底追加」，去重后用「，」拼接
- [ ] `RiderQueueSupport.resolveProjectedUserNote` / `resolveProjectedAdminNote` 同样改造
      （用户备注为空时仍返回 `-`，保持骑手端既有语义）
- [ ] `DispatchQueryModule.resolveProjectedUserNote` / `resolveProjectedAdminNote` 同样改造
- [ ] 三处 `NoteAccumulator.toProjection()` 的分隔符由 `" / "` 改为 `"，"`
- [ ] 空串与 `-` 不参与拼接
- [ ] `OrderPrepNoteProjectionIntegrationTest` 断言同步为逗号分隔，并新增
      「列值备注 + 快照备注合并」用例
