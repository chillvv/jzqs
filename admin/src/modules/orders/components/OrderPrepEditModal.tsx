import React, { useState, useEffect } from "react";
import { X } from "lucide-react";
import { updateOrderProfile } from "../../../shared/api/http";
import { AppSelect } from "../../../shared/components/AppSelect";
import { SafeInput, SafeTextarea } from "../../../shared/components/SafeInput";
import { RemarkField } from "../../../shared/components/RemarkField";
import { toast } from "../../../shared/components/Toast";
import type { OrderPrepItemResponse } from "../../../shared/api/types";
import { resolveMealPeriod } from "../orderPrepPage.helpers";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  activeItem: OrderPrepItemResponse | null;
}

export function OrderPrepEditModal({ isOpen, onClose, onSuccess, activeItem }: Props) {
  const [submittingEdit, setSubmittingEdit] = useState(false);
  const [editForm, setEditForm] = useState({
    mealPeriod: "LUNCH",
    quantity: "1",
    deliveryAddress: "",
    merchantRemark: "",
    priorityCustomer: false,
    status: "PENDING_DISPATCH"
  });

  useEffect(() => {
    if (isOpen && activeItem) {
      setEditForm({
        mealPeriod: resolveMealPeriod(activeItem),
        quantity: String(activeItem.quantity || 1),
        deliveryAddress: activeItem.deliveryAddress || "",
        merchantRemark: activeItem.merchantRemark || "",
        priorityCustomer: Boolean(activeItem.priorityCustomer),
        status: activeItem.status || "PENDING_DISPATCH"
      });
    }
  }, [isOpen, activeItem]);

  async function handleEditSubmit() {
    if (!activeItem || !editForm.mealPeriod || !editForm.deliveryAddress) return;
    if (submittingEdit) return;
    const trimmedAddress = editForm.deliveryAddress.trim();
    setSubmittingEdit(true);
    try {
      await updateOrderProfile(activeItem.id, {
        mealPeriod: editForm.mealPeriod as "LUNCH" | "DINNER",
        quantity: Number(editForm.quantity) || 1,
        deliveryAddress: trimmedAddress,
        merchantRemark: editForm.merchantRemark,
        priorityCustomer: editForm.priorityCustomer,
        status: editForm.status
      });
      onClose();
      onSuccess();
      toast("订单已更新");
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "保存订单失败", "error");
    } finally {
      setSubmittingEdit(false);
    }
  }

  if (!isOpen || !activeItem) return null;

  return (
    <div className="modal-overlay">
      <div className="modal-content">
        <div className="modal-header">
          <span>编辑订单 - {activeItem.customerName}</span>
          <button type="button" className="modal-close" disabled={submittingEdit} onClick={submittingEdit ? undefined : onClose}><X size={20} /></button>
        </div>
        <div className="modal-body">
          <div className="form-group">
            <label className="form-label"><span className="required">*</span>餐次</label>
            <AppSelect
              value={editForm.mealPeriod}
              options={[
                { label: "午餐", value: "LUNCH" },
                { label: "晚餐", value: "DINNER" }
              ]}
              onChange={(val) => setEditForm({ ...editForm, mealPeriod: val as string })}
              style={{ width: "100%" }}
            />
          </div>
          <div className="form-group">
            <label className="form-label"><span className="required">*</span>份数</label>
            <SafeInput className="form-control" type="number" min="1" value={editForm.quantity} onValueChange={(value) => setEditForm({ ...editForm, quantity: value })} />
          </div>
          <div className="form-group">
            <label className="form-label"><span className="required">*</span>订单状态</label>
            <AppSelect
              value={editForm.status}
              options={[
                { label: "待配送", value: "PENDING_DISPATCH" },
                { label: "已送达", value: "DELIVERED" },
                { label: "已取消", value: "CANCELLED" }
              ]}
              onChange={(val) => setEditForm({ ...editForm, status: val as string })}
              style={{ width: "100%" }}
            />
          </div>
          <div className="form-group">
            <label className="form-label"><span className="required">*</span>配送地址</label>
            <SafeTextarea className="form-control" value={editForm.deliveryAddress} onValueChange={(value) => setEditForm({ ...editForm, deliveryAddress: value })} placeholder="填写详细配送地址" />
          </div>
          <RemarkField
            label="商家备注"
            value={editForm.merchantRemark}
            onChange={(value) => setEditForm({ ...editForm, merchantRemark: value })}
            placeholder="例如：少饭、多菜、先送"
            scene="ORDER_REMARK"
            multiline
          />
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label" style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <input 
                type="checkbox" 
                checked={editForm.priorityCustomer} 
                onChange={e => setEditForm({ ...editForm, priorityCustomer: e.target.checked })}
                style={{ width: "auto", margin: 0 }}
              />
              <span>标记为重点客户</span>
            </label>
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-outline" onClick={onClose} disabled={submittingEdit}>取消</button>
          <button className="btn btn-primary" disabled={submittingEdit} onClick={() => handleEditSubmit().catch(() => undefined)}>
            {submittingEdit ? "提交中..." : "保存订单"}
          </button>
        </div>
      </div>
    </div>
  );
}
