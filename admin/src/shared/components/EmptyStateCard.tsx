import { type ReactNode } from "react";

type EmptyStateCardProps = {
  title: string;
  description?: string;
  primaryActionText?: string;
  onPrimaryAction?: () => void;
  children?: ReactNode;
};

/**
 * 带行动按钮的空状态卡片。
 * 用于列表/表格无数据时引导用户进行下一步操作。
 */
export function EmptyStateCard({ title, description, primaryActionText, onPrimaryAction, children }: EmptyStateCardProps) {
  return (
    <div className="empty-state-card">
      <div className="empty-state-card__title">{title}</div>
      {description ? <div className="empty-state-card__desc">{description}</div> : null}
      {primaryActionText && onPrimaryAction ? (
        <button type="button" className="btn btn-primary empty-state-card__action" onClick={onPrimaryAction}>
          {primaryActionText}
        </button>
      ) : null}
      {children}
    </div>
  );
}
