import React, { useState, useEffect, useRef } from "react";
import { X, Search, MapPin, User, Calendar, UtensilsCrossed } from "lucide-react";
import { createSubscriptionRule, updateSubscriptionRule, fetchCustomerAssets, fetchCustomerDetail } from "../../shared/api/http";
import type { SubscriptionRuleResponse, SubscriptionRuleFormData, CustomerAssetResponse } from "../../shared/api/types";
import { DatePicker } from "../../shared/components/DatePicker";
import { AppSelect } from "../../shared/components/AppSelect";
import { toast } from "../../shared/components/Toast";

type AddressOption = {
  id: number;
  addressLine: string;
  isDefault: boolean;
};

type SelectedCustomerSummary = Pick<CustomerAssetResponse, "id" | "name" | "phone" | "remainingMeals">;

function resolveSelectableAddressId(addresses: AddressOption[], preferredAddressId?: number | null) {
  if (preferredAddressId && addresses.some((address) => address.id === preferredAddressId)) {
    return preferredAddressId;
  }
  return addresses.find((address) => address.isDefault)?.id ?? addresses[0]?.id ?? null;
}

type Props = {
  item: SubscriptionRuleResponse | null;
  onClose: () => void;
};

export function SubscriptionRuleForm({ item, onClose }: Props) {
  const isEdit = Boolean(item);
  const [form, setForm] = useState<SubscriptionRuleFormData>({
    customerId: 0,
    startDate: "",
    endDate: "",
    lunchEnabled: false,
    lunchQuantity: 1,
    lunchDeliveryMealPeriod: "LUNCH",
    dinnerEnabled: false,
    dinnerQuantity: 1,
    dinnerDeliveryMealPeriod: "DINNER",
    defaultAddressId: null,
    merchantRemark: "",
    isPriorityFollow: false
  });

  const [customerKeyword, setCustomerKeyword] = useState("");
  const [allCustomers, setAllCustomers] = useState<CustomerAssetResponse[]>([]);
  const [filteredCustomers, setFilteredCustomers] = useState<CustomerAssetResponse[]>([]);
  const [selectedCustomer, setSelectedCustomer] = useState<SelectedCustomerSummary | null>(null);
  const [showDropdown, setShowDropdown] = useState(false);
  const [loadingCustomers, setLoadingCustomers] = useState(false);

  const [addresses, setAddresses] = useState<AddressOption[]>([]);
  const [loadingAddresses, setLoadingAddresses] = useState(false);

  const [submitting, setSubmitting] = useState(false);
  const dropdownRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    setLoadingCustomers(true);
    fetchCustomerAssets()
      .then((data) => setAllCustomers(data.items))
      .catch(() => setAllCustomers([]))
      .finally(() => setLoadingCustomers(false));
  }, []);

  useEffect(() => {
    if (!item) return;
    setSelectedCustomer({
      id: item.customerId,
      name: item.customerName,
      phone: item.customerPhone,
      remainingMeals: item.remainingMeals
    });
    setForm({
      customerId: item.customerId,
      startDate: item.startDate,
      endDate: item.endDate,
      lunchEnabled: item.lunchEnabled,
      lunchQuantity: item.lunchQuantity,
      lunchDeliveryMealPeriod: item.lunchDeliveryMealPeriod,
      dinnerEnabled: item.dinnerEnabled,
      dinnerQuantity: item.dinnerQuantity,
      dinnerDeliveryMealPeriod: item.dinnerDeliveryMealPeriod,
      defaultAddressId: item.defaultAddressId,
      merchantRemark: item.merchantRemark || "",
      isPriorityFollow: item.isPriorityFollow
    });
    setCustomerKeyword(`${item.customerName}（${item.customerPhone}）`);
    loadAddresses(item.customerId);
  }, [item]);

  useEffect(() => {
    if (!customerKeyword.trim()) {
      setFilteredCustomers([]);
      return;
    }
    const kw = customerKeyword.toLowerCase();
    setFilteredCustomers(
      allCustomers
        .filter((c) => c.name.toLowerCase().includes(kw) || c.phone.includes(kw))
        .slice(0, 8)
    );
  }, [customerKeyword, allCustomers]);

  useEffect(() => {
    function onClickOutside(e: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setShowDropdown(false);
      }
    }
    document.addEventListener("mousedown", onClickOutside);
    return () => document.removeEventListener("mousedown", onClickOutside);
  }, []);

  async function loadAddresses(customerId: number) {
    setLoadingAddresses(true);
    try {
      const detail = await fetchCustomerDetail(customerId) as any;
      const list: AddressOption[] = (detail.addresses || []).map((a: any) => ({
        id: Number(a.addressId ?? a.id),
        addressLine: a.addressLine,
        isDefault: a.isDefault
      }));
      setAddresses(list);
      setForm((prev) => ({
        ...prev,
        defaultAddressId: resolveSelectableAddressId(list, item ? prev.defaultAddressId : null)
      }));
    } catch {
      setAddresses([]);
      setForm((prev) => ({ ...prev, defaultAddressId: null }));
    } finally {
      setLoadingAddresses(false);
    }
  }

  function handleSelectCustomer(c: CustomerAssetResponse) {
    setSelectedCustomer(c);
    setCustomerKeyword(`${c.name}（${c.phone}）`);
    setShowDropdown(false);
    setForm((prev) => ({ ...prev, customerId: c.id, defaultAddressId: null }));
    loadAddresses(c.id);
  }

  async function handleSubmit() {
    if (!form.customerId) {
      toast("请选择客户", "error");
      return;
    }
    if (!form.startDate || !form.endDate) {
      toast("请选择时间段", "error");
      return;
    }
    if (form.startDate > form.endDate) {
      toast("开始日期不能晚于结束日期", "error");
      return;
    }
    if (!form.lunchEnabled && !form.dinnerEnabled) {
      toast("至少启用午餐或晚餐之一", "error");
      return;
    }
    if (addresses.length === 0) {
      toast("该客户暂无地址，请先去客户地址管理补充", "error");
      return;
    }
    if (!form.defaultAddressId) {
      toast("请选择该客户自己的配送地址", "error");
      return;
    }

    setSubmitting(true);
    try {
      if (item) {
        await updateSubscriptionRule(item.id, form);
      } else {
        await createSubscriptionRule(form);
      }
      toast(isEdit ? "固定订餐计划已更新" : "固定订餐计划已创建");
      onClose();
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "保存失败", "error");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="modal-overlay" onClick={submitting ? undefined : onClose}>
      <div
        className="modal-content"
        style={{ maxWidth: "540px" }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="modal-header">
          <span>{isEdit ? "编辑固定订餐计划" : "新增固定订餐计划"}</span>
          <button type="button" className="modal-close" onClick={submitting ? undefined : onClose} disabled={submitting}><X size={18} /></button>
        </div>

        {/* Body */}
        <div className="modal-body" style={{ display: "grid", gap: "20px" }}>

          {/* 客户选择 */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label">
              <span className="required">*</span>客户
            </label>
            <div ref={dropdownRef} style={{ position: "relative" }}>
              <div style={{ position: "relative" }}>
                <Search
                  size={15}
                  style={{
                    position: "absolute",
                    left: "12px",
                    top: "50%",
                    transform: "translateY(-50%)",
                    color: "var(--text-sub)",
                    pointerEvents: "none"
                  }}
                />
                <input
                  className="form-control"
                  style={{ paddingLeft: "36px" }}
                  placeholder={loadingCustomers ? "加载客户列表中..." : "输入姓名或手机号搜索"}
                  value={customerKeyword}
                  disabled={isEdit}
                  onChange={(e) => {
                    setCustomerKeyword(e.target.value);
                    setShowDropdown(true);
                    if (!e.target.value) {
                      setSelectedCustomer(null);
                      setForm((prev) => ({ ...prev, customerId: 0, defaultAddressId: null }));
                      setAddresses([]);
                    }
                  }}
                  onFocus={() => { if (!isEdit) setShowDropdown(true); }}
                />
              </div>

              {showDropdown && filteredCustomers.length > 0 && (
                <div style={{
                  position: "absolute",
                  top: "calc(100% + 4px)",
                  left: 0,
                  right: 0,
                  background: "#fff",
                  border: "1px solid rgba(203,213,225,0.9)",
                  borderRadius: "12px",
                  boxShadow: "0 8px 24px rgba(0,0,0,0.10)",
                  zIndex: 200,
                  overflow: "hidden"
                }}>
                  {filteredCustomers.map((c, i) => (
                    <div
                      key={c.id}
                      onMouseDown={() => handleSelectCustomer(c)}
                      style={{
                        padding: "10px 14px",
                        cursor: "pointer",
                        display: "flex",
                        justifyContent: "space-between",
                        alignItems: "center",
                        borderBottom: i < filteredCustomers.length - 1 ? "1px solid rgba(226,232,240,0.7)" : "none",
                        transition: "background 0.12s"
                      }}
                      onMouseEnter={(e) => (e.currentTarget.style.background = "rgba(239,246,255,0.8)")}
                      onMouseLeave={(e) => (e.currentTarget.style.background = "transparent")}
                    >
                      <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
                        <div style={{
                          width: "32px", height: "32px", borderRadius: "50%",
                          background: "linear-gradient(135deg,#dbeafe,#ede9fe)",
                          display: "flex", alignItems: "center", justifyContent: "center",
                          flexShrink: 0
                        }}>
                          <User size={14} color="#6366f1" />
                        </div>
                        <div>
                          <div style={{ fontWeight: 700, fontSize: "14px", color: "var(--text-main)" }}>{c.name}</div>
                          <div style={{ fontSize: "12px", color: "var(--text-sub)" }}>{c.phone}</div>
                        </div>
                      </div>
                      <div style={{
                        fontSize: "12px",
                        padding: "2px 8px",
                        borderRadius: "999px",
                        background: c.remainingMeals <= 3 ? "rgba(254,226,226,0.8)" : "rgba(220,252,231,0.8)",
                        color: c.remainingMeals <= 3 ? "#dc2626" : "#16a34a",
                        fontWeight: 600
                      }}>
                        余额 {c.remainingMeals} 餐
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>

          {/* 时间段 */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label">
              <span className="required">*</span>时间段
            </label>
            <div style={{ display: "flex", gap: "10px", alignItems: "center" }}>
              <DatePicker
                value={form.startDate}
                onChange={(d) => setForm({ ...form, startDate: d })}
                showTomorrowShortcut={false}
              />
              <span style={{ color: "var(--text-sub)", fontSize: "13px", flexShrink: 0 }}>至</span>
              <DatePicker
                value={form.endDate}
                onChange={(d) => setForm({ ...form, endDate: d })}
                showTomorrowShortcut={false}
              />
            </div>
          </div>

          {/* 餐次配置 */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label">
              <span className="required">*</span>餐次配置
            </label>
            <div style={{
              border: "1px solid rgba(203,213,225,0.8)",
              borderRadius: "12px",
              overflow: "hidden"
            }}>
              {/* 午餐行 */}
              <div style={{
                display: "flex",
                alignItems: "center",
                padding: "14px 16px",
                gap: "12px",
                borderBottom: "1px solid rgba(226,232,240,0.7)",
                background: form.lunchEnabled ? "rgba(255,247,237,0.5)" : "transparent"
              }}>
                <label style={{ display: "flex", alignItems: "center", gap: "8px", cursor: "pointer", flex: 1 }}>
                  <input
                    type="checkbox"
                    checked={form.lunchEnabled}
                    onChange={(e) => setForm({ ...form, lunchEnabled: e.target.checked })}
                    style={{ width: "16px", height: "16px", accentColor: "var(--primary-color)" }}
                  />
                  <span style={{ fontSize: "14px", fontWeight: 600, color: "var(--text-main)" }}>午餐</span>
                  <span style={{
                    fontSize: "11px", padding: "1px 7px", borderRadius: "999px",
                    background: "rgba(251,191,36,0.15)", color: "#d97706", fontWeight: 600
                  }}>LUNCH</span>
                </label>
                {form.lunchEnabled && (
                  <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                    <span style={{ fontSize: "13px", color: "var(--text-sub)" }}>每日</span>
                    <input
                      type="number"
                      className="form-control"
                      value={form.lunchQuantity}
                      onChange={(e) => setForm({ ...form, lunchQuantity: Math.max(1, Number(e.target.value)) })}
                      min="1"
                      style={{ width: "64px", textAlign: "center" }}
                    />
                    <span style={{ fontSize: "13px", color: "var(--text-sub)" }}>份</span>
                    <AppSelect
                      value={form.lunchDeliveryMealPeriod}
                      options={[
                        { label: "午餐配送", value: "LUNCH" },
                        { label: "晚餐配送", value: "DINNER" }
                      ]}
                      onChange={(value) => setForm({ ...form, lunchDeliveryMealPeriod: value as "LUNCH" | "DINNER" })}
                      style={{ minWidth: "112px" }}
                    />
                  </div>
                )}
              </div>

              {/* 晚餐行 */}
              <div style={{
                display: "flex",
                alignItems: "center",
                padding: "14px 16px",
                gap: "12px",
                background: form.dinnerEnabled ? "rgba(240,253,244,0.5)" : "transparent"
              }}>
                <label style={{ display: "flex", alignItems: "center", gap: "8px", cursor: "pointer", flex: 1 }}>
                  <input
                    type="checkbox"
                    checked={form.dinnerEnabled}
                    onChange={(e) => setForm({ ...form, dinnerEnabled: e.target.checked })}
                    style={{ width: "16px", height: "16px", accentColor: "var(--primary-color)" }}
                  />
                  <span style={{ fontSize: "14px", fontWeight: 600, color: "var(--text-main)" }}>晚餐</span>
                  <span style={{
                    fontSize: "11px", padding: "1px 7px", borderRadius: "999px",
                    background: "rgba(167,243,208,0.3)", color: "#059669", fontWeight: 600
                  }}>DINNER</span>
                </label>
                {form.dinnerEnabled && (
                  <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                    <span style={{ fontSize: "13px", color: "var(--text-sub)" }}>每日</span>
                    <input
                      type="number"
                      className="form-control"
                      value={form.dinnerQuantity}
                      onChange={(e) => setForm({ ...form, dinnerQuantity: Math.max(1, Number(e.target.value)) })}
                      min="1"
                      style={{ width: "64px", textAlign: "center" }}
                    />
                    <span style={{ fontSize: "13px", color: "var(--text-sub)" }}>份</span>
                    <AppSelect
                      value={form.dinnerDeliveryMealPeriod}
                      options={[
                        { label: "午餐配送", value: "LUNCH" },
                        { label: "晚餐配送", value: "DINNER" }
                      ]}
                      onChange={(value) => setForm({ ...form, dinnerDeliveryMealPeriod: value as "LUNCH" | "DINNER" })}
                      style={{ minWidth: "112px" }}
                    />
                  </div>
                )}
              </div>
            </div>
          </div>

          {/* 配送地址 */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label">配送地址</label>
            {!form.customerId ? (
              <div style={{
                padding: "12px 14px",
                borderRadius: "10px",
                background: "rgba(248,250,252,0.8)",
                border: "1px dashed rgba(203,213,225,0.8)",
                color: "var(--text-sub)",
                fontSize: "13px",
                display: "flex",
                alignItems: "center",
                gap: "8px"
              }}>
                <MapPin size={14} />
                请先选择客户
              </div>
            ) : loadingAddresses ? (
              <div style={{ color: "var(--text-sub)", fontSize: "13px", padding: "10px 0" }}>加载地址中...</div>
            ) : addresses.length === 0 ? (
              <div style={{
                padding: "12px 14px",
                borderRadius: "10px",
                background: "rgba(248,250,252,0.8)",
                border: "1px dashed rgba(203,213,225,0.8)",
                color: "var(--text-sub)",
                fontSize: "13px"
              }}>
                该客户暂无地址，请先去客户地址管理补充
              </div>
            ) : (
              <div style={{
                border: "1px solid rgba(203,213,225,0.8)",
                borderRadius: "12px",
                overflow: "hidden"
              }}>
                {addresses.map((addr, i) => (
                  <label
                    key={addr.id}
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "10px",
                      padding: "11px 14px",
                      cursor: "pointer",
                      borderBottom: i < addresses.length - 1 ? "1px solid rgba(226,232,240,0.7)" : "none",
                      background: form.defaultAddressId === addr.id ? "rgba(239,246,255,0.6)" : "transparent"
                    }}
                  >
                    <input
                      type="radio"
                      name="address"
                      checked={form.defaultAddressId === addr.id}
                      onChange={() => setForm({ ...form, defaultAddressId: addr.id })}
                      style={{ accentColor: "var(--primary-color)" }}
                    />
                    <MapPin size={13} color="var(--text-sub)" style={{ flexShrink: 0 }} />
                    <span style={{ fontSize: "13px", color: "var(--text-main)", flex: 1 }}>{addr.addressLine}</span>
                    {addr.isDefault && (
                      <span style={{
                        fontSize: "11px", padding: "1px 7px", borderRadius: "999px",
                        background: "rgba(219,234,254,0.8)", color: "#2563eb", fontWeight: 600
                      }}>默认</span>
                    )}
                  </label>
                ))}
              </div>
            )}
            {form.customerId && addresses.length > 0 ? (
              <div style={{ marginTop: "8px", color: "var(--text-sub)", fontSize: "12px" }}>
                固定订餐只会使用该客户已保存的地址，不再走后台兜底默认地址。
              </div>
            ) : null}
          </div>

          {/* 商家备注 */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label">商家备注</label>
            <input
              className="form-control"
              value={form.merchantRemark}
              onChange={(e) => setForm({ ...form, merchantRemark: e.target.value })}
              placeholder="计划期内自动生成的每一单都会带上"
            />
          </div>

          {/* 优先跟进 */}
          <div style={{ display: "flex", alignItems: "center", gap: "10px" }}>
            <input
              type="checkbox"
              id="priority-follow"
              checked={form.isPriorityFollow}
              onChange={(e) => setForm({ ...form, isPriorityFollow: e.target.checked })}
              style={{ width: "16px", height: "16px", accentColor: "var(--primary-color)" }}
            />
            <label htmlFor="priority-follow" style={{ fontSize: "14px", color: "var(--text-main)", cursor: "pointer" }}>
              优先跟进
            </label>
          </div>

        </div>

        {/* Footer */}
        <div className="modal-footer">
          <button className="btn btn-outline" onClick={onClose} disabled={submitting}>
            取消
          </button>
          <button className="btn btn-primary" onClick={handleSubmit} disabled={submitting}>
            {submitting ? "保存中..." : isEdit ? "保存修改" : "创建计划"}
          </button>
        </div>
      </div>
    </div>
  );
}
