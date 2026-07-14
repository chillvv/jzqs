import React from "react";
import { Copy } from "lucide-react";
import { AdminDialog } from "../../../shared/components/AdminDialog";
import {
  buildOrderRemarkLabelItems,
  buildRemarkLabelBatchText,
  buildRemarkLabelText,
  mealPeriodLabel,
  type OrderPrepMealPeriodFilter
} from "../orderPrepPage.helpers";

type RemarkLabelItem = ReturnType<typeof buildOrderRemarkLabelItems>[number];

interface OrderRemarkLabelDialogProps {
  open: boolean;
  mealPeriodFilter: OrderPrepMealPeriodFilter;
  items: RemarkLabelItem[];
  onClose: () => void;
  onCopy: (text: string, successMessage: string) => void;
}

export function OrderRemarkLabelDialog({
  open,
  mealPeriodFilter,
  items,
  onClose,
  onCopy
}: OrderRemarkLabelDialogProps) {
  return (
    <AdminDialog
      open={open}
      title={`${mealPeriodLabel(mealPeriodFilter)}备注标签`}
      width={760}
      onClose={onClose}
      footer={null}
    >
      <div style={{ display: "grid", gap: "16px" }}>
        <div
          style={{
            display: "flex",
            justifyContent: "space-between",
            alignItems: "center",
            gap: "16px",
            padding: "18px 20px",
            borderRadius: "18px",
            border: "1px solid rgba(15, 23, 42, 0.08)",
            background: "linear-gradient(135deg, rgba(249, 250, 251, 0.96) 0%, rgba(239, 246, 255, 0.92) 100%)"
          }}
        >
          <div style={{ display: "grid", gap: "6px" }}>
            <div style={{ fontSize: "20px", fontWeight: 800, color: "var(--text-main)" }}>
              共 {items.length} 条
            </div>
            <div style={{ color: "var(--text-sub)", fontSize: "13px", lineHeight: 1.6 }}>
              已按当前日期与{mealPeriodLabel(mealPeriodFilter)}筛选，仅保留有备注订单，复制后可直接粘贴到文本标签打印机。
            </div>
          </div>
          <button
            className="btn btn-primary"
            onClick={() => onCopy(buildRemarkLabelBatchText(items), "已复制全部备注标签")}
            style={{ whiteSpace: "nowrap" }}
          >
            <Copy size={16} />
            一键复制全部
          </button>
        </div>

        {items.length === 0 ? (
          <div
            style={{
              padding: "32px 20px",
              borderRadius: "18px",
              border: "1px dashed var(--border-color)",
              color: "var(--text-sub)",
              textAlign: "center",
              background: "#F8FAFC"
            }}
          >
            当前餐次没有需要打印标签的备注订单
          </div>
        ) : (
          <div style={{ display: "grid", gap: "14px", maxHeight: "60vh", overflowY: "auto", paddingRight: "4px" }}>
            {items.map((item, index) => {
              const previewText = buildRemarkLabelText(item);
              return (
                <section
                  key={item.orderId}
                  style={{
                    border: "1px solid rgba(15, 23, 42, 0.08)",
                    borderRadius: "18px",
                    background: "linear-gradient(180deg, #FFFFFF 0%, #F8FAFC 100%)",
                    boxShadow: "0 10px 30px rgba(15, 23, 42, 0.05)",
                    padding: "18px"
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", gap: "12px", alignItems: "flex-start", marginBottom: "14px" }}>
                    <div style={{ display: "grid", gap: "6px" }}>
                      <div style={{ display: "flex", alignItems: "center", gap: "10px", flexWrap: "wrap" }}>
                        <span
                          style={{
                            minWidth: "28px",
                            height: "28px",
                            padding: "0 8px",
                            borderRadius: "999px",
                            background: "rgba(37, 99, 235, 0.12)",
                            color: "#1D4ED8",
                            fontWeight: 700,
                            fontSize: "12px",
                            display: "inline-flex",
                            alignItems: "center",
                            justifyContent: "center"
                          }}
                        >
                          {index + 1}
                        </span>
                        <span style={{ fontWeight: 800, fontSize: "18px", color: "var(--text-main)" }}>{item.customerName}</span>
                      </div>
                      <div style={{ color: "var(--text-sub)", fontSize: "12px" }}>
                        订单 #{item.orderId}
                      </div>
                    </div>
                    <button
                      className="btn btn-outline btn-sm"
                      onClick={() => onCopy(previewText, `已复制 ${item.customerName} 的标签内容`)}
                    >
                      <Copy size={14} />
                      复制
                    </button>
                  </div>

                  <div style={{ display: "grid", gap: "10px" }}>
                    {[
                      { label: "电话", value: item.customerPhone },
                      { label: "地址", value: item.deliveryAddress },
                      { label: "备注", value: item.remarkLine }
                    ].map((row) => (
                      <div
                        key={row.label}
                        style={{
                          display: "grid",
                          gridTemplateColumns: "72px minmax(0, 1fr)",
                          gap: "12px",
                          alignItems: "start",
                          padding: "10px 12px",
                          borderRadius: "12px",
                          background: row.label === "备注" ? "rgba(255, 247, 237, 0.85)" : "rgba(248, 250, 252, 0.95)"
                        }}
                      >
                        <span style={{ color: "var(--text-sub)", fontSize: "12px", fontWeight: 700, letterSpacing: "0.04em" }}>{row.label}</span>
                        <span style={{ color: "var(--text-main)", fontSize: "14px", lineHeight: 1.7, wordBreak: "break-word", whiteSpace: "pre-wrap" }}>{row.value}</span>
                      </div>
                    ))}
                  </div>

                  <div
                    style={{
                      marginTop: "14px",
                      padding: "14px 16px",
                      borderRadius: "14px",
                      background: "#0F172A",
                      color: "#E2E8F0"
                    }}
                  >
                    <div style={{ fontSize: "12px", fontWeight: 700, letterSpacing: "0.04em", color: "#93C5FD", marginBottom: "8px" }}>
                      复制预览
                    </div>
                    <pre
                      style={{
                        margin: 0,
                        whiteSpace: "pre-wrap",
                        wordBreak: "break-word",
                        fontSize: "13px",
                        lineHeight: 1.7,
                        fontFamily: "ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, Liberation Mono, monospace"
                      }}
                    >
                      {previewText}
                    </pre>
                  </div>
                </section>
              );
            })}
          </div>
        )}
      </div>
    </AdminDialog>
  );
}
