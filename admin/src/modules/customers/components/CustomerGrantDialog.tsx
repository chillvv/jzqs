import React from "react";
import { X } from "lucide-react";
import { DatePicker } from "../../../shared/components/DatePicker";
import { RemarkField } from "../../../shared/components/RemarkField";
import { SafeInput } from "../../../shared/components/SafeInput";
import { resolveCustomerStatusLabel } from "../customerAssetPage.helpers";

interface CustomerSummary {
  name: string;
  remainingMeals: number;
  customerStatus: string;
}

export interface GrantForm {
  mealDelta: string;
  validityDays: string;
  remark: string;
  expiredAt: string;
}

interface CustomerGrantDialogProps {
  open: boolean;
  activeItem: CustomerSummary | null;
  grantForm: GrantForm;
  submitting: boolean;
  onClose: () => void;
  onSubmit: () => void;
  onChange: (next: GrantForm) => void;
  onValidityDaysChange: (value: string) => void;
  onExpiredAtChange: (date: string) => void;
}

export function CustomerGrantDialog({
  open,
  activeItem,
  grantForm,
  submitting,
  onClose,
  onSubmit,
  onChange,
  onValidityDaysChange,
  onExpiredAtChange
}: CustomerGrantDialogProps) {
  if (!open || !activeItem) {
    return null;
  }

  return (
    <div className="modal-overlay">
      <div className="modal-content modal-content--customer-grant">
        <div className="modal-header">
          <span>加餐 - {activeItem.name}</span>
          <span className="modal-close" onClick={onClose}><X size={20} /></span>
        </div>
        <div className="modal-body customer-operation-modal">
          <div className="customer-operation-topbar">
            <div className="customer-operation-topbar__item">
              <span className="customer-operation-topbar__label">当前余额</span>
              <span className="customer-operation-topbar__value">{activeItem.remainingMeals} 餐</span>
            </div>
            <div className="customer-operation-topbar__item">
              <span className="customer-operation-topbar__label">客户状态</span>
              <span className="customer-operation-topbar__value">{resolveCustomerStatusLabel(activeItem.customerStatus)}</span>
            </div>
          </div>
          <section className="customer-operation-panel customer-operation-panel--success">
            <div className="customer-operation-panel__title">本次加餐信息</div>
            <div className="customer-operation-form-grid">
              <div className="form-group">
                <label className="form-label"><span className="required">*</span>加餐数量（餐）</label>
                <SafeInput
                  className="form-control"
                  type="number"
                  min="1"
                  value={grantForm.mealDelta}
                  onValueChange={(value) => onChange({ ...grantForm, mealDelta: String(value || "").replace(/[^\d]/g, "") })}
                />
              </div>
              <div className="form-group">
                <label className="form-label">有效期天数（当前时间 + N 天）</label>
                <SafeInput
                  className="form-control"
                  type="number"
                  min="1"
                  value={grantForm.validityDays}
                  onValueChange={onValidityDaysChange}
                />
              </div>
              <div className="form-group">
                <label className="form-label">或直接选择到期日期</label>
                <DatePicker value={grantForm.expiredAt} onChange={onExpiredAtChange} showTomorrowShortcut={false} />
              </div>
            </div>
            <div className="customer-detail-note-block" style={{ marginTop: 12 }}>
              <div className="customer-detail-note-block__label">本次加餐后到期</div>
              <div className="customer-detail-note-block__value">{grantForm.expiredAt || "-"}</div>
            </div>
            <div style={{ marginTop: 12 }}>
              <RemarkField
                label="加餐原因"
                required
                value={grantForm.remark}
                onChange={(value) => onChange({ ...grantForm, remark: value })}
                placeholder="必须填写，例如：客户微信转账续卡 30 餐 / 活动赠送 5 餐"
                scene="WALLET_REMARK"
                multiline
              />
            </div>
          </section>
        </div>
        <div className="modal-footer">
          <button className="btn btn-outline" disabled={submitting} onClick={onClose}>取消</button>
          <button className="btn btn-primary" disabled={submitting} onClick={onSubmit}>
            {submitting ? "加餐中..." : "确认加餐"}
          </button>
        </div>
      </div>
    </div>
  );
}
