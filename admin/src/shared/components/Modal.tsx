import React, { ReactNode } from "react";
import { X } from "lucide-react";

export interface ModalProps {
  open: boolean;
  title: string;
  description?: string;
  width?: number | string;
  zOffset?: number;
  onClose: () => void;
  footer?: ReactNode;
  children: ReactNode;
  disableOverlayClose?: boolean;
  closeDisabled?: boolean;
}

export function Modal({
  open,
  title,
  description,
  width = 560,
  zOffset = 0,
  onClose,
  footer,
  children,
  disableOverlayClose = false,
  closeDisabled = false,
}: ModalProps) {
  if (!open) return null;

  return (
    <div
      className="admin-modal-overlay"
      data-open="true"
      style={{ zIndex: 1000 + zOffset }}
      onClick={disableOverlayClose ? undefined : onClose}
      data-testid="modal-overlay"
    >
      <div
        className="admin-modal"
        data-open="true"
        style={{ maxWidth: typeof width === "number" ? `${width}px` : width, overflow: "visible" }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="admin-modal__header">
          <div className="admin-modal__heading">
            <h3 className="admin-modal__title">{title}</h3>
            {description && <p className="admin-modal__description">{description}</p>}
          </div>
          {!closeDisabled && (
            <button
              type="button"
              className="admin-modal__close"
              onClick={onClose}
              aria-label="关闭弹窗"
            >
              <X size={16} />
            </button>
          )}
        </div>
        <div className="admin-modal__body">{children}</div>
        {footer && <div className="admin-modal__footer">{footer}</div>}
      </div>
    </div>
  );
}
