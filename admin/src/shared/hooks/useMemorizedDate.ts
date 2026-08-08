import { useCallback, useState } from "react";
import { formatLocalDateInputValue } from "../utils/dateTime";

// 日期记忆存储前缀：商家在日历/日期筛选上选择的日期会持久化到 localStorage，
// 刷新页面后保持上次选择，避免每次刷新都退回"今天"导致重复操作。
const DATE_MEMORY_PREFIX = "admin-date-memory:";

function rememberDate(storageKey: string, value: string) {
  try {
    window.localStorage.setItem(DATE_MEMORY_PREFIX + storageKey, value);
  } catch {
    // 隐私模式或存储不可用时静默忽略，不影响正常使用
  }
}

function restoreDate(storageKey: string, fallback = formatLocalDateInputValue()) {
  try {
    const stored = window.localStorage.getItem(DATE_MEMORY_PREFIX + storageKey);
    if (!stored || !/^\d{4}-\d{2}-\d{2}$/.test(stored)) {
      // 首次访问（无记录）或记录格式异常 -> 默认今天
      return fallback;
    }
    if (stored < fallback) {
      // YYYY-MM-DD 字典序即日期序：早于今天的日期视为"很久未登录"，回退今天
      return fallback;
    }
    return stored;
  } catch {
    return fallback;
  }
}

/**
 * 记忆日期的 useState：读取时从 localStorage 恢复（今天及未来的日期保留，
 * 过去日期/首次访问回退今天）；更新时同步写入 localStorage，刷新不丢失。
 */
export function useMemorizedDate(storageKey: string) {
  const [date, setDate] = useState(() => restoreDate(storageKey));
  const updateDate = useCallback(
    (next: string) => {
      rememberDate(storageKey, next);
      setDate(next);
    },
    [storageKey]
  );
  return [date, updateDate] as const;
}
