import React from "react";

export interface ButtonProps extends React.ButtonHTMLAttributes<HTMLButtonElement> {
  variant?: "primary" | "outline" | "danger" | "text" | "compact";
  isLoading?: boolean;
}

export function Button({ children, variant, className = "", isLoading, ...props }: ButtonProps) {
  let btnClass = "btn";
  if (variant) {
    btnClass += ` btn-${variant}`;
  }
  if (className) {
    btnClass += ` ${className}`;
  }

  return (
    <button className={btnClass} disabled={isLoading || props.disabled} {...props}>
      {isLoading ? "加载中..." : children}
    </button>
  );
}
