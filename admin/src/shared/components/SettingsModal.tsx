import React from "react";
import { Modal } from "./Modal";
import { Button } from "./Button";

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
  return (
    <Modal
      open={open}
      title={title}
      onClose={onClose}
      disableOverlayClose={submitting}
      closeDisabled={submitting}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={submitting}>
            取消
          </Button>
          <Button
            variant={submitDanger ? "danger" : "primary"}
            onClick={onSubmit}
            isLoading={submitting}
          >
            {submitLabel}
          </Button>
        </>
      }
    >
      {children}
    </Modal>
  );
}
