import React, { useState, useEffect, useMemo } from "react";
import { X, Search, MapPin, CheckCircle } from "lucide-react";
import { searchManualCreateCustomers, fetchCurrentMenuWeek, createManualOrder } from "../../../shared/api/http";
import { SafeInput } from "../../../shared/components/SafeInput";
import { AppSelect } from "../../../shared/components/AppSelect";
import { RemarkField } from "../../../shared/components/RemarkField";
import { toast } from "../../../shared/components/Toast";
import type { AdminMenuWeekResponse, ManualCreateCustomerSearchResponse } from "../../../shared/api/types";
import {
  createInitialManualCreateForm,
  shouldShowManualCustomerEmptyState,
  applyManualCreateCustomerSelection,
  applyManualCreateAddressSelection,
  resolveManualCreateMenuOptions,
  applyManualCreateMealPeriodSelection,
  buildManualCreatePayload
} from "../manualCreateOrder.helpers";

interface Props {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
  filterDate: string;
}

export function OrderPrepManualCreateModal({ isOpen, onClose, onSuccess, filterDate }: Props) {
  const [manualForm, setManualForm] = useState(createInitialManualCreateForm);
  const [manualCustomers, setManualCustomers] = useState<ManualCreateCustomerSearchResponse[]>([]);
  const [manualSelectedCustomer, setManualSelectedCustomer] = useState<ManualCreateCustomerSearchResponse | null>(null);
  const [manualSearchLoading, setManualSearchLoading] = useState(false);
  const [manualMenuWeek, setManualMenuWeek] = useState<AdminMenuWeekResponse | null>(null);
  const [manualMenuLoading, setManualMenuLoading] = useState(false);
  const [submittingManualCreate, setSubmittingManualCreate] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setManualForm(createInitialManualCreateForm());
      setManualCustomers([]);
      setManualSelectedCustomer(null);
      setManualSearchLoading(false);
      setManualMenuWeek(null);
      setManualMenuLoading(false);
    }
  }, [isOpen]);

  useEffect(() => {
    if (!isOpen) return;
    const keyword = manualForm.customerKeyword.trim();
    if (!keyword) {
      setManualCustomers([]);
      setManualSearchLoading(false);
      return;
    }
    let cancelled = false;
    const timer = window.setTimeout(() => {
      setManualSearchLoading(true);
      searchManualCreateCustomers(keyword)
        .then((data) => {
          if (!cancelled) setManualCustomers(data);
        })
        .catch(() => {
          if (!cancelled) setManualCustomers([]);
        })
        .finally(() => {
          if (!cancelled) setManualSearchLoading(false);
        });
    }, 250);
    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [isOpen, manualForm.customerKeyword]);

  useEffect(() => {
    if (!isOpen) return;
    let cancelled = false;
    setManualMenuLoading(true);
    fetchCurrentMenuWeek(filterDate)
      .then((week) => {
        if (!cancelled) setManualMenuWeek(week);
      })
      .catch(() => {
        if (!cancelled) setManualMenuWeek(null);
      })
      .finally(() => {
        if (!cancelled) setManualMenuLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [isOpen, filterDate]);

  const manualMenuOptions = useMemo(
    () => resolveManualCreateMenuOptions(manualMenuWeek, filterDate),
    [manualMenuWeek, filterDate]
  );

  useEffect(() => {
    if (!isOpen || !manualForm.mealPeriod) return;
    setManualForm((current) => applyManualCreateMealPeriodSelection(current, current.mealPeriod as "LUNCH" | "DINNER", manualMenuOptions));
  }, [isOpen, manualForm.mealPeriod, manualMenuOptions]);

  function handleManualCustomerSelect(customer: ManualCreateCustomerSearchResponse) {
    setManualSelectedCustomer(customer);
    setManualForm((current) => applyManualCreateCustomerSelection(current, customer));
  }

  function handleManualAddressSelect(addressId: number) {
    if (!manualSelectedCustomer) return;
    setManualForm((current) => applyManualCreateAddressSelection(current, manualSelectedCustomer.addresses, addressId));
  }

  async function handleManualCreateSubmit() {
    if (submittingManualCreate) return;
    setSubmittingManualCreate(true);
    try {
      await createManualOrder(buildManualCreatePayload(manualForm, filterDate));
      onClose();
      onSuccess();
      toast("代客订单已录入");
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "录入代客订单失败", "error");
    } finally {
      setSubmittingManualCreate(false);
    }
  }

  if (!isOpen) return null;

  return (
    <div className="modal-overlay">
      <div className="modal-content">
        <div className="modal-header">
          <span>录入代客订单</span>
          <button type="button" className="modal-close" disabled={submittingManualCreate} onClick={submittingManualCreate ? undefined : onClose}><X size={20} /></button>
        </div>
        <div className="modal-body">
          <div className="form-group">
            <label className="form-label"><span className="required">*</span>客户搜索</label>
            <div style={{ position: "relative" }}>
              <Search size={15} style={{ position: "absolute", left: "12px", top: "50%", transform: "translateY(-50%)", color: "var(--text-sub)" }} />
              <SafeInput
                className="form-control"
                style={{ paddingLeft: "36px" }}
                value={manualForm.customerKeyword}
                onValueChange={(value) => {
                  setManualForm((current) => ({
                    ...current,
                    customerKeyword: value,
                    customerId: "",
                    addressId: null,
                    deliveryAddress: ""
                  }));
                  setManualSelectedCustomer(null);
                }}
                placeholder="输入客户姓名或手机号"
              />
            </div>
            <div style={{ marginTop: "8px", color: "var(--text-sub)", fontSize: "12px" }}>
              先搜客户，再从该客户已有地址里选择配送地址。
            </div>
            {manualSearchLoading && (
              <div style={{ marginTop: "10px", color: "var(--text-sub)", fontSize: "13px" }}>搜索中...</div>
            )}
            {shouldShowManualCustomerEmptyState({
              keyword: manualForm.customerKeyword,
              isLoading: manualSearchLoading,
              customers: manualCustomers,
              selectedCustomerId: manualSelectedCustomer?.customerId ?? null
            }) && (
              <div style={{ marginTop: "10px", color: "var(--error-color)", fontSize: "13px" }}>未搜到匹配客户</div>
            )}
            {manualCustomers.length > 0 && (
              <div style={{ display: "flex", flexDirection: "column", gap: "8px", marginTop: "12px", maxHeight: "220px", overflowY: "auto" }}>
                {manualCustomers.map((customer) => (
                  <div
                    key={customer.customerId}
                    className={`address-card ${manualSelectedCustomer?.customerId === customer.customerId ? "selected" : ""}`}
                    onClick={() => handleManualCustomerSelect(customer)}
                  >
                    <MapPin size={18} className="address-icon" />
                    <div className="address-content">
                      <div className="address-title">
                        {customer.customerName}
                        <span style={{ marginLeft: "8px", color: "var(--text-sub)", fontWeight: 500 }}>{customer.customerPhone}</span>
                      </div>
                      <div className="address-detail" style={{ display: "flex", gap: "12px" }}>
                        <span>已有地址 {customer.addresses.length} 个</span>
                        <span style={{ color: customer.remainingMeals >= manualForm.quantity ? "var(--primary-color)" : "var(--error-color)" }}>
                          剩余餐次: {customer.remainingMeals}
                        </span>
                      </div>
                    </div>
                    {manualSelectedCustomer?.customerId === customer.customerId && (
                      <CheckCircle size={20} color="var(--primary-color)" style={{ flexShrink: 0, marginTop: "2px" }} />
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
          {manualSelectedCustomer && (
            <div className="form-group">
              <label className="form-label">客户信息</label>
              <div className="address-card selected" style={{ cursor: "default" }}>
                <MapPin size={20} className="address-icon" />
                <div className="address-content">
                  <div className="address-title">{manualSelectedCustomer.customerName}</div>
                  <div className="address-detail">手机号：{manualSelectedCustomer.customerPhone}</div>
                  <div className="address-detail" style={{ color: manualSelectedCustomer.remainingMeals >= manualForm.quantity ? "var(--primary-color)" : "var(--error-color)", fontWeight: 600 }}>
                    剩余餐次：{manualSelectedCustomer.remainingMeals} 
                    {manualSelectedCustomer.remainingMeals < manualForm.quantity && (
                      <span style={{ marginLeft: "8px", fontSize: "12px" }}>(余额不足)</span>
                    )}
                  </div>
                </div>
              </div>
            </div>
          )}
          <div className="form-group">
            <label className="form-label"><span className="required">*</span>配送地址</label>
            {manualSelectedCustomer ? (
              manualSelectedCustomer.addresses.length > 0 ? (
                <AppSelect
                  value={manualForm.addressId?.toString() || ""}
                  options={manualSelectedCustomer.addresses.map((address) => ({
                    label: `${address.addressLine}${address.isDefault ? " (默认)" : ""}`,
                    value: address.addressId.toString()
                  }))}
                  onChange={(val) => handleManualAddressSelect(Number(val))}
                  placeholder="选择配送地址"
                  style={{ width: "100%" }}
                />
              ) : (
                <div style={{ color: "var(--error-color)", fontSize: "13px" }}>该客户暂无可用地址，请先去客户中心维护地址。</div>
              )
            ) : (
              <div style={{ color: "var(--text-sub)", fontSize: "13px" }}>请先搜索并选择客户</div>
            )}
          </div>
          <div className="form-group">
            <label className="form-label"><span className="required">*</span>餐次</label>
            <div style={{ color: "var(--text-sub)", fontSize: "12px", marginBottom: "10px" }}>
              当前录单日期：{filterDate}
            </div>
            {manualMenuLoading ? (
              <div style={{ color: "var(--text-sub)", fontSize: "13px" }}>菜单加载中...</div>
            ) : (
              <div style={{ display: "flex", flexDirection: "column", gap: "8px" }}>
                {manualMenuOptions.map((option) => (
                  <div
                    key={option.mealPeriod}
                    className={`address-card ${manualForm.mealPeriod === option.mealPeriod ? "selected" : ""}`}
                    onClick={() => {
                      if (!option.available) return;
                      setManualForm((current) => applyManualCreateMealPeriodSelection(current, option.mealPeriod, manualMenuOptions));
                    }}
                    style={{ opacity: option.available ? 1 : 0.6, cursor: option.available ? "pointer" : "not-allowed" }}
                  >
                    <div className="address-content">
                      <div className="address-title">{option.label}</div>
                      <div className="address-detail">{option.available ? "可选" : option.disabledReason}</div>
                    </div>
                    {manualForm.mealPeriod === option.mealPeriod && (
                      <CheckCircle size={20} color="var(--primary-color)" style={{ flexShrink: 0, marginTop: "2px" }} />
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
          <div className="form-group">
            <label className="form-label"><span className="required">*</span>份数</label>
            <SafeInput 
              className="form-control" 
              type="number" 
              min="1" 
              value={manualForm.quantity.toString()} 
              onValueChange={(value) => setManualForm({ ...manualForm, quantity: Math.max(1, parseInt(value, 10) || 1) })} 
            />
          </div>
          <div>
            <div className="admin-panel-note" style={{ marginBottom: "8px" }}>
              仅此单生效，不会改客户中心里的长期商家备注。
            </div>
            <RemarkField
              label="商家备注"
              value={manualForm.merchantRemark}
              onChange={(value) => setManualForm({ ...manualForm, merchantRemark: value })}
              placeholder="只对当前这一单生效"
              scene="ORDER_REMARK"
              customerId={manualSelectedCustomer?.customerId ?? null}
            />
          </div>
        </div>
        <div className="modal-footer">
          <button className="btn btn-outline" onClick={onClose} disabled={submittingManualCreate}>取消</button>
          <button className="btn btn-primary" disabled={submittingManualCreate} onClick={() => handleManualCreateSubmit().catch(() => undefined)}>
            {submittingManualCreate ? "提交中..." : "确认录入"}
          </button>
        </div>
      </div>
    </div>
  );
}
