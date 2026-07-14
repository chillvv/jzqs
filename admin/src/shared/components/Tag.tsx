import React from "react";

export interface TagProps extends React.HTMLAttributes<HTMLSpanElement> {
  variant?: "blue" | "green" | "orange" | "amber" | "red" | "gray" | "outline";
}

export function Tag({ children, variant, className = "", ...props }: TagProps) {
  let tagClass = "tag";
  if (variant) {
    tagClass += ` tag-${variant}`;
  }
  if (className) {
    tagClass += ` ${className}`;
  }

  return (
    <span className={tagClass} {...props}>
      {children}
    </span>
  );
}
