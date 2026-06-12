import React from "react";
import { X } from "lucide-react";

interface SettingsModalProps {
  open: boolean;
  title: string;
  onClose: () => void;
  onSubmit: () => void;
  submitLabel?: string;
  submitDanger?: boolean;
  submitting?: boolean;
  children: React.ReactNode;
}

export function SettingsModal({
  open,
  title,
  onClose,
  onSubmit,
  submitLabel = "保存",
  submitDanger = false,
  submitting = false,
  children
}: SettingsModalProps) {
  if (!open) return null;

  return (
    <div className="modal-overlay">
      <div className="modal-content">
        <div className="modal-header">
          <span>{title}</span>
          <span className="modal-close" onClick={onClose}>
            <X size={20} />
          </span>
        </div>
        <div className="modal-body">{children}</div>
        <div className="modal-footer">
          <button className="btn btn-outline" onClick={onClose} disabled={submitting}>
            取消
          </button>
          <button
            className={submitDanger ? "btn btn-danger" : "btn btn-primary"}
            onClick={onSubmit}
            disabled={submitting}
          >
            {submitting ? "保存中..." : submitLabel}
          </button>
        </div>
      </div>
    </div>
  );
}
