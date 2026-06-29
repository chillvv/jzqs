import React, { useEffect, useState } from "react";

type ToastType = "success" | "error" | "info";

interface ToastItem {
  id: number;
  message: string;
  type: ToastType;
}

let _addToast: ((message: string, type: ToastType) => void) | null = null;

export function toast(message: string, type: ToastType = "success") {
  _addToast?.(message, type);
}

export function ToastContainer() {
  const [items, setItems] = useState<ToastItem[]>([]);

  useEffect(() => {
    _addToast = (message, type) => {
      const id = Date.now();
      setItems((prev) => [...prev, { id, message, type }]);
      setTimeout(() => {
        setItems((prev) => prev.filter((t) => t.id !== id));
      }, 3000);
    };
    return () => {
      _addToast = null;
    };
  }, []);

  if (items.length === 0) return null;

  return (
    <div className="toast-container">
      {items.map((item) => (
        <div key={item.id} className={`toast toast--${item.type}`}>
          <span className="toast__icon">
            {item.type === "success" ? "✓" : item.type === "error" ? "✕" : "ℹ"}
          </span>
          <span className="toast__message">{item.message}</span>
        </div>
      ))}
    </div>
  );
}
