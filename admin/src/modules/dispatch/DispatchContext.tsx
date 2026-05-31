import React, { createContext, useContext, useState } from "react";
import type { DispatchMealPeriod } from "./dispatchCenterLayout.helpers";

interface DispatchContextValue {
  serveDate: string;
  setServeDate: (date: string) => void;
  mealPeriod: DispatchMealPeriod;
  setMealPeriod: (period: DispatchMealPeriod) => void;
}

const DispatchContext = createContext<DispatchContextValue | null>(null);

export function DispatchProvider({ children }: { children: React.ReactNode }) {
  const [serveDate, setServeDate] = useState(() => {
    const today = new Date();
    return today.toISOString().slice(0, 10);
  });
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
