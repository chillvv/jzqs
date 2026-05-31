import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import { AppSelect } from "./AppSelect";

describe("AppSelect", () => {
  it("renders the shared enterprise select class names", () => {
    const element = AppSelect(
      {
        value: "ALL",
        options: [
          { label: "全部状态", value: "ALL" },
          { label: "正式客户", value: "FORMAL" }
        ]
      }
    );

    expect(element.props.className).toBe("app-select");
    expect(element.props.classNames.popup.root).toBe("app-select-popup");
  });

  it("defines centered trigger alignment rules in shared css", () => {
    const css = readFileSync(resolve(__dirname, "../../index.css"), "utf8");

    expect(css).toContain(".app-select.ant-select.ant-select-single");
    expect(css).toContain("height: 44px !important;");
    expect(css).toContain("align-items: center;");
    expect(css).toContain("top: 50%;");
    expect(css).toContain("transform: translateY(-50%);");
  });
});
