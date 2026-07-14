import React, { useState, useEffect } from "react";
import { directRefund, createOrderAftersale } from "../../../shared/api/http";
import { SafeTextarea } from "../../../shared/components/SafeInput";
import { toast } from "../../../shared/components/Toast";
import type { OrderPrepItemResponse } from "../../../shared/api/types";
import { mealPeriodLabel, resolveOrderDisplayStatus, resolveOrderDisplayStatusLabel } from "../orderPrepPage.helpers";
import { Modal } from "../../../shared/components/Modal";
import { Button } from "../../../shared/components/Button";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  activeItem: OrderPrepItemResponse | null;
}

export function OrderPrepAftersaleModal({ isOpen, onClose, onSuccess, activeItem }: Props) {
  const [submittingOrderAftersale, setSubmittingOrderAftersale] = useState(false);
  const [orderAftersaleForm, setOrderAftersaleForm] = useState({
    intent: "DIRECT_REFUND",
    reasonText: "",
    remark: ""
  });

  useEffect(() => {
    if (isOpen && activeItem) {
      setOrderAftersaleForm({
        intent: "DIRECT_REFUND",
        reasonText: `订单 #${activeItem.id} 售后处理`,
        remark: ""
      });
    }
  }, [isOpen, activeItem]);

  async function handleOrderAftersaleSubmit() {
    if (!activeItem) return;
    const reasonText = orderAftersaleForm.reasonText.trim();
    const remark = orderAftersaleForm.remark.trim();
    if (!reasonText) {
      toast("请填写售后原因", "error");
      return;
    }

    setSubmittingOrderAftersale(true);
    try {
      if (orderAftersaleForm.intent === "DIRECT_REFUND") {
        await directRefund(activeItem.id, {
          reasonCode: "ADMIN_DIRECT_REFUND",
          reasonText
        });
        toast("订单已直接退款");
      } else {
        await createOrderAftersale(activeItem.id, {
          type: "COMPENSATION",
          reasonCode: orderAftersaleForm.intent === "COMPENSATION" ? "ADMIN_COMPENSATION" : "ADMIN_EXCEPTION",
          reasonText,
          remark: remark || (orderAftersaleForm.intent === "REGISTER_ONLY" ? "已登记异常，等待后续处理" : "请前往售后台账继续处理")
        });
        toast(orderAftersaleForm.intent === "REGISTER_ONLY" ? "异常已登记到售后台账" : "补偿售后已创建");
      }
      onClose();
      onSuccess();
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "售后处理失败", "error");
    } finally {
      setSubmittingOrderAftersale(false);
    }
  }

  if (!isOpen || !activeItem) return null;

  return (
    <Modal
      open={isOpen}
      onClose={onClose}
      title={`售后处理 - ${activeItem.customerName}`}
      width={680}
      disableOverlayClose={submittingOrderAftersale}
      closeDisabled={submittingOrderAftersale}
      footer={
        <>
          <Button variant="outline" onClick={onClose} disabled={submittingOrderAftersale}>取消</Button>
          <Button
            variant="primary"
            onClick={() => handleOrderAftersaleSubmit().catch(() => undefined)}
            isLoading={submittingOrderAftersale}
          >
            确认处理
          </Button>
        </>
      }
    >
      <div style={{ display: "grid", gap: "18px" }}>
        <div className="auth-panel">
          <div className="auth-panel__title">订单信息</div>
          <div className="auth-panel__grid">
            <div><strong>订单</strong><span>#{activeItem.id}</span></div>
            <div><strong>客户</strong><span>{activeItem.customerName} / {activeItem.customerPhone}</span></div>
            <div><strong>出餐 / 配送</strong><span>{mealPeriodLabel(activeItem.mealPeriod)} / {mealPeriodLabel(activeItem.deliveryMealPeriod)} ×{activeItem.quantity}</span></div>
            <div><strong>当前状态</strong><span>{activeItem.displayStatusLabel || resolveOrderDisplayStatusLabel(resolveOrderDisplayStatus(activeItem))}</span></div>
          </div>
        </div>

        <div className="form-group" style={{ marginBottom: 0 }}>
          <label className="form-label">处理意图</label>
          <div className="action-chip-row">
            {[
              { key: "DIRECT_REFUND", label: "直接退款" },
              { key: "COMPENSATION", label: "登记补偿" },
              { key: "REGISTER_ONLY", label: "登记异常" }
            ].map((option) => (
              <button
                key={option.key}
                type="button"
                className={`action-chip ${orderAftersaleForm.intent === option.key ? "active" : ""}`}
                onClick={() => setOrderAftersaleForm((current) => ({ ...current, intent: option.key }))}
              >
                {option.label}
              </button>
            ))}
          </div>
        </div>

        <div className="form-group" style={{ marginBottom: 0 }}>
          <label className="form-label">售后原因</label>
          <SafeTextarea
            className="form-control"
            value={orderAftersaleForm.reasonText}
            onValueChange={(value) => setOrderAftersaleForm((current) => ({ ...current, reasonText: value }))}
            rows={3}
            placeholder="请填写退款、补偿或异常原因"
          />
        </div>

        <div className="form-group" style={{ marginBottom: 0 }}>
          <label className="form-label">商家备注</label>
          <SafeTextarea
            className="form-control"
            value={orderAftersaleForm.remark}
            onValueChange={(value) => setOrderAftersaleForm((current) => ({ ...current, remark: value }))}
            rows={3}
            placeholder="补充处理说明、异常细节或后续跟进备注"
          />
        </div>
      </div>
    </Modal>
  );
}
