# 03 — 打开网关访问日志（Caddyfile 缺 log 指令）

**What to build:** 在 `Caddyfile` 的 `jzqs.top` 站点块开启访问日志，使每个请求的 `uri / status / duration`
可查；当前连“骑手请求到底慢不慢、错多少”都无法从网关侧观测。

**Blocked by:** None

**Status:** todo

**类型:** chore
**优先级:** medium

## 背景与证据

2026-09-10 排查骑手卡顿时：

- `Caddyfile` 里只有反代配置，**没有任何 `log` 指令** → 网关不记录访问日志
- `docker logs jzqs-caddy` 里只能捞到 `warn/error` 级别的零星记录
  （48 小时内 `/api/` 只有 2 条，且都是 502），完全无法统计请求延迟与错误率
- 直接后果：只能靠后端业务日志 + MySQL 侧证据反推，多花了大量时间

## 建议改法（最小改动）

在 `jzqs.top { ... }` 块内加：

```
    log
```

Caddy 2 默认把访问日志以 JSON 写到 stdout，可直接 `docker logs jzqs-caddy` 查看，
每行含 `status` 与 `duration`，足够做慢请求统计（本次缺的就是这两个字段）。

若担心 stdout 噪音，可改为落文件并轮转：

```
    log {
        output file /data/logs/access-jzqs.log {
            roll_size 50MiB
            roll_keep 5
        }
    }
```

## Acceptance criteria

- [ ] `caddy validate` 通过（改配置前先校验，避免网关起不来）
- [ ] `docker exec jzqs-caddy caddy reload --config /etc/caddy/Caddyfile` 生效
- [ ] `docker logs jzqs-caddy` 能看到带 `duration` 的 `/api/*` 访问记录
- [ ] 能一条命令列出最近 24h 最慢的 10 个请求（用于后续容量与慢接口排查）

## 风险

日志量增加 I/O；当前日活量很小，可忽略。改配置后必须确认站点仍可访问（回滚即删除 `log` 指令再 reload）。
