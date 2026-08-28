// @vitest-environment jsdom

import React, { useState } from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { afterEach, describe, expect, it } from "vitest";
import { SafeInput, SafeTextarea } from "./SafeInput";

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

function ControlledInput({ initialValue = "原内容" }: { initialValue?: string }) {
  const [value, setValue] = useState(initialValue);
  return <SafeInput value={value} onValueChange={setValue} aria-label="safe-input" />;
}

function ControlledTextarea({ initialValue = "原备注" }: { initialValue?: string }) {
  const [value, setValue] = useState(initialValue);
  return <SafeTextarea value={value} onValueChange={setValue} aria-label="safe-textarea" />;
}

afterEach(() => {
  document.body.innerHTML = "";
});

describe("SafeInput", () => {
  it("updates immediately for normal typing", () => {
    const view = renderIntoDom(<ControlledInput initialValue="" />);
    const input = view.container.querySelector("input");

    expect(input).not.toBeNull();

    act(() => {
      const nativeInput = input as HTMLInputElement;
      nativeInput.value = "张三";
      nativeInput.dispatchEvent(new Event("input", { bubbles: true }));
    });

    expect((input as HTMLInputElement).value).toBe("张三");
    view.unmount();
  });

  it("commits the final composed value instead of preserving the appended interim value", () => {
    const view = renderIntoDom(<ControlledInput />);
    const input = view.container.querySelector("input") as HTMLInputElement;

    act(() => {
      input.dispatchEvent(new CompositionEvent("compositionstart", { bubbles: true }));
      input.value = "原内容张";
      input.dispatchEvent(new Event("input", { bubbles: true }));
    });

    act(() => {
      input.value = "张";
      input.dispatchEvent(new CompositionEvent("compositionend", { bubbles: true }));
    });

    expect(input.value).toBe("张");
    view.unmount();
  });
});

describe("SafeTextarea", () => {
  it("commits the final composed textarea value", () => {
    const view = renderIntoDom(<ControlledTextarea />);
    const textarea = view.container.querySelector("textarea") as HTMLTextAreaElement;

    act(() => {
      textarea.dispatchEvent(new CompositionEvent("compositionstart", { bubbles: true }));
      textarea.value = "原备注新的";
      textarea.dispatchEvent(new Event("input", { bubbles: true }));
      textarea.value = "新的";
      textarea.dispatchEvent(new CompositionEvent("compositionend", { bubbles: true }));
    });

    expect(textarea.value).toBe("新的");
    view.unmount();
  });
});
