-- 骑手月薪（元）。
-- 背景：后台「骑手管理」需要按骑手核算配送人工成本——当月送了多少单、单量占比、
-- 以及单均成本（月薪 ÷ 当月已送达单量）。此前系统内没有任何骑手报酬字段
-- （rider_profiles 无价格列，cost_entries 只有全站粒度的成本台账）。
-- 允许为空：未设置月薪的骑手在统计中只显示单量与占比，不计入单均成本。
ALTER TABLE `rider_profiles`
  ADD COLUMN `monthly_salary` decimal(10,2) DEFAULT NULL COMMENT '骑手月薪（元），用于计算单均人工成本' AFTER `remark`;
