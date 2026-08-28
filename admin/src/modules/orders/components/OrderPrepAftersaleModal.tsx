import React, { useState, useEffect } from "react";
import { createOrderAftersale } from "../../../shared/api/http";
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
    reasonText: ""
  });

  useEffect(() => {
    if (isOpen && activeItem) {
      setOrderAftersaleForm({
        reasonText: ""
      });
    }
  }, [isOpen, activeItem]);

  async function handleOrderAftersaleSubmit() {
    if (!activeItem) return;
    const reasonText = orderAftersaleForm.reasonText.trim();
    if (!reasonText) {
      toast("请填写处理说明", "error");
      return;
    }

    setSubmittingOrderAftersale(true);
    try {
      await createOrderAftersale(activeItem.id, {
        type: "REFUND",
        reasonCode: "ADMIN_DIRECT",
        reasonText,
        // 处理说明同时作为商家处理结果，用户端小程序会展示该内容
        remark: reasonText
      });
      toast(`已退款并作废该订单，退回 ${activeItem.quantity} 餐`);
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

        <div className="auth-panel" style={{ borderColor: "rgba(37, 99, 235, 0.3)", background: "rgba(37, 99, 235, 0.04)" }}>
          <div className="auth-panel__grid">
            <div style={{ gridColumn: "1 / -1" }}>
              <strong>退款说明</strong>
              <span>提交后直接退款并作废该订单：退回该订单 {activeItem.quantity} 餐，订单从订单中心移除，退款后请重新下单。</span>
            </div>
          </div>
        </div>

        <div className="form-group" style={{ marginBottom: 0 }}>
          <label className="form-label">处理说明</label>
          <SafeTextarea
            className="form-control"
            value={orderAftersaleForm.reasonText}
            onValueChange={(value) => setOrderAftersaleForm((current) => ({ ...current, reasonText: value }))}
            rows={3}
            placeholder="请填写问题原因与处理结果，将展示给用户查看"
          />
        </div>
      </div>
    </Modal>
  );
}
