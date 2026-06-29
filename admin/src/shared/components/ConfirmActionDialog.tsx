import { AdminDialog } from "./AdminDialog";

type ConfirmActionDialogProps = {
  open: boolean;
  title: string;
  description: string;
  confirmText?: string;
  cancelText?: string;
  onConfirm: () => void;
  onCancel: () => void;
};

/**
 * 通用确认弹窗，基于 AdminDialog 封装。
 * 用于批量操作前的二次确认（核销、删除等不可逆动作）。
 */
export function ConfirmActionDialog({
  open,
  title,
  description,
  confirmText = "确认",
  cancelText = "取消",
  onConfirm,
  onCancel
}: ConfirmActionDialogProps) {
  return (
    <AdminDialog
      open={open}
      title={title}
      description={description}
      width={420}
      onClose={onCancel}
      footer={
        <div style={{ display: "flex", justifyContent: "flex-end", gap: "8px" }}>
          <button className="btn btn-outline" onClick={onCancel}>
            {cancelText}
          </button>
          <button className="btn btn-primary" onClick={onConfirm}>
            {confirmText}
          </button>
        </div>
      }
    >
      <p style={{ fontSize: "14px", color: "var(--text-sub)", lineHeight: 1.6, margin: 0 }}>
        {description}
      </p>
    </AdminDialog>
  );
}
