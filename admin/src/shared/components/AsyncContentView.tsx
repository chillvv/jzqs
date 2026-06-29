import { type ReactNode } from "react";
import { RotateCcw } from "lucide-react";

/**
 * 异步内容视图的状态。
 * - loading：数据加载中
 * - error：加载失败
 * - empty：加载成功但无数据
 * - success：加载成功且有数据，此时渲染 children
 */
export type AsyncContentViewStatus = "loading" | "empty" | "error" | "success";

type AsyncContentViewProps = {
  /** 当前异步状态。success 时渲染 children，其余三态渲染统一占位视图。 */
  status: AsyncContentViewStatus;
  /** loading 状态下的文案，默认“加载中...”。 */
  loadingText?: string;
  /** empty 状态下的文案，默认“暂无记录”。 */
  emptyText?: string;
  /** error 状态下的错误信息，支持字符串或 Error 对象。 */
  error?: string | Error;
  /** 提供该回调时，error 状态会展示“重试”按钮。 */
  onRetry?: () => void;
  /** status 为 success 时渲染的内容。 */
  children?: ReactNode;
};

const DEFAULT_LOADING_TEXT = "加载中...";
const DEFAULT_EMPTY_TEXT = "暂无记录";
const DEFAULT_ERROR_TEXT = "加载失败";

function resolveErrorText(error: string | Error | undefined): string {
  if (!error) {
    return DEFAULT_ERROR_TEXT;
  }
  if (typeof error === "string") {
    return error || DEFAULT_ERROR_TEXT;
  }
  return error.message || DEFAULT_ERROR_TEXT;
}

/**
 * 统一的异步内容三态视图组件。
 *
 * 替代各页面内联且不一致的 loading/empty/error 处理（“加载中...”、“加载失败：{error}”、
 * “暂无...记录”以及 empty-state / dispatch-empty 等散乱的 className）。
 *
 * - 颜色统一走 tokens.css 中的 CSS 变量（--text-muted / --error-color-dark /
 *   --primary-color 等），主题色保持 #2563eb。
 * - 无障碍：loading 容器标记 aria-busy；error 使用 role="alert"；
 *   loading/empty 使用 role="status" + aria-live="polite"。
 */
export function AsyncContentView({
  status,
  loadingText,
  emptyText,
  error,
  onRetry,
  children
}: AsyncContentViewProps) {
  if (status === "success") {
    return <>{children ?? null}</>;
  }

  const loading = status === "loading";
  const isError = status === "error";
  const errorText = isError ? resolveErrorText(error) : "";

  return (
    <div
      className="async-content-view"
      data-status={status}
      role={isError ? "alert" : "status"}
      aria-live={isError ? "assertive" : "polite"}
      aria-busy={loading ? true : undefined}
    >
      {loading ? (
        <>
          <span className="async-content-view__spinner" aria-hidden="true" />
          <span className="async-content-view__text">
            {loadingText ?? DEFAULT_LOADING_TEXT}
          </span>
        </>
      ) : isError ? (
        <>
          <span className="async-content-view__text async-content-view__text--error">
            {errorText}
          </span>
          {onRetry ? (
            <button type="button" className="async-content-view__retry" onClick={onRetry}>
              <RotateCcw size={14} aria-hidden="true" />
              <span>重试</span>
            </button>
          ) : null}
        </>
      ) : (
        <div style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: "8px" }}>
          <span className="async-content-view__text">
            {emptyText ?? DEFAULT_EMPTY_TEXT}
          </span>
          {children}
        </div>
      )}
    </div>
  );
}
