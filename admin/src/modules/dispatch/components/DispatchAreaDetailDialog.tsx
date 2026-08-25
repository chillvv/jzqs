
import React, { useState } from "react";
import { DragDropContext, Draggable, Droppable, type DropResult } from "@hello-pangea/dnd";
import { Bot, Check, GripVertical, Pencil, Trash2, UserPlus } from "lucide-react";
import type { DispatchAreaBindingResponse, DispatchAreaOrderItemResponse } from "../../../shared/api/types";
import { AdminDialog } from "../../../shared/components/AdminDialog";
import { AppSelect } from "../../../shared/components/AppSelect";
import { hasDisplayValue, mealPeriodLabel } from "../dispatchCenterLayout.helpers";

// 触屏设备检测：手机/平板上拖拽（@hello-pangea/dnd）依赖长按激活，与滚动手势冲突极易失败，
// 因此触屏设备改用「上移/下移」按钮排序，桌面端保留拖拽。
const isTouchDevice = typeof window !== "undefined" && ("ontouchstart" in window || navigator.maxTouchPoints > 0);

function DraggableOrderItem({
  order,
  index,
  total,
  isReordering,
  selected,
  isTouchDevice: touch,
  onDetailClick,
  onToggleSelect,
  onMove
}: {
  order: DispatchAreaOrderItemResponse;
  index: number;
  total: number;
  isReordering: boolean;
  selected: boolean;
  isTouchDevice: boolean;
  onDetailClick: () => void;
  onToggleSelect: () => void;
  onMove: (index: number, direction: -1 | 1) => void;
}) {
  const statusClass = order.deliveryStatus === "DELIVERED" ? "delivered" : "dispatching";
  const isMultiple = order.quantity && order.quantity > 1;
  const itemId = `order-${order.orderId}`;
  const userNote = hasDisplayValue(order.userNote) ? order.userNote.trim() : "";
  const merchantNote = hasDisplayValue(order.merchantRemark) ? order.merchantRemark.trim() : "";
  const receiptNote = hasDisplayValue(order.receiptNote) ? order.receiptNote.trim() : "";

  return (
    <Draggable draggableId={itemId} index={index} isDragDisabled={!isReordering || touch}>
      {(provided, snapshot) => (
        <div
          ref={provided.innerRef}
          {...provided.draggableProps}
          className={`dispatch-area-orders__item dispatch-order-tile ${statusClass} ${snapshot.isDragging ? "dragging" : ""} ${!isReordering ? "no-drag" : "reordering"} ${isMultiple ? "multiple-order" : ""} ${selected ? "selected" : ""}`}
          onClick={() => (isReordering ? onToggleSelect() : onDetailClick())}
          title={isReordering ? (touch ? "点击勾选，点击 ↑↓ 调整顺序" : "点击勾选，拖拽左侧手柄调整顺序") : "点击查看订单详情"}
        >
          <div className="dispatch-order-tile__top">
            <div className="dispatch-order-tile__left">
              {isReordering && touch ? (
                <div className="dispatch-order-tile__move-btns">
                  <button
                    type="button"
                    aria-label="上移"
                    disabled={index === 0}
                    onClick={(event) => {
                      event.stopPropagation();
                      onMove(index, -1);
                    }}
                  >↑</button>
                  <button
                    type="button"
                    aria-label="下移"
                    disabled={index === total - 1}
                    onClick={(event) => {
                      event.stopPropagation();
                      onMove(index, 1);
                    }}
                  >↓</button>
                </div>
              ) : (
                <div
                  className={`dispatch-order-tile__handle ${isReordering ? "active" : ""}`}
                  {...(isReordering ? provided.dragHandleProps : {})}
                  onClick={(event) => event.stopPropagation()}
                >
                  <GripVertical size={16} />
                </div>
              )}
              {isReordering ? (
                <span className={`dispatch-order-tile__checkbox ${selected ? "checked" : ""}`}>
                  {selected ? <Check size={12} strokeWidth={3} /> : null}
                </span>
              ) : null}
              <span className="dispatch-order-tile__seq">#{index + 1}</span>
              <span className="dispatch-order-tile__name">{order.customerName}</span>
              {isMultiple ? <span className="quantity-badge">×{order.quantity}</span> : null}
            </div>
            <span className={`tag ${order.deliveryStatus === "DELIVERED" ? "tag-green" : "tag-amber"}`} style={{ flexShrink: 0 }}>
              {order.deliveryStatus === "DELIVERED" ? "已送达" : "待配送"}
            </span>
          </div>

          <div className="dispatch-order-tile__address">
            {order.deliveryAddress || "-"}
          </div>

          {userNote ? (
            <div className="dispatch-order-tile__note note-user">
              <span className="dispatch-order-tile__note-label">用户备注</span>
              <span className="dispatch-order-tile__note-text">{userNote}</span>
            </div>
          ) : null}
          {merchantNote ? (
            <div className="dispatch-order-tile__note note-merchant">
              <span className="dispatch-order-tile__note-label">商家备注</span>
              <span className="dispatch-order-tile__note-text">{merchantNote}</span>
            </div>
          ) : null}
          {receiptNote ? (
            <div className="dispatch-order-tile__note note-rider">
              <span className="dispatch-order-tile__note-label">骑手备注</span>
              <span className="dispatch-order-tile__note-text">{receiptNote}</span>
            </div>
          ) : null}
        </div>
      )}
    </Draggable>
  );
}

interface DispatchAreaDetailDialogProps {
  activeArea: DispatchAreaBindingResponse | null;
  mealPeriod: Parameters<typeof mealPeriodLabel>[0];
  isReordering: boolean;
  displayOrders: DispatchAreaOrderItemResponse[];
  selectedOrderIds: number[];
  batchMoving: boolean;
  targetAreaOptions: { value: string; label: string }[];
  onClose: () => void;
  onOpenAssignRider: () => void;
  onOpenAiCorrection: () => void;
  onStartRename: () => void;
  onRequestDeleteArea: () => void;
  onToggleReorder: () => void;
  onCancelReorder: () => void;
  onDragEnd: (result: DropResult) => void;
  onSelectOrderDetail: (orderId: number) => void;
  onToggleSelect: (orderId: number) => void;
  onBatchMove: (targetAreaCode: string) => void;
  onMoveOrder: (index: number, direction: -1 | 1) => void;
}

export function DispatchAreaDetailDialog({
  activeArea,
  mealPeriod,
  isReordering,
  displayOrders,
  selectedOrderIds,
  batchMoving,
  targetAreaOptions,
  onClose,
  onOpenAssignRider,
  onOpenAiCorrection,
  onStartRename,
  onRequestDeleteArea,
  onToggleReorder,
  onCancelReorder,
  onDragEnd,
  onSelectOrderDetail,
  onToggleSelect,
  onBatchMove,
  onMoveOrder
}: DispatchAreaDetailDialogProps) {
  const [batchTargetArea, setBatchTargetArea] = useState("");

  const selectedSet = new Set(selectedOrderIds);

  return (
    <AdminDialog
      open={Boolean(activeArea)}
      title="区域详情"
      description={activeArea ? `${activeArea.areaCode} · ${mealPeriodLabel(mealPeriod)}` : undefined}
      onClose={onClose}
      footer={
        <>
          <button className="btn btn-outline" onClick={onOpenAiCorrection}>
            <Bot size={14} /> AI纠正
          </button>
          <button className="btn btn-primary" onClick={onOpenAssignRider}>
            <UserPlus size={14} /> 更换骑手
          </button>
          <button className="btn btn-outline" onClick={onStartRename}>
            <Pencil size={14} /> 改名
          </button>
          <button className="btn-delete" onClick={onRequestDeleteArea}>
            <Trash2 size={14} /> 删除区域
          </button>
        </>
      }
    >
      {activeArea ? (
        <div className="dispatch-area-detail">
          <div className="dispatch-dialog-header">
            <div>
              <div className="dispatch-card__title">{activeArea.areaCode}</div>
              <div className="dispatch-inline-note">当前骑手：{activeArea.currentRiderName || activeArea.defaultRiderName || "暂无骑手"}</div>
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: "8px" }}>
              <span className={activeArea.currentRiderName ? "tag tag-green" : "tag tag-red"}>
                {activeArea.currentRiderName || "缺骑手"}
              </span>
              {!isReordering ? (
                <button className="btn btn-outline btn-compact" onClick={onToggleReorder}>
                  <GripVertical size={14} /> 编辑
                </button>
              ) : (
                <button className="btn btn-primary btn-compact" onClick={onToggleReorder}>
                  <Check size={14} /> 完成
                </button>
              )}
            </div>
          </div>

          {isReordering ? (
            <div className="dispatch-inline-note" style={{ marginBottom: "12px" }}>
              <span style={{ display: "flex", alignItems: "center", gap: "8px", color: "#f97316", fontWeight: 600 }}>
                <GripVertical size={16} />
                {isTouchDevice
                  ? '点击卡片勾选（可多选），点击卡片左侧 ↑↓ 调整顺序，完成后点击"完成"'
                  : '点击卡片勾选（可多选），拖拽左侧手柄调整顺序，完成后点击"完成"'}
              </span>
            </div>
          ) : null}

          {displayOrders.length === 0 ? (
            <div className="dispatch-empty">当前区域暂无订单。</div>
          ) : (
            <DragDropContext onDragEnd={onDragEnd} key={activeArea.areaCode}>
              <Droppable droppableId={`droppable-${activeArea.areaCode}`}>
                {(provided, snapshot) => (
                  <div
                    className="dispatch-area-orders"
                    ref={provided.innerRef}
                    {...provided.droppableProps}
                    style={{
                      background: snapshot.isDraggingOver ? "rgba(251, 146, 60, 0.05)" : "transparent",
                      borderRadius: "12px",
                      transition: "background 0.2s ease",
                      padding: snapshot.isDraggingOver ? "8px" : "0"
                    }}
                  >
                    {displayOrders.map((order, index) => (
                      <DraggableOrderItem
                        key={`order-${order.orderId}-${index}`}
                        order={order}
                        index={index}
                        total={displayOrders.length}
                        isReordering={isReordering}
                        selected={selectedSet.has(order.orderId)}
                        isTouchDevice={isTouchDevice}
                        onDetailClick={() => onSelectOrderDetail(order.orderId)}
                        onToggleSelect={() => onToggleSelect(order.orderId)}
                        onMove={onMoveOrder}
                      />
                    ))}
                    {provided.placeholder}
                  </div>
                )}
              </Droppable>
            </DragDropContext>
          )}

          {isReordering ? (
            <div className="dispatch-batch-bar">
              <span className="dispatch-batch-bar__info">已选 {selectedOrderIds.length} 单</span>
              <AppSelect
                value={batchTargetArea}
                placeholder="选择移入区域"
                options={targetAreaOptions}
                onChange={setBatchTargetArea}
                style={{ minWidth: 180, flex: "1 1 220px" }}
              />
              <button
                className="btn btn-outline btn-compact"
                disabled={!batchTargetArea || selectedOrderIds.length === 0 || batchMoving}
                onClick={() => {
                  if (batchTargetArea && selectedOrderIds.length > 0) {
                    onBatchMove(batchTargetArea);
                  }
                }}
              >
                移出到区域
              </button>
              <button
                className="btn btn-outline btn-compact"
                disabled={batchMoving}
                onClick={onCancelReorder}
              >
                取消
              </button>
            </div>
          ) : null}
        </div>
      ) : null}
    </AdminDialog>
  );
}
