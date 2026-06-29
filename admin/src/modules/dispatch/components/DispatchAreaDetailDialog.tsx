import React from "react";
import { DragDropContext, Draggable, Droppable, type DropResult } from "@hello-pangea/dnd";
import { GripVertical, Pencil, Trash2, UserPlus } from "lucide-react";
import type { DispatchAreaBindingResponse, DispatchAreaOrderItemResponse } from "../../../shared/api/types";
import { AdminDialog } from "../../../shared/components/AdminDialog";
import { hasOrderAttention, mealPeriodLabel } from "../dispatchCenterLayout.helpers";

function DraggableOrderItem({
  order,
  index,
  isReordering,
  onDetailClick,
  onMoveClick
}: {
  order: DispatchAreaOrderItemResponse;
  index: number;
  isReordering: boolean;
  onDetailClick: () => void;
  onMoveClick: () => void;
}) {
  const statusClass = order.deliveryStatus === "DELIVERED" ? "delivered" : "dispatching";
  const isMultiple = order.quantity && order.quantity > 1;
  const itemId = `order-${order.orderId}`;
  const hasAttention = hasOrderAttention(order);

  return (
    <Draggable draggableId={itemId} index={index} isDragDisabled={!isReordering}>
      {(provided, snapshot) => (
        <div
          ref={provided.innerRef}
          {...provided.draggableProps}
          {...provided.dragHandleProps}
          className={`dispatch-area-orders__item dispatch-order-tile ${statusClass} ${snapshot.isDragging ? "dragging" : ""} ${!isReordering ? "no-drag" : ""} ${isMultiple ? "multiple-order" : ""} ${isReordering ? "reordering" : ""}`}
        >
          <div className="dispatch-order-tile__top">
            <div style={{ display: "inline-flex", alignItems: "center", gap: "8px" }}>
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  padding: "4px 6px",
                  borderRadius: "6px",
                  background: isReordering ? "rgba(251, 146, 60, 0.15)" : "transparent"
                }}
              >
                <GripVertical
                  size={16}
                  style={{
                    opacity: isReordering ? 1 : 0.3,
                    color: isReordering ? "#f97316" : "inherit"
                  }}
                />
              </div>
              <strong>#{index + 1}</strong>
              <span>{order.customerName}</span>
              {isMultiple ? <span className="quantity-badge">×{order.quantity}</span> : null}
            </div>
            <span className={`tag ${order.deliveryStatus === "DELIVERED" ? "tag-green" : "tag-amber"}`} style={{ flexShrink: 0 }}>
              {order.deliveryStatus === "DELIVERED" ? "已送达" : "待配送"}
            </span>
          </div>
          <div
            className="dispatch-inline-note"
            style={{
              overflow: "hidden",
              textOverflow: "ellipsis",
              whiteSpace: "nowrap",
              maxWidth: "100%"
            }}
          >
            {order.deliveryAddress || "-"}
          </div>

          <div className="dispatch-chip-list">
            {hasAttention ? <span className="tag tag-amber" style={{ fontSize: "11px" }}>需留意</span> : null}
            {order.userNote ? <span className="tag tag-blue" style={{ fontSize: "11px" }}>用户备注</span> : null}
            {order.merchantRemark ? <span className="tag tag-orange" style={{ fontSize: "11px" }}>商家备注</span> : null}
            {order.receiptNote ? <span className="tag tag-blue" style={{ fontSize: "11px" }}>骑手备注</span> : null}
          </div>

          <div className="dispatch-order-tile__actions">
            <button className="btn btn-outline btn-compact" onClick={onDetailClick}>查看详情</button>
            <button className="btn btn-outline btn-compact" onClick={onMoveClick}>移区</button>
          </div>
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
  onClose: () => void;
  onOpenAssignRider: () => void;
  onStartRename: () => void;
  onRequestDeleteArea: () => void;
  onToggleReorder: () => void;
  onDragEnd: (result: DropResult) => void;
  onSelectOrderDetail: (orderId: number) => void;
  onMoveOrder: (orderId: number) => void;
}

export function DispatchAreaDetailDialog({
  activeArea,
  mealPeriod,
  isReordering,
  displayOrders,
  onClose,
  onOpenAssignRider,
  onStartRename,
  onRequestDeleteArea,
  onToggleReorder,
  onDragEnd,
  onSelectOrderDetail,
  onMoveOrder
}: DispatchAreaDetailDialogProps) {
  return (
    <AdminDialog
      open={Boolean(activeArea)}
      title="区域详情"
      description={activeArea ? `${activeArea.areaCode} · ${mealPeriodLabel(mealPeriod)}` : undefined}
      onClose={onClose}
      footer={
        <>
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
                  <GripVertical size={14} /> 排序
                </button>
              ) : (
                <button className="btn btn-primary btn-compact" onClick={onToggleReorder}>
                  <GripVertical size={14} /> 保存顺序
                </button>
              )}
            </div>
          </div>
          {isReordering ? (
            <div className="dispatch-inline-note">
              <span style={{ display: "flex", alignItems: "center", gap: "8px", color: "#f97316", fontWeight: 600 }}>
                <GripVertical size={16} />
                拖拽订单卡片调整顺序，完成后点击"保存顺序"
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
                        isReordering={isReordering}
                        onDetailClick={() => onSelectOrderDetail(order.orderId)}
                        onMoveClick={() => onMoveOrder(order.orderId)}
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
  );
}
