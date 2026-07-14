import React, { useState, useMemo } from "react";
import { X } from "lucide-react";
import { assignDispatch } from "../../../shared/api/http";
import { AppSelect } from "../../../shared/components/AppSelect";
import { toast } from "../../../shared/components/Toast";
import type { OrderPrepItemResponse, DispatchManagedRiderResponse, DispatchAreaBindingResponse } from "../../../shared/api/types";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  activeItem: OrderPrepItemResponse | null;
  assignRiders: DispatchManagedRiderResponse[];
  assignAreaBindings: DispatchAreaBindingResponse[];
}

export function OrderPrepAssignModal({ isOpen, onClose, onSuccess, activeItem, assignRiders, assignAreaBindings }: Props) {
  const [assignForm, setAssignForm] = useState({ riderName: "", areaCode: "" });
  const [submittingAssign, setSubmittingAssign] = useState(false);

  const assignRiderOptions = useMemo(
    () => assignRiders
      .filter((r) => r.authStatus === "ACTIVE")
      .map((r) => ({ label: r.riderName, value: r.riderName })),
    [assignRiders]
  );

  const assignAreaOptions = useMemo(
    () => Array.from(new Set(assignAreaBindings.map((b) => b.areaCode)))
      .sort((a, b) => a.localeCompare(b, "zh-CN"))
      .map((areaCode) => ({ label: areaCode, value: areaCode })),
    [assignAreaBindings]
  );

  async function handleAssignSubmit() {
    if (!activeItem || !assignForm.riderName || !assignForm.areaCode) return;
    if (submittingAssign) return;
    setSubmittingAssign(true);
    try {
      await assignDispatch(activeItem.id, assignForm.riderName, assignForm.areaCode);
      onClose();
      onSuccess();
      toast("骑手分配成功");
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "分配骑手失败", "error");
    } finally {
      setSubmittingAssign(false);
    }
  }

  if (!isOpen || !activeItem) return null;

  return (
    <div className="modal-overlay">
      <div className="modal-content">
        <div className="modal-header">
          <span>分配骑手 - {activeItem.customerName}</span>
          <button type="button" className="modal-close" disabled={submittingAssign} onClick={submittingAssign ? undefined : onClose}><X size={20} /></button>
        </div>
        <div className="modal-body">
          <div className="form-group">
            <label className="form-label"><span className="required">*</span>配送员名称</label>
            <AppSelect value={assignForm.riderName} options={assignRiderOptions} onChange={(val) => setAssignForm({...assignForm, riderName: val as string})} placeholder="选择骑手" showSearch style={{ width: "100%" }} />
          </div>
          <div className="form-group">
            <label className="form-label"><span className="required">*</span>配送区域</label>
            <AppSelect value={assignForm.areaCode} options={assignAreaOptions} onChange={(val) => setAssignForm({...assignForm, areaCode: val as string})} placeholder="选择区域" showSearch style={{ width: "100%" }} />
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-outline" onClick={onClose} disabled={submittingAssign}>取消</button>
          <button className="btn btn-primary" disabled={submittingAssign} onClick={() => handleAssignSubmit().catch(() => undefined)}>
            {submittingAssign ? "提交中..." : "确认分配"}
          </button>
        </div>
      </div>
    </div>
  );
}
