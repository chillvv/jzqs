import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchRemarkSuggestions, http } from "./http";

describe("fetchRemarkSuggestions", () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it("passes customerId for order remark suggestions", async () => {
    const getSpy = vi.spyOn(http, "get").mockResolvedValue({
      data: {
        data: {
          scene: "ORDER_REMARK",
          items: []
        }
      }
    } as never);

    await fetchRemarkSuggestions("ORDER_REMARK", 7);

    expect(getSpy).toHaveBeenCalledWith("/api/admin/customers/remark-suggestions?scene=ORDER_REMARK&customerId=7");
  });

  it("does not append customerId for other remark suggestion scenes", async () => {
    const getSpy = vi.spyOn(http, "get").mockResolvedValue({
      data: {
        data: {
          scene: "RECEIPT_NOTE",
          items: []
        }
      }
    } as never);

    await fetchRemarkSuggestions("RECEIPT_NOTE", 7);

    expect(getSpy).toHaveBeenCalledWith("/api/admin/customers/remark-suggestions?scene=RECEIPT_NOTE");
  });
});
