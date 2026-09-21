-- 骑手登录会话版本号。
-- 背景：骑手账号严格「一人一号」，但 JWT 是无状态的，同一账号在另一台设备/另一个微信
-- 登录成功后，先登录者手里的 token 仍然有效（最长 7 天），会同时收到派单、都能操作订单。
-- 方案：每次「身份切换」（登录所用 openid 与库里 current_openid 不同）时 token_version+1，
-- 校验 token 时与库里的值比对，不一致即判定已被顶下线。
-- 存量 token 不含 tokenVersion（解析为 null），按兼容处理放行，等骑手下次登录自然带上版本号。
ALTER TABLE `rider_profiles`
  ADD COLUMN `token_version` bigint NOT NULL DEFAULT 1 COMMENT '登录会话版本号，递增即踢掉此前签发的 token' AFTER `wechat_open_id`;
