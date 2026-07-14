import React from "react";
import { X } from "lucide-react";
import { handleAdminImageFallback, resolveAdminMediaUrl } from "../systemSettingsPage.helpers";

interface BannerPreviewModalProps {
  imageUrl: string;
  onClose: () => void;
}

export function BannerPreviewModal({ imageUrl, onClose }: BannerPreviewModalProps) {
  if (!imageUrl) {
    return null;
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content modal-content--banner-preview" onClick={(event) => event.stopPropagation()}>
        <div className="modal-header">
          <span>轮播图大图预览</span>
          <span className="modal-close" onClick={onClose}><X size={18} /></span>
        </div>
        <div className="modal-body">
          <img
            src={resolveAdminMediaUrl(imageUrl)}
            alt="轮播图大图预览"
            className="banner-preview-dialog__image"
            onError={handleAdminImageFallback}
          />
        </div>
        <div className="modal-footer">
          <button className="btn btn-outline" onClick={onClose}>关闭预览</button>
        </div>
      </div>
    </div>
  );
}
