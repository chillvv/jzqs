import React, { useState, useEffect } from "react";
import { X, AlertTriangle } from "lucide-react";
import { fetchSubscriptionPreview, bulkImportSubscription, checkSubscriptionPreview } from "../../../shared/api/http";
import { SafeInput } from "../../../shared/components/SafeInput";
import { toast } from "../../../shared/components/Toast";
import type { SubscriptionPreviewItem, SubscriptionPreviewCheckResponse } from "../../../shared/api/types";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  filterDate: string;
  subscriptionNoteSuggestions: string[];
}

export function OrderPrepSubscriptionPreviewModal({ isOpen, onClose, onSuccess, filterDate, subscriptionNoteSuggestions }: Props) {
  const [isSubmittingImport, setIsSubmittingImport] = useState(false);
  const [previewItems, setPreviewItems] = useState<SubscriptionPreviewItem[]>([]);
  const [previewCheckResult, setPreviewCheckResult] = useState<SubscriptionPreviewCheckResponse | null>(null);
  const [viewState, setViewState] = useState<"LOADING" | "CHECK" | "PREVIEW">("LOADING");

  useEffect(() => {
    if (isOpen) {
      setViewState("LOADING");
      checkSubscriptionPreview(filterDate)
        .then((checkResult) => {
          setPreviewCheckResult(checkResult);
          if (checkResult.insufficientCount > 0) {
            setViewState("CHECK");
          } else {
            loadPreviewItems(false);
          }
        })
        .catch((err) => {
          toast(err?.response?.data?.message || err?.message || "获取包月预览列表失败", "error");
          onClose();
        });
    }
  }, [isOpen, filterDate]);

  async function loadPreviewItems(skipInsufficient: boolean) {
    try {
      const data = await fetchSubscriptionPreview(filterDate);
      if (skipInsufficient) {
        setPreviewItems(data.filter(item => item.hasBalance).map(item => ({ ...item, selected: true })));
      } else {
        setPreviewItems(data.map(item => ({ ...item, selected: item.hasBalance })));
      }
      setViewState("PREVIEW");
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "获取包月预览列表失败", "error");
      onClose();
    }
  }

  async function handleConfirmBulkImport() {
    if (isSubmittingImport) return;
    const selectedItems = previewItems.filter(i => i.selected && i.hasBalance);
    if (selectedItems.length === 0) {
      toast("未选择任何有效的订单", "error");
      return;
    }
    setIsSubmittingImport(true);
    try {
      const payload = selectedItems.map(item => ({
        customerId: item.customerId,
        mealPeriod: item.mealPeriod,
        addressId: item.addressId,
        merchantRemark: item.merchantRemark || ""
      }));
      const result = await bulkImportSubscription(filterDate, payload);
      toast(`成功生成包月订单 ${result.successCount} 份`);
      onClose();
      onSuccess();
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "生成失败，请重试", "error");
    } finally {
      setIsSubmittingImport(false);
    }
  }

  const handleTogglePreviewItem = (index: number) => {
    const newItems = [...previewItems];
    if (newItems[index].hasBalance) {
      newItems[index].selected = !newItems[index].selected;
      setPreviewItems(newItems);
    }
  };

  const handleUpdatePreviewNote = (index: number, val: string) => {
    const newItems = [...previewItems];
    newItems[index].merchantRemark = val;
    setPreviewItems(newItems);
  };

  if (!isOpen) return null;

  if (viewState === "LOADING") {
    return (
      <div className="modal-overlay">
        <div className="modal-content">
          <div className="modal-body" style={{ textAlign: "center", padding: "40px" }}>
            加载中...
          </div>
        </div>
      </div>
    );
  }

  if (viewState === "CHECK" && previewCheckResult) {
    return (
      <div className="modal-overlay">
        <div className="modal-content">
          <div className="modal-header">
            <h3 className="modal-title">余额预检</h3>
            <button className="modal-close" onClick={onClose}><X size={20} /></button>
          </div>
          <div className="modal-body">
            <div style={{ marginBottom: "16px", padding: "12px", background: "#FEF3C7", borderRadius: "8px", color: "#92400E" }}>
              共 {previewCheckResult.totalCount} 人，其中 {previewCheckResult.insufficientCount} 人余额不足
            </div>
            {previewCheckResult.insufficientCustomers.length > 0 && (
              <div className="table-container">
                <table className="data-table">
                  <thead>
                    <tr>
                      <th>客户</th>
                      <th>电话</th>
                      <th>当前余额</th>
                      <th>需要餐数</th>
                      <th>餐次</th>
                    </tr>
                  </thead>
                  <tbody>
                    {previewCheckResult.insufficientCustomers.map((customer, index) => (
                      <tr key={index}>
                        <td>{customer.customerName}</td>
                        <td>{customer.customerPhone}</td>
                        <td>
                          <span style={{ color: "var(--error-color)", fontWeight: 600 }}>
                            {customer.remainingMeals} 餐
                          </span>
                        </td>
                        <td>{customer.requiredMeals} 餐</td>
                        <td>{customer.mealPeriod === "LUNCH" ? "午餐" : "晚餐"}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            )}
          </div>
          <div className="modal-footer">
            <button className="btn btn-outline" onClick={onClose}>取消导入</button>
            <button className="btn btn-primary" onClick={() => loadPreviewItems(true)}>
              仅导入余额充足的 ({previewCheckResult.sufficientCount} 人)
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="modal-overlay">
      <div className="modal-content" style={{ maxWidth: "800px" }}>
        <div className="modal-header">
          <span>自动导入包月订单 ({filterDate})</span>
          <button type="button" className="modal-close" disabled={isSubmittingImport} onClick={isSubmittingImport ? undefined : onClose}><X size={20} /></button>
        </div>
        <div className="modal-body" style={{ padding: "0", background: "#F8FAFC" }}>
          <div style={{ padding: "16px 24px", color: "var(--text-sub)", fontSize: "14px" }}>
            请核对明日的包月名单。取消勾选即可跳过请假用户，或在右侧直接补充临时口味备注。
          </div>
          <div style={{ margin: "0 24px 16px", padding: "14px 16px", borderRadius: "16px", background: "#FFFFFF", border: "1px solid var(--border-color)", display: "flex", gap: "24px", flexWrap: "wrap" }}>
            <div style={{ color: "var(--text-sub)", fontSize: "13px" }}>可导入 <span style={{ color: "var(--text-main)", fontWeight: 800, fontSize: "18px" }}>{previewItems.filter(i => i.hasBalance).length}</span></div>
            <div style={{ color: "var(--text-sub)", fontSize: "13px" }}>余额不足 <span style={{ color: "var(--error-color)", fontWeight: 800, fontSize: "18px" }}>{previewItems.filter(i => !i.hasBalance).length}</span></div>
            <div style={{ color: "var(--text-sub)", fontSize: "13px" }}>已勾选 <span style={{ color: "var(--primary-color)", fontWeight: 800, fontSize: "18px" }}>{previewItems.filter(i => i.selected && i.hasBalance).length}</span></div>
          </div>
          <div className="table-responsive">
            <table style={{ background: "#FFFFFF", borderTop: "1px solid var(--border-color)", borderBottom: "1px solid var(--border-color)" }}>
            <thead>
              <tr>
                <th style={{ width: "40px", paddingLeft: "24px" }}>
                  <input type="checkbox" checked={previewItems.every(i => !i.hasBalance || i.selected)} onChange={(e) => {
                    const val = e.target.checked;
                    setPreviewItems(previewItems.map(i => i.hasBalance ? { ...i, selected: val } : i));
                  }} />
                </th>
                <th>姓名</th>
                <th>餐次</th>
                <th>默认地址</th>
                <th>临时备注</th>
              </tr>
            </thead>
            <tbody>
              {previewItems.map((item, index) => {
                const isDisabled = !item.hasBalance;
                const isSelected = item.selected && !isDisabled;
                return (
                  <tr key={`${item.customerId}-${item.mealPeriod}`} style={{ opacity: isSelected ? 1 : 0.6 }}>
                    <td style={{ paddingLeft: "24px" }}>
                      <input 
                        type="checkbox" 
                        checked={isSelected} 
                        disabled={isDisabled} 
                        onChange={() => handleTogglePreviewItem(index)} 
                      />
                    </td>
                    <td style={{ textDecoration: !isSelected ? "line-through" : "none" }}>{item.customerName}</td>
                    <td style={{ textDecoration: !isSelected ? "line-through" : "none" }}>
                      <span className={`tag ${item.mealPeriod === "LUNCH" ? "tag-orange" : "tag-green"}`}>
                        {item.mealPeriod === "LUNCH" ? "午餐" : "晚餐"}
                      </span>
                    </td>
                    <td style={{ textDecoration: !isSelected ? "line-through" : "none", maxWidth: "200px", whiteSpace: "nowrap", overflow: "hidden", textOverflow: "ellipsis" }}>
                      {item.deliveryAddress}
                    </td>
                    <td>
                      {isDisabled ? (
                        <span style={{ color: "var(--error-color)", fontSize: "13px", fontWeight: 500, display: "flex", alignItems: "center", gap: "4px" }}>
                          <AlertTriangle size={14} /> 余额不足
                        </span>
                      ) : (
                        <SafeInput 
                          type="text" 
                          className="input-box" 
                          style={{ width: "160px", height: "30px", padding: "4px 8px" }} 
                          value={item.merchantRemark || ""} 
                          onValueChange={(value) => handleUpdatePreviewNote(index, value)} 
                          list="subscription-note-suggestions"
                          disabled={!isSelected}
                          placeholder="例如: 少饭"
                        />
                      )}
                    </td>
                  </tr>
                );
              })}
              {previewItems.length === 0 && (
                <tr>
                  <td colSpan={5} className="empty-state">没有需要导入的包月规则</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
        <div className="modal-footer" style={{ justifyContent: "space-between", alignItems: "center" }}>
          <div style={{ color: "var(--text-sub)", fontSize: "14px", fontWeight: 500 }}>
            已选: <span style={{ color: "var(--primary-color)", fontWeight: 600 }}>{previewItems.filter(i => i.selected && i.hasBalance).length}</span> 人 | 
            跳过: <span style={{ color: "var(--text-main)", fontWeight: 600 }}>{previewItems.filter(i => !i.selected || !i.hasBalance).length}</span> 人
          </div>
          <div style={{ display: "flex", gap: "12px" }}>
            <button className="btn btn-outline" disabled={isSubmittingImport} onClick={onClose}>取消</button>
            <button className="btn btn-primary" onClick={() => handleConfirmBulkImport().catch(() => undefined)} disabled={isSubmittingImport}>
              {isSubmittingImport ? "生成中..." : "确认生成订单"}
            </button>
          </div>
        </div>
      </div>
    </div>
    </div>
  );
}
