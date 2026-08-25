import { useCallback, useEffect, useRef, useState } from "react";

/**
 * 与 localStorage 绑定的状态 Hook。
 * 用于让页面筛选状态（餐期、日期、来源、状态、搜索关键字、分页等）在以下场景保持"上次记忆"：
 *   1. 在后台不同页面之间来回切换（组件卸载再挂载）不会回退到默认值；
 *   2. 刷新页面后依然保持上次选择；
 *   3. 仅当显式清除缓存（如重新登录浏览器）时回到默认值。
 *
 * 所有"页面记忆"键名统一以 `page-mem:` 前缀开头，便于登录时一次性清除。
 */
const PAGE_MEMORY_PREFIX = "page-mem:";

export const PAGE_MEMORY_KEYS = {
  orderMealPeriod: `${PAGE_MEMORY_PREFIX}orders-meal-period`,
  orderSourceFilter: `${PAGE_MEMORY_PREFIX}orders-source-filter`,
  orderStatusFilter: `${PAGE_MEMORY_PREFIX}orders-status-filter`,
  orderRemarkFilter: `${PAGE_MEMORY_PREFIX}orders-remark-filter`,
  orderKeyword: `${PAGE_MEMORY_PREFIX}orders-keyword`,
  orderActiveTab: `${PAGE_MEMORY_PREFIX}orders-active-tab`,
  orderCurrentPage: `${PAGE_MEMORY_PREFIX}orders-current-page`,
  dispatchMealPeriod: `${PAGE_MEMORY_PREFIX}dispatch-meal-period`,
  dispatchRidersStatus: `${PAGE_MEMORY_PREFIX}dispatch-riders-status`,
  dispatchRidersSearch: `${PAGE_MEMORY_PREFIX}dispatch-riders-search`
} as const;

// 历史遗留的订单餐期记忆键：登录重置时一并清除，避免旧记忆残留。
export const LEGACY_ORDER_MEAL_PERIOD_KEY = "admin-order-prep-meal-period";

export function readPageMemory(rawKey: string): string | null {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(rawKey);
  } catch {
    return null;
  }
}

export function writePageMemory(rawKey: string, value: string) {
  if (typeof window === "undefined") return;
  try {
    window.localStorage.setItem(rawKey, value);
  } catch {
    /* 忽略写入失败（隐私模式 / 配额超限等） */
  }
}

export function clearPageMemories() {
  if (typeof window === "undefined") return;
  try {
    const keysToRemove: string[] = [];
    for (let index = 0; index < window.localStorage.length; index += 1) {
      const key = window.localStorage.key(index);
      if (key && key.startsWith(PAGE_MEMORY_PREFIX)) {
        keysToRemove.push(key);
      }
    }
    keysToRemove.push(LEGACY_ORDER_MEAL_PERIOD_KEY);
    keysToRemove.forEach((key) => window.localStorage.removeItem(key));
  } catch {
    /* 忽略清除失败 */
  }
}

function isServer() {
  return typeof window === "undefined";
}

function parseStored<T>(raw: string | null, fallback: T): T {
  if (raw === null) return fallback;
  try {
    return JSON.parse(raw) as T;
  } catch {
    return fallback;
  }
}

/**
 * 任意可 JSON 序列化值的持久化状态，用法与普通的 useState 完全一致：
 *   const [value, setValue] = usePersistedState(key, defaultValue);
 * 值会在变更时写入 localStorage，组件卸载再挂载或刷新页面后都会恢复。
 */
export function usePersistedState<T>(storageKey: string, defaultValue: T): [T, (next: T | ((prev: T) => T)) => void] {
  const [value, setValue] = useState<T>(() => {
    if (isServer()) return defaultValue;
    return parseStored(readPageMemory(storageKey), defaultValue);
  });

  const keyRef = useRef(storageKey);
  keyRef.current = storageKey;

  const setPersisted = useCallback((next: T | ((prev: T) => T)) => {
    setValue((prev) => {
      const resolved = typeof next === "function" ? (next as (prev: T) => T)(prev) : next;
      writePageMemory(keyRef.current, JSON.stringify(resolved));
      return resolved;
    });
  }, []);

  useEffect(() => {
    if (isServer()) return undefined;
    function onStorage(event: StorageEvent) {
      if (event.key === keyRef.current && event.newValue !== null) {
        setValue(parseStored<T>(event.newValue, defaultValue));
      }
    }
    window.addEventListener("storage", onStorage);
    return () => window.removeEventListener("storage", onStorage);
  }, [defaultValue]);

  return [value, setPersisted];
}
