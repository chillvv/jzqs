import React from "react";
import { Copy } from "lucide-react";
import { AdminDialog } from "../../../shared/components/AdminDialog";
import { Checkbox } from "../../../shared/components/ui/checkbox";
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
  // 记「被取消勾选」的订单而不是「已勾选」：默认全选，新进来的订单自动处于选中态，
  // 商家只需取消少数不需要打印的标签，比反过来逐个勾更省事。
  const [excludedIds, setExcludedIds] = React.useState<Set<number>>(() => new Set());

  // 每次重新打开都回到全选初始态，避免上一次的取消勾选残留导致漏打标签
  React.useEffect(() => {
    if (open) {
      setExcludedIds(new Set());
    }
  }, [open]);

  const selectedItems = React.useMemo(
    () => items.filter((item) => !excludedIds.has(item.orderId)),
    [items, excludedIds]
  );
  const selectedCount = selectedItems.length;
  const allSelected = items.length > 0 && selectedCount === items.length;
  const noneSelected = selectedCount === 0;
  const selectAllState: boolean | "indeterminate" = allSelected
    ? true
    : noneSelected
      ? false
      : "indeterminate";

  const toggleAll = () => {
    setExcludedIds(allSelected ? new Set(items.map((item) => item.orderId)) : new Set());
  };

  const toggleOne = (orderId: number) => {
    setExcludedIds((prev) => {
      const next = new Set(prev);
      if (next.has(orderId)) {
        next.delete(orderId);
      } else {
        next.add(orderId);
      }
      return next;
    });
  };

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
              {excludedIds.size > 0 && (
                <span style={{ marginLeft: "10px", fontSize: "13px", fontWeight: 700, color: "#1D4ED8" }}>
                  已取消 {excludedIds.size} 条
                </span>
              )}
            </div>
            <div style={{ color: "var(--text-sub)", fontSize: "13px", lineHeight: 1.6 }}>
              已按当前日期与{mealPeriodLabel(mealPeriodFilter)}筛选，仅保留有备注订单。勾选需要的标签后复制，可直接粘贴到文本标签打印机。
            </div>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: "12px", whiteSpace: "nowrap" }}>
            <div
              onClick={toggleAll}
              style={{
                display: "flex",
                alignItems: "center",
                gap: "8px",
                cursor: "pointer",
                userSelect: "none",
                fontSize: "13px",
                fontWeight: 700,
                color: "var(--text-sub)",
                padding: "6px 10px",
                borderRadius: "10px",
                background: "rgba(255, 255, 255, 0.72)",
                border: "1px solid rgba(15, 23, 42, 0.08)"
              }}
            >
              {/* 阻止冒泡：否则点复选框会同时触发 Checkbox 与外层 onClick，等于没切换 */}
              <span onClick={(event) => event.stopPropagation()} style={{ display: "inline-flex" }}>
                <Checkbox
                  checked={selectAllState}
                  onCheckedChange={toggleAll}
                  aria-label={allSelected ? "取消全选" : "全选"}
                  className="h-[17px] w-[17px]"
                />
              </span>
              <span>{allSelected ? "取消全选" : "全选"}</span>
            </div>
            <button
              className="btn btn-primary"
              disabled={noneSelected}
              onClick={() =>
                onCopy(
                  buildRemarkLabelBatchText(selectedItems),
                  allSelected ? "已复制全部备注标签" : `已复制选中的 ${selectedCount} 条备注标签`
                )
              }
              style={{ whiteSpace: "nowrap" }}
            >
              <Copy size={16} />
              {allSelected ? "一键复制全部" : `复制选中 ${selectedCount} 条`}
            </button>
          </div>
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
              const checked = !excludedIds.has(item.orderId);
              return (
                <section
                  key={item.orderId}
                  style={{
                    border: checked
                      ? "1px solid rgba(15, 23, 42, 0.08)"
                      : "1px dashed rgba(15, 23, 42, 0.18)",
                    borderRadius: "18px",
                    background: checked
                      ? "linear-gradient(180deg, #FFFFFF 0%, #F8FAFC 100%)"
                      : "#F1F5F9",
                    boxShadow: checked ? "0 10px 30px rgba(15, 23, 42, 0.05)" : "none",
                    padding: "18px",
                    opacity: checked ? 1 : 0.62,
                    transition: "opacity 0.15s ease, background 0.15s ease"
                  }}
                >
                  <div style={{ display: "flex", justifyContent: "space-between", gap: "12px", alignItems: "flex-start", marginBottom: "14px" }}>
                    <div style={{ display: "flex", alignItems: "flex-start", gap: "12px" }}>
                      {/* 只有复选框可点：整行可点容易误触，漏打一张标签就是顾客收不到备注 */}
                      <span style={{ display: "inline-flex", paddingTop: "5px" }}>
                        <Checkbox
                          checked={checked}
                          onCheckedChange={() => toggleOne(item.orderId)}
                          aria-label={`${checked ? "取消选择" : "选择"} ${item.customerName}`}
                          className="h-[17px] w-[17px]"
                        />
                      </span>
                      <div style={{ display: "grid", gap: "6px" }}>
                        <div style={{ display: "flex", alignItems: "center", gap: "10px", flexWrap: "wrap" }}>
                          <span
                            style={{
                              minWidth: "28px",
                              height: "28px",
                              padding: "0 8px",
                              borderRadius: "999px",
                              background: checked ? "rgba(37, 99, 235, 0.12)" : "rgba(100, 116, 139, 0.14)",
                              color: checked ? "#1D4ED8" : "#64748B",
                              fontWeight: 700,
                              fontSize: "12px",
                              display: "inline-flex",
                              alignItems: "center",
                              justifyContent: "center"
                            }}
                          >
                            {index + 1}
                          </span>
                          <span
                            style={{
                              fontWeight: 800,
                              fontSize: "18px",
                              color: checked ? "var(--text-main)" : "var(--text-sub)",
                              textDecoration: checked ? "none" : "line-through"
                            }}
                          >
                            {item.customerName}
                          </span>
                        </div>
                        <div style={{ color: "var(--text-sub)", fontSize: "12px" }}>
                          订单 #{item.orderId}
                        </div>
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
