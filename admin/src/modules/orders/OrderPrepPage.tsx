import React, { useEffect, useMemo, useState } from "react";
import * as XLSX from "xlsx";
import {
  assignDispatch,
  deleteDeliveryReceipt,
  deleteOrder,
  cancelSubscriptionConfirmation,
  confirmSubscription,
  createManualOrder,
  fetchDispatchAreaBindings,
  fetchDispatchManagedRiders,
  fetchCurrentMenuWeek,
  fetchOrderPrepList,
  fetchOrderPrepStats,
  fetchSubscriptionConfirmations,
  recordDeliveryReceipt,
  uploadDeliveryReceiptImage,
  fetchSubscriptionPreview,
  bulkImportSubscription,
  fetchRemarkSuggestions,
  updateOrderProfile,
  applyOrderSpecialDispatch,
  clearOrderSpecialDispatch,
  checkSubscriptionPreview,
  createOrderAftersale,
  directRefund,
  searchManualCreateCustomers
} from "../../shared/api/http";
import type { AdminMenuWeekResponse, DispatchAreaBindingResponse, DispatchManagedRiderResponse, ManualCreateCustomerSearchResponse, OrderPrepItemResponse, OrderPrepStatsResponse, SubscriptionConfirmationItem, SubscriptionPreviewItem, SubscriptionPreviewCheckResponse } from "../../shared/api/types";
import {
  buildCrossMealDeliveryRemark,
  buildOrderPrepCompactSummary,
  buildOrderPrepDefaultTab,
  buildSubscriptionConfirmationPanelState,
  buildMealPrepExportRows,
  buildOrderPrepSummary,
  buildOrderPrepView,
  formatOrderNote,
  isCrossMealDelivery,
  mealPeriodLabel,
  resolveMealPeriod,
  resolveOrderDisplayStatus,
  resolveOrderDisplayStatusLabel,
  resolveOrderSourceLabel,
  resolveOrderStatusTone,
  type OrderPrepTab,
  type OrderPrepMealPeriodFilter,
  type OrderPrepRemarkFilter,
  type OrderPrepSourceFilter,
  type OrderPrepStatusFilter
} from "./orderPrepPage.helpers";
import {
  applyManualCreateAddressSelection,
  applyManualCreateCustomerSelection,
  applyManualCreateMealPeriodSelection,
  buildManualCreatePayload,
  createInitialManualCreateForm,
  resolveManualCreateMenuOptions,
  shouldShowManualCustomerEmptyState
} from "./manualCreateOrder.helpers";
import { Printer, CheckCircle, Search, RotateCcw, UserPlus, X, Bot, MapPin, ChevronLeft, ChevronRight, Trash2, AlertTriangle } from "lucide-react";
import { AppSelect } from "../../shared/components/AppSelect";
import { AdminDialog } from "../../shared/components/AdminDialog";
import { RemarkField } from "../../shared/components/RemarkField";
import { DatePicker } from "../../shared/components/DatePicker";
import { toast } from "../../shared/components/Toast";
import { formatLocalDateInputValue } from "../../shared/utils/dateTime";
import { SubscriptionManagementTab, type SubscriptionManagementFilters, type SubscriptionMealPeriod, type SubscriptionStatusFilter } from "./SubscriptionManagementTab";

function defaultFilterDate() {
  return formatLocalDateInputValue();
}

const DEFAULT_FILTER_DATE = defaultFilterDate();
const PAGE_SIZE = 10;
const ORDER_PREP_MEAL_PERIOD_STORAGE_KEY = "admin-order-prep-meal-period";
const MAX_RECEIPT_FILE_SIZE = 5 * 1024 * 1024;
const RECEIPT_UPLOAD_INPUT_ID = "admin-receipt-upload-input";

type ReceiptSelectedFile = {
  name: string;
  size: number;
};

function hasImageValue(value: string | null | undefined) {
  return Boolean(value && value.trim());
}

function formatFileSize(bytes: number) {
  if (bytes >= 1024 * 1024) {
    return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
  }
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

function getReceiptUploadErrorMessage(error: any) {
  if (error?.response?.status === 413) {
    return "图片太大，请上传 5MB 以内的图片";
  }
  return error?.response?.data?.message || error?.message || "上传回执图片失败";
}

function resolveStoredOrderMealPeriod() {
  if (typeof window === "undefined") {
    return "LUNCH" as OrderPrepMealPeriodFilter;
  }
  const storedValue = window.localStorage.getItem(ORDER_PREP_MEAL_PERIOD_STORAGE_KEY);
  return storedValue === "DINNER" ? "DINNER" : "LUNCH";
}

export function OrderPrepPage() {
  const [stats, setStats] = useState<OrderPrepStatsResponse>({
    totalMeals: 105,
    lunchCount: 62,
    dinnerCount: 43,
    selfOrderCount: 85,
    staffOrderCount: 20,
    subscriptionCount: 0,
    adminRemarkCount: 0,
    labelRequiredCount: 0
  });
  const [items, setItems] = useState<OrderPrepItemResponse[]>([]);
  const [confirmationItems, setConfirmationItems] = useState<SubscriptionConfirmationItem[]>([]);

  // Modals state
  const [isManualCreateOpen, setIsManualCreateOpen] = useState(false);
  const [isAssignOpen, setIsAssignOpen] = useState(false);
  const [isReceiptOpen, setIsReceiptOpen] = useState(false);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isSpecialProcessOpen, setIsSpecialProcessOpen] = useState(false);
  const [isSubscriptionPreviewOpen, setIsSubscriptionPreviewOpen] = useState(false);
  const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false);
  const [isOrderDetailOpen, setIsOrderDetailOpen] = useState(false);
  const [activeItem, setActiveItem] = useState<OrderPrepItemResponse | null>(null);
  const [orderAftersaleItem, setOrderAftersaleItem] = useState<OrderPrepItemResponse | null>(null);
  const [orderAftersaleForm, setOrderAftersaleForm] = useState({
    intent: "DIRECT_REFUND",
    reasonText: "商家后台售后处理",
    remark: ""
  });
  const [submittingOrderAftersale, setSubmittingOrderAftersale] = useState(false);

  // Subscription Bulk Import state
  const [previewItems, setPreviewItems] = useState<SubscriptionPreviewItem[]>([]);
  const [isSubmittingImport, setIsSubmittingImport] = useState(false);
  const [subscriptionNoteSuggestions, setSubscriptionNoteSuggestions] = useState<string[]>([]);
  const [previewCheckResult, setPreviewCheckResult] = useState<SubscriptionPreviewCheckResponse | null>(null);
  const [isPreviewCheckOpen, setIsPreviewCheckOpen] = useState(false);

  // Pagination & Filter state
  const [currentPage, setCurrentPage] = useState(1);
  const [activeTab, setActiveTab] = useState<OrderPrepTab>("ORDERS");
  const [hasManualTabSelection, setHasManualTabSelection] = useState(false);
  const [filterDate, setFilterDate] = useState(DEFAULT_FILTER_DATE);
  const [mealPeriodFilter, setMealPeriodFilter] = useState<OrderPrepMealPeriodFilter>(() => resolveStoredOrderMealPeriod());
  const [sourceFilter, setSourceFilter] = useState<OrderPrepSourceFilter>("ALL");
  const [statusFilter, setStatusFilter] = useState<OrderPrepStatusFilter>("ALL");
  const [remarkFilter, setRemarkFilter] = useState<OrderPrepRemarkFilter>("ALL");
  const [keywordFilter, setKeywordFilter] = useState("");
  const [loadingOrders, setLoadingOrders] = useState(false);
  const [ordersError, setOrdersError] = useState("");
  const [subscriptionFilters, setSubscriptionFilters] = useState<SubscriptionManagementFilters>({
    keyword: "",
    statusFilter: "ALL",
    mealPeriod: "LUNCH"
  });
  const [subscriptionQueryVersion, setSubscriptionQueryVersion] = useState(0);
  const [subscriptionVisibleCount, setSubscriptionVisibleCount] = useState(0);

  // Forms state
  const [manualForm, setManualForm] = useState(createInitialManualCreateForm);
  const [manualCustomers, setManualCustomers] = useState<ManualCreateCustomerSearchResponse[]>([]);
  const [manualSelectedCustomer, setManualSelectedCustomer] = useState<ManualCreateCustomerSearchResponse | null>(null);
  const [manualSearchLoading, setManualSearchLoading] = useState(false);
  const [manualMenuWeek, setManualMenuWeek] = useState<AdminMenuWeekResponse | null>(null);
  const [manualMenuLoading, setManualMenuLoading] = useState(false);
  const [submittingManualCreate, setSubmittingManualCreate] = useState(false);
  const [submittingAssign, setSubmittingAssign] = useState(false);
  const [submittingReceipt, setSubmittingReceipt] = useState(false);
  const [uploadingReceipt, setUploadingReceipt] = useState(false);
  const [submittingEdit, setSubmittingEdit] = useState(false);
  const [submittingSpecialProcess, setSubmittingSpecialProcess] = useState(false);
  const [submittingDelete, setSubmittingDelete] = useState(false);
  const [processingConfirmationId, setProcessingConfirmationId] = useState<number | null>(null);
  const [processingConfirmationAction, setProcessingConfirmationAction] = useState<"confirm" | "cancel" | null>(null);
  const [assignForm, setAssignForm] = useState({ riderName: "", areaCode: "" });
  const [receiptForm, setReceiptForm] = useState({ receiptUrl: "", receiptNote: "" });
  const [selectedReceiptFile, setSelectedReceiptFile] = useState<ReceiptSelectedFile | null>(null);
  const [editForm, setEditForm] = useState({
    mealPeriod: "LUNCH",
    quantity: "1",
    deliveryAddress: "",
    merchantRemark: "",
    priorityCustomer: false,
    status: "PENDING_DISPATCH"
  });
  const [specialProcessForm, setSpecialProcessForm] = useState<{
    deliveryMealPeriod: "LUNCH" | "DINNER";
  }>({
    deliveryMealPeriod: "LUNCH"
  });
  const [assignRiders, setAssignRiders] = useState<DispatchManagedRiderResponse[]>([]);
  const [assignAreaBindings, setAssignAreaBindings] = useState<DispatchAreaBindingResponse[]>([]);
  function getErrorMessage(error: any, fallback: string) {
    return error?.response?.data?.message || error?.message || fallback;
  }

  function openReceiptModal(item: OrderPrepItemResponse) {
    setActiveItem(item);
    setReceiptForm({ receiptUrl: item.receiptUrl || "", receiptNote: item.receiptNote || "" });
    setSelectedReceiptFile(null);
    setIsReceiptOpen(true);
  }

  function openOrderDetail(item: OrderPrepItemResponse) {
    setActiveItem(item);
    setIsOrderDetailOpen(true);
  }

  function openEditModal(item: OrderPrepItemResponse) {
    setActiveItem(item);
    setEditForm({
      mealPeriod: resolveMealPeriod(item),
      quantity: String(item.quantity || 1),
      deliveryAddress: item.deliveryAddress || "",
      merchantRemark: item.merchantRemark || "",
      priorityCustomer: Boolean(item.priorityCustomer),
      status: item.status || "PENDING_DISPATCH"
    });
    setIsEditOpen(true);
  }

  function openSpecialProcessModal(item: OrderPrepItemResponse) {
    setActiveItem(item);
    setSpecialProcessForm({
      deliveryMealPeriod: item.deliveryMealPeriod === "DINNER" ? "DINNER" : "LUNCH"
    });
    setIsSpecialProcessOpen(true);
  }

  function openDeleteConfirm(item: OrderPrepItemResponse) {
    setActiveItem(item);
    setIsDeleteConfirmOpen(true);
  }

  useEffect(() => {
    reloadOrders(DEFAULT_FILTER_DATE).catch(() => undefined);

    fetchRemarkSuggestions("SUBSCRIPTION_NOTE")
      .then((response) => setSubscriptionNoteSuggestions(response.items))
      .catch((err) => { console.error('加载备注意见失败', err); setSubscriptionNoteSuggestions([]); });

    fetchDispatchManagedRiders()
      .then(setAssignRiders)
      .catch((err) => { console.error('加载骑手列表失败', err); setAssignRiders([]); });

    fetchDispatchAreaBindings()
      .then(setAssignAreaBindings)
      .catch((err) => { console.error('加载区域绑定失败', err); setAssignAreaBindings([]); });
  }, []);

  useEffect(() => {
    if (!isManualCreateOpen) {
      return;
    }

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
          if (cancelled) {
            return;
          }
          setManualCustomers(data);
        })
        .catch((err) => {
          if (cancelled) {
            return;
          }
          console.error("搜索录单客户失败", err);
          setManualCustomers([]);
        })
        .finally(() => {
          if (!cancelled) {
            setManualSearchLoading(false);
          }
        });
    }, 250);

    return () => {
      cancelled = true;
      window.clearTimeout(timer);
    };
  }, [isManualCreateOpen, manualForm.customerKeyword]);

  useEffect(() => {
    if (!isManualCreateOpen) {
      return;
    }

    let cancelled = false;
    setManualMenuLoading(true);
    fetchCurrentMenuWeek(filterDate)
      .then((week) => {
        if (cancelled) {
          return;
        }
        setManualMenuWeek(week);
      })
      .catch((err) => {
        if (cancelled) {
          return;
        }
        console.error("加载录单菜单失败", err);
        setManualMenuWeek(null);
      })
      .finally(() => {
        if (!cancelled) {
          setManualMenuLoading(false);
        }
      });

    return () => {
      cancelled = true;
    };
  }, [isManualCreateOpen, filterDate]);

  const manualMenuOptions = useMemo(
    () => resolveManualCreateMenuOptions(manualMenuWeek, filterDate),
    [manualMenuWeek, filterDate]
  );

  useEffect(() => {
    if (!isManualCreateOpen || !manualForm.mealPeriod) {
      return;
    }
    setManualForm((current) => applyManualCreateMealPeriodSelection(current, current.mealPeriod as "LUNCH" | "DINNER", manualMenuOptions));
  }, [isManualCreateOpen, manualForm.mealPeriod, manualMenuOptions]);

  const view = useMemo(
    () => buildOrderPrepView(items, {
      keyword: keywordFilter,
      mealPeriod: mealPeriodFilter,
      source: sourceFilter,
      status: statusFilter,
      remark: remarkFilter
    }, currentPage, PAGE_SIZE),
    [items, keywordFilter, mealPeriodFilter, sourceFilter, statusFilter, remarkFilter, currentPage]
  );
  const hasOrderFilters = keywordFilter.trim().length > 0
    || sourceFilter !== "ALL"
    || statusFilter !== "ALL"
    || remarkFilter !== "ALL";
  const emptyOrderText = hasOrderFilters ? "未找到相关数据" : "暂无订单";

  const summary = useMemo(
    () => buildOrderPrepSummary(items, confirmationItems),
    [items, confirmationItems]
  );

  const compactSummary = useMemo(
    () => buildOrderPrepCompactSummary(stats, summary),
    [stats, summary]
  );
  const confirmationPanel = useMemo(
    () => buildSubscriptionConfirmationPanelState(summary.confirmationCount),
    [summary.confirmationCount]
  );

  const assignRiderOptions = useMemo(
    () => assignRiders
      .filter((r) => r.authStatus === "ACTIVE")
      .map((r) => ({ label: r.riderName, value: r.riderName })),
    [assignRiders]
  );

  const assignAreaOptions = useMemo(
    () => Array.from(new Set(assignAreaBindings.map((b) => b.areaCode)))
      .sort((a, b) => a.localeCompare(b, "zh-CN"))
      .map((areaCode) => ({ label: areaCode, value: areaCode })),
    [assignAreaBindings]
  );

  useEffect(() => {
    if (currentPage !== view.currentPage) {
      setCurrentPage(view.currentPage);
    }
  }, [currentPage, view.currentPage]);

  useEffect(() => {
    setCurrentPage(1);
  }, [items, keywordFilter, mealPeriodFilter, sourceFilter, statusFilter, remarkFilter]);

  useEffect(() => {
    if (typeof window !== "undefined") {
      window.localStorage.setItem(ORDER_PREP_MEAL_PERIOD_STORAGE_KEY, mealPeriodFilter);
    }
  }, [mealPeriodFilter]);

  useEffect(() => {
    setActiveTab((currentTab) => {
      // 如果没有待确认项，且当前正好在“待确认”页，则强制切回“订单”页
      if (summary.confirmationCount === 0 && currentTab === "CONFIRMATION") {
        return "ORDERS";
      }
      // 如果手动选择过 Tab，则尊重用户的选择
      if (hasManualTabSelection) {
        return currentTab;
      }
      // 否则，根据业务逻辑自动决定默认 Tab（有待确认则选待确认，否则选订单）
      return buildOrderPrepDefaultTab(summary.confirmationCount);
    });
  }, [summary.confirmationCount, hasManualTabSelection]);

  async function reloadOrders(serveDate = filterDate) {
    setLoadingOrders(true);
    setOrdersError("");
    try {
      const [statsResponse, listResponse, confirmationsResponse] = await Promise.all([
        fetchOrderPrepStats(),
        fetchOrderPrepList(serveDate),
        fetchSubscriptionConfirmations(serveDate)
      ]);
      setStats(statsResponse);
      setItems(listResponse.items);
      setConfirmationItems(confirmationsResponse);
      setCurrentPage(1);
    } catch (err: any) {
      const message = getErrorMessage(err, "加载订单失败");
      setOrdersError(message);
      toast(message, "error");
      throw err;
    } finally {
      setLoadingOrders(false);
    }
  }

  async function handleAutoImportClick() {
    try {
      // 先进行预检
      const checkResult = await checkSubscriptionPreview(filterDate);
      setPreviewCheckResult(checkResult);
      
      // 如果有余额不足的客户，显示预检弹窗
      if (checkResult.insufficientCount > 0) {
        setIsPreviewCheckOpen(true);
        return;
      }
      
      // 如果全部余额充足，直接显示导入预览
      const data = await fetchSubscriptionPreview(filterDate);
      setPreviewItems(data.map(item => ({ ...item, selected: item.hasBalance })));
      setIsSubscriptionPreviewOpen(true);
    } catch (err) {
      toast(getErrorMessage(err, "获取包月预览列表失败"), "error");
    }
  }

  async function handleConfirmPreviewCheck(skipInsufficient: boolean) {
    setIsPreviewCheckOpen(false);
    try {
      const data = await fetchSubscriptionPreview(filterDate);
      if (skipInsufficient) {
        // 仅导入余额充足的客户
        setPreviewItems(data.filter(item => item.hasBalance).map(item => ({ ...item, selected: true })));
      } else {
        setPreviewItems(data.map(item => ({ ...item, selected: item.hasBalance })));
      }
      setIsSubscriptionPreviewOpen(true);
    } catch (err) {
      toast(getErrorMessage(err, "获取包月预览列表失败"), "error");
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
      setIsSubscriptionPreviewOpen(false);
      await reloadOrders();
    } catch (err: any) {
      toast(getErrorMessage(err, "生成失败，请重试"), "error");
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

  async function handleAssignSubmit() {
    if (!activeItem || !assignForm.riderName || !assignForm.areaCode) return;
    if (submittingAssign) return;
    setSubmittingAssign(true);
    try {
      await assignDispatch(activeItem.id, assignForm.riderName, assignForm.areaCode);
      setIsAssignOpen(false);
      await reloadOrders();
      toast("骑手分配成功");
    } catch (err: any) {
      toast(getErrorMessage(err, "分配骑手失败"), "error");
      throw err;
    } finally {
      setSubmittingAssign(false);
    }
  }

  async function handleManualCreateSubmit() {
    if (submittingManualCreate) return;
    setSubmittingManualCreate(true);
    try {
      await createManualOrder(buildManualCreatePayload(manualForm, filterDate));
      closeManualCreateModal();
      await reloadOrders();
      toast("代客订单已录入");
    } catch (err: any) {
      toast(getErrorMessage(err, "录入代客订单失败"), "error");
      throw err;
    } finally {
      setSubmittingManualCreate(false);
    }
  }

  function openManualCreateModal() {
    setManualForm(createInitialManualCreateForm());
    setManualCustomers([]);
    setManualSelectedCustomer(null);
    setManualSearchLoading(false);
    setManualMenuWeek(null);
    setManualMenuLoading(false);
    setIsManualCreateOpen(true);
  }

  function closeManualCreateModal() {
    setIsManualCreateOpen(false);
    setManualForm(createInitialManualCreateForm());
    setManualCustomers([]);
    setManualSelectedCustomer(null);
    setManualSearchLoading(false);
    setManualMenuWeek(null);
    setManualMenuLoading(false);
  }

  function handleManualCustomerSelect(customer: ManualCreateCustomerSearchResponse) {
    setManualSelectedCustomer(customer);
    setManualForm((current) => applyManualCreateCustomerSelection(current, customer));
  }

  function handleManualAddressSelect(addressId: number) {
    if (!manualSelectedCustomer) {
      return;
    }
    setManualForm((current) => applyManualCreateAddressSelection(current, manualSelectedCustomer.addresses, addressId));
  }

  function openOrderAftersaleModal(item: OrderPrepItemResponse) {
    setOrderAftersaleItem(item);
    setOrderAftersaleForm({
      intent: "DIRECT_REFUND",
      reasonText: `订单 #${item.id} 售后处理`,
      remark: ""
    });
  }

  function closeOrderAftersaleModal() {
    setOrderAftersaleItem(null);
    setOrderAftersaleForm({
      intent: "DIRECT_REFUND",
      reasonText: "商家后台售后处理",
      remark: ""
    });
  }

  async function handleOrderAftersaleSubmit() {
    if (!orderAftersaleItem) {
      return;
    }
    const reasonText = orderAftersaleForm.reasonText.trim();
    const remark = orderAftersaleForm.remark.trim();
    if (!reasonText) {
      toast("请填写售后原因", "error");
      return;
    }

    setSubmittingOrderAftersale(true);
    try {
      if (orderAftersaleForm.intent === "DIRECT_REFUND") {
        await directRefund(orderAftersaleItem.id, {
          reasonCode: "ADMIN_DIRECT_REFUND",
          reasonText,
          operatorName: "后台客服"
        });
        toast("订单已直接退款");
      } else {
        await createOrderAftersale(orderAftersaleItem.id, {
          type: "COMPENSATION",
          reasonCode: orderAftersaleForm.intent === "COMPENSATION" ? "ADMIN_COMPENSATION" : "ADMIN_EXCEPTION",
          reasonText,
          operatorName: "后台客服",
          remark: remark || (orderAftersaleForm.intent === "REGISTER_ONLY" ? "已登记异常，等待后续处理" : "请前往售后台账继续处理")
        });
        toast(orderAftersaleForm.intent === "REGISTER_ONLY" ? "异常已登记到售后台账" : "补偿售后已创建");
      }
      closeOrderAftersaleModal();
      await reloadOrders();
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "售后处理失败", "error");
    } finally {
      setSubmittingOrderAftersale(false);
    }
  }

  async function handleDelete(item: OrderPrepItemResponse) {
    if (submittingDelete) return;
    setSubmittingDelete(true);
    try {
      await deleteOrder(item.id);
      setIsDeleteConfirmOpen(false);
      setActiveItem(null);
      await reloadOrders();
      toast("订单已删除");
    } catch (err: any) {
      toast(getErrorMessage(err, "删除订单失败"), "error");
      throw err;
    } finally {
      setSubmittingDelete(false);
    }
  }

  async function handleReceiptSubmit() {
    const receiptUrl = receiptForm.receiptUrl.trim();
    if (!activeItem || !receiptUrl) {
      toast("请先上传回执图片", "error");
      return;
    }
    if (submittingReceipt) return;
    setSubmittingReceipt(true);
    try {
      await recordDeliveryReceipt({
        mealSlotOrderId: activeItem.id,
        receiptUrl,
        receiptNote: receiptForm.receiptNote,
        deliveredAt: new Date().toISOString()
      });
      setIsReceiptOpen(false);
      setReceiptForm({ receiptUrl: "", receiptNote: "" });
      setSelectedReceiptFile(null);
      await reloadOrders();
      toast("回执已提交");
    } catch (err: any) {
      toast(getErrorMessage(err, "提交回执失败"), "error");
      throw err;
    } finally {
      setSubmittingReceipt(false);
    }
  }

  async function handleReceiptDelete() {
    if (!activeItem || submittingReceipt) {
      return;
    }
    setSubmittingReceipt(true);
    try {
      await deleteDeliveryReceipt(activeItem.id);
      setReceiptForm((current) => ({ ...current, receiptUrl: "" }));
      setSelectedReceiptFile(null);
      setActiveItem((current) => (current ? { ...current, receiptUrl: "", receiptNote: "" } : current));
      await reloadOrders();
      toast("回执已删除");
    } catch (err: any) {
      toast(getErrorMessage(err, "删除回执失败"), "error");
      throw err;
    } finally {
      setSubmittingReceipt(false);
    }
  }

  async function handleReceiptFileChange(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) {
      return;
    }
    if (!file.type.startsWith("image/")) {
      toast("请上传 JPG、PNG、WEBP 等图片文件", "error");
      event.target.value = "";
      return;
    }
    if (file.size > MAX_RECEIPT_FILE_SIZE) {
      toast("回执图片不能超过 5MB", "error");
      event.target.value = "";
      return;
    }
    setSelectedReceiptFile({ name: file.name, size: file.size });
    setUploadingReceipt(true);
    try {
      const uploaded = await uploadDeliveryReceiptImage(file);
      setReceiptForm((current) => ({ ...current, receiptUrl: uploaded.url }));
      toast("回执图片已上传");
    } catch (error: any) {
      toast(getReceiptUploadErrorMessage(error), "error");
    } finally {
      setUploadingReceipt(false);
      event.target.value = "";
    }
  }

  async function handleEditSubmit() {
    if (!activeItem || !editForm.mealPeriod || !editForm.deliveryAddress) return;
    if (submittingEdit) return;
    const trimmedAddress = editForm.deliveryAddress.trim();
    setSubmittingEdit(true);
    try {
      await updateOrderProfile(activeItem.id, {
        mealPeriod: editForm.mealPeriod as "LUNCH" | "DINNER",
        quantity: Number(editForm.quantity) || 1,
        deliveryAddress: trimmedAddress,
        merchantRemark: editForm.merchantRemark,
        priorityCustomer: editForm.priorityCustomer,
        status: editForm.status
      });
      setIsEditOpen(false);
      await reloadOrders();
      toast("订单已更新");
    } catch (err: any) {
      toast(getErrorMessage(err, "保存订单失败"), "error");
      throw err;
    } finally {
      setSubmittingEdit(false);
    }
  }

  async function handleSpecialProcessSubmit() {
    if (!activeItem || submittingSpecialProcess) {
      return;
    }
    setSubmittingSpecialProcess(true);
    try {
      await applyOrderSpecialDispatch(activeItem.id, specialProcessForm.deliveryMealPeriod);
      setIsSpecialProcessOpen(false);
      setActiveItem(null);
      await reloadOrders();
      toast("特殊处理已生效");
    } catch (err: any) {
      toast(getErrorMessage(err, "特殊处理失败"), "error");
      throw err;
    } finally {
      setSubmittingSpecialProcess(false);
    }
  }

  async function handleSpecialProcessReset() {
    if (!activeItem || submittingSpecialProcess) {
      return;
    }
    setSubmittingSpecialProcess(true);
    try {
      await clearOrderSpecialDispatch(activeItem.id);
      setIsSpecialProcessOpen(false);
      setActiveItem(null);
      await reloadOrders();
      toast("已恢复原配送方式");
    } catch (err: any) {
      toast(getErrorMessage(err, "取消特殊处理失败"), "error");
      throw err;
    } finally {
      setSubmittingSpecialProcess(false);
    }
  }

  async function handleConfirmConfirmation(id: number) {
    if (processingConfirmationId === id) return;
    setProcessingConfirmationId(id);
    setProcessingConfirmationAction("confirm");
    try {
      await confirmSubscription(id);
      await reloadOrders();
      toast("待确认订单已生成");
    } catch (err: any) {
      toast(getErrorMessage(err, "确认生成失败"), "error");
      throw err;
    } finally {
      setProcessingConfirmationId(null);
      setProcessingConfirmationAction(null);
    }
  }

  async function handleCancelConfirmation(id: number) {
    if (processingConfirmationId === id) return;
    setProcessingConfirmationId(id);
    setProcessingConfirmationAction("cancel");
    try {
      await cancelSubscriptionConfirmation(id, "后台取消");
      await reloadOrders();
      toast("待确认订单已取消");
    } catch (err: any) {
      toast(getErrorMessage(err, "取消待确认订单失败"), "error");
      throw err;
    } finally {
      setProcessingConfirmationId(null);
      setProcessingConfirmationAction(null);
    }
  }

  function handleTabChange(nextTab: OrderPrepTab) {
    setHasManualTabSelection(true);
    setActiveTab(nextTab);
  }

  function handleExportMealPrep() {
    const rows = buildMealPrepExportRows(view.filteredItems);
    const worksheet = XLSX.utils.json_to_sheet(rows);
    const workbook = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(workbook, worksheet, "备餐单");
    XLSX.writeFile(workbook, `备餐单-${filterDate}.xlsx`);
  }

  const renderStatus = (item: OrderPrepItemResponse) => {
    const displayStatus = resolveOrderDisplayStatus(item);
    const tone = resolveOrderStatusTone(displayStatus);
    const label = item.displayStatusLabel || resolveOrderDisplayStatusLabel(displayStatus);
    return <span className={`pill pill-${tone}`}>{label}</span>;
  };

  const renderMealPanel = (item: OrderPrepItemResponse) => {
    const mealPeriod = resolveMealPeriod(item);
    const deliveryMealPeriod = item.deliveryMealPeriod === "DINNER" ? "DINNER" : "LUNCH";
    const isLunch = mealPeriod === "LUNCH";
    const isCrossMealDeliveryOrder = isCrossMealDelivery(mealPeriod, deliveryMealPeriod);
    return (
      <div className="order-meal-panel">
        <div className="order-meal-panel__row">
          <span className={`tag ${isLunch ? "tag-orange" : "tag-green"}`}>{isLunch ? "午餐" : "晚餐"}</span>
          <span className="order-meal-panel__text">{isCrossMealDeliveryOrder ? "跨餐配送" : "同餐配送"}</span>
        </div>
        {!isCrossMealDeliveryOrder ? (
          <div className="order-meal-panel__row">
            <span className={`tag ${deliveryMealPeriod === "DINNER" ? "tag-green" : "tag-orange"}`}>
              {mealPeriodLabel(deliveryMealPeriod)}
            </span>
            <span className="order-meal-panel__text">配送</span>
          </div>
        ) : null}
        <div className="order-meal-panel__count">{item.quantity} 餐</div>
      </div>
    );
  };

  const buildMerchantRemarkDisplay = (merchantRemark: string | null | undefined, mealPeriod: string | null | undefined, deliveryMealPeriod: string | null | undefined) =>
    formatOrderNote(buildCrossMealDeliveryRemark(merchantRemark, mealPeriod, deliveryMealPeriod));

  function getRowHighlightClass(item: OrderPrepItemResponse) {
    const displayStatus = resolveOrderDisplayStatus(item);
    if (displayStatus === "AFTERSALE" || displayStatus === "REFUNDED") {
      return "row-danger-highlight";
    }
    if (item.userNote) {
      return "row-warning-highlight";
    }
    return "";
  }



  const renderActions = (item: OrderPrepItemResponse) => {
    return (
      <div className="action-cell-container">
        <button
          className="btn btn-secondary btn-sm"
          onClick={() => { openOrderDetail(item); }}
        >
          查看详情
        </button>
      </div>
    );
  };

  return (
    <>
      <div className="page-header">
        <div>
          <h2 className="page-title">订单运营中心</h2>
          <p className="page-subtitle">备餐、确认、派单与订单处理</p>
        </div>
        <div style={{ display: "flex", gap: "10px", flexWrap: "wrap" }}>
          <button className="btn btn-outline" onClick={openManualCreateModal}>
            <UserPlus size={16} />
            录入代客订单
          </button>
          <button className="btn btn-primary" onClick={() => handleAutoImportClick().catch(() => undefined)}>
            <Bot size={16} />
            导入固定订餐
          </button>
        </div>
      </div>

      <div className="stat-row">
        {compactSummary.map((item) => (
          <div key={item.label} className="stat-card">
            <div className="stat-title">{item.label}</div>
            <div className="stat-val">{item.value}</div>
            <div className="stat-footer">
              {item.label === "当前待出餐"
                ? `有备注 ${summary.remarkedOrderCount} 单`
                : item.label === "餐次结构"
                  ? "按午餐 / 晚餐拆分"
                  : `重点客户待确认 ${summary.priorityConfirmationCount} 人`}
            </div>
          </div>
        ))}
      </div>
      <div className="admin-panel-note" style={{ marginBottom: "16px" }}>
        顾客端下单后会先进入待配送，取消和售后结果会同步回订单页与钱包页。
      </div>

      <div className="toolbar">
        <div className="filter-row">
          <div className="filter-item">
            <span className="filter-label">日期:</span>
            <DatePicker value={filterDate} onChange={(date) => setFilterDate(date)} showTomorrowShortcut={false} />
          </div>
          {activeTab === "ORDERS" && (
            <>
              <div className="filter-item subscription-meal-toggle">
                <span className="filter-label">查看餐次:</span>
                <div className="segmented-control" role="tablist" aria-label="订单餐次切换">
                  {(["LUNCH", "DINNER"] as OrderPrepMealPeriodFilter[]).map((value) => (
                    <button
                      key={value}
                      type="button"
                      className={`segmented-control__item ${mealPeriodFilter === value ? "is-active" : ""}`}
                      onClick={() => setMealPeriodFilter(value)}
                    >
                      {mealPeriodLabel(value)}
                    </button>
                  ))}
                </div>
              </div>
              <div className="filter-item">
                <span className="filter-label">来源:</span>
                <AppSelect
                  className="app-select--filter"
                  style={{ width: "120px" }}
                  value={sourceFilter}
                  options={[
                    { label: "全部渠道", value: "ALL" },
                    { label: "小程序", value: "MINIAPP" },
                    { label: "后台录入", value: "BACKEND" },
                    { label: "固定订餐", value: "SUBSCRIPTION" }
                  ]}
                  onChange={(value) => setSourceFilter(value as OrderPrepSourceFilter)}
                />
              </div>
              <div className="filter-item">
                <span className="filter-label">状态:</span>
                <AppSelect
                  className="app-select--filter"
                  style={{ width: "120px" }}
                  value={statusFilter}
                  options={[
                    { label: "全部状态", value: "ALL" },
                    { label: "待配送", value: "PENDING_DISPATCH" },
                    { label: "配送中", value: "DISPATCHING" },
                    { label: "已完成", value: "DELIVERED" },
                    { label: "退款处理中", value: "REFUND_PROCESSING" },
                    { label: "已退款", value: "REFUNDED" },
                    { label: "已取消", value: "CANCELLED" }
                  ]}
                  onChange={(value) => setStatusFilter(value as OrderPrepStatusFilter)}
                />
              </div>
              <div className="filter-item">
                <span className="filter-label">关键字:</span>
                <input
                  type="text"
                  className="input-box"
                  placeholder="客户姓名/手机号/备注"
                  style={{ width: "200px" }}
                  value={keywordFilter}
                  onChange={(e) => setKeywordFilter(e.target.value)}
                />
              </div>
              <div className="filter-item">
                <span className="filter-label">备注:</span>
                <AppSelect
                  className="app-select--filter"
                  style={{ width: "120px" }}
                  value={remarkFilter}
                  options={[
                    { label: "全部备注", value: "ALL" },
                    { label: "有备注", value: "HAS_REMARK" },
                    { label: "无备注", value: "NO_REMARK" }
                  ]}
                  onChange={(value) => setRemarkFilter(value as OrderPrepRemarkFilter)}
                />
              </div>
            </>
          )}
          {activeTab === "SUBSCRIPTION_MANAGEMENT" && (
            <>
              <div className="filter-item order-meal-toggle">
                <span className="filter-label">查看餐次:</span>
                <div className="segmented-control" role="tablist" aria-label="固定订餐餐次切换">
                  {(["LUNCH", "DINNER"] as SubscriptionMealPeriod[]).map((value) => (
                    <button
                      key={value}
                      type="button"
                      className={`segmented-control__item ${subscriptionFilters.mealPeriod === value ? "is-active" : ""}`}
                      onClick={() => setSubscriptionFilters((current) => ({ ...current, mealPeriod: value }))}
                    >
                      {mealPeriodLabel(value)}
                    </button>
                  ))}
                </div>
              </div>
              <div className="filter-item">
                <span className="filter-label">关键字:</span>
                <input
                  type="text"
                  className="input-box"
                  placeholder="搜索客户姓名或电话"
                  style={{ width: "200px" }}
                  value={subscriptionFilters.keyword}
                  onChange={(e) => setSubscriptionFilters((current) => ({ ...current, keyword: e.target.value }))}
                />
              </div>
              <div className="filter-item">
                <span className="filter-label">状态:</span>
                <AppSelect
                  className="app-select--filter"
                  style={{ width: "120px" }}
                  value={subscriptionFilters.statusFilter}
                  options={[
                    { label: "全部状态", value: "ALL" },
                    { label: "进行中", value: "ACTIVE" },
                    { label: "已停用", value: "STOPPED" },
                    { label: "已过期", value: "EXPIRED" }
                  ]}
                  onChange={(value) => setSubscriptionFilters((current) => ({ ...current, statusFilter: value as SubscriptionStatusFilter }))}
                />
              </div>
            </>
          )}
          {activeTab === "ORDERS" ? <span className="dispatch-table-toolbar__count">{mealPeriodLabel(mealPeriodFilter)}筛选出 {view.filteredItems.length} 条</span> : null}
          <button
            className="btn btn-primary"
            disabled={activeTab === "ORDERS" ? loadingOrders : false}
            onClick={() => {
              if (activeTab === "SUBSCRIPTION_MANAGEMENT") {
                setSubscriptionQueryVersion((current) => current + 1);
                return;
              }
              reloadOrders(filterDate).catch(() => undefined);
            }}
          ><Search size={16} /> {activeTab === "ORDERS" && loadingOrders ? "查询中..." : "查询"}</button>
          <button
            className="btn btn-outline"
            onClick={() => {
              if (activeTab === "SUBSCRIPTION_MANAGEMENT") {
                setSubscriptionFilters({
                  keyword: "",
                  statusFilter: "ALL",
                  mealPeriod: "LUNCH"
                });
                setSubscriptionQueryVersion((current) => current + 1);
                return;
              }
              setFilterDate(DEFAULT_FILTER_DATE);
              setSourceFilter("ALL");
              setStatusFilter("ALL");
              setRemarkFilter("ALL");
              setKeywordFilter("");
              reloadOrders(DEFAULT_FILTER_DATE).catch(() => undefined);
            }}
          >
            <RotateCcw size={16} /> 重置
          </button>
          <div style={{ marginLeft: "auto", color: "var(--text-sub)", fontSize: "13px", fontWeight: 600 }}>
            {activeTab === "CONFIRMATION"
              ? `待确认 ${confirmationItems.length} 份`
              : activeTab === "SUBSCRIPTION_MANAGEMENT"
                ? `当前筛出 ${subscriptionVisibleCount} 条计划`
                : `当前筛出 ${view.totalItems} 条订单`}
          </div>
        </div>
      </div>

      <div className={`table-container ${activeTab === "ORDERS" ? "table-container--orders" : ""}`}>
        <div className="table-header-toolbar">
          <div style={{ display: "grid", gap: "10px" }}>
            <span>
              {activeTab === "CONFIRMATION" 
                ? `待确认订单 (${confirmationItems.length})` 
                : activeTab === "SUBSCRIPTION_MANAGEMENT"
                  ? `固定订餐管理 (${subscriptionVisibleCount})`
                  : `${mealPeriodLabel(mealPeriodFilter)}订单列表 (${view.totalItems})`}
            </span>
            <div className="segmented-control" role="tablist" aria-label="订单视图切换">
              {confirmationPanel.visible && (
                <button
                  type="button"
                  className={`segmented-control__item ${activeTab === "CONFIRMATION" ? "is-active" : ""}`}
                  onClick={() => handleTabChange("CONFIRMATION")}
                >
                  待确认订单
                  <span className="segmented-control__count">{confirmationItems.length}</span>
                </button>
              )}
              <button
                type="button"
                className={`segmented-control__item ${activeTab === "ORDERS" ? "is-active" : ""}`}
                onClick={() => handleTabChange("ORDERS")}
              >
                普通订单
                <span className="segmented-control__count">{view.totalItems}</span>
              </button>
              <button
                type="button"
                className={`segmented-control__item ${activeTab === "SUBSCRIPTION_MANAGEMENT" ? "is-active" : ""}`}
                onClick={() => handleTabChange("SUBSCRIPTION_MANAGEMENT")}
              >
                固定订餐管理
              </button>
            </div>
          </div>
          {activeTab === "ORDERS" ? (
            <button className="btn btn-outline" onClick={handleExportMealPrep}><Printer size={16} /> 导出备餐单</button>
          ) : (
            <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
              <span className="tag tag-red">待确认 {confirmationItems.length} 份</span>
              <span className="tag tag-orange">重点客户 {summary.priorityConfirmationCount} 人</span>
            </div>
          )}
        </div>

        {activeTab === "SUBSCRIPTION_MANAGEMENT" ? (
          <SubscriptionManagementTab
            filters={subscriptionFilters}
            queryVersion={subscriptionQueryVersion}
            onVisibleCountChange={setSubscriptionVisibleCount}
          />
        ) : activeTab === "CONFIRMATION" ? (
          <div style={{ display: "grid", gap: "12px", padding: "20px" }}>
            {confirmationItems.map((item) => (
              <div key={item.id} className="address-card" style={{ cursor: "default" }}>
                <div className="address-content">
                  <div className="address-title">
                    {item.customerName} / {item.customerPhone} / {item.mealPeriod === "LUNCH" ? "午餐" : "晚餐"}
                  </div>
                  <div className="address-detail">{item.addressLine || "未设置地址"}</div>
                  <div className="address-detail">用户备注：{item.userNote || "-"} / 商家备注：{item.merchantRemark || "-"}</div>
                </div>
                <div style={{ display: "flex", gap: "8px" }}>
                  <button className="btn btn-outline" disabled={processingConfirmationId === item.id} onClick={() => handleCancelConfirmation(item.id).catch(() => undefined)}>
                    {processingConfirmationId === item.id && processingConfirmationAction === "cancel" ? "取消中..." : "取消"}
                  </button>
                  <button className="btn btn-primary" disabled={processingConfirmationId === item.id} onClick={() => handleConfirmConfirmation(item.id).catch(() => undefined)}>
                    {processingConfirmationId === item.id && processingConfirmationAction === "confirm" ? "生成中..." : "确认生成"}
                  </button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <>
            <div className="order-list-shell">
            <div className="table-responsive">
              <table className="admin-table" style={{ background: "#FFFFFF", borderTop: "1px solid var(--border-color)", borderBottom: "1px solid var(--border-color)", width: "100%" }}>
                <thead>
                  <tr>
                    <th style={{ width: "40px" }}><input type="checkbox" /></th>
                    <th>客户标识</th>
                    <th>联系电话</th>
                    <th>用户备注</th>
                    <th>商家备注</th>
                    <th>配送地址</th>
                    <th>订单来源</th>
                    <th>状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {loadingOrders ? (
                    <tr>
                      <td colSpan={9} style={{ textAlign: "center", padding: "32px", color: "var(--text-muted)" }}>
                        订单加载中...
                      </td>
                    </tr>
                  ) : ordersError ? (
                    <tr>
                      <td colSpan={9} style={{ textAlign: "center", padding: "32px", color: "var(--error-color-dark)" }}>
                        加载失败：{ordersError}
                      </td>
                    </tr>
                  ) : view.filteredItems.length === 0 ? (
                    <tr>
                      <td colSpan={9} style={{ textAlign: "center", padding: "32px", color: "var(--text-muted)" }}>
                        {emptyOrderText}
                      </td>
                    </tr>
                  ) : (
                    view.pageItems.map((item) => {
                      const sourceLabel = resolveOrderSourceLabel(item);
                      const rowClass = getRowHighlightClass(item);
                      const merchantRemarkDisplay = buildMerchantRemarkDisplay(item.merchantRemark, item.mealPeriod, item.deliveryMealPeriod);
                      return (
                        <tr key={item.id} className={rowClass}>
                          <td><input type="checkbox" /></td>
                          <td>
                            <div style={{ display: "grid", gap: "6px" }}>
                              <div style={{ fontWeight: 700, display: "flex", alignItems: "center", gap: "6px", flexWrap: "wrap" }}>
                                <span>{item.customerName}</span>
                                <span style={{ color: "var(--primary-color)", fontWeight: 700 }}>×{item.quantity}</span>
                                {item.priorityCustomer && <span className="tag tag-orange">重点</span>}
                                {item.fixedSubscription && <span className="tag tag-blue">固定订餐</span>}
                              </div>
                            </div>
                          </td>
                          <td><span style={{ color: "var(--text-sub)" }}>{item.customerPhone}</span></td>
                          <td style={{ color: formatOrderNote(item.userNote) === "-" ? undefined : "var(--error-color)", maxWidth: "160px" }}>{formatOrderNote(item.userNote)}</td>
                          <td style={{ maxWidth: "160px" }}>{merchantRemarkDisplay}</td>
                          <td>
                            <div style={{ display: "flex", alignItems: "flex-start", gap: "8px", color: "var(--text-sub)", maxWidth: "200px" }}>
                              <MapPin size={14} style={{ marginTop: "2px", flexShrink: 0 }} />
                              <span style={{ display: "-webkit-box", WebkitLineClamp: 2, WebkitBoxOrient: "vertical", overflow: "hidden" }}>{item.deliveryAddress}</span>
                            </div>
                          </td>
                          <td><span className={`tag ${sourceLabel === "后台录入" ? "tag-gray" : "tag-blue"}`}>{sourceLabel}</span></td>
                          <td>
                            <div style={{ display: "flex", flexDirection: "column", gap: "4px" }}>
                              {renderStatus(item)}
                              <span style={{ color: "var(--text-sub)", fontSize: "12px", whiteSpace: "nowrap" }}>{item.walletStatusLabel}</span>
                            </div>
                          </td>
                          <td>{renderActions(item)}</td>
                        </tr>
                      );
                    })
                  )}
                </tbody>
              </table>
            </div>

            <div className="mobile-card-list">
              {view.pageItems.map((item) => {
                const sourceLabel = resolveOrderSourceLabel(item);
                const rowClass = getRowHighlightClass(item);
                const merchantRemarkDisplay = buildMerchantRemarkDisplay(item.merchantRemark, item.mealPeriod, item.deliveryMealPeriod);
                return (
                  <div className={`mobile-card ${rowClass}`} key={item.id}>
                    <div className="mobile-card-header">
                      <div>
                        <span style={{ fontWeight: 700 }}>{item.customerName}</span>
                        <span style={{ color: "var(--primary-color)", marginLeft: "4px", fontWeight: 700 }}>×{item.quantity}</span>
                        <span style={{ color: "var(--text-sub)", fontSize: "12px", marginLeft: "8px" }}>{item.customerPhone}</span>
                        <div style={{ display: "flex", gap: "6px", flexWrap: "wrap", marginTop: "6px" }}>
                          {item.priorityCustomer && <span className="tag tag-orange">重点客户</span>}
                          {item.fixedSubscription && <span className="tag tag-blue">固定订餐</span>}
                        </div>
                      </div>
                      <div>
                        {renderStatus(item)}
                      </div>
                    </div>
                    <div style={{ padding: "0 12px", color: "var(--text-sub)", fontSize: "12px" }}>
                      {item.walletStatusLabel}
                    </div>
                    <div className="mobile-card-row">
                      <div className="mobile-card-label">用户备注</div>
                      <div className="mobile-card-value" style={{ color: formatOrderNote(item.userNote) === "-" ? "inherit" : "var(--error-color)" }}>{formatOrderNote(item.userNote)}</div>
                    </div>
                    <div className="mobile-card-row">
                      <div className="mobile-card-label">商家备注</div>
                      <div className="mobile-card-value">{merchantRemarkDisplay}</div>
                    </div>
                    <div className="mobile-card-row">
                      <div className="mobile-card-label">来源</div>
                      <div className="mobile-card-value">{sourceLabel}</div>
                    </div>
                    <div className="mobile-card-row">
                      <div className="mobile-card-label">地址</div>
                      <div className="mobile-card-value">{item.deliveryAddress}</div>
                    </div>
                    <div className="mobile-card-actions">
                    {renderActions(item)}
                  </div>
                  </div>
                );
              })}
              {view.pageItems.length === 0 && !loadingOrders && !ordersError ? (
                <div className="empty-state">{emptyOrderText}</div>
              ) : null}
            </div>

        <div className="order-list-footer">
        <div className="pagination pagination--embedded">
          <div className="pagination-info">共 {view.totalItems} 条记录，第 {view.currentPage} / {view.totalPages} 页</div>
          <div className="pagination-pages">
            {view.totalPages === 1 ? (
              <span className="pagination-pages__single">仅 1 页</span>
            ) : (
              <>
                <button className="page-btn" disabled={view.currentPage === 1} onClick={() => setCurrentPage(c => Math.max(1, c - 1))}><ChevronLeft size={16} /></button>
                {Array.from({ length: view.totalPages }, (_, index) => index + 1).map((page) => (
                  <button
                    key={page}
                    className={`page-btn ${view.currentPage === page ? "active" : ""}`}
                    onClick={() => setCurrentPage(page)}
                  >
                    {page}
                  </button>
                ))}
                <button className="page-btn" disabled={view.currentPage === view.totalPages} onClick={() => setCurrentPage(c => Math.min(view.totalPages, c + 1))}><ChevronRight size={16} /></button>
              </>
            )}
          </div>
        </div>
        </div>
        </div>
          </>
        )}
      </div>

      {/* Modals */}
      {isSubscriptionPreviewOpen && (
        <div className="modal-overlay">
          <div className="modal-content" style={{ maxWidth: "800px" }}>
            <div className="modal-header">
              <span>自动导入包月订单 ({filterDate})</span>
              <button type="button" className="modal-close" disabled={isSubmittingImport} onClick={isSubmittingImport ? undefined : () => setIsSubscriptionPreviewOpen(false)}><X size={20} /></button>
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
                            <input 
                              type="text" 
                              className="input-box" 
                              style={{ width: "160px", height: "30px", padding: "4px 8px" }} 
                              value={item.merchantRemark} 
                              onChange={(e) => handleUpdatePreviewNote(index, e.target.value)} 
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
                <button className="btn btn-outline" disabled={isSubmittingImport} onClick={() => setIsSubscriptionPreviewOpen(false)}>取消</button>
                <button className="btn btn-primary" onClick={() => handleConfirmBulkImport().catch(() => undefined)} disabled={isSubmittingImport}>
                  {isSubmittingImport ? "生成中..." : "确认生成订单"}
                </button>
              </div>
            </div>
          </div>
        </div>
        </div>
      )}

      {isManualCreateOpen && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">
              <span>录入代客订单</span>
              <button type="button" className="modal-close" disabled={submittingManualCreate} onClick={submittingManualCreate ? undefined : closeManualCreateModal}><X size={20} /></button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label className="form-label"><span className="required">*</span>客户搜索</label>
                <div style={{ position: "relative" }}>
                  <Search size={15} style={{ position: "absolute", left: "12px", top: "50%", transform: "translateY(-50%)", color: "var(--text-sub)" }} />
                  <input
                    className="form-control"
                    style={{ paddingLeft: "36px" }}
                    value={manualForm.customerKeyword}
                    onChange={(e) => {
                      const value = e.target.value;
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
                          if (!option.available) {
                            return;
                          }
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
                <input 
                  className="form-control" 
                  type="number" 
                  min="1" 
                  value={manualForm.quantity} 
                  onChange={(e) => setManualForm({ ...manualForm, quantity: Math.max(1, parseInt(e.target.value) || 1) })} 
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
              <button className="btn btn-outline" onClick={closeManualCreateModal} disabled={submittingManualCreate}>取消</button>
              <button className="btn btn-primary" disabled={submittingManualCreate} onClick={() => handleManualCreateSubmit().catch(() => undefined)}>
                {submittingManualCreate ? "提交中..." : "确认录入"}
              </button>
            </div>
          </div>
        </div>
      )}

      {orderAftersaleItem && (
        <div className="modal-overlay">
          <div className="modal-content" style={{ maxWidth: "680px" }}>
            <div className="modal-header">
              <span>售后处理 - {orderAftersaleItem.customerName}</span>
              <button type="button" className="modal-close" disabled={submittingOrderAftersale} onClick={submittingOrderAftersale ? undefined : closeOrderAftersaleModal}><X size={20} /></button>
            </div>
            <div className="modal-body" style={{ display: "grid", gap: "18px" }}>
              <div className="auth-panel">
                <div className="auth-panel__title">订单信息</div>
                <div className="auth-panel__grid">
                  <div><strong>订单</strong><span>#{orderAftersaleItem.id}</span></div>
                  <div><strong>客户</strong><span>{orderAftersaleItem.customerName} / {orderAftersaleItem.customerPhone}</span></div>
                  <div><strong>出餐 / 配送</strong><span>{mealPeriodLabel(orderAftersaleItem.mealPeriod)} / {mealPeriodLabel(orderAftersaleItem.deliveryMealPeriod)} ×{orderAftersaleItem.quantity}</span></div>
                  <div><strong>当前状态</strong><span>{orderAftersaleItem.displayStatusLabel || resolveOrderDisplayStatusLabel(resolveOrderDisplayStatus(orderAftersaleItem))}</span></div>
                </div>
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">处理意图</label>
                <div className="action-chip-row">
                  {[
                    { key: "DIRECT_REFUND", label: "直接退款" },
                    { key: "COMPENSATION", label: "登记补偿" },
                    { key: "REGISTER_ONLY", label: "登记异常" }
                  ].map((option) => (
                    <button
                      key={option.key}
                      type="button"
                      className={`action-chip ${orderAftersaleForm.intent === option.key ? "active" : ""}`}
                      onClick={() => setOrderAftersaleForm((current) => ({ ...current, intent: option.key }))}
                    >
                      {option.label}
                    </button>
                  ))}
                </div>
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">售后原因</label>
                <textarea
                  className="form-control"
                  value={orderAftersaleForm.reasonText}
                  onChange={(event) => setOrderAftersaleForm((current) => ({ ...current, reasonText: event.target.value }))}
                  rows={3}
                  placeholder="请填写退款、补偿或异常原因"
                />
              </div>

              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">商家备注</label>
                <textarea
                  className="form-control"
                  value={orderAftersaleForm.remark}
                  onChange={(event) => setOrderAftersaleForm((current) => ({ ...current, remark: event.target.value }))}
                  rows={3}
                  placeholder="补充处理说明、异常细节或后续跟进备注"
                />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={closeOrderAftersaleModal} disabled={submittingOrderAftersale}>取消</button>
              <button
                className="btn btn-primary"
                onClick={() => handleOrderAftersaleSubmit().catch(() => undefined)}
                disabled={submittingOrderAftersale}
              >
                {submittingOrderAftersale ? "提交中..." : "确认处理"}
              </button>
            </div>
          </div>
        </div>
      )}

      {isAssignOpen && activeItem && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">
              <span>分配骑手 - {activeItem.customerName}</span>
              <button type="button" className="modal-close" disabled={submittingAssign} onClick={submittingAssign ? undefined : () => setIsAssignOpen(false)}><X size={20} /></button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label className="form-label"><span className="required">*</span>配送员名称</label>
                <AppSelect value={assignForm.riderName} options={assignRiderOptions} onChange={(val) => setAssignForm({...assignForm, riderName: val})} placeholder="选择骑手" showSearch style={{ width: "100%" }} />
              </div>
              <div className="form-group">
                <label className="form-label"><span className="required">*</span>配送区域</label>
                <AppSelect value={assignForm.areaCode} options={assignAreaOptions} onChange={(val) => setAssignForm({...assignForm, areaCode: val})} placeholder="选择区域" showSearch style={{ width: "100%" }} />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={() => setIsAssignOpen(false)} disabled={submittingAssign}>取消</button>
              <button className="btn btn-primary" disabled={submittingAssign} onClick={() => handleAssignSubmit().catch(() => undefined)}>
                {submittingAssign ? "提交中..." : "确认分配"}
              </button>
            </div>
          </div>
        </div>
      )}

      {isReceiptOpen && activeItem && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">
              <span>上传送达回执 - {activeItem.customerName}</span>
              <button type="button" className="modal-close" disabled={submittingReceipt} onClick={submittingReceipt ? undefined : () => setIsReceiptOpen(false)}><X size={20} /></button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label className="form-label"><span className="required">*</span>回执图片</label>
                <input
                  id={RECEIPT_UPLOAD_INPUT_ID}
                  type="file"
                  accept="image/png,image/jpeg,image/jpg,image/webp"
                  capture="environment"
                  className="receipt-upload-input"
                  onChange={(event) => handleReceiptFileChange(event).catch(() => undefined)}
                  disabled={uploadingReceipt || submittingReceipt}
                />
                <label
                  htmlFor={RECEIPT_UPLOAD_INPUT_ID}
                  className={`receipt-upload-card ${uploadingReceipt ? "is-uploading" : ""} ${hasImageValue(receiptForm.receiptUrl) ? "has-image" : ""}`}
                >
                  <div className="receipt-upload-card__header">
                    <div>
                      <div className="receipt-upload-card__title">
                        {uploadingReceipt
                          ? "正在上传回执图片..."
                          : hasImageValue(receiptForm.receiptUrl)
                            ? "回执图片已上传，可重新选择"
                            : "点击选择回执图片"}
                      </div>
                      <div className="receipt-upload-card__desc">
                        支持 JPG、PNG、WEBP，单张 5MB 以内。电脑端可选文件，手机端可直接拍照。
                      </div>
                    </div>
                    <span className="receipt-upload-trigger">
                      {uploadingReceipt ? "上传中..." : hasImageValue(receiptForm.receiptUrl) ? "重新选择" : "选择文件"}
                    </span>
                  </div>
                  {selectedReceiptFile ? (
                    <div className="receipt-upload-summary">
                      <span>{selectedReceiptFile.name}</span>
                      <span>{formatFileSize(selectedReceiptFile.size)}</span>
                    </div>
                  ) : null}
                </label>
                {hasImageValue(receiptForm.receiptUrl) ? (
                  <div className="order-detail-image-grid receipt-upload-preview-grid" style={{ marginTop: "12px" }}>
                    <div className="order-detail-image-card receipt-upload-preview-card">
                      <div className="order-detail-image-card__title">当前回执图</div>
                      <img
                        src={receiptForm.receiptUrl}
                        alt="当前回执图"
                        className="order-detail-image-card__image"
                      />
                      <div className="receipt-upload-actions">
                        <button
                          type="button"
                          className="btn btn-outline"
                          onClick={() => window.open(receiptForm.receiptUrl, "_blank")}
                        >
                          查看大图
                        </button>
                      </div>
                    </div>
                  </div>
                ) : null}
              </div>
              <RemarkField
                label="回执备注"
                value={receiptForm.receiptNote}
                onChange={(value) => setReceiptForm({ ...receiptForm, receiptNote: value })}
                placeholder="例如：已放前台"
                scene="RECEIPT_NOTE"
                multiline
              />
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={() => setIsReceiptOpen(false)} disabled={submittingReceipt}>取消</button>
              {hasImageValue(activeItem.receiptUrl) ? (
                <button className="btn-delete" disabled={submittingReceipt} onClick={() => handleReceiptDelete().catch(() => undefined)}>
                  {submittingReceipt ? "处理中..." : "删除回执"}
                </button>
              ) : null}
              <button className="btn btn-primary" disabled={submittingReceipt || uploadingReceipt || !hasImageValue(receiptForm.receiptUrl)} onClick={() => handleReceiptSubmit().catch(() => undefined)}>
                {submittingReceipt ? "提交中..." : "提交回执"}
              </button>
            </div>
          </div>
        </div>
      )}

      {isEditOpen && activeItem && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">
              <span>编辑订单 - {activeItem.customerName}</span>
              <button type="button" className="modal-close" disabled={submittingEdit} onClick={submittingEdit ? undefined : () => setIsEditOpen(false)}><X size={20} /></button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label className="form-label"><span className="required">*</span>餐次</label>
                <AppSelect
                  value={editForm.mealPeriod}
                  options={[
                    { label: "午餐", value: "LUNCH" },
                    { label: "晚餐", value: "DINNER" }
                  ]}
                  onChange={(val) => setEditForm({ ...editForm, mealPeriod: val })}
                  style={{ width: "100%" }}
                />
              </div>
              <div className="form-group">
                <label className="form-label"><span className="required">*</span>份数</label>
                <input className="form-control" type="number" min="1" value={editForm.quantity} onChange={e => setEditForm({ ...editForm, quantity: e.target.value })} />
              </div>
              <div className="form-group">
                <label className="form-label"><span className="required">*</span>订单状态</label>
                <AppSelect
                  value={editForm.status}
                  options={[
                    { label: "待配送", value: "PENDING_DISPATCH" },
                    { label: "已送达", value: "DELIVERED" },
                    { label: "已取消", value: "CANCELLED" }
                  ]}
                  onChange={(val) => setEditForm({ ...editForm, status: val })}
                  style={{ width: "100%" }}
                />
              </div>
              <div className="form-group">
                <label className="form-label"><span className="required">*</span>配送地址</label>
                <textarea className="form-control" value={editForm.deliveryAddress} onChange={e => setEditForm({ ...editForm, deliveryAddress: e.target.value })} placeholder="填写详细配送地址" />
              </div>
              <RemarkField
                label="商家备注"
                value={editForm.merchantRemark}
                onChange={(value) => setEditForm({ ...editForm, merchantRemark: value })}
                placeholder="例如：少饭、多菜、先送"
                scene="ORDER_REMARK"
                multiline
              />
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label" style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                  <input 
                    type="checkbox" 
                    checked={editForm.priorityCustomer} 
                    onChange={e => setEditForm({ ...editForm, priorityCustomer: e.target.checked })}
                    style={{ width: "auto", margin: 0 }}
                  />
                  <span>标记为重点客户</span>
                </label>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={() => setIsEditOpen(false)} disabled={submittingEdit}>取消</button>
              <button className="btn btn-primary" disabled={submittingEdit} onClick={() => handleEditSubmit().catch(() => undefined)}>
                {submittingEdit ? "提交中..." : "保存订单"}
              </button>
            </div>
          </div>
        </div>
      )}

      {isSpecialProcessOpen && activeItem && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">
              <span>特殊处理 - {activeItem.customerName}</span>
              <button
                type="button"
                className="modal-close"
                disabled={submittingSpecialProcess}
                onClick={submittingSpecialProcess ? undefined : () => setIsSpecialProcessOpen(false)}
              >
                <X size={20} />
              </button>
            </div>
            <div className="modal-body" style={{ display: "grid", gap: "16px" }}>
              <div className="auth-panel">
                <div className="auth-panel__title">当前订单</div>
                <div className="auth-panel__grid">
                  <div><strong>订单</strong><span>#{activeItem.id}</span></div>
                  <div><strong>出餐餐次</strong><span>{mealPeriodLabel(activeItem.mealPeriod)}</span></div>
                  <div><strong>当前配送</strong><span>{mealPeriodLabel(activeItem.deliveryMealPeriod)}</span></div>
                  <div><strong>状态</strong><span>{activeItem.displayStatusLabel || resolveOrderDisplayStatusLabel(resolveOrderDisplayStatus(activeItem))}</span></div>
                </div>
              </div>
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">改变配送时间</label>
                <AppSelect
                  value={specialProcessForm.deliveryMealPeriod}
                  options={[
                    { label: "按午餐配送", value: "LUNCH" },
                    { label: "按晚餐配送", value: "DINNER" }
                  ]}
                  onChange={(val) => setSpecialProcessForm({ deliveryMealPeriod: val as "LUNCH" | "DINNER" })}
                  style={{ width: "100%" }}
                />
                <div style={{ color: "var(--text-sub)", fontSize: "12px", marginTop: "8px" }}>
                  修改后，这单仍按原餐次出餐，但只会进入目标配送餐次的待分配和骑手链路。
                </div>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={() => setIsSpecialProcessOpen(false)} disabled={submittingSpecialProcess}>取消</button>
              {isCrossMealDelivery(activeItem.mealPeriod, activeItem.deliveryMealPeriod) ? (
                <button className="btn-delete" disabled={submittingSpecialProcess} onClick={() => handleSpecialProcessReset().catch(() => undefined)}>
                  {submittingSpecialProcess ? "处理中..." : "取消特殊处理"}
                </button>
              ) : null}
              <button className="btn btn-primary" disabled={submittingSpecialProcess} onClick={() => handleSpecialProcessSubmit().catch(() => undefined)}>
                {submittingSpecialProcess ? "提交中..." : "确认处理"}
              </button>
            </div>
          </div>
        </div>
      )}

      <AdminDialog
        open={isOrderDetailOpen}
        title={activeItem ? `订单详情 - ${activeItem.customerName}` : "订单详情"}
        width={640}
        onClose={() => setIsOrderDetailOpen(false)}
        footer={null}
      >
        {activeItem ? (
          <div className="order-detail-view">
            <div className="order-detail-view__header">
              <div className="order-detail-view__id">订单号：#{activeItem.id}</div>
              <div className="order-detail-view__status">
                {renderStatus(activeItem)}
              </div>
            </div>

            <div className="order-detail-view__grid">
              <div className="order-detail-view__section">
                <h4 className="order-detail-view__section-title">客户信息</h4>
                <div className="order-detail-view__list">
                  <div className="order-detail-view__item">
                    <span className="order-detail-view__label">客户</span>
                    <span className="order-detail-view__value">{activeItem.customerName}</span>
                  </div>
                  <div className="order-detail-view__item">
                    <span className="order-detail-view__label">电话</span>
                    <span className="order-detail-view__value">{activeItem.customerPhone}</span>
                  </div>
                  <div className="order-detail-view__item">
                    <span className="order-detail-view__label">钱包状态</span>
                    <span className="order-detail-view__value">{activeItem.walletStatusLabel}</span>
                  </div>
                </div>
              </div>

              <div className="order-detail-view__section">
                <h4 className="order-detail-view__section-title">订单信息</h4>
                <div className="order-detail-view__list">
                  <div className="order-detail-view__item">
                    <span className="order-detail-view__label">来源</span>
                    <span className="order-detail-view__value">{resolveOrderSourceLabel(activeItem)}</span>
                  </div>
                  <div className="order-detail-view__item">
                    <span className="order-detail-view__label">特殊处理</span>
                    <span className="order-detail-view__value">
                      {isCrossMealDelivery(activeItem.mealPeriod, activeItem.deliveryMealPeriod)
                        ? `${mealPeriodLabel(activeItem.mealPeriod)}出餐，${mealPeriodLabel(activeItem.deliveryMealPeriod)}配送`
                        : "无"}
                    </span>
                  </div>
                  <div className="order-detail-view__item order-detail-view__item--full">
                    <span className="order-detail-view__label">配送地址</span>
                    <span className="order-detail-view__value">{activeItem.deliveryAddress || "-"}</span>
                  </div>
                </div>
              </div>

              <div className="order-detail-view__section order-detail-view__section--full">
                <h4 className="order-detail-view__section-title">图片信息</h4>
                <div className="order-detail-image-grid">
                  <section className="order-detail-image-card">
                    <div className="order-detail-image-card__title">参照图</div>
                    {hasImageValue(activeItem.referenceImageUrl) ? (
                      <img
                        src={activeItem.referenceImageUrl}
                        alt="地址参照图"
                        className="order-detail-image-card__image"
                        onClick={() => window.open(activeItem.referenceImageUrl, "_blank")}
                      />
                    ) : (
                      <div className="dispatch-image-empty">暂无参照图</div>
                    )}
                  </section>

                  <section className="order-detail-image-card">
                    <div className="order-detail-image-card__title">核销回执</div>
                    {hasImageValue(activeItem.receiptUrl) ? (
                      <img
                        src={activeItem.receiptUrl}
                        alt="核销回执"
                        className="order-detail-image-card__image"
                        onClick={() => window.open(activeItem.receiptUrl, "_blank")}
                      />
                    ) : (
                      <div className="dispatch-image-empty">暂无回执图</div>
                    )}
                    <div className="order-detail-image-card__meta">
                      <span>骑手备注：{activeItem.receiptNote?.trim() || "-"}</span>
                      <span>送达时间：{activeItem.deliveredAt || "-"}</span>
                    </div>
                  </section>
                </div>
              </div>

              <div className="order-detail-view__section order-detail-view__section--full">
                <h4 className="order-detail-view__section-title">备注信息</h4>
                <div className="order-detail-view__list">
                  <div className="order-detail-view__item order-detail-view__item--full">
                    <span className="order-detail-view__label">用户备注</span>
                    <span className="order-detail-view__value" style={{ color: formatOrderNote(activeItem.userNote) === "-" ? "inherit" : "var(--error-color)" }}>{formatOrderNote(activeItem.userNote)}</span>
                  </div>
                  <div className="order-detail-view__item order-detail-view__item--full">
                    <span className="order-detail-view__label">商家备注</span>
                    <span className="order-detail-view__value">{buildMerchantRemarkDisplay(activeItem.merchantRemark, activeItem.mealPeriod, activeItem.deliveryMealPeriod)}</span>
                  </div>
                </div>
              </div>
            </div>

            <div className="order-detail-view__actions">
              <button
                className="btn btn-outline"
                onClick={() => {
                  setIsOrderDetailOpen(false);
                  openEditModal(activeItem);
                }}
              >
                编辑订单
              </button>
              <button
                className="btn btn-outline"
                onClick={() => {
                  setIsOrderDetailOpen(false);
                  openOrderAftersaleModal(activeItem);
                }}
              >
                售后处理
              </button>
              <button
                className="btn btn-outline"
                onClick={() => {
                  setIsOrderDetailOpen(false);
                  openSpecialProcessModal(activeItem);
                }}
              >
                特殊处理
              </button>
              <button
                className="btn btn-primary"
                onClick={() => {
                  setIsOrderDetailOpen(false);
                  openReceiptModal(activeItem);
                }}
              >
                {hasImageValue(activeItem.receiptUrl) ? "修改回执" : "核销回执"}
              </button>
              {hasImageValue(activeItem.receiptUrl) ? (
                <button
                  className="btn-delete"
                  onClick={() => {
                    setIsOrderDetailOpen(false);
                    openReceiptModal(activeItem);
                  }}
                >
                  删除回执
                </button>
              ) : null}
              <button
                className="btn-delete"
                onClick={() => {
                  setIsOrderDetailOpen(false);
                  openDeleteConfirm(activeItem);
                }}
              >
                删除订单
              </button>
            </div>
          </div>
        ) : null}
      </AdminDialog>

      <AdminDialog
        open={isDeleteConfirmOpen}
        title="⚠️ 删除订单"
        width={500}
        onClose={submittingDelete ? () => undefined : () => { setIsDeleteConfirmOpen(false); setActiveItem(null); }}
        footer={
          <div style={{ display: "flex", gap: "12px", justifyContent: "flex-end" }}>
            <button className="btn btn-outline" onClick={() => { setIsDeleteConfirmOpen(false); setActiveItem(null); }} disabled={submittingDelete}>取消</button>
            <button 
              className="btn-delete"
              disabled={submittingDelete}
              onClick={() => activeItem && handleDelete(activeItem).catch(() => undefined)}
            >
              <Trash2 size={16} />
              {submittingDelete ? "提交中..." : "确认删除"}
            </button>
          </div>
        }
      >
        {activeItem && (
          <div style={{ display: "grid", gap: "16px" }}>
            <div className="delete-confirm-details">
              <div className="delete-confirm-details__item">
                <span className="delete-confirm-details__label">客户：</span>
                <span className="delete-confirm-details__value">{activeItem.customerName}</span>
              </div>
              <div className="delete-confirm-details__item">
                <span className="delete-confirm-details__label">电话：</span>
                <span className="delete-confirm-details__value">{activeItem.customerPhone}</span>
              </div>
              <div className="delete-confirm-details__item">
                <span className="delete-confirm-details__label">餐次：</span>
                <span className="delete-confirm-details__value">{mealPeriodLabel(activeItem.mealPeriod)} / {mealPeriodLabel(activeItem.deliveryMealPeriod)}</span>
              </div>
              <div className="delete-confirm-details__item">
                <span className="delete-confirm-details__label">地址：</span>
                <span className="delete-confirm-details__value">{activeItem.deliveryAddress}</span>
              </div>
              <div className="delete-confirm-details__item">
                <span className="delete-confirm-details__label">状态：</span>
                <span className="delete-confirm-details__value">
                  {activeItem.displayStatusLabel || resolveOrderDisplayStatusLabel(resolveOrderDisplayStatus(activeItem))}
                </span>
              </div>
            </div>
          </div>
        )}
      </AdminDialog>

      {/* Subscription Preview Check Modal */}
      {isPreviewCheckOpen && previewCheckResult && (
        <div className="modal-overlay" onClick={() => setIsPreviewCheckOpen(false)}>
          <div className="modal-content" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="modal-title">余额预检</h3>
              <button className="modal-close" onClick={() => setIsPreviewCheckOpen(false)}>
                ×
              </button>
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
              <button className="btn btn-outline" onClick={() => setIsPreviewCheckOpen(false)}>
                取消导入
              </button>
              <button
                className="btn btn-primary"
                onClick={() => handleConfirmPreviewCheck(true)}
              >
                仅导入余额充足的 ({previewCheckResult.sufficientCount} 人)
              </button>
            </div>
          </div>
        </div>
      )}

      {subscriptionNoteSuggestions.length > 0 && (
        <datalist id="subscription-note-suggestions">
          {subscriptionNoteSuggestions.map((item) => (
            <option key={item} value={item} />
          ))}
        </datalist>
      )}
    </>
  );
}
