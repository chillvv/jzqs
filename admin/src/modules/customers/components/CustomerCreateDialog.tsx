import React, { useState } from "react";
import { X } from "lucide-react";
import { RemarkField } from "../../../shared/components/RemarkField";
import { SafeInput } from "../../../shared/components/SafeInput";
import { TooltipHint } from "../../../shared/components/TooltipHint";
import { MapLocationPickerDialog } from "./MapLocationPickerDialog";
import { normalizeInitialMealsValue, parseCoordinatePair } from "../customerAssetPage.helpers";

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
  initialMealRemark: string;
  addressLine: string;
  doorNumber: string;
  latitude: string;
  longitude: string;
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
  const [isMapPickerOpen, setIsMapPickerOpen] = useState(false);

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
              {Number(form.initialMeals || 0) > 0 && (
                <div className="form-group" style={{ marginTop: "16px" }}>
                  <label className="form-label"><span className="required">*</span>初始加餐原因</label>
                  <SafeInput
                    className="form-control"
                    value={form.initialMealRemark}
                    onValueChange={(value) => onChange({ ...form, initialMealRemark: value })}
                    placeholder="例如：微信转账续卡 30 餐"
                  />
                  <div className="admin-panel-note" style={{ marginTop: "4px" }}>加餐原因会记录在流水上，商家和用户都能看到</div>
                </div>
              )}
              <div className="customer-edit-form-grid">
                <div className="form-group">
                  <label className="form-label"><span className="required">*</span>收货地址</label>
                  <SafeInput className="form-control" value={form.addressLine} onValueChange={(value) => onChange({ ...form, addressLine: value })} placeholder="请输入详细收货地址" />
                </div>
                <div className="form-group">
                  <label className="form-label">门牌号（选填）</label>
                  <SafeInput className="form-control" value={form.doorNumber} onValueChange={(value) => onChange({ ...form, doorNumber: value })} placeholder="如：8栋2单元1603 / 13楼1305" />
                </div>
              </div>
              <div className="form-group" style={{ marginTop: "16px" }}>
                <label className="form-label">地图定位（选填，用于骑手精准导航）</label>
                <div className="admin-panel-note" style={{ marginBottom: 8 }}>
                  点击「在地图上选点」搜索或点击地图位置，坐标和地址自动回填（门牌号等可在地址后补充）。
                </div>
                <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
                  <SafeInput
                    className="form-control"
                    style={{ flex: "1 1 150px" }}
                    value={form.latitude}
                    onValueChange={(value) => {
                      const pair = parseCoordinatePair(value);
                      if (pair) {
                        onChange({ ...form, ...pair });
                        return;
                      }
                      onChange({ ...form, latitude: value });
                    }}
                    placeholder="纬度，如 30.654321"
                  />
                  <SafeInput
                    className="form-control"
                    style={{ flex: "1 1 150px" }}
                    value={form.longitude}
                    onValueChange={(value) => onChange({ ...form, longitude: value })}
                    placeholder="经度，如 104.012345"
                  />
                  <button
                    type="button"
                    className="btn btn-outline"
                    onClick={() => setIsMapPickerOpen(true)}
                  >
                    在地图上选点
                  </button>
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
      <MapLocationPickerDialog
        open={isMapPickerOpen}
        initialLatitude={form.latitude}
        initialLongitude={form.longitude}
        onClose={() => setIsMapPickerOpen(false)}
        onConfirm={(latitude, longitude, resolvedAddress) => {
          onChange({
            ...form,
            latitude,
            longitude,
            addressLine:
              resolvedAddress && resolvedAddress.trim().length > 0
                ? resolvedAddress
                : form.addressLine
          });
          setIsMapPickerOpen(false);
        }}
      />
    </div>
  );
}
