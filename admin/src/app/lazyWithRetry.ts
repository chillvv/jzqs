import { lazy } from "react";

/**
 * 包装 React.lazy 的动态导入，使其在加载失败后自动重试，
 * 最终仍失败时整页刷新以拉取最新的 index.html / 入口 chunk。
 *
 * 背景：部署新版本后，dist 整体被替换，旧的带哈希 chunk 文件会被删除。
 * 仍停留在旧页面的浏览器会话在切换到懒加载路由时会请求已不存在的旧 chunk，
 * 导致 "Failed to fetch dynamically imported module"。整页刷新即可修正。
 */
export function lazyWithRetry<T extends React.ComponentType<any>>(
  factory: () => Promise<{ default: T }>,
  retries = 3,
  delay = 500
) {
  return lazy(() => {
    const attempt = (remaining: number): Promise<{ default: T }> =>
      factory().catch((err) => {
        if (remaining <= 0) {
          // 重试耗尽：通常是部署后旧 chunk 失效，强制整页刷新拉取新入口
          window.location.reload();
          // 保持 pending，避免错误边界闪烁，页面刷新后会替换当前文档
          return new Promise<{ default: T }>(() => {});
        }
        return new Promise((resolve) => setTimeout(resolve, delay)).then(() =>
          attempt(remaining - 1)
        );
      });

    return attempt(retries);
  });
}
