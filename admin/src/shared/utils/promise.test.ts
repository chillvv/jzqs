import { describe, expect, it } from "vitest";
import {
  getRejectedReason,
  isPromiseFulfilledResult,
  isPromiseRejectedResult
} from "./promise";

describe("promise settled helpers", () => {
  it("narrows fulfilled settled results", () => {
    const result: PromiseSettledResult<number> = {
      status: "fulfilled",
      value: 7
    };

    expect(isPromiseFulfilledResult(result)).toBe(true);
    if (isPromiseFulfilledResult(result)) {
      expect(result.value).toBe(7);
    }
  });

  it("narrows rejected settled results", () => {
    const error = new Error("network failed");
    const result: PromiseSettledResult<number> = {
      status: "rejected",
      reason: error
    };

    expect(isPromiseRejectedResult(result)).toBe(true);
    expect(getRejectedReason(result)).toBe(error);
  });
});
