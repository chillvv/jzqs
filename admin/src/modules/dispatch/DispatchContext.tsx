import React, { createContext, useContext } from "react";
import type { DispatchMealPeriod } from "./dispatchCenterLayout.helpers";
import { useServeDate } from "../../shared/hooks/useServeDate";
import { usePersistedState, PAGE_MEMORY_KEYS } from "../../shared/hooks/usePersistedState";

interface DispatchContextValue {
  serveDate: string;
  setServeDate: (date: string) => void;
  mealPeriod: DispatchMealPeriod;
  setMealPeriod: (period: DispatchMealPeriod) => void;
}

const DispatchContext = createContext<DispatchContextValue | null>(null);

export function DispatchProvider({ children }: { children: React.ReactNode }) {
  // 营业日期记忆化（全局共享）：刷新后保持上次选择的日期，首次访问/很久未登录默认今天
  const [serveDate, setServeDate] = useServeDate();
  // 餐期记忆化：离开骑手配送中心模块再回来仍保持上次选择，重新登录浏览器时重置为默认。
  const [mealPeriod, setMealPeriod] = usePersistedState<DispatchMealPeriod>(PAGE_MEMORY_KEYS.dispatchMealPeriod, "LUNCH");

  return (
    <DispatchContext.Provider value={{ serveDate, setServeDate, mealPeriod, setMealPeriod }}>
      {children}
    </DispatchContext.Provider>
  );
}

export function useDispatchContext() {
  const ctx = useContext(DispatchContext);
  if (!ctx) throw new Error("useDispatchContext must be used within DispatchProvider");
  return ctx;
}
