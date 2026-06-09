import React, { useEffect, useMemo, useState, useRef } from "react";
import * as XLSX from "xlsx";
import {
  assignDispatch,
  cancelOrder,
  deleteOrder,
  cancelSubscriptionConfirmation,
  confirmSubscription,
  consumeOrders,
  createManualOrder,
  fetchDispatchAreaBindings,
  fetchDispatchManagedRiders,
  fetchCurrentMenuWeek,
  fetchOrderPrepList,
  fetchOrderPrepStats,
  fetchSpecialOrders,
  fetchSubscriptionConfirmations,
  recordDeliveryReceipt,
  fetchSubscriptionPreview,
  bulkImportSubscription,
  fetchRemarkSuggestions,
  updateOrderAdminNote,
  updateOrderProfile,
  checkSubscriptionPreview,
  createOrderAftersale,
  directRefund,
  searchManualCreateCustomers
} from "../../shared/api/http";
import type { AdminMenuWeekResponse, DispatchAreaBindingResponse, DispatchManagedRiderResponse, ManualCreateCustomerSearchResponse, OrderPrepItemResponse, OrderPrepStatsResponse, SubscriptionConfirmationItem, SubscriptionPreviewItem, SpecialOrderItem, SubscriptionPreviewCheckResponse } from "../../shared/api/types";
import {
  buildOrderPrepCompactSummary,
  buildOrderPrepDefaultTab,
  buildSubscriptionConfirmationPanelState,
  buildMealPrepExportRows,
  buildOrderPrepSummary,
  buildOrderPrepView,
  formatOrderNote,
  resolveMealPeriod,
  resolveOrderDisplayStatus,
  resolveOrderDisplayStatusLabel,
  resolveOrderSourceLabel,
  resolveOrderStatusTone,
  type OrderPrepTab,
  type OrderPrepMealPeriodFilter,
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
import { Printer, CheckCircle, Search, RotateCcw, UserPlus, X, Bot, MapPin, ChevronLeft, ChevronRight, AlertTriangle, Trash2, Settings, MoreHorizontal } from "lucide-react";
import { AppSelect } from "../../shared/components/AppSelect";
import { AdminDialog } from "../../shared/components/AdminDialog";
import { RemarkField } from "../../shared/components/RemarkField";
import { DatePicker } from "../../shared/components/DatePicker";
import { toast } from "../../shared/components/Toast";
import { SubscriptionManagementTab } from "./SubscriptionManagementTab";

function defaultFilterDate() {
  const today = new Date();
  return today.toISOString().slice(0, 10);
}

const DEFAULT_FILTER_DATE = defaultFilterDate();
const PAGE_SIZE = 10;

export function OrderPrepPage() {
  const [stats, setStats] = useState<OrderPrepStatsResponse>({
    totalMeals: 105,
    lunchCount: 62,
    dinnerCount: 43,
    selfOrderCount: 85,
    staffOrderCount: 20,
    subscriptionCount: 0,
    specialOrderCount: 0,
    adminRemarkCount: 0,
    labelRequiredCount: 0
  });
  const [items, setItems] = useState<OrderPrepItemResponse[]>([]);
  const [confirmationItems, setConfirmationItems] = useState<SubscriptionConfirmationItem[]>([]);
  const [specialOrders, setSpecialOrders] = useState<SpecialOrderItem[]>([]);

  // Modals state
  const [isManualCreateOpen, setIsManualCreateOpen] = useState(false);
  const [isAssignOpen, setIsAssignOpen] = useState(false);
  const [isReceiptOpen, setIsReceiptOpen] = useState(false);
  const [isEditOpen, setIsEditOpen] = useState(false);
  const [isSubscriptionPreviewOpen, setIsSubscriptionPreviewOpen] = useState(false);
  const [isSpecialOrdersOpen, setIsSpecialOrdersOpen] = useState(false);
  const [isDeleteConfirmOpen, setIsDeleteConfirmOpen] = useState(false);
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
  const [mealPeriodFilter, setMealPeriodFilter] = useState<OrderPrepMealPeriodFilter>("ALL");
  const [sourceFilter, setSourceFilter] = useState<OrderPrepSourceFilter>("ALL");
  const [statusFilter, setStatusFilter] = useState<OrderPrepStatusFilter>("ALL");
  const [keywordFilter, setKeywordFilter] = useState("");

  // Forms state
  const [manualForm, setManualForm] = useState(createInitialManualCreateForm);
  const [manualCustomers, setManualCustomers] = useState<ManualCreateCustomerSearchResponse[]>([]);
  const [manualSelectedCustomer, setManualSelectedCustomer] = useState<ManualCreateCustomerSearchResponse | null>(null);
  const [manualSearchLoading, setManualSearchLoading] = useState(false);
  const [manualMenuWeek, setManualMenuWeek] = useState<AdminMenuWeekResponse | null>(null);
  const [manualMenuLoading, setManualMenuLoading] = useState(false);
  const [assignForm, setAssignForm] = useState({ riderName: "", areaCode: "" });
  const [receiptForm, setReceiptForm] = useState({ receiptUrl: "", receiptNote: "" });
  const [editForm, setEditForm] = useState({ mealPeriod: "LUNCH", quantity: "1", deliveryAddress: "", adminNote: "", specialTag: "", priorityCustomer: false, status: "PENDING_DISPATCH" });
  const [assignRiders, setAssignRiders] = useState<DispatchManagedRiderResponse[]>([]);
  const [assignAreaBindings, setAssignAreaBindings] = useState<DispatchAreaBindingResponse[]>([]);
  
  // Dropdown Menu state
  const [openDropdownId, setOpenDropdownId] = useState<number | null>(null);
  const dropdownRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (dropdownRef.current && !dropdownRef.current.contains(event.target as Node)) {
        setOpenDropdownId(null);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);
    reloadOrders(DEFAULT_FILTER_DATE).catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)));

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
      status: statusFilter
    }, currentPage, PAGE_SIZE),
    [items, keywordFilter, mealPeriodFilter, sourceFilter, statusFilter, currentPage]
  );

  const summary = useMemo(
    () => buildOrderPrepSummary(items, confirmationItems, specialOrders),
    [items, confirmationItems, specialOrders]
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
  }, [items, keywordFilter, mealPeriodFilter, sourceFilter, statusFilter]);

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
    const [statsResponse, listResponse, confirmationsResponse, specialOrdersResponse] = await Promise.all([
      fetchOrderPrepStats(),
      fetchOrderPrepList(serveDate),
      fetchSubscriptionConfirmations(serveDate),
      fetchSpecialOrders(serveDate)
    ]);
    setStats(statsResponse);
    setItems(listResponse.items);
    setConfirmationItems(confirmationsResponse);
    setSpecialOrders(specialOrdersResponse);
    setCurrentPage(1);
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
      window.alert("获取包月预览列表失败");
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
      window.alert("获取包月预览列表失败");
    }
  }

  async function handleConfirmBulkImport() {
    if (isSubmittingImport) return;
    const selectedItems = previewItems.filter(i => i.selected && i.hasBalance);
    if (selectedItems.length === 0) {
      window.alert("未选择任何有效的订单");
      return;
    }
    setIsSubmittingImport(true);
    try {
      const payload = selectedItems.map(item => ({
        customerId: item.customerId,
        mealPeriod: item.mealPeriod,
        addressId: item.addressId,
        note: item.defaultNote || "-"
      }));
      const result = await bulkImportSubscription(filterDate, payload);
      window.alert(`成功生成包月订单 ${result.successCount} 份`);
      setIsSubscriptionPreviewOpen(false);
      await reloadOrders();
    } catch (err) {
      window.alert("生成失败，请重试");
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
    newItems[index].defaultNote = val;
    setPreviewItems(newItems);
  };

  async function handleAssignSubmit() {
    if (!activeItem || !assignForm.riderName || !assignForm.areaCode) return;
    await assignDispatch(activeItem.id, assignForm.riderName, assignForm.areaCode);
    setIsAssignOpen(false);
    await reloadOrders();
  }

  async function handleManualCreateSubmit() {
    await createManualOrder(buildManualCreatePayload(manualForm, filterDate));
    closeManualCreateModal();
    await reloadOrders();
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

  async function handleCancel(item: OrderPrepItemResponse) {
    if (!item.canCancel) return;
    if (!window.confirm(`确认取消 ${item.customerName} 的订单吗？`)) return;
    await cancelOrder(item.id);
    await reloadOrders();
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
          remark: remark || (orderAftersaleForm.intent === "REGISTER_ONLY" ? "已登记异常，等待后续处理" : "请前往统一售后中心继续处理")
        });
        toast(orderAftersaleForm.intent === "REGISTER_ONLY" ? "异常已登记到售后中心" : "补偿售后已创建");
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
    await deleteOrder(item.id);
    setIsDeleteConfirmOpen(false);
    setActiveItem(null);
    await reloadOrders();
  }

  async function handleReceiptSubmit() {
    if (!activeItem || !receiptForm.receiptUrl) return;
    await recordDeliveryReceipt({
      mealSlotOrderId: activeItem.id,
      receiptUrl: receiptForm.receiptUrl,
      receiptNote: receiptForm.receiptNote,
      deliveredAt: new Date().toISOString()
    });
    setIsReceiptOpen(false);
    await reloadOrders();
  }

  async function handleEditSubmit() {
    if (!activeItem || !editForm.mealPeriod || !editForm.deliveryAddress) return;
    const trimmedAddress = editForm.deliveryAddress.trim();
    await updateOrderProfile(activeItem.id, {
      mealPeriod: editForm.mealPeriod as "LUNCH" | "DINNER",
      quantity: Number(editForm.quantity) || 1,
      deliveryAddress: trimmedAddress,
      adminNote: editForm.adminNote,
      specialTag: editForm.specialTag,
      priorityCustomer: editForm.priorityCustomer,
      status: editForm.status
    });
    setIsEditOpen(false);
    await reloadOrders();
  }

  async function handleConsumeDelivered() {
    const deliveredIds = items.filter((item) => item.status === "DELIVERED").map((item) => item.id);
    if (deliveredIds.length === 0) {
      window.alert("当前没有可核销的已送达订单");
      return;
    }
    if (!window.confirm(`确认核销 ${deliveredIds.length} 笔订单吗？`)) return;
    const result = await consumeOrders(deliveredIds);
    window.alert(`核销完成：成功 ${result.successCount} 条，失败 ${result.failureCount} 条`);
    await reloadOrders();
  }

  async function handleConfirmConfirmation(id: number) {
    await confirmSubscription(id);
    await reloadOrders();
  }

  async function handleCancelConfirmation(id: number) {
    await cancelSubscriptionConfirmation(id, "后台取消");
    await reloadOrders();
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

  function getRowHighlightClass(item: OrderPrepItemResponse) {
    const displayStatus = resolveOrderDisplayStatus(item);
    if (displayStatus === "AFTERSALE" || displayStatus === "REFUNDED") {
      return "row-danger-highlight";
    }
    if (item.specialTag || item.userNote) {
      return "row-warning-highlight";
    }
    return "";
  }

  const renderActions = (item: OrderPrepItemResponse) => {
    const displayStatus = resolveOrderDisplayStatus(item);
    const isDropdownOpen = openDropdownId === item.id;
    
    return (
      <div className="action-cell-container">
        {displayStatus === "PENDING_DISPATCH" && (
          <button
            className="btn btn-primary btn-sm"
            onClick={() => { setActiveItem(item); setIsAssignOpen(true); }}
            style={{ marginRight: "8px" }}
          >
            分配骑手
          </button>
        )}
        {displayStatus === "DISPATCHING" && (
          <button
            className="btn btn-success btn-sm"
            onClick={() => { setActiveItem(item); setReceiptForm({ receiptUrl: "", receiptNote: "" }); setIsReceiptOpen(true); }}
            style={{ marginRight: "8px" }}
          >
            核销回执
          </button>
        )}

        <div className="dropdown-container" ref={isDropdownOpen ? dropdownRef : null}>
          <button 
            className="btn btn-secondary btn-sm btn-icon"
            onClick={() => setOpenDropdownId(isDropdownOpen ? null : item.id)}
          >
            <MoreHorizontal size={16} />
          </button>
          
          {isDropdownOpen && (
            <div className="dropdown-menu">
              <button 
                className="dropdown-item"
                onClick={() => {
                  setActiveItem(item);
                  setEditForm({
                    mealPeriod: resolveMealPeriod(item),
                    quantity: String(item.quantity || 1),
                    deliveryAddress: item.deliveryAddress || "",
                    adminNote: item.adminNote || "",
                    specialTag: item.specialTag || "",
                    priorityCustomer: !!item.priorityCustomer,
                    status: item.status
                  });
                  setIsEditOpen(true);
                  setOpenDropdownId(null);
                }}
              >
                编辑订单
              </button>
              
              {displayStatus !== "PENDING_DISPATCH" && item.canAssign && (
                <button
                  className="dropdown-item"
                  onClick={() => { setActiveItem(item); setIsAssignOpen(true); setOpenDropdownId(null); }}
                >
                  分配骑手
                </button>
              )}
              
              {displayStatus !== "DISPATCHING" && item.canReceipt && (
                <button
                  className="dropdown-item"
                  onClick={() => { setActiveItem(item); setReceiptForm({ receiptUrl: "", receiptNote: "" }); setIsReceiptOpen(true); setOpenDropdownId(null); }}
                >
                  上传回执
                </button>
              )}
              
              <button 
                className="dropdown-item dropdown-item-danger"
                onClick={() => { openOrderAftersaleModal(item); setOpenDropdownId(null); }}
              >
                售后处理
              </button>
              
              {item.canCancel && (
                <button 
                  className="dropdown-item dropdown-item-danger"
                  onClick={() => { handleCancel(item); setOpenDropdownId(null); }}
                >
                  取消订单
                </button>
              )}
            </div>
          )}
        </div>
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
          <button className="btn btn-outline" onClick={() => setIsSpecialOrdersOpen(true)}>
            <AlertTriangle size={16} />
            特殊单 {specialOrders.length}
          </button>
          <button className="btn btn-primary" onClick={() => handleAutoImportClick().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}>
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
              {item.label === "明日待出餐"
                ? `特殊单 ${summary.specialOrderCount} 份`
                : item.label === "餐次结构"
                  ? "按午餐 / 晚餐拆分"
                  : `重点客户待确认 ${summary.priorityConfirmationCount} 人`}
            </div>
          </div>
        ))}
      </div>
      <div className="admin-panel-note" style={{ marginBottom: "16px" }}>
        顾客端下单后会先进入待配送，取消和售后结果会同步回订单页与钱包页。
        {summary.specialKeywordSummary.length > 0 ? ` 当前特殊摘要：${summary.specialKeywordSummary.slice(0, 4).join(" / ")}` : ""}
      </div>

      <div className="toolbar">
        <div className="filter-row">
          <div className="filter-item">
            <span className="filter-label">日期:</span>
            <DatePicker value={filterDate} onChange={(date) => setFilterDate(date)} showTomorrowShortcut={false} />
          </div>
          {activeTab === "ORDERS" && (
            <>
              <div className="filter-item">
                <span className="filter-label">餐次:</span>
                <AppSelect
                  className="app-select--filter"
                  style={{ width: "100px" }}
                  value={mealPeriodFilter}
                  options={[
                    { label: "全部", value: "ALL" },
                    { label: "午餐", value: "LUNCH" },
                    { label: "晚餐", value: "DINNER" }
                  ]}
                  onChange={(value) => setMealPeriodFilter(value as OrderPrepMealPeriodFilter)}
                />
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
            </>
          )}
          <button className="btn btn-primary" onClick={() => reloadOrders(filterDate).catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}><Search size={16} /> 查询</button>
          <button
            className="btn btn-outline"
            onClick={() => {
              setFilterDate(DEFAULT_FILTER_DATE);
              setMealPeriodFilter("ALL");
              setSourceFilter("ALL");
              setStatusFilter("ALL");
              setKeywordFilter("");
              reloadOrders(DEFAULT_FILTER_DATE).catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)));
            }}
          >
            <RotateCcw size={16} /> 重置
          </button>
          <div style={{ marginLeft: "auto", color: "var(--text-sub)", fontSize: "13px", fontWeight: 600 }}>
            {activeTab === "CONFIRMATION"
              ? `待确认 ${confirmationItems.length} 份`
              : `当前筛出 ${view.totalItems} 条订单`}
          </div>
        </div>
      </div>

      <div className="table-container">
        <div className="table-header-toolbar">
          <div style={{ display: "grid", gap: "10px" }}>
            <span>
              {activeTab === "CONFIRMATION" 
                ? `待确认订单 (${confirmationItems.length})` 
                : activeTab === "SUBSCRIPTION_MANAGEMENT"
                  ? "固定订餐管理"
                  : `普通订单列表 (${view.totalItems})`}
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
            <div style={{ display: "flex", gap: "8px" }}>
              <button className="btn btn-outline" onClick={openManualCreateModal}><UserPlus size={16} /> 录入代客订单</button>
              <button className="btn btn-outline" onClick={handleExportMealPrep}><Printer size={16} /> 导出备餐单</button>
              <button className="btn btn-primary" onClick={() => handleConsumeDelivered().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}><CheckCircle size={16} /> 批量核销扣餐</button>
            </div>
          ) : (
            <div style={{ display: "flex", gap: "8px", flexWrap: "wrap" }}>
              <span className="tag tag-red">待确认 {confirmationItems.length} 份</span>
              <span className="tag tag-orange">重点客户 {summary.priorityConfirmationCount} 人</span>
            </div>
          )}
        </div>

        {activeTab === "SUBSCRIPTION_MANAGEMENT" ? (
          <SubscriptionManagementTab />
        ) : activeTab === "CONFIRMATION" ? (
          <div style={{ display: "grid", gap: "12px", padding: "20px" }}>
            {confirmationItems.map((item) => (
              <div key={item.id} className="address-card" style={{ cursor: "default" }}>
                <div className="address-content">
                  <div className="address-title">
                    {item.customerName} / {item.customerPhone} / {item.mealPeriod === "LUNCH" ? "午餐" : "晚餐"}
                  </div>
                  <div className="address-detail">{item.addressLine || "未设置地址"}</div>
                  <div className="address-detail">用户备注：{item.userNote || "-"} / 后台备注：{item.adminNote || "-"}</div>
                </div>
                <div style={{ display: "flex", gap: "8px" }}>
                  <button className="btn btn-outline" onClick={() => handleCancelConfirmation(item.id).catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}>取消</button>
                  <button className="btn btn-primary" onClick={() => handleConfirmConfirmation(item.id).catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}>确认生成</button>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <>
            <div className="table-responsive">
              <table className="admin-table" style={{ background: "#FFFFFF", borderTop: "1px solid var(--border-color)", borderBottom: "1px solid var(--border-color)", width: "100%" }}>
                <thead>
                  <tr>
                    <th style={{ width: "40px" }}><input type="checkbox" /></th>
                    <th>客户标识</th>
                    <th>联系电话</th>
                    <th>餐次</th>
                    <th>用户备注</th>
                    <th>后台备注 / 特殊标签</th>
                    <th>配送地址</th>
                    <th>订单来源</th>
                    <th>状态</th>
                    <th>操作</th>
                  </tr>
                </thead>
                <tbody>
                  {view.filteredItems.length === 0 ? (
                    <tr>
                      <td colSpan={10} style={{ textAlign: "center", padding: "32px", color: "var(--text-muted)" }}>
                        当前条件下没有找到订单
                      </td>
                    </tr>
                  ) : (
                    view.pageItems.map((item) => {
                      const sourceLabel = resolveOrderSourceLabel(item);
                      const mealPeriod = resolveMealPeriod(item);
                      const isLunch = mealPeriod === "LUNCH";
                      const rowClass = getRowHighlightClass(item);
                      return (
                        <tr key={item.id} className={rowClass}>
                          <td><input type="checkbox" /></td>
                          <td>
                            <div style={{ display: "grid", gap: "6px" }}>
                              <div style={{ fontWeight: 700, display: "flex", alignItems: "center", gap: "6px", flexWrap: "wrap" }}>
                                <span>{item.customerName}</span>
                                {item.priorityCustomer && <span className="tag tag-orange">重点</span>}
                                {item.fixedSubscription && <span className="tag tag-blue">固定订餐</span>}
                              </div>
                            </div>
                          </td>
                          <td><span style={{ color: "var(--text-sub)" }}>{item.customerPhone}</span></td>
                          <td>
                            <span className={`tag ${isLunch ? "tag-orange" : "tag-green"}`}>{isLunch ? "午餐" : "晚餐"}</span>{item.quantity > 1 ? ` ×${item.quantity}` : ""}
                          </td>
                          <td style={{ color: formatOrderNote(item.userNote) === "-" ? undefined : "var(--error-color)", maxWidth: "160px" }}>{formatOrderNote(item.userNote)}</td>
                          <td style={{ maxWidth: "160px" }}>{formatOrderNote(item.adminNote)} {formatOrderNote(item.specialTag) === "-" ? "" : `/ ${item.specialTag}`}</td>
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
                const mealPeriod = resolveMealPeriod(item);
                const isLunch = mealPeriod === "LUNCH";
                const rowClass = getRowHighlightClass(item);
                return (
                  <div className={`mobile-card ${rowClass}`} key={item.id}>
                    <div className="mobile-card-header">
                      <div>
                        <span style={{ fontWeight: 700 }}>{item.customerName}</span>
                        <span style={{ color: "var(--text-sub)", fontSize: "12px", marginLeft: "8px" }}>{item.customerPhone}</span>
                        <div style={{ display: "flex", gap: "6px", flexWrap: "wrap", marginTop: "6px" }}>
                          {item.priorityCustomer && <span className="tag tag-orange">重点客户</span>}
                          {item.fixedSubscription && <span className="tag tag-blue">固定订餐</span>}
                          {formatOrderNote(item.specialTag) !== "-" && <span className="tag tag-gray">{item.specialTag}</span>}
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
                      <div className="mobile-card-label">餐次</div>
                      <div className="mobile-card-value">
                        <span className={`tag ${isLunch ? "tag-orange" : "tag-green"}`} style={{ marginRight: "4px" }}>{isLunch ? "午餐" : "晚餐"}</span>{item.quantity > 1 ? ` ×${item.quantity}` : ""}
                      </div>
                    </div>
                    <div className="mobile-card-row">
                      <div className="mobile-card-label">用户备注</div>
                      <div className="mobile-card-value" style={{ color: formatOrderNote(item.userNote) === "-" ? "inherit" : "var(--error-color)" }}>{formatOrderNote(item.userNote)}</div>
                    </div>
                    <div className="mobile-card-row">
                      <div className="mobile-card-label">后台备注</div>
                      <div className="mobile-card-value">{formatOrderNote(item.adminNote)} {formatOrderNote(item.specialTag) === "-" ? "" : `/ ${item.specialTag}`}</div>
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
            </div>

        <div className="pagination">
          <div className="pagination-info">共 {view.totalItems} 条记录，第 {view.currentPage} / {view.totalPages} 页</div>
          <div className="pagination-pages">
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
              <span className="modal-close" onClick={() => setIsSubscriptionPreviewOpen(false)}><X size={20} /></span>
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
                              value={item.defaultNote} 
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
                <button className="btn btn-outline" onClick={() => setIsSubscriptionPreviewOpen(false)}>取消</button>
                <button className="btn btn-primary" onClick={() => handleConfirmBulkImport().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))} disabled={isSubmittingImport}>
                  {isSubmittingImport ? "生成中..." : "确认生成订单"}
                </button>
              </div>
            </div>
          </div>
        </div>
        </div>
      )}

      {isSpecialOrdersOpen && (
        <div className="modal-overlay">
          <div className="modal-content" style={{ maxWidth: "860px" }}>
            <div className="modal-header">
              <span>特殊单明细 ({specialOrders.length})</span>
              <span className="modal-close" onClick={() => setIsSpecialOrdersOpen(false)}><X size={20} /></span>
            </div>
            <div className="modal-body" style={{ padding: 0 }}>
              {specialOrders.length === 0 ? (
                <div className="empty-state">当前没有特殊单</div>
              ) : (
                <div className="table-responsive">
                  <table style={{ margin: 0, border: "none" }}>
                    <thead>
                      <tr>
                        <th>客户</th>
                        <th>餐次</th>
                        <th>地址</th>
                        <th>用户备注</th>
                        <th>老板备注</th>
                        <th>特殊标签</th>
                      </tr>
                    </thead>
                    <tbody>
                      {specialOrders.map((item) => (
                        <tr key={item.id}>
                          <td>{item.customerName} / {item.customerPhone}</td>
                          <td>{item.mealPeriod === "LUNCH" ? "午餐" : "晚餐"}</td>
                          <td>{item.addressLine}</td>
                          <td>{item.userNote || "-"}</td>
                          <td>{item.adminNote || "-"}</td>
                          <td>{item.specialTag || (item.priorityCustomer ? "特殊客户" : "-")}</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                </div>
              )}
            </div>
          </div>
        </div>
      )}

      {isManualCreateOpen && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">
              <span>录入代客订单</span>
              <span className="modal-close" onClick={closeManualCreateModal}><X size={20} /></span>
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
              <RemarkField
                label="订单备注"
                value={manualForm.note}
                onChange={(value) => setManualForm({ ...manualForm, note: value })}
                placeholder="例如：少饭"
                scene="ORDER_REMARK"
                customerId={manualSelectedCustomer?.customerId ?? null}
              />
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={closeManualCreateModal}>取消</button>
              <button className="btn btn-primary" onClick={() => handleManualCreateSubmit().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}>确认录入</button>
            </div>
          </div>
        </div>
      )}

      {orderAftersaleItem && (
        <div className="modal-overlay">
          <div className="modal-content" style={{ maxWidth: "680px" }}>
            <div className="modal-header">
              <span>售后处理 - {orderAftersaleItem.customerName}</span>
              <span className="modal-close" onClick={closeOrderAftersaleModal}><X size={20} /></span>
            </div>
            <div className="modal-body" style={{ display: "grid", gap: "18px" }}>
              <div className="auth-panel">
                <div className="auth-panel__title">订单信息</div>
                <div className="auth-panel__grid">
                  <div><strong>订单</strong><span>#{orderAftersaleItem.id}</span></div>
                  <div><strong>客户</strong><span>{orderAftersaleItem.customerName} / {orderAftersaleItem.customerPhone}</span></div>
                  <div><strong>餐次</strong><span>{resolveMealPeriod(orderAftersaleItem) === "DINNER" ? "晚餐" : "午餐"} ×{orderAftersaleItem.quantity}</span></div>
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
                <label className="form-label">后台备注</label>
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
              <button className="btn btn-outline" onClick={closeOrderAftersaleModal}>取消</button>
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
              <span className="modal-close" onClick={() => setIsAssignOpen(false)}><X size={20} /></span>
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
              <button className="btn btn-outline" onClick={() => setIsAssignOpen(false)}>取消</button>
              <button className="btn btn-primary" onClick={() => handleAssignSubmit().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}>确认分配</button>
            </div>
          </div>
        </div>
      )}

      {isReceiptOpen && activeItem && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">
              <span>上传送达回执 - {activeItem.customerName}</span>
              <span className="modal-close" onClick={() => setIsReceiptOpen(false)}><X size={20} /></span>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label className="form-label"><span className="required">*</span>回执图片 URL</label>
                <input className="form-control" value={receiptForm.receiptUrl} onChange={e => setReceiptForm({...receiptForm, receiptUrl: e.target.value})} placeholder="https://" />
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
              <button className="btn btn-outline" onClick={() => setIsReceiptOpen(false)}>取消</button>
              <button className="btn btn-primary" onClick={() => handleReceiptSubmit().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}>提交回执</button>
            </div>
          </div>
        </div>
      )}

      {isEditOpen && activeItem && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">
              <span>编辑订单 - {activeItem.customerName}</span>
              <span className="modal-close" onClick={() => setIsEditOpen(false)}><X size={20} /></span>
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
                label="老板备注"
                value={editForm.adminNote}
                onChange={(value) => setEditForm({ ...editForm, adminNote: value })}
                placeholder="例如：少饭、多菜、先送"
                scene="ORDER_REMARK"
                multiline
              />
              <div className="form-group" style={{ marginBottom: 0 }}>
                <label className="form-label">特殊标签</label>
                <input className="form-control" value={editForm.specialTag} onChange={e => setEditForm({ ...editForm, specialTag: e.target.value })} placeholder="例如: VIP袋签" />
              </div>
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
              <button className="btn btn-outline" onClick={() => setIsEditOpen(false)}>取消</button>
              <button className="btn btn-primary" onClick={() => handleEditSubmit().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}>保存订单</button>
            </div>
          </div>
        </div>
      )}

      {/* 删除确认对话框 */}
      <AdminDialog
        open={isDeleteConfirmOpen}
        title="⚠️ 删除订单"
        width={500}
        onClose={() => { setIsDeleteConfirmOpen(false); setActiveItem(null); }}
        footer={
          <div style={{ display: "flex", gap: "12px", justifyContent: "flex-end" }}>
            <button className="btn btn-outline" onClick={() => { setIsDeleteConfirmOpen(false); setActiveItem(null); }}>取消</button>
            <button 
              className="btn-delete"
              onClick={() => activeItem && handleDelete(activeItem).catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}
            >
              <Trash2 size={16} />
              确认删除
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
                <span className="delete-confirm-details__value">{resolveMealPeriod(activeItem) === "DINNER" ? "晚餐" : "午餐"}</span>
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
