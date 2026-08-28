// @vitest-environment jsdom

import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import { AsyncContentView } from "./AsyncContentView";

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

function renderIntoDom(element: React.ReactElement) {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() => {
    root.render(element);
  });
  return {
    container,
    unmount() {
      act(() => {
        root.unmount();
      });
      container.remove();
    }
  };
}

afterEach(() => {
  document.body.innerHTML = "";
});

describe("AsyncContentView (css / 类名校验, AppSelect 风格)", () => {
  it("exposes a self-built component", () => {
    expect(typeof AsyncContentView).toBe("function");
  });

  it("defines async-content-view styles using token css variables in shared css", () => {
    const css = readFileSync(resolve(__dirname, "../../index.css"), "utf8");

    // 三态视图必需的类名
    expect(css).toContain(".async-content-view");
    expect(css).toContain(".async-content-view__spinner");
    expect(css).toContain(".async-content-view__text");
    expect(css).toContain(".async-content-view__retry");

    // 颜色必须走 tokens.css 中的 CSS 变量，不允许硬编码主题色
    expect(css).toContain("var(--text-muted)");
    expect(css).toContain("var(--error-color-dark)");
    expect(css).toContain("var(--primary-color)");

    // 主题色 #2563eb 不应被硬编码进该组件样式（必须通过 var(--primary-color) 引用）
    const sectionStart = css.indexOf(".async-content-view");
    const sectionEnd = css.indexOf(".async-content-view__retry:focus-visible");
    const section =
      sectionStart >= 0 && sectionEnd > sectionStart
        ? css.slice(sectionStart, sectionEnd + 200)
        : css.slice(sectionStart);
    expect(section).not.toContain("#2563eb");
  });
});

describe("AsyncContentView (渲染行为, jsdom)", () => {
  it("renders loading text, spinner and aria-busy", () => {
    const view = renderIntoDom(<AsyncContentView status="loading" />);

    const root = view.container.querySelector(".async-content-view");
    expect(root).not.toBeNull();
    expect(root?.getAttribute("aria-busy")).toBe("true");
    expect(root?.getAttribute("role")).toBe("status");
    expect(view.container.querySelector(".async-content-view__spinner")).not.toBeNull();
    expect(view.container.textContent).toContain("加载中...");

    view.unmount();
  });

  it("renders custom loadingText", () => {
    const view = renderIntoDom(<AsyncContentView status="loading" loadingText="订单加载中..." />);
    expect(view.container.textContent).toContain("订单加载中...");
    view.unmount();
  });

  it("renders error message with role=alert", () => {
    const view = renderIntoDom(<AsyncContentView status="error" error="网络超时" />);

    const root = view.container.querySelector(".async-content-view");
    expect(root?.getAttribute("role")).toBe("alert");
    expect(root?.getAttribute("aria-live")).toBe("assertive");
    expect(view.container.querySelector(".async-content-view__text--error")).not.toBeNull();
    expect(view.container.textContent).toContain("网络超时");
    // 未提供 onRetry 时不展示重试按钮
    expect(view.container.querySelector(".async-content-view__retry")).toBeNull();

    view.unmount();
  });

  it("extracts message from Error object", () => {
    const view = renderIntoDom(<AsyncContentView status="error" error={new Error("服务器开小差了")} />);
    expect(view.container.textContent).toContain("服务器开小差了");
    view.unmount();
  });

  it("falls back to default error text when error is missing", () => {
    const view = renderIntoDom(<AsyncContentView status="error" />);
    expect(view.container.textContent).toContain("加载失败");
    view.unmount();
  });

  it("shows a retry button and invokes onRetry when provided", () => {
    const onRetry = vi.fn();
    const view = renderIntoDom(<AsyncContentView status="error" error="出错了" onRetry={onRetry} />);

    const button = view.container.querySelector<HTMLButtonElement>(".async-content-view__retry");
    expect(button).not.toBeNull();
    expect(button?.textContent).toContain("重试");

    act(() => {
      button?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
    });

    expect(onRetry).toHaveBeenCalledTimes(1);
    view.unmount();
  });

  it("renders empty text by default", () => {
    const view = renderIntoDom(<AsyncContentView status="empty" />);

    const root = view.container.querySelector(".async-content-view");
    expect(root?.getAttribute("role")).toBe("status");
    expect(root?.getAttribute("aria-busy")).toBeNull();
    expect(view.container.textContent).toContain("暂无记录");

    view.unmount();
  });

  it("renders custom emptyText", () => {
    const view = renderIntoDom(<AsyncContentView status="empty" emptyText="暂无收货地址" />);
    expect(view.container.textContent).toContain("暂无收货地址");
    view.unmount();
  });

  it("renders children on success and exposes no status placeholder", () => {
    const view = renderIntoDom(
      <AsyncContentView status="success">
        <ul>
          <li>订单 A</li>
          <li>订单 B</li>
        </ul>
      </AsyncContentView>
    );

    expect(view.container.querySelector(".async-content-view")).toBeNull();
    expect(view.container.textContent).toContain("订单 A");
    expect(view.container.textContent).toContain("订单 B");

    view.unmount();
  });

  it("renders nothing on success without children", () => {
    const view = renderIntoDom(<AsyncContentView status="success" />);
    expect(view.container.textContent).toBe("");
    view.unmount();
  });
});
