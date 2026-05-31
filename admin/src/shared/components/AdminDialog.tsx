import { type ReactNode } from "react";
import { X } from "lucide-react";

type AdminDialogProps = {
  open: boolean;
  title: string;
  description?: string;
  width?: number | string;
  zOffset?: number;
  onClose: () => void;
  footer?: ReactNode;
  children: ReactNode;
};

export function AdminDialog({ open, title, description, width = 460, zOffset = 0, onClose, footer, children }: AdminDialogProps) {
  return (
    <div className="admin-modal-overlay" data-open={open} style={{ zIndex: 1000 + zOffset }} onClick={onClose}>
      <div
        className="admin-modal"
        data-open={open}
        style={{ maxWidth: typeof width === "number" ? `${width}px` : width, overflow: "visible" }}
        onClick={(event) => event.stopPropagation()}
      >
        <div className="admin-modal__header">
          <div className="admin-modal__heading">
            <h3 className="admin-modal__title">{title}</h3>
            {description ? <p className="admin-modal__description">{description}</p> : null}
          </div>
          <button type="button" className="admin-modal__close" onClick={onClose} aria-label="关闭弹窗">
            <X size={16} />
          </button>
        </div>
        <div className="admin-modal__body">{children}</div>
        {footer ? <div className="admin-modal__footer">{footer}</div> : null}
      </div>
    </div>
  );
}
