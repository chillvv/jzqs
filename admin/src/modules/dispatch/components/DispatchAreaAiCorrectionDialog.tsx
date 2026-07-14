import React, { useEffect, useMemo, useRef, useState } from "react";
import { Bot, Sparkles } from "lucide-react";
import type { DispatchAreaOrderItemResponse, DispatchAreaAiCorrectionPreviewResponse } from "../../../shared/api/types";
import { confirmAreaAiCorrection, previewAreaAiCorrection } from "../../../shared/api/http";
import { AdminDialog } from "../../../shared/components/AdminDialog";
import { SafeInput, SafeTextarea } from "../../../shared/components/SafeInput";
import { toast } from "../../../shared/components/Toast";

type Props = {
  open: boolean;
  areaCode: string;
  originalOrders: DispatchAreaOrderItemResponse[];
  draftOrders?: DispatchAreaOrderItemResponse[];
  onClose: () => void;
  onPreviewApplied?: (finalOrderIds: number[]) => void;
  onRollbackPreview?: (orderIds: number[]) => void;
  onConfirmed: () => Promise<void> | void;
};

function getErrorMessage(error: unknown, fallback: string) {
  if (typeof error === "object" && error !== null) {
    const errorLike = error as { response?: { data?: { message?: string } }; message?: string };
    return errorLike.response?.data?.message || errorLike.message || fallback;
  }
  return typeof error === "string" ? error : fallback;
}

export function DispatchAreaAiCorrectionDialog({
  open,
  areaCode,
  originalOrders,
  draftOrders,
  onClose,
  onPreviewApplied,
  onRollbackPreview,
  onConfirmed
}: Props) {
  const [merchantInstruction, setMerchantInstruction] = useState("");
  const [merchantReasonSummary, setMerchantReasonSummary] = useState("");
  const [preview, setPreview] = useState<DispatchAreaAiCorrectionPreviewResponse | null>(null);
  const [previewSubmitting, setPreviewSubmitting] = useState(false);
  const [confirmSubmitting, setConfirmSubmitting] = useState(false);
  const initialDraftOrderIdsRef = useRef<number[]>([]);

  const merchantOrders = useMemo(
    () => (draftOrders?.length ? draftOrders : originalOrders),
    [draftOrders, originalOrders]
  );
  const originalOrderIds = useMemo(() => originalOrders.map((item) => item.orderId), [originalOrders]);
  const merchantOrderIds = useMemo(() => merchantOrders.map((item) => item.orderId), [merchantOrders]);
  const inputAddresses = useMemo(() => merchantOrders.map((item) => item.deliveryAddress || ""), [merchantOrders]);
  const hasDraftReorder = useMemo(() => merchantOrderIds.some((orderId, index) => orderId !== originalOrderIds[index]), [merchantOrderIds, originalOrderIds]);
  const previewOrderMap = useMemo(() => {
    const map = new Map<number, DispatchAreaOrderItemResponse>();
    originalOrders.forEach((item) => map.set(item.orderId, item));
    return map;
  }, [originalOrders]);

  const previewOrders = useMemo(() => {
    if (!preview?.finalOrderIds?.length) {
      return merchantOrders;
    }
    return preview.finalOrderIds
      .map((orderId) => previewOrderMap.get(orderId))
      .filter((item): item is DispatchAreaOrderItemResponse => Boolean(item));
  }, [merchantOrders, preview, previewOrderMap]);

  useEffect(() => {
    if (open) {
      initialDraftOrderIdsRef.current = merchantOrderIds;
    }
  }, [open]);

  async function handlePreview() {
    if (!areaCode || !originalOrderIds.length) {
      toast("当前区域暂无可纠偏订单", "error");
      return;
    }
    setPreviewSubmitting(true);
    try {
      const response = await previewAreaAiCorrection(areaCode, {
        originalOrderIds,
        merchantOrderIds,
        inputAddresses,
        merchantInstruction,
        merchantReasonSummary
      });
      setPreview(response);
      if (response.finalOrderIds.length) {
        onPreviewApplied?.(response.finalOrderIds);
      }
    } catch (error) {
      toast(getErrorMessage(error, "AI 纠偏预览失败"), "error");
    } finally {
      setPreviewSubmitting(false);
    }
  }

  async function handleConfirm() {
    if (!preview) {
      toast("请先生成纠偏预览", "error");
      return;
    }
    setConfirmSubmitting(true);
    try {
      await confirmAreaAiCorrection(areaCode, {
        correctionId: preview.correctionId,
        finalOrderIds: preview.finalOrderIds
      });
      await onConfirmed();
      toast("区域 AI 纠偏已永久生效");
      onClose();
      setPreview(null);
      setMerchantInstruction("");
      setMerchantReasonSummary("");
    } catch (error) {
      toast(getErrorMessage(error, "确认区域 AI 纠偏失败"), "error");
    } finally {
      setConfirmSubmitting(false);
    }
  }

  function handleRollbackPreview() {
    if (!initialDraftOrderIdsRef.current.length) {
      return;
    }
    onRollbackPreview?.(initialDraftOrderIdsRef.current);
    setPreview(null);
  }

  return (
    <AdminDialog
      open={open}
      title="AI纠正"
      description={areaCode ? `${areaCode} · 可结合拖拽后的当前顺序和文字纠正，让 AI 给出预览顺序` : undefined}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-outline" disabled={previewSubmitting} onClick={handlePreview}>
            <Sparkles size={14} /> {previewSubmitting ? "重排中..." : "按我的意思重排"}
          </button>
          <button className="btn btn-outline" disabled={!preview} onClick={handleRollbackPreview}>
            回退预览
          </button>
          <button className="btn btn-primary" disabled={!preview || confirmSubmitting} onClick={handleConfirm}>
            <Bot size={14} /> {confirmSubmitting ? "生效中..." : "确认永久生效"}
          </button>
        </>
      }
    >
      <div style={{ display: "grid", gap: 16 }}>
        <label className="admin-field">
          <span className="admin-field-label">纠正说明</span>
          <SafeTextarea
            className="admin-input"
            rows={4}
            value={merchantInstruction}
            onValueChange={setMerchantInstruction}
            placeholder="例如：午餐高峰先写字楼再收住宅，同楼栋尽量连送。"
          />
        </label>
        <label className="admin-field">
          <span className="admin-field-label">记忆摘要</span>
          <SafeInput
            className="admin-input"
            value={merchantReasonSummary}
            onValueChange={setMerchantReasonSummary}
            placeholder="例如：A 区午餐高峰先写字楼后住宅"
          />
        </label>

        <div style={{ padding: 12, background: "var(--bg-sub)", borderRadius: 8 }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "center", marginBottom: 8 }}>
            <div style={{ fontWeight: 600, fontSize: 14 }}>{hasDraftReorder ? "商家当前顺序" : "当前顺序"}</div>
            <span className={hasDraftReorder ? "tag tag-blue" : "tag tag-gray"}>
              {hasDraftReorder ? "已带入拖拽顺序" : "按区域现有顺序"}
            </span>
          </div>
          {hasDraftReorder ? (
            <div style={{ fontSize: 12, color: "var(--text-sub)", marginBottom: 8 }}>
              AI 会同时参考区域原始顺序和你刚刚拖拽后的商家顺序，避免文本纠正和手动改序互相覆盖。
            </div>
          ) : null}
          <div style={{ display: "grid", gap: 6 }}>
            {merchantOrders.map((item, index) => (
              <div key={item.orderId} style={{ fontSize: 13, color: "var(--text-body)" }}>
                <span style={{ color: "var(--text-sub)", marginRight: 4 }}>{index + 1}.</span> 
                <span style={{ fontWeight: 500, marginRight: 4 }}>#{item.orderId}</span> 
                {item.customerName} <span style={{ color: "var(--text-sub)", margin: "0 4px" }}>·</span> {item.deliveryAddress}
              </div>
            ))}
          </div>
        </div>

        {preview ? (
          <div style={{ padding: 12, background: "rgba(59, 130, 246, 0.05)", borderRadius: 8 }}>
            <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "center", marginBottom: 8 }}>
              <div style={{ fontWeight: 600, fontSize: 14, color: "var(--primary-color)" }}>AI 重排预览</div>
              <span className={preview.replanStatus === "SUCCESS" ? "tag tag-green" : "tag tag-amber"}>{preview.replanStatus}</span>
            </div>
            <div style={{ fontSize: 13, color: "var(--text-body)", marginBottom: 12 }}>
              {preview.aiInterpretationSummary || "AI 已生成新的候选顺序。"}
            </div>
            {preview.replanError ? (
              <div style={{ fontSize: 12, color: "var(--warning-text)", marginBottom: 12 }}>{preview.replanError}</div>
            ) : null}
            <div style={{ display: "grid", gap: 6 }}>
              {previewOrders.map((item, index) => (
                <div key={item.orderId} style={{ fontSize: 13, color: "var(--text-body)" }}>
                  <span style={{ color: "var(--text-sub)", marginRight: 4 }}>{index + 1}.</span> 
                  <span style={{ fontWeight: 500, marginRight: 4 }}>#{item.orderId}</span> 
                  {item.customerName} <span style={{ color: "var(--text-sub)", margin: "0 4px" }}>·</span> {item.deliveryAddress}
                </div>
              ))}
            </div>
            {preview.memoryCandidates.length ? (
              <div style={{ marginTop: 16, paddingTop: 12, borderTop: "1px dashed rgba(59, 130, 246, 0.2)", display: "grid", gap: 8 }}>
                <div style={{ fontWeight: 600, fontSize: 13, color: "var(--primary-color)", display: "flex", alignItems: "center", gap: 4 }}>
                  <Sparkles size={14} /> 候选长期记忆
                </div>
                {preview.memoryCandidates.map((item, index) => (
                  <div key={`${item.title}-${index}`} style={{ fontSize: 12, color: "var(--text-sub)", background: "#fff", padding: "8px 12px", borderRadius: 6 }}>
                    <div style={{ fontWeight: 500, color: "var(--text-title)", marginBottom: 4 }}>{item.title}</div>
                    {item.summary}
                  </div>
                ))}
              </div>
            ) : null}
          </div>
        ) : null}
      </div>
    </AdminDialog>
  );
}
