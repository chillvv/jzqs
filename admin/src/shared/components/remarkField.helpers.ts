import type { RemarkSuggestionScene } from "../api/types";

export function shouldLoadRemarkSuggestions(scene: RemarkSuggestionScene, customerId?: number | null) {
  if (scene !== "ORDER_REMARK") {
    return true;
  }
  return Boolean(customerId);
}
