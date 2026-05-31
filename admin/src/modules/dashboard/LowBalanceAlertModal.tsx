import React, { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { fetchLowBalanceSubscriptions } from "../../shared/api/http";
import type { LowBalanceSubscriptionItem } from "../../shared/api/types";
import { http } from "../../shared/api/http";

type LowBalanceAlertModalProps = {
  visible: boolean;
  onClose: () => void;
};

export function LowBalanceAlertModal({ visible, onClose }: LowBalanceAlertModalProps) {
  const navigate = useNavigate();
  const [items, setItems] = useState<LowBalanceSubscriptionItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!visible) {
      return;
    }

    let cancelled = false;
    setLoading(true);
    setError(null);

    fetchLowBalanceSubscriptions()
      .then((data) => {
        if (cancelled) {
          return;
        }
        setItems(data);
      })
      .catch((err) => {
        if (cancelled) {
          return;
        }
        setError(err?.response?.data?.message || err?.message || String(err));
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [visible]);

  const handleMarkDormant = async (customerId: number) => {
    if (!window.confirm("确认将该客户标记为沉睡状态？标记后将不再出现在预警列表中。")) {
      return;
    }

    try {
      await http.put(`/api/admin/customers/${customerId}/status`, {
        customerStatus: "DORMANT"
      });
      // 刷新列表
      const updatedItems = items.filter((item) => item.customerId !== customerId);
      setItems(updatedItems);
    } catch (err: any) {
      alert(err?.response?.data?.message || err?.message || "标记失败");
    }
  };

  const handleGoToRecharge = (customerId: number) => {
    navigate(`/customers/${customerId}`);
    onClose();
  };

  if (!visible) {
    return null;
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal-content modal-content--wide" onClick={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title">固定订餐余额预警</h3>
          <button className="modal-close" onClick={onClose}>
            ×
          </button>
        </div>

        <div className="modal-body">
          {loading && (
            <div className="dispatch-empty">加载中...</div>
          )}

          {error && (
            <div className="dispatch-empty" style={{ color: "var(--error-color)" }}>
              加载失败：{error}
            </div>
          )}

          {!loading && !error && items.length === 0 && (
            <div className="dispatch-empty">暂无余额预警客户</div>
          )}

          {!loading && !error && items.length > 0 && (
            <div className="table-container">
              <table className="data-table">
                <thead>
                  <tr>
                    <th>客户</th>
                    <th>电话</th>
                    <th>剩余餐数</th>
                    <th>固定订餐</th>
                    <th>下次服务日期</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {items.map((item) => (
                    <tr key={item.customerId}>
                      <td>{item.customerName}</td>
                      <td>{item.customerPhone}</td>
                      <td>
                        <span
                          style={{
                            color: item.remainingMeals === 0 ? "var(--error-color)" : "var(--warning-color)",
                            fontWeight: 600
                          }}
                        >
                          {item.remainingMeals} 餐
                        </span>
                      </td>
                      <td>
                        {item.lunchEnabled && "午餐"}
                        {item.lunchEnabled && item.dinnerEnabled && " + "}
                        {item.dinnerEnabled && "晚餐"}
                      </td>
                      <td>{item.nextServeDate}</td>
                      <td>
                        <div style={{ display: "flex", gap: "8px" }}>
                          <button
                            className="btn btn-sm btn-primary"
                            onClick={() => handleGoToRecharge(item.customerId)}
                          >
                            去充值
                          </button>
                          <button
                            className="btn btn-sm btn-outline"
                            onClick={() => handleMarkDormant(item.customerId)}
                          >
                            标记沉睡
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        <div className="modal-footer">
          <button className="btn btn-outline" onClick={onClose}>
            关闭
          </button>
        </div>
      </div>
    </div>
  );
}
