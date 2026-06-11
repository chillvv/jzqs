import React, { useEffect, useMemo, useState } from "react";
import {
  createCustomerProfile,
  fetchCustomerAssets,
  fetchCustomerDetail,
  fetchWalletTransactions,
  grantWalletMeals,
  deductWalletMeals,
  saveCustomerNote,
  updateCustomerProfile
} from "../../shared/api/http";
import type {
  CustomerAssetResponse,
  CustomerDetailResponse,
  WalletTransactionResponse
} from "../../shared/api/types";
import { MinusCircle, RotateCcw, Search, UserPlus, X } from "lucide-react";
import {
  buildCustomerActionLabels,
  buildCustomerAssetStats,
  buildCustomerOverviewSummary,
  extractCustomerNoteGroups,
  filterCustomerAssets,
  formatCustomerNoteSchedule,
  resolveCustomerSpecialMark,
  resolveCustomerStatusLabel,
  type CustomerBalanceState,
  type CustomerOrderModeFilter
} from "./customerAssetPage.helpers";
import { formatDateTimeLabel } from "../../shared/utils/dateTime";
import { AppSelect } from "../../shared/components/AppSelect";
import { RemarkField } from "../../shared/components/RemarkField";
import { toast } from "../../shared/components/Toast";

const emptyEditForm = {
  name: "",
  phone: "",
  remark: "",
  customerStatus: "INTENTION",
  initialMeals: "0"
};
const defaultGrantForm = { mealDelta: "5", remark: "补餐" };
const defaultDeductForm = { mealDelta: "1", remark: "手工扣减" };
const defaultCustomerNoteForm = {
  longTermUserNote: "",
  longTermMerchantNote: "",
  timeBoxedMerchantNote: "",
  timeBoxedStartAt: "",
  timeBoxedEndAt: ""
};

function buildEditForm(detail: CustomerDetailResponse | null, fallback: CustomerAssetResponse | null) {
  return {
    name: String(detail?.name || fallback?.name || ""),
    phone: String(detail?.phone || fallback?.phone || ""),
    remark: String(detail?.remark || fallback?.remark || ""),
    customerStatus: String(detail?.customerStatus || fallback?.customerStatus || "INTENTION")
  };
}

export function CustomerAssetPage() {
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
  const [customerNoteForm, setCustomerNoteForm] = useState(defaultCustomerNoteForm);
  const [keywordFilter, setKeywordFilter] = useState("");
  const [customerStatusFilter, setCustomerStatusFilter] = useState("ALL");
  const [balanceStateFilter, setBalanceStateFilter] = useState<CustomerBalanceState>("ALL");
  const [orderModeFilter, setOrderModeFilter] = useState<CustomerOrderModeFilter>("ALL");
  const [transactions, setTransactions] = useState<WalletTransactionResponse[]>([]);
  const [detail, setDetail] = useState<CustomerDetailResponse | null>(null);

  const [detailActionLabel, deductActionLabel] = useMemo(() => buildCustomerActionLabels(), []);

  useEffect(() => {
    reloadCustomers().catch((error) => window.alert(error?.response?.data?.message || error.message || String(error)));
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
    setCustomerNoteForm(defaultCustomerNoteForm);
  }

  async function refreshCustomerWorkspace(item: CustomerAssetResponse) {
    const nextItems = await reloadCustomers();
    const nextItem = nextItems.find((candidate) => candidate.id === item.id) ?? item;
    await loadCustomerWorkspace(nextItem);
  }

  async function handleGrantSubmit() {
    if (!activeItem || !grantForm.mealDelta) return;
    await grantWalletMeals(activeItem.id, Number(grantForm.mealDelta), "后台客服", grantForm.remark || "充值/补餐");
    setGrantForm(defaultGrantForm);
    await refreshCustomerWorkspace(activeItem);
  }

  async function handleDeductSubmit() {
    if (!activeItem || !deductForm.mealDelta) return;
    await deductWalletMeals(activeItem.id, Number(deductForm.mealDelta), "后台客服", deductForm.remark || "手工扣减");
    setIsDeductOpen(false);
    setDeductForm(defaultDeductForm);
    await reloadCustomers();
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
    if (!editForm.name || !editForm.phone) return;
    try {
      const newCustomer = await createCustomerProfile({
        name: editForm.name,
        phone: editForm.phone,
        remark: editForm.remark,
        priorityCustomer: false,
        priorityTag: "",
        priorityNote: ""
      });
      
      const meals = Number((editForm as any).initialMeals);
      if (meals > 0 && newCustomer && newCustomer.id) {
        await grantWalletMeals(newCustomer.id, meals, "后台客服", "建档初始加餐");
      }

      setIsEditOpen(false);
      setIsCreating(false);
      setEditForm(emptyEditForm);
      await reloadCustomers();
    } catch (err: any) {
      window.alert(err?.response?.data?.message || err?.message || "创建用户失败，请检查输入或重试");
    }
  }

  async function handleInlineEditSubmit() {
    if (!activeItem || !editForm.name || !editForm.phone) return;
    await updateCustomerProfile(activeItem.id, {
      name: editForm.name,
      phone: editForm.phone,
      remark: editForm.remark,
      customerStatus: editForm.customerStatus,
      priorityCustomer: false,
      priorityTag: "",
      priorityNote: ""
    });
    await refreshCustomerWorkspace(activeItem);
    setDetailMode("view");
  }

  const stats = useMemo(() => buildCustomerAssetStats(items), [items]);
  const overviewSummary = useMemo(() => buildCustomerOverviewSummary(stats), [stats]);

  const filteredItems = useMemo(
    () => filterCustomerAssets(items, {
      keyword: keywordFilter,
      customerStatus: customerStatusFilter,
      balanceState: balanceStateFilter,
      orderMode: orderModeFilter
    }),
    [items, keywordFilter, customerStatusFilter, balanceStateFilter, orderModeFilter]
  );
  const noteGroups = useMemo(() => extractCustomerNoteGroups(detail), [detail]);

  async function handleSaveCustomerNote(kind: "user" | "merchant" | "timeBoxedMerchant") {
    if (!activeItem) return;

    try {
      if (kind === "user") {
        const content = customerNoteForm.longTermUserNote.trim();
        if (!content) {
          toast("请填写长期用户备注", "error");
          return;
        }
        await saveCustomerNote(activeItem.id, {
          noteType: "USER",
          scopeType: "LONG_TERM",
          content,
          displayOrder: noteGroups.userNotes.length
        });
        setCustomerNoteForm((prev) => ({ ...prev, longTermUserNote: "" }));
      }

      if (kind === "merchant") {
        const content = customerNoteForm.longTermMerchantNote.trim();
        if (!content) {
          toast("请填写长期商家备注", "error");
          return;
        }
        await saveCustomerNote(activeItem.id, {
          noteType: "MERCHANT",
          scopeType: "LONG_TERM",
          content,
          displayOrder: noteGroups.longTermMerchantNotes.length
        });
        setCustomerNoteForm((prev) => ({ ...prev, longTermMerchantNote: "" }));
      }

      if (kind === "timeBoxedMerchant") {
        const content = customerNoteForm.timeBoxedMerchantNote.trim();
        if (!content) {
          toast("请填写限时商家备注", "error");
          return;
        }
        if (!customerNoteForm.timeBoxedStartAt || !customerNoteForm.timeBoxedEndAt) {
          toast("请填写限时备注开始和结束时间", "error");
          return;
        }
        await saveCustomerNote(activeItem.id, {
          noteType: "MERCHANT",
          scopeType: "TIME_BOXED",
          content,
          startAt: customerNoteForm.timeBoxedStartAt,
          endAt: customerNoteForm.timeBoxedEndAt,
          displayOrder: noteGroups.timeBoxedMerchantNotes.length
        });
        setCustomerNoteForm((prev) => ({
          ...prev,
          timeBoxedMerchantNote: "",
          timeBoxedStartAt: "",
          timeBoxedEndAt: ""
        }));
      }

      await refreshCustomerWorkspace(activeItem);
      toast("客户备注已保存");
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "保存客户备注失败", "error");
    }
  }

  function resetCustomerFilters() {
    setKeywordFilter("");
    setCustomerStatusFilter("ALL");
    setBalanceStateFilter("ALL");
    setOrderModeFilter("ALL");
  }

  function resolveWalletTransactionTypeLabel(type: string) {
    if (type === "GRANT") return "后台发放";
    if (type === "RESERVE") return "下单占用";
    if (type === "RELEASE") return "取消释放";
    if (type === "MANUAL_DEDUCT") return "手工扣减";
    if (type === "AFTERSALE_ROLLBACK") return "售后回滚";
    return type || "餐次变动";
  }

  const renderActions = (item: CustomerAssetResponse) => (
    <div className="customer-action-group">
      <button type="button" className="customer-action-btn customer-action-btn--primary" onClick={() => handleOpenDetail(item).catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}>
        {detailActionLabel}
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
          <input
            type="text"
            className="input-box customer-filter-input"
            placeholder="搜索客户手机号/姓名/备注"
            value={keywordFilter}
            onChange={(e) => setKeywordFilter(e.target.value)}
          />
          <AppSelect
            className="app-select--filter customer-filter-select"
            value={customerStatusFilter}
            options={[
              { label: "全部状态", value: "ALL" },
              { label: "意向客户", value: "INTENTION" },
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
          <button className="btn btn-primary" onClick={() => reloadCustomers().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}><Search size={16} /> 刷新</button>
          <button
            className="btn btn-outline"
            onClick={() => {
              resetCustomerFilters();
              reloadCustomers().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)));
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
        <div className="table-responsive table-responsive--fixed-height">
          <table>
          <thead>
            <tr>
              <th>客户姓名</th>
              <th>联系电话</th>
              <th>客户状态</th>
              <th>客户备注</th>
              <th>餐次余额</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {filteredItems.map((item) => {
              const exhausted = item.status === "EXHAUSTED";
              const statusLabel = resolveCustomerStatusLabel(item.customerStatus);
              const specialMark = resolveCustomerSpecialMark(item.remark);
              return (
                <tr key={item.id}>
                  <td>
                    <div className="customer-table-name">
                      <div className="customer-table-name__main">
                        <span style={{ whiteSpace: "nowrap" }}>{item.name}</span>
                        {specialMark && <span className="customer-special-mark" title={specialMark}>特殊备注</span>}
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
                    <div className="customer-table-note">{item.remark || "暂无备注"}</div>
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
                <td colSpan={6}>
                  <div className="empty-state">暂无符合条件的客户记录</div>
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
            const specialMark = resolveCustomerSpecialMark(item.remark);
            return (
              <div className="mobile-card" key={item.id}>
                <div className="mobile-card-header">
                  <div>
                    <div className="customer-mobile-name-row">
                      <span className="customer-mobile-name-row__name">{item.name}</span>
                      {specialMark && <span className="customer-special-mark" title={specialMark}>特殊备注</span>}
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
                  <div className="mobile-card-label">备注</div>
                  <div className="mobile-card-value">{item.remark || "-"}</div>
                </div>
                <div className="mobile-card-footer">
                  {renderActions(item)}
                </div>
              </div>
            );
          })}
          {filteredItems.length === 0 && (
            <div className="empty-state">当前筛选条件下没有客户</div>
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
                      <button className="btn btn-outline" onClick={handleCancelInlineEdit}>取消编辑</button>
                      <button className="btn btn-primary" onClick={() => handleInlineEditSubmit().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}>保存资料</button>
                    </>
                  ) : (
                    <button className="btn btn-outline" onClick={handleStartInlineEdit}>编辑资料</button>
                  )}
                </div>
              </div>

              <div className="customer-detail-kpi-grid">
                <div className="customer-detail-kpi">
                  <div className="customer-detail-kpi__label">当前余额</div>
                  <div className={`customer-detail-kpi__value ${Number(detail?.remainingMeals ?? activeItem.remainingMeals) <= 0 ? "is-danger" : ""}`}>
                    {String(detail?.remainingMeals ?? activeItem.remainingMeals)} 餐
                  </div>
                </div>
                <div className="customer-detail-kpi">
                  <div className="customer-detail-kpi__label">客户状态</div>
                  {detailMode === "edit" ? (
                    <div className="customer-detail-kpi__value">
                      <AppSelect
                        value={editForm.customerStatus}
                        options={[
                          { label: "意向客户", value: "INTENTION" },
                          { label: "正式客户", value: "FORMAL" },
                          { label: "沉睡客户", value: "DORMANT" }
                        ]}
                        onChange={(val) => setEditForm({ ...editForm, customerStatus: val })}
                        style={{ width: "100%" }}
                      />
                    </div>
                  ) : (
                    <div className="customer-detail-kpi__value">{resolveCustomerStatusLabel(String(detail?.customerStatus || activeItem.customerStatus))}</div>
                  )}
                </div>
              </div>

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
                          <input className="form-control" value={editForm.name} onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} />
                        </div>
                        <div className="form-group">
                          <label className="form-label"><span className="required">*</span>联系电话</label>
                          <input className="form-control" value={editForm.phone} onChange={(e) => setEditForm({ ...editForm, phone: e.target.value })} />
                        </div>
                      </div>
                      <div className="customer-detail-inline-form__remark">
                        <RemarkField
                          label="客户备注"
                          value={editForm.remark}
                          onChange={(value) => setEditForm({ ...editForm, remark: value })}
                          placeholder="记录客户习惯、地址说明、常见沟通点"
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
                      </div>
                      <div className="customer-detail-note-block">
                        <div className="customer-detail-note-block__label">客户备注</div>
                        <div className="customer-detail-note-block__value">{String(detail?.remark || activeItem.remark || "-")}</div>
                      </div>
                    </>
                  )}
                </section>

                <section className="customer-detail-card">
                  <div className="customer-detail-card__header">
                    <div className="customer-detail-card__title">客户级备注</div>
                  </div>
                  <div className="customer-detail-note-block">
                    <div className="customer-detail-note-block__label">长期用户备注</div>
                    <div className="customer-detail-note-block__value">
                      {noteGroups.userNotes.length > 0
                        ? noteGroups.userNotes.map((note) => note.content).join(" / ")
                        : "-"}
                    </div>
                  </div>
                  <div className="customer-detail-note-block">
                    <div className="customer-detail-note-block__label">长期商家备注</div>
                    <div className="customer-detail-note-block__value">
                      {noteGroups.longTermMerchantNotes.length > 0
                        ? noteGroups.longTermMerchantNotes.map((note) => note.content).join(" / ")
                        : "-"}
                    </div>
                  </div>
                  <div className="customer-detail-note-block">
                    <div className="customer-detail-note-block__label">限时商家备注</div>
                    <div className="customer-detail-note-block__value">
                      {noteGroups.timeBoxedMerchantNotes.length > 0
                        ? noteGroups.timeBoxedMerchantNotes.map((note) => `${note.content}（${formatCustomerNoteSchedule(note)}）`).join(" / ")
                        : "-"}
                    </div>
                  </div>
                  <div className="customer-detail-inline-form" style={{ marginTop: "12px" }}>
                    <div className="customer-edit-form-grid">
                      <div className="form-group">
                        <label className="form-label">长期用户备注</label>
                        <input
                          className="form-control"
                          value={customerNoteForm.longTermUserNote}
                          onChange={(e) => setCustomerNoteForm((prev) => ({ ...prev, longTermUserNote: e.target.value }))}
                          placeholder="例如：少饭"
                        />
                      </div>
                      <div className="form-group" style={{ display: "flex", alignItems: "flex-end" }}>
                        <button className="btn btn-outline" onClick={() => handleSaveCustomerNote("user")}>保存用户备注</button>
                      </div>
                    </div>
                    <div className="customer-edit-form-grid">
                      <div className="form-group">
                        <label className="form-label">长期商家备注</label>
                        <input
                          className="form-control"
                          value={customerNoteForm.longTermMerchantNote}
                          onChange={(e) => setCustomerNoteForm((prev) => ({ ...prev, longTermMerchantNote: e.target.value }))}
                          placeholder="例如：重点关注"
                        />
                      </div>
                      <div className="form-group" style={{ display: "flex", alignItems: "flex-end" }}>
                        <button className="btn btn-outline" onClick={() => handleSaveCustomerNote("merchant")}>保存商家备注</button>
                      </div>
                    </div>
                    <div className="customer-edit-form-grid">
                      <div className="form-group">
                        <label className="form-label">限时商家备注</label>
                        <input
                          className="form-control"
                          value={customerNoteForm.timeBoxedMerchantNote}
                          onChange={(e) => setCustomerNoteForm((prev) => ({ ...prev, timeBoxedMerchantNote: e.target.value }))}
                          placeholder="例如：周卡体验"
                        />
                      </div>
                      <div className="form-group">
                        <label className="form-label">开始时间</label>
                        <input
                          className="form-control"
                          type="datetime-local"
                          value={customerNoteForm.timeBoxedStartAt}
                          onChange={(e) => setCustomerNoteForm((prev) => ({ ...prev, timeBoxedStartAt: e.target.value }))}
                        />
                      </div>
                    </div>
                    <div className="customer-edit-form-grid">
                      <div className="form-group">
                        <label className="form-label">结束时间</label>
                        <input
                          className="form-control"
                          type="datetime-local"
                          value={customerNoteForm.timeBoxedEndAt}
                          onChange={(e) => setCustomerNoteForm((prev) => ({ ...prev, timeBoxedEndAt: e.target.value }))}
                        />
                      </div>
                      <div className="form-group" style={{ display: "flex", alignItems: "flex-end" }}>
                        <button className="btn btn-outline" onClick={() => handleSaveCustomerNote("timeBoxedMerchant")}>保存限时备注</button>
                      </div>
                    </div>
                  </div>
                </section>

                <section className="customer-detail-card">
                  <div className="customer-detail-card__title">充值补餐</div>
                  <div className="customer-operation-form-grid">
                    <div className="form-group">
                      <label className="form-label"><span className="required">*</span>充值/补餐数量</label>
                      <input className="form-control" type="number" value={grantForm.mealDelta} onChange={(e) => setGrantForm({ ...grantForm, mealDelta: e.target.value })} />
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
                    <button className="btn btn-primary" onClick={() => handleGrantSubmit().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}>确认充值</button>
                  </div>
                </section>
              </div>

              <section className="customer-detail-card customer-detail-card--full">
                <div className="customer-detail-card__title">流水记录</div>
                {transactions.length === 0 ? (
                  <div className="empty-state customer-detail-empty-state">暂无流水记录</div>
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

      {isEditOpen && isCreating && (
        <div className="modal-overlay">
          <div className="modal-content modal-content--customer-create">
            <div className="modal-header">
              <span>新建客户档案</span>
              <span className="modal-close" onClick={() => { setIsEditOpen(false); setIsCreating(false); }}><X size={20} /></span>
            </div>
            <div className="modal-body customer-edit-modal">
              <div className="customer-edit-grid">
                <section className="customer-edit-section">
                  <div className="customer-edit-section__title">基础资料</div>
                  <div className="customer-edit-form-grid">
                    <div className="form-group">
                      <label className="form-label"><span className="required">*</span>客户姓名</label>
                      <input className="form-control" value={editForm.name} onChange={(e) => setEditForm({ ...editForm, name: e.target.value })} />
                    </div>
                    <div className="form-group">
                      <label className="form-label"><span className="required">*</span>联系电话</label>
                      <input className="form-control" value={editForm.phone} onChange={(e) => setEditForm({ ...editForm, phone: e.target.value })} />
                    </div>
                  </div>
                  <div className="form-group" style={{ marginTop: "16px" }}>
                    <label className="form-label">初始加餐数量（选填）</label>
                    <input 
                      className="form-control" 
                      type="number" 
                      min="0"
                      value={(editForm as any).initialMeals || "0"} 
                      onChange={(e) => setEditForm({ ...editForm, initialMeals: e.target.value } as any)} 
                    />
                    <div className="admin-panel-note" style={{ marginTop: "4px" }}>如果填写大于 0，将在建档后自动为用户加餐</div>
                  </div>
                  <div className="customer-create-remark-field">
                    <RemarkField
                      label="客户备注"
                      value={editForm.remark}
                      onChange={(value) => setEditForm({ ...editForm, remark: value })}
                      placeholder="记录客户习惯、地址说明、常见沟通点"
                      scene="CUSTOMER_REMARK"
                      multiline
                    />
                  </div>
                </section>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={() => { setIsEditOpen(false); setIsCreating(false); }}>取消</button>
              <button className="btn btn-primary" onClick={() => handleCreateSubmit().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}>确认创建</button>
            </div>
          </div>
        </div>
      )}

      {isDeductOpen && activeItem && (
        <div className="modal-overlay">
          <div className="modal-content modal-content--customer-deduct">
            <div className="modal-header">
              <span>扣餐 - {activeItem.name}</span>
              <span className="modal-close" onClick={() => setIsDeductOpen(false)}><X size={20} /></span>
            </div>
            <div className="modal-body customer-operation-modal">
              <div className="customer-operation-topbar">
                <div className="customer-operation-topbar__item">
                  <span className="customer-operation-topbar__label">当前余额</span>
                  <span className="customer-operation-topbar__value">{activeItem.remainingMeals} 餐</span>
                </div>
                <div className="customer-operation-topbar__item">
                  <span className="customer-operation-topbar__label">客户状态</span>
                  <span className="customer-operation-topbar__value">{resolveCustomerStatusLabel(activeItem.customerStatus)}</span>
                </div>
              </div>
              <section className="customer-operation-panel customer-operation-panel--danger">
                <div className="customer-operation-panel__title">本次扣餐信息</div>
                <div className="customer-operation-form-grid">
                  <div className="form-group">
                    <label className="form-label"><span className="required">*</span>扣减数量</label>
                    <input className="form-control" type="number" value={deductForm.mealDelta} onChange={(e) => setDeductForm({ ...deductForm, mealDelta: e.target.value })} />
                  </div>
                  <RemarkField
                    label="操作备注"
                    value={deductForm.remark}
                    onChange={(value) => setDeductForm({ ...deductForm, remark: value })}
                    placeholder="例如：客户微信群确认本餐作废，后台扣回 1 餐"
                    scene="WALLET_REMARK"
                  />
                </div>
              </section>
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={() => setIsDeductOpen(false)}>取消</button>
              <button className="btn btn-danger" onClick={() => handleDeductSubmit().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}>确认扣减</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
