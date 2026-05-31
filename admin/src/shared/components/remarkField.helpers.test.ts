import { describe, expect, it } from "vitest";
import { shouldLoadRemarkSuggestions } from "./remarkField.helpers";

describe("shouldLoadRemarkSuggestions", () => {
  it("requires customerId for order remark suggestions", () => {
    expect(shouldLoadRemarkSuggestions("ORDER_REMARK", null)).toBe(false);
    expect(shouldLoadRemarkSuggestions("ORDER_REMARK", 7)).toBe(true);
  });

  it("still loads non-order remark suggestions without customerId", () => {
    expect(shouldLoadRemarkSuggestions("RECEIPT_NOTE", null)).toBe(true);
  });
});
