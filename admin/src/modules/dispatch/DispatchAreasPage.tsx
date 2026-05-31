import React, { useEffect, useMemo, useState } from "react";
import { GripVertical, Pencil, PlusCircle, Shuffle, Trash2, UserPlus } from "lucide-react";
import { DragDropContext, Droppable, Draggable, type DropResult } from '@hello-pangea/dnd';
import {
  DispatchAreaDeleteBlockedError,
  deleteDispatchArea,
  deleteOrder,
  fetchDispatchAreaBindings,
  fetchDispatchManagedRiders,
  moveOrderToArea,
  renameDispatchArea,
  assignRiderToArea,
  assignRiderToAreaOrder,
  reorderAreaOrders,
  updateDispatchAreaBinding
} from "../../shared/api/http";
import type {
  DispatchAreaBlockingOrder,
  DispatchAreaBindingResponse,
  DispatchManagedRiderResponse,
  DispatchAreaOrderItemResponse
} from "../../shared/api/types";
import { AppSelect } from "../../shared/components/AppSelect";
import { AdminDialog } from "../../shared/components/AdminDialog";
import {
  buildDispatchAreaStats,
  DEFAULT_OPERATOR,
  normalizeDispatchAreaBindings,
  mealPeriodLabel
} from "./dispatchCenterLayout.helpers";
import { useDispatchContext } from "./DispatchContext";

const inputStyle: React.CSSProperties = {
  height: "36px",
  borderRadius: "10px",
  border: "1px solid var(--border-color)",
  padding: "0 10px",
  backgroundColor: "#fff",
  color: "var(--text-main)",
  width: "100%"
};

const selectStyle: React.CSSProperties = { width: "100%" };

type DeleteBlockedState = {
  areaCode: string;
  message: string;
  activeOrderCount: number;
  orders: DispatchAreaBlockingOrder[];
};

// 可拖拽的订单项组件
function DraggableOrderItem({ 
  order, 
  index, 
  isReordering,
  onDetailClick,
  onMoveClick,
}: { 
  order: DispatchAreaOrderItemResponse;
  index: number;
  isReordering: boolean;
  onDetailClick: () => void;
  onMoveClick: () => void;
}) {
  const statusClass = 
    order.deliveryStatus === "DELIVERED" ? "delivered" : "dispatching";
  
  const isMultiple = order.quantity && order.quantity > 1;
  const itemId = `order-${order.orderId}`;

  return (
    <Draggable 
      draggableId={itemId} 
      index={index}
      isDragDisabled={!isReordering}
    >
      {(provided, snapshot) => (
        <div
          ref={provided.innerRef}
          {...provided.draggableProps}
          {...provided.dragHandleProps}
          className={`dispatch-area-orders__item ${statusClass} ${snapshot.isDragging ? "dragging" : ""} ${!isReordering ? "no-drag" : ""} ${isMultiple ? "multiple-order" : ""} ${isReordering ? "reordering" : ""}`}
        >
          <div className="dispatch-area-orders__top">
            <div style={{ display: "inline-flex", alignItems: "center", gap: "8px" }}>
              <div
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  padding: '4px 6px',
                  borderRadius: '6px',
                  background: isReordering ? 'rgba(251, 146, 60, 0.15)' : 'transparent',
                }}
              >
                <GripVertical 
                  size={16} 
                  style={{ 
                    opacity: isReordering ? 1 : 0.3,
                    color: isReordering ? '#f97316' : 'inherit',
                  }} 
                />
              </div>
              <strong>#{index + 1}</strong>
              <span>{order.customerName}</span>
              {isMultiple && (
                <span className="quantity-badge">×{order.quantity}</span>
              )}
            </div>
            <span className={`tag ${order.deliveryStatus === "DELIVERED" ? "tag-green" : "tag-amber"}`}>
              {order.deliveryStatus === "DELIVERED" ? "已送达" : "待配送"}
            </span>
          </div>
          <div style={{ 
            overflow: "hidden", 
            textOverflow: "ellipsis", 
            whiteSpace: "nowrap",
            maxWidth: "100%"
          }}>
            {order.deliveryAddress}
          </div>
          
          <div style={{ display: "flex", gap: "8px", marginTop: "8px", flexWrap: "wrap" }}>
            {order.userNote && (
              <span className="tag tag-gray" style={{ fontSize: "11px" }}>用户备注</span>
            )}
            {order.adminNote && (
              <span className="tag tag-gray" style={{ fontSize: "11px" }}>商家备注</span>
            )}
            {order.receiptNote && (
              <span className="tag tag-blue" style={{ fontSize: "11px" }}>骑手备注</span>
            )}
            {order.receiptUrl && (
              <span className="tag tag-green" style={{ fontSize: "11px" }}>📷 有图片</span>
            )}
          </div>
          
          {order.deliveredAt && (
            <div style={{ fontSize: "11px", color: "#999", marginTop: "4px" }}>
              送达：{order.deliveredAt}
            </div>
          )}
          
          <div className="dispatch-order-item__actions">
            <button
              className="btn btn-outline btn-compact"
              onClick={(e) => { e.stopPropagation(); onDetailClick(); }}
            >
              详情
            </button>
            <button
              className="btn btn-outline btn-compact"
              onClick={(e) => { e.stopPropagation(); onMoveClick(); }}
            >
              <Shuffle size={14} /> 移出
            </button>
          </div>
        </div>
      )}
    </Draggable>
  );
}

export function DispatchAreasPage() {
  const { serveDate, mealPeriod } = useDispatchContext();
  const [bindings, setBindings] = useState<DispatchAreaBindingResponse[]>([]);
  const [riders, setRiders] = useState<DispatchManagedRiderResponse[]>([]);
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [savingArea, setSavingArea] = useState<string | null>(null);
  const [newArea, setNewArea] = useState({ name: "", riderId: "" });
  const [renamingArea, setRenamingArea] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState("");
  const [deletingArea, setDeletingArea] = useState<string | null>(null);
  const [deleteBlockedState, setDeleteBlockedState] = useState<DeleteBlockedState | null>(null);
  const [activeAreaCode, setActiveAreaCode] = useState<string | null>(null);
  const [assignRiderAreaCode, setAssignRiderAreaCode] = useState<string | null>(null);
  const [selectedRiderId, setSelectedRiderId] = useState("");
  const [moveState, setMoveState] = useState<{ areaCode: string; orderId: number; targetAreaCode: string } | null>(null);
  const [isReordering, setIsReordering] = useState(false);
  const [localOrders, setLocalOrders] = useState<DispatchAreaOrderItemResponse[]>([]);
  const [orderDetailId, setOrderDetailId] = useState<number | null>(null);
  const [orderRiderChangeState, setOrderRiderChangeState] = useState<{ orderId: number; riderId: string } | null>(null);
  const [deleteConfirmState, setDeleteConfirmState] = useState<{ orderId: number; customerName: string } | null>(null);

  useEffect(() => {
    reload().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)));
  }, [mealPeriod, serveDate]);

  const riderOptions = useMemo(
    () =>
      riders
        .filter((rider) => rider.authStatus === "ACTIVE")
        .map((rider) => ({ label: `${rider.riderName} (${rider.phone || "--"})`, value: String(rider.riderId) })),
    [riders]
  );

  const areaStats = useMemo(() => buildDispatchAreaStats(bindings), [bindings]);

  const activeArea = useMemo(
    () => bindings.find((item) => item.areaCode === activeAreaCode) ?? null,
    [activeAreaCode, bindings]
  );
  const activeAreaOrders = activeArea?.orders ?? [];
  
  // 使用本地状态或原始数据
  const displayOrders = isReordering && localOrders.length > 0 ? localOrders : activeAreaOrders;
  
  // 当区域切换时重置本地状态（只在区域切换时触发，不在拖拽时触发）
  useEffect(() => {
    if (activeAreaCode) {
      setLocalOrders([]);
      setIsReordering(false);
    }
  }, [activeAreaCode]); // 只依赖 activeAreaCode
  
  const orderDetail = useMemo(
    () => displayOrders.find((item) => item.orderId === orderDetailId) ?? null,
    [orderDetailId, displayOrders]
  );

  async function reload() {
    const results = await Promise.allSettled([
      fetchDispatchAreaBindings(mealPeriod, serveDate),
      fetchDispatchManagedRiders()
    ]);
    const [bindingResult, riderResult] = results;
    if (bindingResult.status === "fulfilled") setBindings(normalizeDispatchAreaBindings(bindingResult.value));
    if (riderResult.status === "fulfilled") setRiders(riderResult.value);
  }

  async function handleAssignRider() {
    if (!assignRiderAreaCode || !selectedRiderId) return;
    const area = bindings.find((item) => item.areaCode === assignRiderAreaCode);
    const rider = riders.find((item) => String(item.riderId) === selectedRiderId);
    if (!area || !rider) return;
    setSavingArea(assignRiderAreaCode);
    try {
      await updateDispatchAreaBinding(assignRiderAreaCode, {
        keywords: area.keywords,
        defaultRiderId: rider.riderId,
        updatedBy: DEFAULT_OPERATOR
      });
      const result = await assignRiderToArea(assignRiderAreaCode, rider.riderName, mealPeriod);
      setAssignRiderAreaCode(null);
      setSelectedRiderId("");
      await reload();
      if (result.assignedCount === 0) {
        window.alert(`已将 ${rider.riderName} 绑定为「${assignRiderAreaCode}」的默认骑手，但当前${mealPeriodLabel(mealPeriod)}该区域暂无待分配订单，新订单归入后会自动指派。`);
      }
    } catch (err: any) {
      window.alert(err?.response?.data?.message || err.message || String(err));
    } finally {
      setSavingArea(null);
    }
  }

  async function handleCreateArea() {
    if (!newArea.name.trim()) return;
    setSavingArea("__new__");
    try {
      await updateDispatchAreaBinding(newArea.name.trim(), {
        keywords: newArea.name.trim(),
        defaultRiderId: newArea.riderId ? Number(newArea.riderId) : null,
        updatedBy: DEFAULT_OPERATOR
      });
      setNewArea({ name: "", riderId: "" });
      setShowCreateModal(false);
      await reload();
    } catch (err: any) {
      window.alert(err?.response?.data?.message || err.message || String(err));
    } finally {
      setSavingArea(null);
    }
  }

  async function handleMoveOrder() {
    if (!moveState || !moveState.targetAreaCode) return;
    setSavingArea(moveState.areaCode);
    try {
      await moveOrderToArea(moveState.areaCode, moveState.orderId, {
        targetAreaCode: moveState.targetAreaCode,
        updatedBy: DEFAULT_OPERATOR
      });
      setMoveState(null);
      await reload();
    } catch (err: any) {
      window.alert(err?.response?.data?.message || err.message || String(err));
    } finally {
      setSavingArea(null);
    }
  }

  async function handleDeleteOrder(orderId: number, customerName: string) {
    setDeleteConfirmState({ orderId, customerName });
  }

  async function confirmDeleteOrder() {
    if (!deleteConfirmState) return;
    
    try {
      await deleteOrder(deleteConfirmState.orderId);
      setDeleteConfirmState(null);
      await reload();
      window.alert('订单已删除');
    } catch (err: any) {
      window.alert(err?.response?.data?.message || err.message || String(err));
    }
  }

  function toggleReorderMode() {
    if (isReordering) {
      saveOrderSequence();
    } else {
      setIsReordering(true);
      setLocalOrders([...activeAreaOrders]);
    }
  }
  
  async function saveOrderSequence() {
    if (!activeArea || localOrders.length === 0) {
      setIsReordering(false);
      return;
    }
    
    setSavingArea(activeArea.areaCode);
    try {
      await reorderAreaOrders(activeArea.areaCode, localOrders.map((item, index) => ({
        orderId: item.orderId,
        sequenceNumber: index + 1
      })));
      await reload();
      setIsReordering(false);
      setLocalOrders([]);
    } catch (err: any) {
      window.alert(err?.response?.data?.message || err.message || String(err));
    } finally {
      setSavingArea(null);
    }
  }

  function handleDragEnd(result: DropResult) {
    if (!result.destination) return;
    const sourceIndex = result.source.index;
    const destIndex = result.destination.index;
    if (sourceIndex === destIndex) return;
    setLocalOrders((prevItems) => {
      const newItems = Array.from(prevItems);
      const [removed] = newItems.splice(sourceIndex, 1);
      newItems.splice(destIndex, 0, removed);
      return newItems;
    });
  }

  function startRename(areaCode: string) {
    setRenamingArea(areaCode);
    setRenameValue(areaCode);
  }

  function cancelRename() {
    setRenamingArea(null);
    setRenameValue("");
  }

  async function handleRename() {
    if (!renamingArea || !renameValue.trim() || renameValue.trim() === renamingArea) {
      cancelRename();
      return;
    }
    setSavingArea(renamingArea);
    try {
      await renameDispatchArea(renamingArea, renameValue.trim());
      if (activeAreaCode === renamingArea) {
        setActiveAreaCode(renameValue.trim());
      }
      cancelRename();
      await reload();
    } catch (err: any) {
      window.alert(err?.response?.data?.message || err.message || String(err));
    } finally {
      setSavingArea(null);
    }
  }

  async function handleDelete(areaCode: string) {
    setSavingArea(areaCode);
    try {
      await deleteDispatchArea(areaCode);
      if (deleteBlockedState?.areaCode === areaCode) {
        setDeleteBlockedState(null);
      }
      if (activeAreaCode === areaCode) {
        setActiveAreaCode(null);
      }
      setDeletingArea(null);
      await reload();
    } catch (err: any) {
      if (err instanceof DispatchAreaDeleteBlockedError) {
        setDeleteBlockedState({
          areaCode: err.details.areaCode,
          message: err.message,
          activeOrderCount: err.details.activeOrderCount,
          orders: err.details.orders
        });
        return;
      }
      window.alert(err?.response?.data?.message || err.message || String(err));
    } finally {
      setSavingArea(null);
    }
  }

  function closeDeleteDialog() {
    if (deleteBlockedState?.areaCode === deletingArea) {
      setDeleteBlockedState(null);
    }
    setDeletingArea(null);
  }

  async function handleRefreshAreaBinding(areaCode: string) {
    const area = bindings.find((item) => item.areaCode === areaCode);
    if (!area) return;
    if (!selectedRiderId) return;
    setSavingArea(areaCode);
    try {
      await updateDispatchAreaBinding(areaCode, {
        keywords: area.keywords,
        defaultRiderId: Number(selectedRiderId),
        updatedBy: DEFAULT_OPERATOR
      });
      setAssignRiderAreaCode(null);
      setSelectedRiderId("");
      await reload();
    } catch (err: any) {
      window.alert(err?.response?.data?.message || err.message || String(err));
    } finally {
      setSavingArea(null);
    }
  }

  async function handleChangeOrderRider() {
    if (!orderRiderChangeState || !activeArea) return;
    const rider = riders.find((r) => String(r.riderId) === orderRiderChangeState.riderId);
    if (!rider) return;
    setSavingArea(activeArea.areaCode);
    try {
      await assignRiderToAreaOrder(activeArea.areaCode, orderRiderChangeState.orderId, rider.riderName);
      setOrderRiderChangeState(null);
      await reload();
      window.alert(`已将订单 #${orderRiderChangeState.orderId} 分配给 ${rider.riderName}`);
    } catch (err: any) {
      window.alert(err?.response?.data?.message || err.message || String(err));
    } finally {
      setSavingArea(null);
    }
  }

  const activeDeleteBlock = deleteBlockedState?.areaCode === deletingArea ? deleteBlockedState : null;

  return (
    <div className="admin-stack">
      <div className="toolbar">
        <div className="dispatch-toolbar">
          <div>
            <div className="dispatch-section__title">区域管理</div>
            <div className="dispatch-section__note">当前查看 {mealPeriodLabel(mealPeriod)} 区域卡片，点击卡片进入区域详情弹窗，可直接拖拽排序、移区和更换骑手。</div>
          </div>
          <div className="dispatch-toolbar__actions">
            <button className="btn btn-primary" onClick={() => setShowCreateModal(true)}>
              <PlusCircle size={16} /> 新增区域
            </button>
          </div>
        </div>
      </div>

      <div className="admin-grid-3">
        <div className="admin-panel" style={{ padding: "16px", display: "grid", gap: "8px" }}>
          <div className="admin-panel-note">区域总数</div>
          <div className="admin-panel-title">{areaStats.totalCount} 个</div>
        </div>
        <div className="admin-panel" style={{ padding: "16px", display: "grid", gap: "8px" }}>
          <div className="admin-panel-note">待配送订单</div>
          <div className="admin-panel-title" style={{ color: "var(--primary-color)" }}>{areaStats.dispatchingCount} 单</div>
        </div>
        <div className="admin-panel" style={{ padding: "16px", display: "grid", gap: "8px", borderLeft: "4px solid var(--error-color)" }}>
          <div className="admin-panel-note">缺骑手区域</div>
          <div className="admin-panel-title" style={{ color: "var(--error-color)" }}>{areaStats.missingRiderAreaCount} 个</div>
        </div>
      </div>

      {bindings.length === 0 ? (
        <div className="dispatch-empty">暂无区域，请先创建一个区域并绑定骑手。</div>
      ) : (
        <div className="dispatch-area-grid">
          {bindings.map((area) => (
            <button
              key={area.areaCode}
              type="button"
              className={`dispatch-card ${area.missingRider ? "has-warning" : ""}`}
              style={{ textAlign: "left", cursor: "pointer" }}
              onClick={() => setActiveAreaCode(area.areaCode)}
            >
              <div className="dispatch-card__header">
                <div>
                  <div className="dispatch-card__title">{area.areaCode}</div>
                  <div className="dispatch-card__subtitle">骑手：{area.currentRiderName || (area.missingRider ? "缺骑手" : area.defaultRiderName || "未设置")}</div>
                  <div className="dispatch-card__subtitle">订单数：{area.orderCount}</div>
                </div>
                <span className={`tag ${area.missingRider ? "tag-red" : "tag-blue"}`}>
                  {area.missingRider ? "缺骑手" : `${area.orderCount} 单`}
                </span>
              </div>

              {area.missingRider ? (
                <div className="dispatch-area-warning" style={{ margin: "0 0 12px" }}>
                  <div className="dispatch-area-warning__title">该区域当前有订单但没有骑手，请尽快处理</div>
                </div>
              ) : null}
              <div className="dispatch-chip-list" style={{ marginTop: "12px" }}>
                <span className={area.currentRiderName ? "tag tag-green" : "tag tag-gray"}>
                  {area.currentRiderName || area.defaultRiderName || "暂无骑手"}
                </span>
              </div>
            </button>
          ))}
        </div>
      )}

      <AdminDialog
        open={showCreateModal}
        title="新增区域"
        description="创建一个新区域后，可以立即绑定默认骑手。"
        onClose={() => setShowCreateModal(false)}
        footer={
          <>
            <button className="btn btn-outline" onClick={() => setShowCreateModal(false)}>取消</button>
            <button className="btn btn-primary" disabled={savingArea === "__new__" || !newArea.name.trim()} onClick={handleCreateArea}>
              <PlusCircle size={14} /> 创建区域
            </button>
          </>
        }
      >
        <label className="admin-field">
          <span className="admin-field-label">区域名称</span>
          <input
            value={newArea.name}
            onChange={(event) => setNewArea((prev) => ({ ...prev, name: event.target.value }))}
            placeholder="例如：万达商圈"
            style={inputStyle}
          />
        </label>
        <label className="admin-field">
          <span className="admin-field-label">默认骑手</span>
          <AppSelect
            value={newArea.riderId}
            placeholder="可选：立即添加骑手"
            options={[{ label: "暂不添加骑手", value: "" }, ...riderOptions]}
            onChange={(value) => setNewArea((prev) => ({ ...prev, riderId: value }))}
            style={selectStyle}
          />
        </label>
      </AdminDialog>

      <AdminDialog
        open={Boolean(activeArea)}
        title="区域详情"
        description={activeArea ? `${activeArea.areaCode} · ${mealPeriodLabel(mealPeriod)}` : undefined}
        onClose={() => {
          setActiveAreaCode(null);
          setIsReordering(false);
          setLocalOrders([]);
        }}
        footer={
          <>
            <button
              className="btn btn-primary"
              onClick={() => {
                if (!activeArea) return;
                setAssignRiderAreaCode(activeArea.areaCode);
                setSelectedRiderId(activeArea.defaultRiderId ? String(activeArea.defaultRiderId) : "");
              }}
            >
              <UserPlus size={14} /> 更换骑手
            </button>
            <button className="btn btn-outline" onClick={() => activeArea && startRename(activeArea.areaCode)}>
              <Pencil size={14} /> 改名
            </button>
            <button className="btn-delete" onClick={() => activeArea && setDeletingArea(activeArea.areaCode)}>
              <Trash2 size={14} /> 删除区域
            </button>
          </>
        }
      >
        {activeArea ? (
          <div className="dispatch-area-detail">
            <div className="dispatch-area-detail__header">
              <div>
                <div className="dispatch-card__title">{activeArea.areaCode}</div>
                <div className="dispatch-inline-note">当前骑手：{activeArea.currentRiderName || activeArea.defaultRiderName || "暂无骑手"}</div>
              </div>
              <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
                <span className={activeArea.currentRiderName ? "tag tag-green" : "tag tag-red"}>
                  {activeArea.currentRiderName || "缺骑手"}
                </span>
                {!isReordering ? (
                  <button className="btn btn-outline btn-compact" onClick={toggleReorderMode}>
                    <GripVertical size={14} /> 排序
                  </button>
                ) : (
                  <button className="btn btn-primary btn-compact" onClick={toggleReorderMode}>
                    <GripVertical size={14} /> 保存顺序
                  </button>
                )}
              </div>
            </div>
            {isReordering && (
              <div className="dispatch-inline-note">
                <span style={{ display: 'flex', alignItems: 'center', gap: '8px', color: '#f97316', fontWeight: 600 }}>
                  <GripVertical size={16} />
                  拖拽订单卡片调整顺序，完成后点击"保存顺序"
                </span>
              </div>
            )}
            {displayOrders.length === 0 ? (
              <div className="dispatch-empty">当前区域暂无订单。</div>
            ) : (
              <DragDropContext onDragEnd={handleDragEnd} key={activeArea.areaCode}>
                <Droppable droppableId={`droppable-${activeArea.areaCode}`}>
                  {(provided, snapshot) => (
                    <div 
                      className="dispatch-area-orders"
                      ref={provided.innerRef}
                      {...provided.droppableProps}
                      style={{
                        background: snapshot.isDraggingOver 
                          ? 'rgba(251, 146, 60, 0.05)' 
                          : 'transparent',
                        borderRadius: '12px',
                        transition: 'background 0.2s ease',
                        padding: snapshot.isDraggingOver ? '8px' : '0',
                      }}
                    >
                      {displayOrders.map((order, index) => (
                        <DraggableOrderItem
                          key={`order-${order.orderId}-${index}`}
                          order={order}
                          index={index}
                          isReordering={isReordering}
                          onDetailClick={() => setOrderDetailId(order.orderId)}
                          onMoveClick={() => setMoveState({ areaCode: activeArea.areaCode, orderId: order.orderId, targetAreaCode: "" })}
                        />
                      ))}
                      {provided.placeholder}
                    </div>
                  )}
                </Droppable>
              </DragDropContext>
            )}
          </div>
        ) : null}
      </AdminDialog>

      <AdminDialog
        open={Boolean(renamingArea)}
        title="修改区域名称"
        description={renamingArea ? `当前区域：${renamingArea}` : undefined}
        zOffset={10}
        onClose={cancelRename}
        footer={
          <>
            <button className="btn btn-outline" onClick={cancelRename}>取消</button>
            <button className="btn btn-primary" disabled={!renameValue.trim() || savingArea === renamingArea} onClick={handleRename}>
              保存名称
            </button>
          </>
        }
      >
        <label className="admin-field">
          <span className="admin-field-label">新区域名称</span>
          <input value={renameValue} onChange={(event) => setRenameValue(event.target.value)} style={inputStyle} autoFocus />
        </label>
      </AdminDialog>

      <AdminDialog
        open={Boolean(deletingArea)}
        title="删除区域"
        description={activeDeleteBlock ? `区域“${deletingArea}”当前仍有配送单，先处理后再删除。` : deletingArea ? `确定删除区域“${deletingArea}”吗？` : undefined}
        zOffset={10}
        onClose={closeDeleteDialog}
        footer={
          <>
            <button className="btn btn-outline" onClick={closeDeleteDialog}>{activeDeleteBlock ? "我知道了" : "取消"}</button>
            {activeDeleteBlock ? null : (
              <button
                className="btn-delete"
                disabled={!deletingArea || savingArea === deletingArea}
                onClick={() => deletingArea && handleDelete(deletingArea)}
              >
                <Trash2 size={16} /> 确认删除
              </button>
            )}
          </>
        }
      >
        {activeDeleteBlock ? (
          <div className="dispatch-area-dialog-block">
            <div className="dispatch-area-warning">
              <div className="dispatch-area-warning__title">暂不能删除</div>
              <div className="dispatch-area-warning__body">{activeDeleteBlock.message}</div>
            </div>
            <div className="dispatch-area-detail">
              <div className="dispatch-area-detail__header">
                <div>
                  <div className="dispatch-card__title" style={{ fontSize: "13px" }}>阻塞删除的订单</div>
                  <div className="dispatch-inline-note">先把这些订单改派或处理完成，再回来删除区域。</div>
                </div>
                <span className="tag tag-red">{activeDeleteBlock.activeOrderCount} 单</span>
              </div>
              <div className="dispatch-area-orders">
                {activeDeleteBlock.orders.map((order) => (
                  <div key={order.orderId} className="dispatch-area-orders__item">
                    <div className="dispatch-area-orders__top">
                      <strong>订单 #{order.orderId}</strong>
                      <span className={`tag ${order.deliveryStatus === "PENDING_DISPATCH" ? "tag-amber" : "tag-gray"}`}>
                        {order.deliveryStatus}
                      </span>
                    </div>
                    <div>{order.customerName}</div>
                    <div className="dispatch-order-item__meta">
                      <span style={{ color: "var(--primary-color)", fontWeight: 500 }}>送餐日期: {order.serveDate}</span>
                    </div>
                    <div className="dispatch-order-item__meta">{order.deliveryAddress}</div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        ) : (
          <div className="dispatch-inline-note">删除前请确认该区域没有待配送的订单。</div>
        )}
      </AdminDialog>

      <AdminDialog
        open={Boolean(assignRiderAreaCode)}
        title="更换骑手"
        description={assignRiderAreaCode ? `将 ${assignRiderAreaCode} 当前${mealPeriodLabel(mealPeriod)}订单统一分配给骑手` : undefined}
        zOffset={10}
        onClose={() => {
          setAssignRiderAreaCode(null);
          setSelectedRiderId("");
        }}
        footer={
          <>
            <button className="btn btn-outline" onClick={() => {
              setAssignRiderAreaCode(null);
              setSelectedRiderId("");
            }}>取消</button>
            <button
              className="btn btn-primary"
              disabled={!selectedRiderId || !assignRiderAreaCode || savingArea === assignRiderAreaCode}
              onClick={handleAssignRider}
            >
              确认更换
            </button>
          </>
        }
      >
        <label className="admin-field">
          <span className="admin-field-label">选择骑手</span>
          <AppSelect
            value={selectedRiderId}
            placeholder="搜索骑手姓名"
            options={riderOptions}
            showSearch
            onChange={(value) => setSelectedRiderId(value)}
            style={selectStyle}
          />
        </label>
      </AdminDialog>

      <AdminDialog
        open={Boolean(moveState)}
        title="移出订单"
        description={moveState ? `将订单 #${moveState.orderId} 移到其他区域` : undefined}
        zOffset={10}
        onClose={() => setMoveState(null)}
        footer={
          <>
            <button className="btn btn-outline" onClick={() => setMoveState(null)}>取消</button>
            <button
              className="btn btn-primary"
              disabled={!moveState?.targetAreaCode || savingArea === moveState?.areaCode}
              onClick={handleMoveOrder}
            >
              确认移出
            </button>
          </>
        }
      >
        <label className="admin-field">
          <span className="admin-field-label">目标区域</span>
          <AppSelect
            value={moveState?.targetAreaCode || ""}
            placeholder="选择目标区域"
            options={bindings
              .filter((item) => item.areaCode !== moveState?.areaCode)
              .map((item) => ({ label: item.areaCode, value: item.areaCode }))}
            onChange={(value) => setMoveState((prev) => prev ? { ...prev, targetAreaCode: value } : prev)}
            style={selectStyle}
          />
        </label>
      </AdminDialog>

      <AdminDialog
        open={Boolean(orderDetail)}
        title="订单详情"
        description={orderDetail ? `订单 #${orderDetail.orderId} · ${orderDetail.customerName}` : undefined}
        zOffset={10}
        onClose={() => setOrderDetailId(null)}
        footer={
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", width: "100%" }}>
            <button
              className="btn-delete btn-compact"
              onClick={() => {
                if (orderDetail) {
                  setOrderDetailId(null);
                  handleDeleteOrder(orderDetail.orderId, orderDetail.customerName);
                }
              }}
            >
              <Trash2 size={14} /> 删除订单
            </button>
            <button className="btn btn-outline" onClick={() => setOrderDetailId(null)}>关闭</button>
          </div>
        }
      >
        {orderDetail ? (
          <div style={{ display: "grid", gap: "16px" }}>
            <div>
              <div style={{ fontSize: "12px", color: "#999", marginBottom: "4px" }}>配送地址</div>
              <div style={{ fontSize: "14px", fontWeight: 500 }}>{orderDetail.deliveryAddress}</div>
            </div>
            
            <div>
              <div style={{ fontSize: "12px", color: "#999", marginBottom: "4px" }}>当前骑手</div>
              <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
                <div style={{ fontSize: "14px", fontWeight: 500 }}>
                  {activeArea?.currentRiderName || activeArea?.defaultRiderName || "暂无骑手"}
                </div>
                <button
                  className="btn btn-outline btn-compact"
                  onClick={() => setOrderRiderChangeState({ 
                    orderId: orderDetail.orderId, 
                    riderId: activeArea?.defaultRiderId ? String(activeArea.defaultRiderId) : "" 
                  })}
                >
                  <UserPlus size={14} /> 切换骑手
                </button>
              </div>
            </div>
            
            {orderDetail.userNote && (
              <div>
                <div style={{ fontSize: "12px", color: "#999", marginBottom: "4px" }}>用户备注</div>
                <div style={{ fontSize: "14px", padding: "8px 12px", backgroundColor: "#f5f5f5", borderRadius: "4px" }}>
                  {orderDetail.userNote}
                </div>
              </div>
            )}
            
            {orderDetail.adminNote && (
              <div>
                <div style={{ fontSize: "12px", color: "#999", marginBottom: "4px" }}>商家备注</div>
                <div style={{ fontSize: "14px", padding: "8px 12px", backgroundColor: "#fff7ed", borderRadius: "4px" }}>
                  {orderDetail.adminNote}
                </div>
              </div>
            )}
            
            {orderDetail.receiptNote && (
              <div>
                <div style={{ fontSize: "12px", color: "#999", marginBottom: "4px" }}>骑手备注</div>
                <div style={{ fontSize: "14px", padding: "8px 12px", backgroundColor: "#eff6ff", borderRadius: "4px" }}>
                  {orderDetail.receiptNote}
                </div>
              </div>
            )}
            
            {orderDetail.receiptUrl && (
              <div>
                <div style={{ fontSize: "12px", color: "#999", marginBottom: "8px" }}>回执照片</div>
                <img 
                  src={orderDetail.receiptUrl}
                  alt="回执照片" 
                  style={{ 
                    maxWidth: "100%", 
                    maxHeight: "400px", 
                    borderRadius: "6px", 
                    cursor: "pointer", 
                    border: "1px solid #e5e7eb",
                    display: "block"
                  }}
                  onClick={() => window.open(orderDetail.receiptUrl, '_blank')}
                  onError={(e) => {
                    const target = e.target as HTMLImageElement;
                    target.style.display = 'none';
                    const errorDiv = document.createElement('div');
                    errorDiv.style.cssText = 'padding: 16px; backgroundColor: #fef3c7; border: 2px solid #f59e0b; borderRadius: 8px; fontSize: 14px; color: #92400e;';
                    errorDiv.innerHTML = `
                      <div style="display: flex; align-items: center; gap: 8px; margin-bottom: 8px;">
                        <span style="font-size: 20px;">⚠️</span>
                        <div style="font-weight: 600;">图片加载失败</div>
                      </div>
                      <div style="font-size: 12px; margin-bottom: 8px;">请检查图片链接是否有效</div>
                      <div style="padding: 8px 12px; background-color: #fff; border-radius: 4px; font-size: 11px; font-family: monospace; word-break: break-all; color: #475569;">
                        ${orderDetail.receiptUrl}
                      </div>
                    `;
                    target.parentElement?.appendChild(errorDiv);
                  }}
                />
              </div>
            )}
            
            {orderDetail.deliveredAt && (
              <div>
                <div style={{ fontSize: "12px", color: "#999", marginBottom: "4px" }}>送达时间</div>
                <div style={{ fontSize: "14px" }}>{orderDetail.deliveredAt}</div>
              </div>
            )}
          </div>
        ) : null}
      </AdminDialog>

      <AdminDialog
        open={Boolean(orderRiderChangeState)}
        title="切换订单骑手"
        description={orderRiderChangeState ? `为订单 #${orderRiderChangeState.orderId} 指定新的配送骑手` : undefined}
        zOffset={20}
        onClose={() => setOrderRiderChangeState(null)}
        footer={
          <>
            <button className="btn btn-outline" onClick={() => setOrderRiderChangeState(null)}>取消</button>
            <button
              className="btn btn-primary"
              disabled={!orderRiderChangeState?.riderId || Boolean(activeArea && savingArea === activeArea.areaCode)}
              onClick={handleChangeOrderRider}
            >
              确认切换
            </button>
          </>
        }
      >
        <label className="admin-field">
          <span className="admin-field-label">选择骑手</span>
          <AppSelect
            value={orderRiderChangeState?.riderId || ""}
            placeholder="搜索骑手姓名"
            options={riderOptions}
            showSearch
            onChange={(value) => setOrderRiderChangeState((prev) => prev ? { ...prev, riderId: value } : prev)}
            style={selectStyle}
          />
        </label>
      </AdminDialog>

      {/* 删除订单确认对话框 */}
      <AdminDialog
        open={Boolean(deleteConfirmState)}
        title="⚠️ 删除订单"
        width={500}
        zOffset={20}
        onClose={() => setDeleteConfirmState(null)}
        footer={
          <div style={{ display: "flex", gap: "12px", justifyContent: "flex-end" }}>
            <button className="btn btn-outline" onClick={() => setDeleteConfirmState(null)}>取消</button>
            <button 
              className="btn-delete"
              onClick={() => confirmDeleteOrder().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}
            >
              <Trash2 size={16} />
              确认删除
            </button>
          </div>
        }
      >
        {deleteConfirmState && (
          <div style={{ display: "grid", gap: "16px" }}>
            <div className="delete-confirm-details">
              <div className="delete-confirm-details__item">
                <span className="delete-confirm-details__label">客户：</span>
                <span className="delete-confirm-details__value">{deleteConfirmState.customerName}</span>
              </div>
              <div className="delete-confirm-details__item">
                <span className="delete-confirm-details__label">订单ID：</span>
                <span className="delete-confirm-details__value">{deleteConfirmState.orderId}</span>
              </div>
            </div>
          </div>
        )}
      </AdminDialog>
    </div>
  );
}
