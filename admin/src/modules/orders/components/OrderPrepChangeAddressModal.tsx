import React, { useState, useEffect, useMemo } from "react";
import { MapPin, AlertTriangle } from "lucide-react";
import { AdminDialog } from "../../../shared/components/AdminDialog";
import { toast } from "../../../shared/components/Toast";
import { fetchCustomerDetail, changeOrderAddress } from "../../../shared/api/http";
import type { CustomerAddressItem, OrderPrepItemResponse } from "../../../shared/api/types";
import { formatLocalDateInputValue } from "../../../shared/utils/dateTime";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  activeItem: OrderPrepItemResponse | null;
}

function resolveCurrentAddressId(addressLine: string | undefined, addresses: CustomerAddressItem[]): number | null {
  const current = (addressLine || "").trim();
  if (!current) return null;
  const hit = addresses.find((a) => (a.addressLine || "").trim() === current);
  return hit ? hit.id : null;
}

export function OrderPrepChangeAddressModal({ isOpen, onClose, onSuccess, activeItem }: Props) {
  const [addresses, setAddresses] = useState<CustomerAddressItem[]>([]);
  const [loadingAddresses, setLoadingAddresses] = useState(false);
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const isSameDay = useMemo(() => {
    if (!activeItem?.serveDate) return false;
    return activeItem.serveDate === formatLocalDateInputValue();
  }, [activeItem]);

  const currentAddressId = useMemo(
    () => resolveCurrentAddressId(activeItem?.deliveryAddress, addresses),
    [activeItem, addresses]
  );

  useEffect(() => {
    if (!isOpen || !activeItem) return;
    if (!activeItem.customerId) {
      toast("未获取到客户信息，无法加载地址", "error");
      return;
    }
    setSelectedAddressId(null);
    setLoadingAddresses(true);
    fetchCustomerDetail(activeItem.customerId)
      .then((detail) => {
        const list = detail.addresses || [];
        setAddresses(list);
        setSelectedAddressId(resolveCurrentAddressId(activeItem.deliveryAddress, list));
      })
      .catch((err) => {
        console.error("加载客户地址失败", err);
        setAddresses([]);
        toast("加载客户地址失败", "error");
      })
      .finally(() => setLoadingAddresses(false));
  }, [isOpen, activeItem]);

  async function handleSubmit() {
    if (!activeItem || selectedAddressId == null || submitting) return;
    if (selectedAddressId === currentAddressId) {
      toast("新地址与当前地址相同", "error");
      return;
    }
    setSubmitting(true);
    try {
      const result = await changeOrderAddress(activeItem.id, {
        addressId: selectedAddressId,
        customerId: activeItem.customerId
      });
      if (result.status === "ADDRESS_UNCHANGED") {
        toast("地址未发生变化");
      } else {
        toast("配送地址已更新");
      }
      onClose();
      onSuccess();
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "修改地址失败", "error");
    } finally {
      setSubmitting(false);
    }
  }

  if (!isOpen || !activeItem) return null;

  const canSubmit = selectedAddressId != null && selectedAddressId !== currentAddressId && !submitting;

  return (
    <AdminDialog
      open={isOpen}
      title={`修改配送地址 - ${activeItem.customerName}`}
      width={560}
      onClose={submitting ? () => undefined : onClose}
      footer={
        <div style={{ display: "flex", gap: "12px", justifyContent: "flex-end" }}>
          <button className="btn btn-outline" onClick={onClose} disabled={submitting}>取消</button>
          <button
            className="btn btn-primary"
            disabled={!canSubmit}
            onClick={() => handleSubmit().catch(() => undefined)}
          >
            {submitting ? "提交中..." : "确认修改地址"}
          </button>
        </div>
      }
    >
      <div style={{ display: "grid", gap: "16px" }}>
        <div className="delete-confirm-details">
          <div className="delete-confirm-details__item">
            <span className="delete-confirm-details__label">当前地址：</span>
            <span className="delete-confirm-details__value">{activeItem.deliveryAddress || "-"}</span>
          </div>
          <div className="delete-confirm-details__item">
            <span className="delete-confirm-details__label">送餐日期：</span>
            <span className="delete-confirm-details__value">{activeItem.serveDate}</span>
          </div>
        </div>

        {isSameDay ? (
          <div style={{
            padding: "12px 14px",
            background: "var(--warning-bg, #fff7e6)",
            color: "var(--warning-color, #ad6800)",
            borderRadius: "8px",
            display: "flex",
            gap: "8px",
            alignItems: "flex-start",
            fontSize: "13px",
            lineHeight: 1.6
          }}>
            <AlertTriangle size={16} style={{ flexShrink: 0, marginTop: 2 }} />
            <span>这是送餐当天的订单，改地址会影响骑手今天的派送安排。请先与骑手/顾客协商一致后再提交，避免送错或漏送。</span>
          </div>
        ) : null}

        <div className="form-group" style={{ marginBottom: 0 }}>
          <label className="form-label"><span className="required">*</span>选择新配送地址</label>
          {loadingAddresses ? (
            <div style={{ color: "var(--text-sub)", fontSize: "13px" }}>地址加载中...</div>
          ) : addresses.length === 0 ? (
            <div style={{ color: "var(--text-sub)", fontSize: "13px" }}>该客户暂无可用的收货地址，请先在客户管理中添加新地址。</div>
          ) : (
            <div style={{ display: "grid", gap: "10px" }}>
              {addresses.map((addr) => {
                const isCurrent = addr.id === currentAddressId;
                const isSelected = addr.id === selectedAddressId;
                return (
                  <label
                    key={addr.id}
                    className="address-card"
                    style={{
                      cursor: isCurrent ? "not-allowed" : "pointer",
                      opacity: isCurrent ? 0.6 : 1,
                      borderColor: isSelected ? "var(--primary-color)" : undefined,
                      boxShadow: isSelected ? "0 0 0 2px rgba(64, 128, 255, 0.25)" : undefined
                    }}
                  >
                    <div style={{ display: "flex", alignItems: "flex-start", gap: "10px" }}>
                      <input
                        type="radio"
                        name="change-address"
                        checked={isSelected}
                        disabled={isCurrent}
                        onChange={() => setSelectedAddressId(addr.id)}
                        style={{ marginTop: 4 }}
                      />
                      <div style={{ display: "grid", gap: "4px" }}>
                        <div className="address-title" style={{ fontWeight: 600, display: "flex", gap: "8px", alignItems: "center", flexWrap: "wrap" }}>
                          <MapPin size={14} />
                          <span>{addr.addressLine}</span>
                          {addr.isDefault ? <span className="tag tag-blue">默认</span> : null}
                          {isCurrent ? <span className="tag tag-gray">当前地址</span> : null}
                        </div>
                        <div className="address-detail" style={{ color: "var(--text-sub)", fontSize: "12px" }}>
                          {addr.contactName} / {addr.contactPhone}
                          {addr.areaCode ? ` · 区域 ${addr.areaCode}` : ""}
                        </div>
                      </div>
                    </div>
                  </label>
                );
              })}
            </div>
          )}
        </div>
      </div>
    </AdminDialog>
  );
}
