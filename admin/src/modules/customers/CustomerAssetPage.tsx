import React, { useEffect, useMemo, useState } from "react";
import {
  createCustomerAddress,
  createCustomerProfile,
  deleteCustomerAddress,
  fetchCustomerAssets,
  fetchCustomerDetail,
  fetchWalletTransactions,
  grantWalletMeals,
  deductWalletMeals,
  updateCustomerAddress,
  updateCustomerProfile
} from "../../shared/api/http";
import type {
  CustomerAssetResponse,
  CustomerAddressItem,
  CustomerAddressMutationPayload,
  CustomerDetailResponse,
  WalletTransactionResponse
} from "../../shared/api/types";
import { MinusCircle, PlusCircle, RotateCcw, Search, UserPlus, X } from "lucide-react";
import {
  buildCustomerActionLabels,
  buildCustomerAssetStats,
  buildVisibleCustomerAddresses,
  buildCustomerOverviewSummary,
  filterCustomerAssets,
  normalizeInitialMealsValue,
  resolvePrimaryCustomerAddress,
  shouldShowAddressExpandToggle,
  resolveCustomerStatusLabel,
  type CustomerBalanceState,
  type CustomerOrderModeFilter,
  type CustomerRemainingValidityState
} from "./customerAssetPage.helpers";
import { formatDateTimeLabel } from "../../shared/utils/dateTime";
import { AppSelect } from "../../shared/components/AppSelect";
import { AdminDialog } from "../../shared/components/AdminDialog";
import { AsyncContentView } from "../../shared/components/AsyncContentView";
import { DatePicker } from "../../shared/components/DatePicker";
import { RemarkField } from "../../shared/components/RemarkField";
import { SafeInput } from "../../shared/components/SafeInput";
import { toast } from "../../shared/components/Toast";
import { TooltipHint } from "../../shared/components/TooltipHint";
import { CustomerCreateDialog } from "./components/CustomerCreateDialog";
import { CustomerDeductDialog } from "./components/CustomerDeductDialog";

const emptyEditForm = {
  name: "",
  phone: "",
  remark: "",
  customerStatus: "FORMAL",
  openedAt: "",
  expiredAt: "",
  remainingValidityDays: "",
  initialMeals: "0",
  initialValidityDays: "30",
  addressLine: ""
};
const defaultGrantForm = { mealDelta: "5", validityDays: "30", remark: "补餐" };
const defaultDeductForm = { mealDelta: "1", remark: "手工扣减" };
const emptyAddressForm = {
  contactName: "",
  contactPhone: "",
  addressLine: "",
  areaCode: "",
  isDefault: false
};

function normalizeCustomerName(value: string) {
  return String(value || "").trim();
}

function normalizeCustomerPhone(value: string) {
  return String(value || "").replace(/\D/g, "");
}

function isValidCustomerName(value: string) {
  return /^[\u4e00-\u9fa5A-Za-z·\s]{2,20}$/.test(normalizeCustomerName(value));
}

function isValidCustomerPhone(value: string) {
  return /^1\d{10}$/.test(normalizeCustomerPhone(value));
}

function validateCustomerForm(input: { name: string; phone: string; addressLine?: string }) {
  if (!normalizeCustomerName(input.name)) {
    return "请填写客户姓名";
  }
  if (!isValidCustomerName(input.name)) {
    return "请填写正确的客户姓名";
  }
  if (!normalizeCustomerPhone(input.phone)) {
    return "请填写手机号";
  }
  if (!isValidCustomerPhone(input.phone)) {
    return "请填写正确的11位手机号";
  }
  if (typeof input.addressLine === "string") {
    const addressLine = input.addressLine.trim();
    if (addressLine.length < 4 || addressLine.length > 120) {
      return "收货地址长度需在4到120个字符之间";
    }
  }
  return "";
}

function resolveErrorMessage(error: unknown, fallback = "操作失败") {
  if (typeof error === "object" && error !== null) {
    const errorLike = error as {
      response?: { data?: { message?: string } };
      message?: string;
    };
    return errorLike.response?.data?.message || errorLike.message || fallback;
  }
  return typeof error === "string" ? error : fallback;
}

function findDuplicateCustomer(items: CustomerAssetResponse[], current: { name: string; phone: string }, excludeId?: number | null) {
  const targetName = normalizeCustomerName(current.name);
  const targetPhone = normalizeCustomerPhone(current.phone);
  return items.find((item) => {
    if (excludeId != null && item.id === excludeId) {
      return false;
    }
    return normalizeCustomerName(item.name) === targetName || normalizeCustomerPhone(item.phone) === targetPhone;
  }) || null;
}

function buildEditForm(
  detail: CustomerDetailResponse | null,
  fallback: CustomerAssetResponse | null
): typeof emptyEditForm {
  const openedAt = String(detail?.openedAt || detail?.wallet?.openedAt || fallback?.openedAt || "");
  const expiredAt = String(detail?.expiredAt || detail?.wallet?.expiredAt || fallback?.packageExpiredAt || "");
  const remainingValidityDays = detail?.remainingValidityDays
    ?? detail?.wallet?.remainingValidityDays
    ?? fallback?.remainingValidityDays
    ?? "";
  return {
    name: String(detail?.name || fallback?.name || ""),
    phone: String(detail?.phone || fallback?.phone || ""),
    remark: String(detail?.merchantRemark || fallback?.merchantRemark || ""),
    customerStatus: String(detail?.customerStatus || fallback?.customerStatus || "FORMAL"),
    openedAt: normalizeDateTimeLocalInput(openedAt),
    expiredAt: normalizeDateInput(expiredAt),
    remainingValidityDays: remainingValidityDays === "" ? "" : String(remainingValidityDays),
    initialMeals: "0",
    initialValidityDays: "30",
    addressLine: ""
  };
}

function normalizeDateInput(value?: string | null) {
  return String(value || "").trim().slice(0, 10);
}

function normalizeDateTimeLocalInput(value?: string | null) {
  return String(value || "").trim().slice(0, 16);
}

function resolveDatePart(value?: string | null) {
  return normalizeDateInput(value);
}

function resolveDateTimeLocalValue(date: string, currentValue?: string | null) {
  if (!date) {
    return "";
  }
  const normalizedCurrent = normalizeDateTimeLocalInput(currentValue);
  const timePart = normalizedCurrent.includes("T") ? normalizedCurrent.split("T")[1] : "";
  return `${date}T${timePart || "00:00"}`;
}

function normalizeRemainingValidityInput(value: string) {
  return String(value || "").replace(/[^\d-]/g, "");
}

function resolveRemainingValidityDaysFromDate(expiredAt: string) {
  if (!expiredAt) {
    return "";
  }
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const target = new Date(`${expiredAt}T00:00:00`);
  if (Number.isNaN(target.getTime())) {
    return "";
  }
  const diff = Math.round((target.getTime() - today.getTime()) / (24 * 60 * 60 * 1000));
  return String(diff);
}

function resolveExpiredAtFromRemainingDays(remainingValidityDays: string) {
  if (remainingValidityDays === "") {
    return "";
  }
  const parsed = Number(remainingValidityDays);
  if (!Number.isFinite(parsed)) {
    return "";
  }
  const target = new Date();
  target.setHours(0, 0, 0, 0);
  target.setDate(target.getDate() + parsed);
  const year = target.getFullYear();
  const month = String(target.getMonth() + 1).padStart(2, "0");
  const day = String(target.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function buildPackageExpiryLabel(expiredAt?: string | null) {
  return expiredAt || "未设置";
}

function buildRemainingValidityLabel(expiredAt?: string | null, remainingValidityDays?: number | null) {
  if (!expiredAt) {
    return "-";
  }
  if ((remainingValidityDays ?? 0) < 0) {
    return "已过期";
  }
  if ((remainingValidityDays ?? 0) === 0) {
    return "今日到期";
  }
  return `剩余 ${remainingValidityDays ?? 0} 天`;
}

function shouldRenderPackageAlert(alertLabel?: string | null, alertCode?: string | null) {
  return Boolean(alertLabel) && alertCode !== "EXPIRED";
}

function resolveCustomerAddresses(detail: CustomerDetailResponse | null) {
  return Array.isArray(detail?.addresses) ? detail.addresses as CustomerAddressItem[] : [];
}

function buildAddressForm(address?: CustomerAddressItem | null) {
  if (!address) {
    return emptyAddressForm;
  }
  return {
    contactName: address.contactName,
    contactPhone: address.contactPhone,
    addressLine: address.addressLine,
    areaCode: address.areaCode ?? "",
    isDefault: address.isDefault
  };
}

export function CustomerAssetPage() {
  const [selectedRowIds, setSelectedRowIds] = useState<Set<number>>(new Set());
  const [items, setItems] = useState<CustomerAssetResponse[]>([]);
  const [activeItem, setActiveItem] = useState<CustomerAssetResponse | null>(null);
  const [isDeductOpen, setIsDeductOpen] = useState(false);
  const [isDetailOpen, setIsDetailOpen] = useState(false);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isCreating, setIsCreating] = useState(false);
  const [detailMode, setDetailMode] = useState<"view" | "edit">("view");
  const [grantForm, setGrantForm] = useState(defaultGrantForm);
  const [deductForm, setDeductForm] = useState(defaultDeductForm);
  const [editForm, setEditForm] = useState(emptyEditForm);
  const [keywordFilter, setKeywordFilter] = useState("");
  const [customerStatusFilter, setCustomerStatusFilter] = useState("ALL");
  const [balanceStateFilter, setBalanceStateFilter] = useState<CustomerBalanceState>("ALL");
  const [orderModeFilter, setOrderModeFilter] = useState<CustomerOrderModeFilter>("ALL");
  const [remainingValidityStateFilter, setRemainingValidityStateFilter] = useState<CustomerRemainingValidityState>("ALL");
  const [transactions, setTransactions] = useState<WalletTransactionResponse[]>([]);
  const [detail, setDetail] = useState<CustomerDetailResponse | null>(null);
  const [isAddressExpanded, setIsAddressExpanded] = useState(false);
  const [isAddressEditorOpen, setIsAddressEditorOpen] = useState(false);
  const [editingAddressId, setEditingAddressId] = useState<number | null>(null);
  const [deleteAddressTarget, setDeleteAddressTarget] = useState<CustomerAddressItem | null>(null);
  const [addressForm, setAddressForm] = useState(emptyAddressForm);
  const [submittingProfile, setSubmittingProfile] = useState(false);
  const [submittingAddress, setSubmittingAddress] = useState(false);
  const [submittingAddressActionId, setSubmittingAddressActionId] = useState<number | null>(null);
  const [submittingGrant, setSubmittingGrant] = useState(false);
  const [submittingCreate, setSubmittingCreate] = useState(false);
  const [submittingDeduct, setSubmittingDeduct] = useState(false);
  const deductCount = Number(deductForm.mealDelta || 0);
  const remainingMeals = activeItem?.remainingMeals ?? 0;
  const deductDisabled = remainingMeals <= 0 || deductCount <= 0 || remainingMeals < deductCount;

  const [detailActionLabel, grantActionLabel, deductActionLabel] = useMemo(() => buildCustomerActionLabels(), []);

  const detailAddresses = useMemo(() => resolveCustomerAddresses(detail), [detail]);
  const primaryAddress = useMemo(
    () => resolvePrimaryCustomerAddress(detailAddresses),
    [detailAddresses]
  );
  const visibleAddresses = useMemo(
    () => buildVisibleCustomerAddresses(detailAddresses, isAddressExpanded),
    [detailAddresses, isAddressExpanded]
  );
  const showAddressExpandToggle = useMemo(
    () => shouldShowAddressExpandToggle(detailAddresses),
    [detailAddresses]
  );

  useEffect(() => {
    reloadCustomers().catch((error) => toast(resolveErrorMessage(error, "加载客户列表失败"), "error"));
  }, []);

  async function reloadCustomers() {
    const page = await fetchCustomerAssets();
    setItems(page.items);
    return page.items;
  }

  async function loadCustomerWorkspace(item: CustomerAssetResponse) {
    const [detailResponse, txPage] = await Promise.all([
      fetchCustomerDetail(item.id),
      fetchWalletTransactions(item.id)
    ]);
    setActiveItem(item);
    setDetail(detailResponse);
    setTransactions(txPage.items);
    setEditForm(buildEditForm(detailResponse, item));
    setIsAddressExpanded(false);
    resetAddressEditor();
  }

  async function refreshCustomerWorkspace(item: CustomerAssetResponse) {
    const nextItems = await reloadCustomers();
    const nextItem = nextItems.find((candidate) => candidate.id === item.id) ?? item;
    await loadCustomerWorkspace(nextItem);
  }

  function resetAddressEditor() {
    setEditingAddressId(null);
    setAddressForm(emptyAddressForm);
    setIsAddressEditorOpen(false);
  }

  function handleStartCreateAddress() {
    setEditingAddressId(null);
    setAddressForm({
      ...emptyAddressForm,
      isDefault: detailAddresses.length === 0
    });
    setIsAddressEditorOpen(true);
  }

  function handleStartEditAddress(address: CustomerAddressItem) {
    setEditingAddressId(address.id);
    setAddressForm(buildAddressForm(address));
    setIsAddressEditorOpen(true);
  }

  function buildAddressPayload(form: typeof emptyAddressForm): CustomerAddressMutationPayload {
    const boundName = normalizeCustomerName(String(detail?.name || activeItem?.name || ""));
    const boundPhone = normalizeCustomerPhone(String(detail?.phone || activeItem?.phone || ""));
    return {
      contactName: boundName,
      contactPhone: boundPhone,
      addressLine: form.addressLine.trim(),
      areaCode: form.areaCode.trim(),
      isDefault: form.isDefault
    };
  }

  async function handleAddressSubmit() {
    if (!activeItem) return;
    const payload = buildAddressPayload(addressForm);
    const validationError = validateCustomerForm({
      name: payload.contactName,
      phone: payload.contactPhone,
      addressLine: payload.addressLine
    });
    if (validationError) {
      toast(validationError, "error");
      return;
    }
    if (submittingAddress) {
      return;
    }
    setSubmittingAddress(true);
    try {
      if (editingAddressId == null) {
        await createCustomerAddress(activeItem.id, payload);
      } else {
        await updateCustomerAddress(activeItem.id, editingAddressId, payload);
      }
      await refreshCustomerWorkspace(activeItem);
      setIsAddressExpanded(true);
      resetAddressEditor();
    } finally {
      setSubmittingAddress(false);
    }
  }

  async function handleSetDefaultAddress(address: CustomerAddressItem) {
    if (!activeItem) return;
    if (submittingAddress) {
      return;
    }
    setSubmittingAddress(true);
    setSubmittingAddressActionId(address.id);
    try {
      await updateCustomerAddress(activeItem.id, address.id, {
        contactName: address.contactName,
        contactPhone: address.contactPhone,
        addressLine: address.addressLine,
        areaCode: address.areaCode ?? "",
        isDefault: true
      });
      await refreshCustomerWorkspace(activeItem);
      setIsAddressExpanded(true);
    } finally {
      setSubmittingAddress(false);
      setSubmittingAddressActionId(null);
    }
  }

  function handleRequestDeleteAddress(address: CustomerAddressItem) {
    setDeleteAddressTarget(address);
  }

  async function handleDeleteAddress() {
    if (!activeItem) return;
    if (!deleteAddressTarget) return;
    if (submittingAddress) {
      return;
    }
    setSubmittingAddress(true);
    setSubmittingAddressActionId(deleteAddressTarget.id);
    try {
      await deleteCustomerAddress(activeItem.id, deleteAddressTarget.id);
      await refreshCustomerWorkspace(activeItem);
      setIsAddressExpanded(true);
      resetAddressEditor();
      setDeleteAddressTarget(null);
    } finally {
      setSubmittingAddress(false);
      setSubmittingAddressActionId(null);
    }
  }

  async function handleGrantSubmit() {
    if (!activeItem || !grantForm.mealDelta) return;
    if (Number(grantForm.validityDays || 0) <= 0) {
      toast("请填写有效期天数", "error");
      return;
    }
    if (submittingGrant) {
      return;
    }
    setSubmittingGrant(true);
    try {
      await grantWalletMeals(
        activeItem.id,
        Number(grantForm.mealDelta),
        Number(grantForm.validityDays),
        grantForm.remark || "补餐"
      );
      setGrantForm(defaultGrantForm);
      await refreshCustomerWorkspace(activeItem);
    } finally {
      setSubmittingGrant(false);
    }
  }

  async function handleDeductSubmit() {
    if (!activeItem || !deductForm.mealDelta) return;
    if (deductDisabled) {
      toast("余额不足", "error");
      return;
    }
    if (submittingDeduct) {
      return;
    }
    setSubmittingDeduct(true);
    try {
      await deductWalletMeals(activeItem.id, Number(deductForm.mealDelta), deductForm.remark || "手工扣减");
      setIsDeductOpen(false);
      setDeductForm(defaultDeductForm);
      await reloadCustomers();
    } finally {
      setSubmittingDeduct(false);
    }
  }

  async function handleOpenDetail(item: CustomerAssetResponse) {
    setDetailMode("view");
    await loadCustomerWorkspace(item);
    setIsDetailOpen(true);
  }

  function handleOpenCreate() {
    setIsCreating(true);
    setActiveItem(null);
    setDetail(null);
    setEditForm(emptyEditForm);
    setIsEditOpen(true);
  }

  function handleStartInlineEdit() {
    setEditForm(buildEditForm(detail, activeItem));
    setDetailMode("edit");
  }

  function handleCancelInlineEdit() {
    setEditForm(buildEditForm(detail, activeItem));
    setDetailMode("view");
  }

  async function handleCreateSubmit() {
    const validationError = validateCustomerForm({
      name: editForm.name,
      phone: editForm.phone,
      addressLine: String(editForm.addressLine || "")
    });
    if (validationError) {
      toast(validationError, "error");
      return;
    }
    const duplicate = findDuplicateCustomer(items, { name: editForm.name, phone: editForm.phone });
    if (duplicate) {
      if (normalizeCustomerName(duplicate.name) === normalizeCustomerName(editForm.name)) {
        toast("姓名已存在", "error");
        return;
      }
      if (normalizeCustomerPhone(duplicate.phone) === normalizeCustomerPhone(editForm.phone)) {
        toast("手机号已存在", "error");
        return;
      }
    }
    if (!editForm.addressLine) {
      toast("请填写收货地址", "error");
      return;
    }
    const meals = Number(editForm.initialMeals);
    const validityDays = Number(editForm.initialValidityDays);
    if (meals > 0 && validityDays <= 0) {
      toast("请填写初始有效期天数", "error");
      return;
    }
    if (submittingCreate) {
      return;
    }
    setSubmittingCreate(true);
    try {
      const newCustomer = await createCustomerProfile({
        name: normalizeCustomerName(editForm.name),
        phone: normalizeCustomerPhone(editForm.phone),
        merchantRemark: editForm.remark,
        addressLine: editForm.addressLine,
        contactName: normalizeCustomerName(editForm.name),
        contactPhone: normalizeCustomerPhone(editForm.phone),
        initialMealDelta: meals,
        initialMealRemark: meals > 0 ? "建档初始加餐" : "",
        initialValidityDays: meals > 0 ? validityDays : undefined
      });

      setIsEditOpen(false);
      setIsCreating(false);
      setEditForm(emptyEditForm);
      await reloadCustomers();
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "创建用户失败，请检查输入或重试", "error");
    } finally {
      setSubmittingCreate(false);
    }
  }

  async function handleInlineEditSubmit() {
    if (!activeItem) return;
    const validationError = validateCustomerForm({
      name: editForm.name,
      phone: editForm.phone
    });
    if (validationError) {
      toast(validationError, "error");
      return;
    }
    const duplicate = findDuplicateCustomer(items, { name: editForm.name, phone: editForm.phone }, activeItem.id);
    if (duplicate) {
      if (normalizeCustomerName(duplicate.name) === normalizeCustomerName(editForm.name)) {
        toast("姓名已存在", "error");
        return;
      }
      if (normalizeCustomerPhone(duplicate.phone) === normalizeCustomerPhone(editForm.phone)) {
        toast("手机号已存在", "error");
        return;
      }
    }
    if (submittingProfile) {
      return;
    }
    setSubmittingProfile(true);
    try {
      await updateCustomerProfile(activeItem.id, {
        name: normalizeCustomerName(editForm.name),
        phone: normalizeCustomerPhone(editForm.phone),
        merchantRemark: editForm.remark,
        customerStatus: editForm.customerStatus,
        openedAt: editForm.openedAt || null,
        expiredAt: editForm.expiredAt || null,
        remainingValidityDays: editForm.remainingValidityDays === "" ? null : Number(editForm.remainingValidityDays)
      });
      await refreshCustomerWorkspace(activeItem);
      setDetailMode("view");
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "保存失败", "error");
    } finally {
      setSubmittingProfile(false);
    }
  }

  const stats = useMemo(() => buildCustomerAssetStats(items), [items]);
  const overviewSummary = useMemo(() => buildCustomerOverviewSummary(stats), [stats]);

  const filteredItems = useMemo(
    () => filterCustomerAssets(items, {
      keyword: keywordFilter,
      customerStatus: customerStatusFilter,
      balanceState: balanceStateFilter,
      orderMode: orderModeFilter,
      remainingValidityState: remainingValidityStateFilter
    }),
    [items, keywordFilter, customerStatusFilter, balanceStateFilter, orderModeFilter, remainingValidityStateFilter]
  );

  const isAllSelected = filteredItems.length > 0 && selectedRowIds.size === filteredItems.length;
  const isIndeterminate = selectedRowIds.size > 0 && selectedRowIds.size < filteredItems.length;

  function handleSelectAll() {
    if (isAllSelected) {
      setSelectedRowIds(new Set());
    } else {
      setSelectedRowIds(new Set(filteredItems.map(item => item.id)));
    }
  }

  function handleSelectRow(id: number) {
    const nextSelected = new Set(selectedRowIds);
    if (nextSelected.has(id)) {
      nextSelected.delete(id);
    } else {
      nextSelected.add(id);
    }
    setSelectedRowIds(nextSelected);
  }

  function resetCustomerFilters() {
    setKeywordFilter("");
    setCustomerStatusFilter("ALL");
    setBalanceStateFilter("ALL");
    setOrderModeFilter("ALL");
    setRemainingValidityStateFilter("ALL");
  }

  function resolveWalletTransactionTypeLabel(type: string) {
    if (type === "OPEN") return "开卡";
    if (type === "GRANT") return "后台发放";
    if (type === "RESERVE") return "下单占用";
    if (type === "RELEASE") return "取消释放";
    if (type === "MANUAL_DEDUCT") return "手工扣减";
    if (type === "AFTERSALE_ROLLBACK") return "售后回滚";
    return type || "餐次变动";
  }

  const renderActions = (item: CustomerAssetResponse) => (
    <div className="customer-action-group">
      <button type="button" className="customer-action-btn customer-action-btn--primary" onClick={() => handleOpenDetail(item).catch((err) => toast(resolveErrorMessage(err, "打开客户详情失败"), "error"))}>
        {detailActionLabel}
      </button>
      <button type="button" className="customer-action-btn customer-action-btn--accent" onClick={() => handleOpenDetail(item).catch(() => undefined)}>
        <PlusCircle size={14} />
        加餐
      </button>
      <button
        type="button"
        className="customer-action-btn customer-action-btn--success"
        onClick={() => handleOpenDetail(item).catch((err) => toast(resolveErrorMessage(err, "打开客户补餐失败"), "error"))}
      >
        <PlusCircle size={14} />
        {grantActionLabel}
      </button>
      <button
        type="button"
        className="customer-action-btn customer-action-btn--danger"
        onClick={() => {
          setActiveItem(item);
          setDeductForm(defaultDeductForm);
          setIsDeductOpen(true);
        }}
      >
        <MinusCircle size={14} />
        {deductActionLabel}
      </button>
    </div>
  );

  return (
    <div className="customer-asset-page">
      <div className="page-header">
        <div>
          <h2 className="page-title">客户经营中心</h2>
        </div>
        <div className="customer-page-header-actions">
          <button className="btn btn-primary" onClick={handleOpenCreate}>
            <UserPlus size={16} />
            新建客户档案
          </button>
        </div>
      </div>

      <div className="customer-kpi-grid">
        {overviewSummary.map((item) => (
          <section key={item.label} className={`customer-kpi-card customer-kpi-card--${item.tone}`}>
            <span className="customer-kpi-card__label">{item.label}</span>
            <strong className="customer-kpi-card__value">{item.value}</strong>
          </section>
        ))}
      </div>

      <div className="toolbar">
        <div className="customer-filter-toolbar">
          <div className="customer-filter-toolbar__row">
          <SafeInput
            type="text"
            className="input-box customer-filter-input"
            placeholder="搜索客户手机号/姓名/商家备注"
            value={keywordFilter}
            onValueChange={setKeywordFilter}
          />
          <AppSelect
            className="app-select--filter customer-filter-select"
            value={customerStatusFilter}
            options={[
              { label: "全部状态", value: "ALL" },
              { label: "正式客户", value: "FORMAL" },
              { label: "沉睡客户", value: "DORMANT" }
            ]}
            onChange={(value) => setCustomerStatusFilter(value)}
          />
          <AppSelect
            className="app-select--filter customer-filter-select"
            value={balanceStateFilter}
            options={[
              { label: "全部余额", value: "ALL" },
              { label: "有余额", value: "HAS_BALANCE" },
              { label: "无余额", value: "NO_BALANCE" },
              { label: "低余额", value: "LOW_BALANCE" }
            ]}
            onChange={(value) => setBalanceStateFilter(value as CustomerBalanceState)}
          />
          <AppSelect
            className="app-select--filter customer-filter-select"
            value={orderModeFilter}
            options={[
              { label: "全部订餐方式", value: "ALL" },
              { label: "普通下单", value: "NORMAL" },
              { label: "固定订餐", value: "SUBSCRIPTION" }
            ]}
            onChange={(value) => setOrderModeFilter(value as CustomerOrderModeFilter)}
          />
          <AppSelect
            className="app-select--filter customer-filter-select"
            value={remainingValidityStateFilter}
            options={[
              { label: "全部天数状态", value: "ALL" },
              { label: "有效中", value: "VALID" },
              { label: "即将到期", value: "EXPIRING_SOON" },
              { label: "已过期", value: "EXPIRED" },
              { label: "未设置", value: "NO_EXPIRY" }
            ]}
            onChange={(value) => setRemainingValidityStateFilter(value as CustomerRemainingValidityState)}
          />
          <button className="btn btn-primary" onClick={() => reloadCustomers().catch((err) => toast(resolveErrorMessage(err, "刷新客户列表失败"), "error"))}><Search size={16} /> 刷新</button>
          <button
            className="btn btn-outline"
            onClick={() => {
              resetCustomerFilters();
              reloadCustomers().catch((err) => toast(resolveErrorMessage(err, "重置后刷新客户列表失败"), "error"));
            }}
          >
            <RotateCcw size={16} /> 重置
          </button>
          </div>
          <div className="customer-filter-toolbar__row customer-filter-toolbar__row--secondary">
            <div className="customer-filter-status">
              <span>当前筛出 {filteredItems.length} 人</span>
            </div>
          </div>
        </div>
      </div>
      <div className="table-container">
        <div className="table-header-toolbar">
          <div className="customer-table-toolbar-main">
            <span>客户列表 ({filteredItems.length} 人)</span>
          </div>
        </div>

        {selectedRowIds.size > 0 && (
          <div className="table-bulk-bar">
            <div className="table-bulk-bar__left">
              <span className="table-bulk-bar__count">已选择 {selectedRowIds.size} 项</span>
            </div>
            <div className="table-bulk-bar__actions">
              <button className="btn btn-outline btn-compact" onClick={() => setSelectedRowIds(new Set())}>取消选中</button>
              <button className="btn btn-primary btn-compact" onClick={() => toast("批量操作暂未接入", "info")}>批量加餐</button>
            </div>
          </div>
        )}

        <div className="table-responsive table-responsive--fixed-height">
          <table>
          <thead>
            <tr>
              <th style={{ width: 48, textAlign: "center" }}>
                <input
                  type="checkbox"
                  checked={isAllSelected}
                  ref={(input) => { if (input) input.indeterminate = isIndeterminate; }}
                  onChange={handleSelectAll}
                />
              </th>
              <th>客户姓名</th>
              <th>联系电话</th>
              <th>客户状态</th>
              <th>商家备注</th>
              <th>到期日</th>
              <th>剩余天数</th>
              <th>餐次余额</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {filteredItems.map((item) => {
              const isSelected = selectedRowIds.has(item.id);
              const exhausted = item.status === "EXHAUSTED";
              const statusLabel = resolveCustomerStatusLabel(item.customerStatus);
              const packageExpiryLabel = buildPackageExpiryLabel(item.packageExpiredAt);
              const remainingValidityLabel = buildRemainingValidityLabel(item.packageExpiredAt, item.remainingValidityDays);
              const shouldShowPackageAlert = shouldRenderPackageAlert(item.packageAlertLabel, item.packageAlertCode);
              return (
                <tr key={item.id} className={isSelected ? "row-selected" : ""}>
                  <td style={{ textAlign: "center" }}>
                    <input
                      type="checkbox"
                      checked={isSelected}
                      onChange={() => handleSelectRow(item.id)}
                    />
                  </td>
                  <td>
                    <div className="customer-table-name">
                      <div className="customer-table-name__main">
                        <span style={{ whiteSpace: "nowrap" }}>{item.name}</span>
                      </div>
                    </div>
                  </td>
                  <td>
                    <div className="customer-table-phone">
                      <div className="customer-table-phone__number">{item.phone}</div>
                    </div>
                  </td>
                  <td><span className="customer-status-text" style={{ whiteSpace: "nowrap" }}>{statusLabel}</span></td>
                  <td>
                    <div className="customer-table-note">{item.merchantRemark || "暂无备注"}</div>
                  </td>
                  <td>
                    <div className="customer-table-note">{packageExpiryLabel}</div>
                  </td>
                  <td>
                    <div className="customer-table-note">{remainingValidityLabel}</div>
                    {shouldShowPackageAlert ? <div className="customer-table-note">{`当前提醒：${item.packageAlertLabel}`}</div> : null}
                  </td>
                  <td>
                    <div className="customer-balance-cell">
                      <span className={`customer-balance-cell__value ${exhausted ? "is-danger" : ""}`}>{item.remainingMeals}</span>
                    </div>
                  </td>
                  <td>{renderActions(item)}</td>
                </tr>
              );
            })}
            {filteredItems.length === 0 && (
              <tr>
                <td colSpan={9} style={{ padding: "0" }}>
                  <AsyncContentView status="empty" emptyText="暂无符合条件的客户记录">
                    <div style={{ marginTop: 16 }}>
                      <button className="btn btn-primary" onClick={handleOpenCreate}>
                        <UserPlus size={16} /> 新增第一个客户
                      </button>
                    </div>
                  </AsyncContentView>
                </td>
              </tr>
            )}
          </tbody>
          </table>
        </div>

        {/* Mobile View */}
        <div className="mobile-card-list">
          {filteredItems.map((item) => {
            const exhausted = item.status === "EXHAUSTED";
            const statusLabel = resolveCustomerStatusLabel(item.customerStatus);
            const packageExpiryLabel = buildPackageExpiryLabel(item.packageExpiredAt);
            const remainingValidityLabel = buildRemainingValidityLabel(item.packageExpiredAt, item.remainingValidityDays);
            const shouldShowPackageAlert = shouldRenderPackageAlert(item.packageAlertLabel, item.packageAlertCode);
            return (
              <div className="mobile-card" key={item.id}>
                <div className="mobile-card-header">
                  <div>
                    <div className="customer-mobile-name-row">
                      <span className="customer-mobile-name-row__name">{item.name}</span>
                      <span className="customer-mobile-name-row__phone">{item.phone}</span>
                    </div>
                  </div>
                  <div className="customer-mobile-status">{statusLabel}</div>
                </div>
                <div className="customer-mobile-summary">
                  <div className="customer-mobile-summary__item">
                    <span className="customer-mobile-summary__label">余额</span>
                    <span className={`customer-mobile-summary__value ${exhausted ? "is-danger" : ""}`}>{item.remainingMeals}</span>
                  </div>
                </div>
                <div className="mobile-card-row">
                  <div className="mobile-card-label">商家备注</div>
                  <div className="mobile-card-value">{item.merchantRemark || "-"}</div>
                </div>
                <div className="mobile-card-row">
                  <div className="mobile-card-label">餐包有效期</div>
                  <div className="mobile-card-value">{packageExpiryLabel}</div>
                </div>
                <div className="mobile-card-row">
                  <div className="mobile-card-label">剩余天数</div>
                  <div className="mobile-card-value">{remainingValidityLabel}</div>
                </div>
                {shouldShowPackageAlert ? (
                  <div className="mobile-card-row">
                    <div className="mobile-card-label">当前提醒</div>
                    <div className="mobile-card-value">{item.packageAlertLabel}</div>
                  </div>
                ) : null}
                <div className="mobile-card-footer">
                  {renderActions(item)}
                </div>
              </div>
            );
          })}
          {filteredItems.length === 0 && (
            <AsyncContentView status="empty" emptyText="当前筛选条件下没有客户">
              <div style={{ marginTop: 16 }}>
                <button className="btn btn-primary" onClick={handleOpenCreate}>
                  <UserPlus size={16} /> 新增第一个客户
                </button>
              </div>
            </AsyncContentView>
          )}
        </div>
      </div>

      {/* Modals */}
      {isDetailOpen && activeItem && (
        <div className="modal-overlay">
          <div className="modal-content modal-content--customer-detail">
            <div className="modal-header">
              <span>详情资料 - {activeItem.name}</span>
              <span className="modal-close" onClick={() => setIsDetailOpen(false)}><X size={20} /></span>
            </div>
            <div className="modal-body customer-detail-modal customer-detail-workbench">
              <div className="customer-detail-hero">
                <div className="customer-detail-hero__main">
                  <div className="customer-detail-hero__name">{String(detail?.name || activeItem.name)}</div>
                  <div className="customer-detail-hero__meta">
                    <span>{String(detail?.phone || activeItem.phone)}</span>
                    <span>{resolveCustomerStatusLabel(String(detail?.customerStatus || activeItem.customerStatus))}</span>
                  </div>
                </div>
                <div className="customer-detail-hero__actions">
                  {detailMode === "edit" ? (
                    <>
                      <button className="btn btn-outline" disabled={submittingProfile} onClick={handleCancelInlineEdit}>取消编辑</button>
                      <button className="btn btn-primary" disabled={submittingProfile} onClick={() => handleInlineEditSubmit().catch((err) => toast(resolveErrorMessage(err, "保存资料失败"), "error"))}>{submittingProfile ? "保存中..." : "保存资料"}</button>
                    </>
                  ) : (
                    <button className="btn btn-outline" onClick={handleStartInlineEdit}>编辑资料</button>
                  )}
                </div>
              </div>

              {shouldRenderPackageAlert(
                detail?.wallet?.packageAlertLabel || activeItem.packageAlertLabel,
                detail?.wallet?.packageAlertCode || activeItem.packageAlertCode
              ) ? (
                <div className="customer-detail-note-block" style={{ marginTop: 16 }}>
                  <div className="customer-detail-note-block__label">当前提醒</div>
                  <div className="customer-detail-note-block__value">{String(detail?.wallet?.packageAlertLabel || activeItem.packageAlertLabel)}</div>
                </div>
              ) : null}

              <div className="customer-detail-grid">
                <section className="customer-detail-card">
                  <div className="customer-detail-card__header">
                    <div className="customer-detail-card__title">基础资料</div>
                  </div>
                  {detailMode === "edit" ? (
                    <div className="customer-detail-inline-form">
                      <div className="customer-edit-form-grid">
                        <div className="form-group">
                          <label className="form-label"><span className="required">*</span>客户姓名</label>
                          <SafeInput className="form-control" value={editForm.name} onValueChange={(value) => setEditForm({ ...editForm, name: value })} />
                        </div>
                        <div className="form-group">
                          <label className="form-label">
                            <span className="required">*</span>联系电话
                            <TooltipHint content="就是你平时用的手机号，11 位数字那个" />
                          </label>
                          <SafeInput className="form-control" value={editForm.phone} onValueChange={(value) => setEditForm({ ...editForm, phone: normalizeCustomerPhone(value) })} />
                        </div>
                        <div className="form-group">
                          <label className="form-label">当前余额</label>
                          <input
                            className="form-control"
                            value={`${String(detail?.remainingMeals ?? activeItem.remainingMeals)} 餐`}
                            readOnly
                          />
                        </div>
                        <div className="form-group">
                          <label className="form-label">客户状态</label>
                          <AppSelect
                            value={editForm.customerStatus}
                            options={[
                              { label: "正式客户", value: "FORMAL" },
                              { label: "沉睡客户", value: "DORMANT" }
                            ]}
                            onChange={(val) => setEditForm({ ...editForm, customerStatus: val })}
                            style={{ width: "100%" }}
                          />
                        </div>
                        <div className="form-group">
                          <label className="form-label">开卡时间</label>
                          <DatePicker
                            value={resolveDatePart(editForm.openedAt)}
                            onChange={(date) => setEditForm({ ...editForm, openedAt: resolveDateTimeLocalValue(date, editForm.openedAt) })}
                            showTomorrowShortcut={false}
                          />
                        </div>
                        <div className="form-group">
                          <label className="form-label">到期时间</label>
                          <DatePicker
                            value={editForm.expiredAt}
                            onChange={(date) => setEditForm({
                              ...editForm,
                              expiredAt: date,
                              remainingValidityDays: resolveRemainingValidityDaysFromDate(date)
                            })}
                            showTomorrowShortcut={false}
                          />
                        </div>
                        <div className="form-group">
                          <label className="form-label">剩余天数</label>
                          <SafeInput
                            className="form-control"
                            type="number"
                            value={editForm.remainingValidityDays}
                            onValueChange={(value) => {
                              const nextRemainingValidityDays = normalizeRemainingValidityInput(value);
                              setEditForm({
                                ...editForm,
                                remainingValidityDays: nextRemainingValidityDays,
                                expiredAt: resolveExpiredAtFromRemainingDays(nextRemainingValidityDays)
                              });
                            }}
                          />
                        </div>
                      </div>
                      <div className="customer-detail-inline-form__remark">
                        <div className="admin-panel-note" style={{ marginBottom: 8 }}>
                          长期生效，默认带到后续订单；订单中心填写的商家备注仅此单生效。
                        </div>
                        <RemarkField
                          label="商家备注"
                          value={editForm.remark}
                          onChange={(value) => setEditForm({ ...editForm, remark: value })}
                          placeholder="记录商家侧需要注意的事项"
                          scene="CUSTOMER_REMARK"
                          multiline
                        />
                      </div>
                    </div>
                  ) : (
                    <>
                      <div className="customer-detail-info-list">
                        <div className="customer-detail-info-item">
                          <span className="customer-detail-info-item__label">客户姓名</span>
                          <span className="customer-detail-info-item__value">{String(detail?.name || activeItem.name)}</span>
                        </div>
                        <div className="customer-detail-info-item">
                          <span className="customer-detail-info-item__label">联系电话</span>
                          <span className="customer-detail-info-item__value">{String(detail?.phone || activeItem.phone)}</span>
                        </div>
                        <div className="customer-detail-info-item">
                          <span className="customer-detail-info-item__label">收货地址</span>
                          <span className="customer-detail-info-item__value">{primaryAddress?.addressLine || "未设置"}</span>
                        </div>
                        <div className="customer-detail-info-item">
                          <span className="customer-detail-info-item__label">客户状态</span>
                          <span className="customer-detail-info-item__value">{resolveCustomerStatusLabel(String(detail?.customerStatus || activeItem.customerStatus))}</span>
                        </div>
                        <div className="customer-detail-info-item">
                          <span className="customer-detail-info-item__label">当前余额</span>
                          <span className="customer-detail-info-item__value">{String(detail?.remainingMeals ?? activeItem.remainingMeals)} 餐</span>
                        </div>
                        <div className="customer-detail-info-item">
                          <span className="customer-detail-info-item__label">开卡时间</span>
                          <span className="customer-detail-info-item__value">{String(detail?.openedAt || detail?.wallet?.openedAt || activeItem.openedAt || "未设置")}</span>
                        </div>
                        <div className="customer-detail-info-item">
                          <span className="customer-detail-info-item__label">到期时间</span>
                          <span className="customer-detail-info-item__value">{buildPackageExpiryLabel(detail?.expiredAt || detail?.wallet?.expiredAt || activeItem.packageExpiredAt)}</span>
                        </div>
                        <div className="customer-detail-info-item">
                          <span className="customer-detail-info-item__label">剩余天数</span>
                          <span className="customer-detail-info-item__value">
                            {buildRemainingValidityLabel(
                              detail?.expiredAt || detail?.wallet?.expiredAt || activeItem.packageExpiredAt,
                              detail?.remainingValidityDays ?? detail?.wallet?.remainingValidityDays ?? activeItem.remainingValidityDays
                            )}
                          </span>
                        </div>
                      </div>
                      <div className="customer-detail-note-block">
                        <div className="customer-detail-note-block__label">商家备注</div>
                        <div className="admin-panel-note" style={{ marginBottom: 8 }}>
                          长期生效，默认带到后续订单。
                        </div>
                        <div className="customer-detail-note-block__value">{String(detail?.merchantRemark || activeItem.merchantRemark || "-")}</div>
                      </div>
                    </>
                  )}

                  <div style={{ marginTop: 18, display: "grid", gap: 12 }}>
                    <div className="customer-detail-card__header">
                      <div className="customer-detail-card__title">收货地址</div>
                      <div className="customer-detail-card__actions" style={{ gap: 8, flexWrap: "wrap" }}>
                        {showAddressExpandToggle && (
                          <button
                            type="button"
                            className="btn btn-outline"
                            disabled={submittingAddress}
                            onClick={() => setIsAddressExpanded((current) => !current)}
                          >
                            {isAddressExpanded ? "收起地址" : "展开全部"}
                          </button>
                        )}
                        <button type="button" className="btn btn-outline" disabled={submittingAddress} onClick={handleStartCreateAddress}>
                          {submittingAddress && editingAddressId == null ? "处理中..." : "新增地址"}
                        </button>
                      </div>
                    </div>

                    {visibleAddresses.length === 0 ? (
                      <AsyncContentView status="empty" emptyText="暂无收货地址" />
                    ) : (
                      <div style={{ display: "grid", gap: 12 }}>
                        {visibleAddresses.map((address) => (
                          <div key={address.id} className="customer-detail-note-block">
                            <div
                              style={{
                                display: "flex",
                                justifyContent: "space-between",
                                gap: 12,
                                alignItems: "center",
                                flexWrap: "wrap"
                              }}
                            >
                              <div
                                style={{
                                  display: "flex",
                                  gap: 8,
                                  alignItems: "center",
                                  flexWrap: "wrap",
                                  color: "var(--text-main)",
                                  fontWeight: 700
                                }}
                              >
                                <span>{address.contactName}</span>
                                <span>{address.contactPhone}</span>
                                {address.isDefault && (
                                  <span
                                    style={{
                                      display: "inline-flex",
                                      alignItems: "center",
                                      padding: "2px 8px",
                                      borderRadius: 999,
                                      background: "rgba(37, 99, 235, 0.1)",
                                      color: "var(--primary-color)",
                                      fontSize: 12,
                                      fontWeight: 800
                                    }}
                                  >
                                    默认地址
                                  </span>
                                )}
                              </div>
                              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                                {!address.isDefault && (
                                  <button
                                    type="button"
                                    className="btn btn-outline"
                                    disabled={submittingAddress}
                                    onClick={() => handleSetDefaultAddress(address).catch((err) => toast(err?.response?.data?.message || err.message || String(err), "error"))}
                                  >
                                    {submittingAddress && submittingAddressActionId === address.id ? "处理中..." : "设为默认"}
                                  </button>
                                )}
                                <button
                                  type="button"
                                  className="btn btn-outline"
                                  disabled={submittingAddress}
                                  onClick={() => handleStartEditAddress(address)}
                                >
                                  编辑
                                </button>
                                <button
                                  type="button"
                                  className="btn btn-danger"
                                  disabled={detailAddresses.length <= 1 || submittingAddress}
                                  onClick={() => handleRequestDeleteAddress(address)}
                                >
                                  {submittingAddress && submittingAddressActionId === address.id ? "处理中..." : "删除"}
                                </button>
                              </div>
                            </div>
                            <div className="customer-detail-note-block__value">
                              {address.addressLine}
                              {address.areaCode ? ` (${address.areaCode})` : ""}
                            </div>
                          </div>
                        ))}
                      </div>
                    )}

                    {isAddressEditorOpen && (
                      <div
                        style={{
                          padding: 16,
                          borderRadius: 16,
                          border: "1px solid rgba(203, 213, 225, 0.9)",
                          background: "#fff"
                        }}
                      >
                        <div className="customer-detail-card__header" style={{ marginBottom: 12 }}>
                          <div className="customer-detail-card__title">{editingAddressId == null ? "新增地址" : "编辑地址"}</div>
                          <div className="customer-detail-card__actions">
                            <button type="button" className="btn btn-outline" disabled={submittingAddress} onClick={resetAddressEditor}>取消</button>
                          </div>
                        </div>
                        <div className="customer-edit-form-grid">
                          <div className="form-group">
                            <label className="form-label"><span className="required">*</span>客户姓名（自动同步）</label>
                            <input
                              className="form-control"
                              value={String(detail?.name || activeItem?.name || "")}
                              readOnly
                            />
                          </div>
                          <div className="form-group">
                            <label className="form-label"><span className="required">*</span>客户手机号（自动同步）</label>
                            <input
                              className="form-control"
                              value={String(detail?.phone || activeItem?.phone || "")}
                              readOnly
                            />
                          </div>
                        </div>
                        <div className="form-group" style={{ marginTop: 14 }}>
                          <label className="form-label"><span className="required">*</span>收货地址</label>
                          <SafeInput
                            className="form-control"
                            value={addressForm.addressLine}
                            onValueChange={(value) => setAddressForm({ ...addressForm, addressLine: value })}
                          />
                        </div>
                        <div className="customer-edit-form-grid" style={{ marginTop: 14 }}>
                          <div className="form-group">
                            <label className="form-label">片区</label>
                            <SafeInput
                              className="form-control"
                              value={addressForm.areaCode}
                              onValueChange={(value) => setAddressForm({ ...addressForm, areaCode: value })}
                              placeholder="可选，例如高新区"
                            />
                          </div>
                          <div className="form-group">
                            <label className="form-label">默认地址</label>
                            <label
                              style={{
                                display: "flex",
                                alignItems: "center",
                                gap: 8,
                                minHeight: 44,
                                color: "var(--text-main)",
                                fontWeight: 600
                              }}
                            >
                              <input
                                type="checkbox"
                                checked={addressForm.isDefault}
                                onChange={(e) => setAddressForm({ ...addressForm, isDefault: e.target.checked })}
                              />
                              设为该客户默认地址
                            </label>
                          </div>
                        </div>
                        <div className="customer-detail-card__actions" style={{ marginTop: 16 }}>
                          <button
                            type="button"
                            className="btn btn-primary"
                            disabled={submittingAddress}
                            onClick={() => handleAddressSubmit().catch((err) => toast(resolveErrorMessage(err, "保存地址失败"), "error"))}
                          >
                            {submittingAddress ? "保存中..." : "保存地址"}
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                </section>

                <section className="customer-detail-card">
                  <div className="customer-detail-card__title">补餐</div>
                  <div className="customer-operation-form-grid">
                    <div className="form-group">
                      <label className="form-label"><span className="required">*</span>补餐数量</label>
                      <SafeInput className="form-control" type="number" value={grantForm.mealDelta} onValueChange={(value) => setGrantForm({ ...grantForm, mealDelta: value })} />
                    </div>
                    <div className="form-group">
                      <label className="form-label"><span className="required">*</span>有效期天数</label>
                      <SafeInput className="form-control" type="number" min="1" value={grantForm.validityDays} onValueChange={(value) => setGrantForm({ ...grantForm, validityDays: value })} />
                    </div>
                    <RemarkField
                      label="操作备注"
                      value={grantForm.remark}
                      onChange={(value) => setGrantForm({ ...grantForm, remark: value })}
                      placeholder="例如：客户微信转账续卡，后台补 10 餐"
                      scene="WALLET_REMARK"
                    />
                  </div>
                  <div className="customer-detail-card__actions">
                    <button className="btn btn-primary" disabled={submittingGrant} onClick={() => handleGrantSubmit().catch((err) => toast(resolveErrorMessage(err, "补餐失败"), "error"))}>{submittingGrant ? "补餐中..." : "确认补餐"}</button>
                  </div>
                </section>
              </div>

              <section className="customer-detail-card customer-detail-card--full">
                <div className="customer-detail-card__title">流水记录</div>
                {transactions.length === 0 ? (
                  <AsyncContentView status="empty" emptyText="暂无流水记录" />
                ) : (
                  <div className="customer-transaction-table-wrap">
                    <table className="customer-transaction-table customer-transaction-table--embedded">
                      <thead>
                        <tr>
                          <th>类型</th>
                          <th>变动额</th>
                          <th>备注</th>
                          <th>时间</th>
                        </tr>
                      </thead>
                      <tbody>
                        {transactions.map((tx) => (
                          <tr key={tx.id}>
                            <td>{resolveWalletTransactionTypeLabel(tx.transactionType)}</td>
                            <td>
                              <span className={tx.mealDelta > 0 ? "customer-transaction-delta is-plus" : "customer-transaction-delta is-minus"}>
                                {tx.mealDelta > 0 ? `+${tx.mealDelta}` : tx.mealDelta}
                              </span>
                            </td>
                            <td>{tx.remark || "-"}</td>
                            <td><span className="customer-transaction-time">{formatDateTimeLabel(tx.createdAt)}</span></td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
              </section>
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={() => setIsDetailOpen(false)}>关闭</button>
              <button className="btn btn-primary" disabled={submittingGrant} onClick={() => handleGrantSubmit().catch((err) => toast(resolveErrorMessage(err, "补餐失败"), "error"))}>
                {submittingGrant ? "补餐中..." : grantActionLabel}
              </button>
              <button
                className="btn btn-danger"
                onClick={() => {
                  setIsDetailOpen(false);
                  setDeductForm(defaultDeductForm);
                  setIsDeductOpen(true);
                }}
              >
                {deductActionLabel}
              </button>
            </div>
          </div>
        </div>
      )}

      <CustomerCreateDialog
        open={isEditOpen && isCreating}
        form={editForm}
        submitting={submittingCreate}
        onClose={() => { setIsEditOpen(false); setIsCreating(false); }}
        onSubmit={() => handleCreateSubmit().catch((err) => toast(resolveErrorMessage(err, "创建客户失败"), "error"))}
        onChange={setEditForm}
        normalizeCustomerPhone={normalizeCustomerPhone}
      />

      <CustomerDeductDialog
        open={isDeductOpen}
        activeItem={activeItem}
        deductForm={deductForm}
        submitting={submittingDeduct}
        deductDisabled={deductDisabled}
        remainingMeals={remainingMeals}
        onClose={() => setIsDeductOpen(false)}
        onSubmit={() => handleDeductSubmit().catch((err) => toast(err?.response?.data?.message || err.message || String(err), "error"))}
        onChange={setDeductForm}
      />

      <AdminDialog
        open={!!deleteAddressTarget}
        title="确定要删除吗？"
        description={deleteAddressTarget ? `确认删除地址“${deleteAddressTarget.addressLine}”吗？` : ""}
        width={420}
        onClose={submittingAddress ? () => undefined : () => setDeleteAddressTarget(null)}
        footer={(
          <>
            <button className="btn btn-outline" disabled={submittingAddress} onClick={() => setDeleteAddressTarget(null)}>取消</button>
            <button className="btn-delete" disabled={submittingAddress} onClick={() => handleDeleteAddress().catch((err) => toast(err?.response?.data?.message || err.message || String(err), "error"))}>
              {submittingAddress ? "删除中..." : "确认删除"}
            </button>
          </>
        )}
      >
        <div style={{ color: "var(--text-sub)", padding: "8px 0" }}>
          删除后该配送地址将从客户资料中移除，请确认后再执行。
        </div>
      </AdminDialog>
    </div>
  );
}
