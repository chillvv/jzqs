import React, { useState, useEffect, useRef } from "react";
import { X, Search, MapPin, User, Calendar, UtensilsCrossed } from "lucide-react";
import { createSubscriptionRule, updateSubscriptionRule, fetchCustomerAssets, fetchCustomerDetail } from "../../shared/api/http";
import type { SubscriptionRuleResponse, SubscriptionRuleFormData, CustomerAssetResponse } from "../../shared/api/types";
import { DatePicker } from "../../shared/components/DatePicker";
import { AppSelect } from "../../shared/components/AppSelect";
import { SafeInput } from "../../shared/components/SafeInput";
import { toast } from "../../shared/components/Toast";

type AddressOption = {
  id: number;
  addressLine: string;
  isDefault: boolean;
};

type SelectedCustomerSummary = Pick<CustomerAssetResponse, "id" | "name" | "phone" | "remainingMeals">;

const WEEK_DAY_OPTIONS = [
  { value: "1", label: "周一" },
  { value: "2", label: "周二" },
  { value: "3", label: "周三" },
  { value: "4", label: "周四" },
  { value: "5", label: "周五" },
  { value: "6", label: "周六" },
  { value: "7", label: "周日" }
];

const ALL_WEEK_DAYS = WEEK_DAY_OPTIONS.map((option) => option.value).join(",");

function formatDateOffset(days: number) {
  const date = new Date();
  date.setDate(date.getDate() + days);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function formatTomorrowDate() {
  return formatDateOffset(1);
}

const DEFAULT_PLAN_DURATION_DAYS = 10;

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
    startDate: formatDateOffset(1),
    endDate: formatDateOffset(DEFAULT_PLAN_DURATION_DAYS),
    weekDays: ALL_WEEK_DAYS,
    lunchEnabled: false,
    lunchQuantity: 1,
    lunchDeliveryMealPeriod: "LUNCH",
    dinnerEnabled: false,
    dinnerQuantity: 1,
    dinnerDeliveryMealPeriod: "DINNER",
    defaultAddressId: null,
    merchantRemark: ""
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
      weekDays: item.weekDays || ALL_WEEK_DAYS,
      lunchEnabled: item.lunchEnabled,
      lunchQuantity: item.lunchQuantity,
      lunchDeliveryMealPeriod: item.lunchDeliveryMealPeriod,
      dinnerEnabled: item.dinnerEnabled,
      dinnerQuantity: item.dinnerQuantity,
      dinnerDeliveryMealPeriod: item.dinnerDeliveryMealPeriod,
      defaultAddressId: item.defaultAddressId,
      merchantRemark: item.merchantRemark || ""
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
    // 用 pointerdown 替代 mousedown：触屏（手机/平板）上也能关闭下拉
    function onClickOutside(e: PointerEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(e.target as Node)) {
        setShowDropdown(false);
      }
    }
    document.addEventListener("pointerdown", onClickOutside);
    return () => document.removeEventListener("pointerdown", onClickOutside);
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

  function toggleWeekDay(day: string) {
    const current = new Set(form.weekDays.split(",").map((item) => item.trim()).filter(Boolean));
    if (current.has(day)) {
      current.delete(day);
    } else {
      current.add(day);
    }
    setForm({ ...form, weekDays: Array.from(current).sort().join(",") });
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
    if (!form.weekDays || form.weekDays.split(",").filter(Boolean).length === 0) {
      toast("请至少勾选一个每周配送日", "error");
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
                <SafeInput
                  className="form-control"
                  style={{ paddingLeft: "36px" }}
                  placeholder={loadingCustomers ? "加载客户列表中..." : "输入姓名或手机号搜索"}
                  value={customerKeyword}
                  disabled={isEdit}
                  onValueChange={(value) => {
                    setCustomerKeyword(value);
                    setShowDropdown(true);
                    if (!value) {
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
                      onPointerDown={() => handleSelectCustomer(c)}
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
                minDate={formatTomorrowDate()}
              />
              <span style={{ color: "var(--text-sub)", fontSize: "13px", flexShrink: 0 }}>至</span>
              <DatePicker
                value={form.endDate}
                onChange={(d) => setForm({ ...form, endDate: d })}
                showTomorrowShortcut={false}
                minDate={form.startDate || formatTomorrowDate()}
              />
            </div>
            <div style={{ marginTop: "6px", fontSize: "12px", color: "var(--text-sub)" }}>
              最早从明天开始生效（今天不可开始）；结束日期当天正常配送。
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
                    style={{ width: "18px", height: "18px", accentColor: "var(--primary-color)" }}
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
                    <SafeInput
                      type="number"
                      className="form-control"
                      value={form.lunchQuantity}
                      onValueChange={(value) => setForm({ ...form, lunchQuantity: Math.max(1, Number(value)) })}
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
                    style={{ width: "18px", height: "18px", accentColor: "var(--primary-color)" }}
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
                    <SafeInput
                      type="number"
                      className="form-control"
                      value={form.dinnerQuantity}
                      onValueChange={(value) => setForm({ ...form, dinnerQuantity: Math.max(1, Number(value)) })}
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

          {/* 每周配送日 */}
          <div className="form-group" style={{ marginBottom: 0 }}>
            <label className="form-label">每周配送日</label>
            <div style={{ display: "flex", flexWrap: "wrap", gap: "8px" }}>
              {WEEK_DAY_OPTIONS.map((option) => {
                const checked = form.weekDays.split(",").includes(option.value);
                return (
                  <label
                    key={option.value}
                    style={{
                      display: "flex",
                      alignItems: "center",
                      gap: "6px",
                      padding: "6px 12px",
                      borderRadius: "999px",
                      cursor: "pointer",
                      border: checked ? "1px solid var(--primary-color)" : "1px solid rgba(203,213,225,0.8)",
                      background: checked ? "rgba(239,246,255,0.8)" : "transparent"
                    }}
                  >
                    <input
                      type="checkbox"
                      checked={checked}
                      onChange={() => toggleWeekDay(option.value)}
                      style={{ width: "18px", height: "18px", accentColor: "var(--primary-color)" }}
                    />
                    <span style={{ fontSize: "13px", fontWeight: checked ? 600 : 400, color: "var(--text-main)" }}>
                      {option.label}
                    </span>
                  </label>
                );
              })}
            </div>
            <div style={{ marginTop: "6px", fontSize: "12px", color: "var(--text-sub)" }}>
              只在勾选的星期几生成订单并扣餐；店铺休息日（菜单排期标为休息）自动跳过，不扣餐。
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
            <SafeInput
              className="form-control"
              value={form.merchantRemark}
              onValueChange={(value) => setForm({ ...form, merchantRemark: value })}
              placeholder="计划期内自动生成的每一单都会带上"
            />
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
