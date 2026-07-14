import React from "react";
import { X } from "lucide-react";
import { RemarkField } from "../../../shared/components/RemarkField";
import { SafeInput } from "../../../shared/components/SafeInput";
import { resolveCustomerStatusLabel } from "../customerAssetPage.helpers";

interface CustomerSummary {
  name: string;
  remainingMeals: number;
  customerStatus: string;
}

interface DeductForm {
  mealDelta: string;
  remark: string;
}

interface CustomerDeductDialogProps {
  open: boolean;
  activeItem: CustomerSummary | null;
  deductForm: DeductForm;
  submitting: boolean;
  deductDisabled: boolean;
  remainingMeals: number;
  onClose: () => void;
  onSubmit: () => void;
  onChange: (next: DeductForm) => void;
}

export function CustomerDeductDialog({
  open,
  activeItem,
  deductForm,
  submitting,
  deductDisabled,
  remainingMeals,
  onClose,
  onSubmit,
  onChange
}: CustomerDeductDialogProps) {
  if (!open || !activeItem) {
    return null;
  }

  return (
    <div className="modal-overlay">
      <div className="modal-content modal-content--customer-deduct">
        <div className="modal-header">
          <span>扣餐 - {activeItem.name}</span>
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
          <section className="customer-operation-panel customer-operation-panel--danger">
            <div className="customer-operation-panel__title">本次扣餐信息</div>
            <div
              className="customer-operation-panel__hint"
              style={{ color: deductDisabled ? "var(--error-color-dark)" : "var(--text-sub)" }}
            >
              当前剩余餐次：{remainingMeals}{deductDisabled ? "，余额不足" : ""}
            </div>
            <div className="customer-operation-form-grid">
              <div className="form-group">
                <label className="form-label"><span className="required">*</span>扣减数量</label>
                <SafeInput className="form-control" type="number" value={deductForm.mealDelta} onValueChange={(value) => onChange({ ...deductForm, mealDelta: value })} />
              </div>
              <RemarkField
                label="操作备注"
                value={deductForm.remark}
                onChange={(value) => onChange({ ...deductForm, remark: value })}
                placeholder="例如：客户微信群确认本餐作废，后台扣回 1 餐"
                scene="WALLET_REMARK"
              />
            </div>
          </section>
        </div>
        <div className="modal-footer">
          <button className="btn btn-outline" disabled={submitting} onClick={onClose}>取消</button>
          <button className="btn btn-danger" disabled={submitting || deductDisabled} onClick={onSubmit}>
            {submitting ? "扣减中..." : "确认扣减"}
          </button>
        </div>
      </div>
    </div>
  );
}
