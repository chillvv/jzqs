import React, { createContext, useContext, useState } from "react";
import type { DispatchMealPeriod } from "./dispatchCenterLayout.helpers";
import { useServeDate } from "../../shared/hooks/useServeDate";

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
  const [mealPeriod, setMealPeriod] = useState<DispatchMealPeriod>("LUNCH");

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
