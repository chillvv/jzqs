import { useMemorizedDate } from "./useMemorizedDate";

// 全局"营业日期"记忆键：订单运营中心与骑手配送中心共用同一个本地缓存键，
// 确保选择某个日期（如"明天"）后：
//   1. 在订单中心 / 骑手中心之间来回切换，日期保持一致，不会回退到"今天"；
//   2. 刷新页面后依然保持上次选择的日期；
//   3. 仅当 localStorage 缓存被清除时，才回退到默认值（今天）。
const GLOBAL_SERVE_DATE_KEY = "global-serve-date";

/**
 * 全局营业日期 Hook。
 * 订单运营中心（OrderPrepPage）与骑手配送中心（DispatchContext）都应通过它读写日期，
 * 以保证跨页面、跨刷新的连续性。
 */
export function useServeDate() {
  return useMemorizedDate(GLOBAL_SERVE_DATE_KEY);
}
