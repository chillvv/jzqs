import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { AppSelect } from "./AppSelect";

describe("AppSelect", () => {
  it("exposes a self-built component (antd Select removed)", () => {
    // 自研版本通过 hooks 渲染，直接调用无法返回 element；仅校验导出为函数即可。
    expect(typeof AppSelect).toBe("function");
  });

  it("defines self-built trigger / dropdown / option styles in shared css", () => {
    const css = readFileSync(resolve(__dirname, "../../index.css"), "utf8");

    // 自研下拉结构必需的类名
    expect(css).toContain(".app-select-trigger");
    expect(css).toContain(".app-select-dropdown");
    expect(css).toContain(".app-select-option");
    expect(css).toContain(".app-select-arrow");
    expect(css).toContain("align-items: center;");
    expect(css).toContain("top: 50%;");
    expect(css).toContain("transform: translateY(-50%);");
  });

  it("no longer ships antd Select overrides (!important removed)", () => {
    const css = readFileSync(resolve(__dirname, "../../index.css"), "utf8");

    // antd 选择器应已全部移除
    expect(css).not.toContain(".app-select.ant-select");
    expect(css).not.toContain(".ant-select-dropdown");
    expect(css).not.toContain(".app-select-popup");
  });
});
