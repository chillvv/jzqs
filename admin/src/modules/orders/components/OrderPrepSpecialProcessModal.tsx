import React, { useState, useEffect } from "react";
import { X } from "lucide-react";
import { applyOrderSpecialDispatch, clearOrderSpecialDispatch } from "../../../shared/api/http";
import { AppSelect } from "../../../shared/components/AppSelect";
import { toast } from "../../../shared/components/Toast";
import type { OrderPrepItemResponse } from "../../../shared/api/types";
import { resolveOrderDisplayStatus, resolveOrderDisplayStatusLabel, isCrossMealDelivery, mealPeriodLabel } from "../orderPrepPage.helpers";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  activeItem: OrderPrepItemResponse | null;
}

export function OrderPrepSpecialProcessModal({ isOpen, onClose, onSuccess, activeItem }: Props) {
  const [submittingSpecialProcess, setSubmittingSpecialProcess] = useState(false);
  const [specialProcessForm, setSpecialProcessForm] = useState<{
    deliveryMealPeriod: "LUNCH" | "DINNER";
  }>({
    deliveryMealPeriod: "LUNCH"
  });

  useEffect(() => {
    if (isOpen && activeItem) {
      setSpecialProcessForm({
        deliveryMealPeriod: activeItem.deliveryMealPeriod === "DINNER" ? "DINNER" : "LUNCH"
      });
    }
  }, [isOpen, activeItem]);

  async function handleSpecialProcessSubmit() {
    if (!activeItem || submittingSpecialProcess) {
      return;
    }
    setSubmittingSpecialProcess(true);
    try {
      await applyOrderSpecialDispatch(activeItem.id, specialProcessForm.deliveryMealPeriod);
      onClose();
      onSuccess();
      toast("特殊处理已生效");
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "特殊处理失败", "error");
    } finally {
      setSubmittingSpecialProcess(false);
    }
  }

  async function handleSpecialProcessReset() {
    if (!activeItem || submittingSpecialProcess) {
      return;
    }
    setSubmittingSpecialProcess(true);
    try {
      await clearOrderSpecialDispatch(activeItem.id);
      onClose();
      onSuccess();
      toast("已恢复原配送方式");
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "取消特殊处理失败", "error");
    } finally {
      setSubmittingSpecialProcess(false);
    }
  }

  if (!isOpen || !activeItem) return null;

  return (
    <div className="modal-overlay">
      <div className="modal-content">
        <div className="modal-header">
          <span>特殊处理 - {activeItem.customerName}</span>
          <button
            type="button"
            className="modal-close"
            disabled={submittingSpecialProcess}
            onClick={submittingSpecialProcess ? undefined : onClose}
          >
            <X size={20} />
          </button>
        </div>
        <div className="modal-body" style={{ display: "grid", gap: "16px" }}>
          <div className="auth-panel">
            <div className="auth-panel__title">当前订单</div>
            <div className="auth-panel__grid">
              <div><strong>订单</strong><span>#{activeItem.id}</span></div>
              <div><strong>出餐餐次</strong><span>{mealPeriodLabel(activeItem.mealPeriod)}</span></div>
              <div><strong>当前配送</strong><span>{mealPeriodLabel(activeItem.deliveryMealPeriod)}</span></div>
              <div><strong>状态</strong><span>{activeItem.displayStatusLabel || resolveOrderDisplayStatusLabel(resolveOrderDisplayStatus(activeItem))}</span></div>
            </div>
          </div>
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label">改变配送时间</label>
            <AppSelect
              value={specialProcessForm.deliveryMealPeriod}
              options={[
                { label: "按午餐配送", value: "LUNCH" },
                { label: "按晚餐配送", value: "DINNER" }
              ]}
              onChange={(val) => setSpecialProcessForm({ deliveryMealPeriod: val as "LUNCH" | "DINNER" })}
              style={{ width: "100%" }}
            />
            <div style={{ color: "var(--text-sub)", fontSize: "12px", marginTop: "8px" }}>
              修改后，这单仍按原餐次出餐，但只会进入目标配送餐次的待分配和骑手链路。
            </div>
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-outline" onClick={onClose} disabled={submittingSpecialProcess}>取消</button>
          {isCrossMealDelivery(activeItem.mealPeriod, activeItem.deliveryMealPeriod) ? (
            <button className="btn-delete" disabled={submittingSpecialProcess} onClick={() => handleSpecialProcessReset().catch(() => undefined)}>
              {submittingSpecialProcess ? "处理中..." : "取消特殊处理"}
            </button>
          ) : null}
          <button className="btn btn-primary" disabled={submittingSpecialProcess} onClick={() => handleSpecialProcessSubmit().catch(() => undefined)}>
            {submittingSpecialProcess ? "提交中..." : "确认处理"}
          </button>
        </div>
      </div>
    </div>
  );
}
