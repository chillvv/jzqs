import React, { useState, useEffect } from "react";
import { X } from "lucide-react";
import { recordDeliveryReceipt, deleteDeliveryReceipt, uploadDeliveryReceiptImage } from "../../../shared/api/http";
import { RemarkField } from "../../../shared/components/RemarkField";
import { toast } from "../../../shared/components/Toast";
import type { OrderPrepItemResponse } from "../../../shared/api/types";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  activeItem: OrderPrepItemResponse | null;
}

const MAX_RECEIPT_FILE_SIZE = 5 * 1024 * 1024;
const RECEIPT_UPLOAD_INPUT_ID = "admin-receipt-upload-input";

type ReceiptSelectedFile = {
  name: string;
  size: number;
};

function hasImageValue(value: string | null | undefined) {
  return Boolean(value && value.trim());
}

function formatFileSize(bytes: number) {
  if (bytes >= 1024 * 1024) {
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  }
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

function getReceiptUploadErrorMessage(error: any) {
  if (error?.response?.status === 413) {
    return "图片太大，请上传 5MB 以内的图片";
  }
  return error?.response?.data?.message || error?.message || "上传回执图片失败";
}

export function OrderPrepReceiptModal({ isOpen, onClose, onSuccess, activeItem }: Props) {
  const [submittingReceipt, setSubmittingReceipt] = useState(false);
  const [uploadingReceipt, setUploadingReceipt] = useState(false);
  const [receiptForm, setReceiptForm] = useState({ receiptUrl: "", receiptNote: "" });
  const [selectedReceiptFile, setSelectedReceiptFile] = useState<ReceiptSelectedFile | null>(null);

  useEffect(() => {
    if (isOpen && activeItem) {
      setReceiptForm({ receiptUrl: activeItem.receiptUrl || "", receiptNote: activeItem.receiptNote || "" });
      setSelectedReceiptFile(null);
    }
  }, [isOpen, activeItem]);

  async function handleReceiptSubmit() {
    const receiptUrl = receiptForm.receiptUrl.trim();
    if (!activeItem || !receiptUrl) {
      toast("请先上传回执图片", "error");
      return;
    }
    if (submittingReceipt) return;
    setSubmittingReceipt(true);
    try {
      await recordDeliveryReceipt({
        mealSlotOrderId: activeItem.id,
        receiptUrl,
        receiptNote: receiptForm.receiptNote,
        deliveredAt: new Date().toISOString()
      });
      onClose();
      onSuccess();
      toast("回执已提交");
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "提交回执失败", "error");
    } finally {
      setSubmittingReceipt(false);
    }
  }

  async function handleReceiptDelete() {
    if (!activeItem || submittingReceipt) {
      return;
    }
    setSubmittingReceipt(true);
    try {
      await deleteDeliveryReceipt(activeItem.id);
      setReceiptForm((current) => ({ ...current, receiptUrl: "" }));
      setSelectedReceiptFile(null);
      onClose();
      onSuccess();
      toast("回执已删除");
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "删除回执失败", "error");
    } finally {
      setSubmittingReceipt(false);
    }
  }

  async function handleReceiptFileChange(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    if (!file.type.startsWith("image/")) {
      toast("请上传 JPG、PNG、WEBP 等图片文件", "error");
      event.target.value = "";
      return;
    }
    if (file.size > MAX_RECEIPT_FILE_SIZE) {
      toast("回执图片不能超过 5MB", "error");
      event.target.value = "";
      return;
    }
    setSelectedReceiptFile({ name: file.name, size: file.size });
    setUploadingReceipt(true);
    try {
      const uploaded = await uploadDeliveryReceiptImage(file);
      setReceiptForm((current) => ({ ...current, receiptUrl: uploaded.url }));
      toast("回执图片已上传");
    } catch (error: any) {
      toast(getReceiptUploadErrorMessage(error), "error");
    } finally {
      setUploadingReceipt(false);
      event.target.value = "";
    }
  }

  if (!isOpen || !activeItem) return null;

  return (
    <div className="modal-overlay">
      <div className="modal-content">
        <div className="modal-header">
          <span>上传送达回执 - {activeItem.customerName}</span>
          <button type="button" className="modal-close" disabled={submittingReceipt} onClick={submittingReceipt ? undefined : onClose}><X size={20} /></button>
        </div>
        <div className="modal-body">
          <div className="form-group">
            <label className="form-label"><span className="required">*</span>回执图片</label>
            <input
              id={RECEIPT_UPLOAD_INPUT_ID}
              type="file"
              accept="image/png,image/jpeg,image/jpg,image/webp"
              capture="environment"
              className="receipt-upload-input"
              onChange={(event) => handleReceiptFileChange(event).catch(() => undefined)}
              disabled={uploadingReceipt || submittingReceipt}
            />
            <label
              htmlFor={RECEIPT_UPLOAD_INPUT_ID}
              className={`receipt-upload-card ${uploadingReceipt ? "is-uploading" : ""} ${hasImageValue(receiptForm.receiptUrl) ? "has-image" : ""}`}
            >
              <div className="receipt-upload-card__header">
                <div>
                  <div className="receipt-upload-card__title">
                    {uploadingReceipt
                      ? "正在上传回执图片..."
                      : hasImageValue(receiptForm.receiptUrl)
                        ? "回执图片已上传，可重新选择"
                        : "点击选择回执图片"}
                  </div>
                  <div className="receipt-upload-card__desc">
                    支持 JPG、PNG、WEBP，单张 5MB 以内。电脑端可选文件，手机端可直接拍照。
                  </div>
                </div>
                <span className="receipt-upload-trigger">
                  {uploadingReceipt ? "上传中..." : hasImageValue(receiptForm.receiptUrl) ? "重新选择" : "选择文件"}
                </span>
              </div>
              {selectedReceiptFile ? (
                <div className="receipt-upload-summary">
                  <span>{selectedReceiptFile.name}</span>
                  <span>{formatFileSize(selectedReceiptFile.size)}</span>
                </div>
              ) : null}
            </label>
            {hasImageValue(receiptForm.receiptUrl) ? (
              <div className="order-detail-image-grid receipt-upload-preview-grid" style={{ marginTop: "12px" }}>
                <div className="order-detail-image-card receipt-upload-preview-card">
                  <div className="order-detail-image-card__title">当前回执图</div>
                  <img
                    src={receiptForm.receiptUrl}
                    alt="当前回执图"
                    className="order-detail-image-card__image"
                  />
                  <div className="receipt-upload-actions">
                    <button
                      type="button"
                      className="btn btn-outline"
                      onClick={() => window.open(receiptForm.receiptUrl, "_blank")}
                    >
                      查看大图
                    </button>
                  </div>
                </div>
              </div>
            ) : null}
          </div>
          <RemarkField
            label="回执备注"
            value={receiptForm.receiptNote}
            onChange={(value) => setReceiptForm({ ...receiptForm, receiptNote: value })}
            placeholder="例如：已放前台"
            scene="RECEIPT_NOTE"
            multiline
          />
        </div>
        <div className="modal-footer">
          <button className="btn btn-outline" onClick={onClose} disabled={submittingReceipt}>取消</button>
          {hasImageValue(activeItem.receiptUrl) ? (
            <button className="btn-delete" disabled={submittingReceipt} onClick={() => handleReceiptDelete().catch(() => undefined)}>
              {submittingReceipt ? "处理中..." : "删除回执"}
            </button>
          ) : null}
          <button className="btn btn-primary" disabled={submittingReceipt || uploadingReceipt || !hasImageValue(receiptForm.receiptUrl)} onClick={() => handleReceiptSubmit().catch(() => undefined)}>
            {submittingReceipt ? "提交中..." : "提交回执"}
          </button>
        </div>
      </div>
    </div>
  );
}
