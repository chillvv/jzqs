-- 添加唯一约束：同一个订单只能有一个分配记录
-- 防止并发或重复调用分配接口导致派单重复
ALTER TABLE dispatch_assignments ADD CONSTRAINT uk_dispatch_assignments_mso UNIQUE (meal_slot_order_id);
