import { type ReactNode, useState } from "react";
import { Info } from "lucide-react";

type TooltipHintProps = {
  content: string;
  children?: ReactNode;
};

/**
 * 轻量级提示组件：图标 hover 时展示文字气泡。
 * 用于表单标签旁补充说明，不依赖任何第三方 popover 库。
 */
export function TooltipHint({ content, children }: TooltipHintProps) {
  const [visible, setVisible] = useState(false);

  return (
    <span
      className="tooltip-hint"
      onMouseEnter={() => setVisible(true)}
      onMouseLeave={() => setVisible(false)}
      onFocus={() => setVisible(true)}
      onBlur={() => setVisible(false)}
    >
      {children ?? <Info size={14} className="tooltip-hint__icon" />}
      {visible && (
        <span className="tooltip-hint__bubble" role="tooltip">
          {content}
        </span>
      )}
    </span>
  );
}
