import React from "react";
import { X } from "lucide-react";
import { RemarkField } from "../../../shared/components/RemarkField";
import { SafeInput } from "../../../shared/components/SafeInput";
import { TooltipHint } from "../../../shared/components/TooltipHint";
import { normalizeInitialMealsValue } from "../customerAssetPage.helpers";

interface CustomerCreateForm {
  name: string;
  phone: string;
  remark: string;
  customerStatus: string;
  openedAt: string;
  expiredAt: string;
  remainingValidityDays: string;
  initialMeals: string;
  initialValidityDays: string;
  addressLine: string;
}

interface CustomerCreateDialogProps {
  open: boolean;
  form: CustomerCreateForm;
  submitting: boolean;
  onClose: () => void;
  onSubmit: () => void;
  onChange: (next: CustomerCreateForm) => void;
  normalizeCustomerPhone: (value: string) => string;
}

export function CustomerCreateDialog({
  open,
  form,
  submitting,
  onClose,
  onSubmit,
  onChange,
  normalizeCustomerPhone
}: CustomerCreateDialogProps) {
  if (!open) {
    return null;
  }

  return (
    <div className="modal-overlay">
      <div className="modal-content modal-content--customer-create">
        <div className="modal-header">
          <span>新建客户档案</span>
          <span className="modal-close" onClick={onClose}><X size={20} /></span>
        </div>
        <div className="modal-body customer-edit-modal">
          <div className="customer-edit-grid">
            <section className="customer-edit-section">
              <div className="customer-edit-section__title">基础资料</div>
              <div className="customer-edit-form-grid">
                <div className="form-group">
                  <label className="form-label"><span className="required">*</span>客户姓名</label>
                  <SafeInput className="form-control" value={form.name} onValueChange={(value) => onChange({ ...form, name: value })} />
                </div>
                <div className="form-group">
                  <label className="form-label">
                    <span className="required">*</span>联系电话
                    <TooltipHint content="就是你平时用的手机号，11 位数字那个" />
                  </label>
                  <SafeInput className="form-control" value={form.phone} onValueChange={(value) => onChange({ ...form, phone: normalizeCustomerPhone(value) })} />
                </div>
              </div>
              <div className="form-group" style={{ marginTop: "16px" }}>
                <label className="form-label">初始加餐数量（选填）</label>
                <SafeInput
                  className="form-control"
                  type="number"
                  min="0"
                  value={normalizeInitialMealsValue(form.initialMeals)}
                  onValueChange={(value) => onChange({ ...form, initialMeals: value })}
                />
                <div className="admin-panel-note" style={{ marginTop: "4px" }}>如果填写大于 0，将在建档后自动为用户加餐</div>
              </div>
              <div className="form-group" style={{ marginTop: "16px" }}>
                <label className="form-label">初始有效期天数</label>
                <SafeInput
                  className="form-control"
                  type="number"
                  min="1"
                  value={String(form.initialValidityDays || "30")}
                  onValueChange={(value) => onChange({ ...form, initialValidityDays: value })}
                />
                <div className="admin-panel-note" style={{ marginTop: "4px" }}>填写后会同步生成该客户当前餐包的到期日</div>
              </div>
              <div className="customer-edit-form-grid">
                <div className="form-group">
                  <label className="form-label"><span className="required">*</span>收货地址</label>
                  <SafeInput className="form-control" value={form.addressLine} onValueChange={(value) => onChange({ ...form, addressLine: value })} placeholder="请输入详细收货地址" />
                </div>
              </div>
              <div className="admin-panel-note" style={{ marginTop: "12px" }}>
                首个收货地址会自动绑定当前客户姓名和手机号，后续在后台修改客户资料时会同步更新地址联系人与电话。
              </div>
              <div className="customer-create-remark-field">
                <div className="admin-panel-note" style={{ marginBottom: 8 }}>
                  长期生效，默认带到后续订单；订单中心填写的商家备注仅此单生效。
                </div>
                <RemarkField
                  label="商家备注"
                  value={form.remark}
                  onChange={(value) => onChange({ ...form, remark: value })}
                  placeholder="记录商家侧需要注意的事项"
                  scene="CUSTOMER_REMARK"
                  multiline
                />
              </div>
            </section>
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-outline" disabled={submitting} onClick={onClose}>取消</button>
          <button className="btn btn-primary" disabled={submitting} onClick={onSubmit}>{submitting ? "创建中..." : "确认创建"}</button>
        </div>
      </div>
    </div>
  );
}
