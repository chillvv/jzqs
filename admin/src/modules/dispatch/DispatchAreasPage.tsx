import React, { useEffect, useMemo, useState } from "react";
import { Pencil, PlusCircle, Trash2, MapPin } from "lucide-react";
import type { DropResult } from "@hello-pangea/dnd";
import {
  DispatchAreaDeleteBlockedError,
  deleteDispatchArea,
  moveOrderToArea,
  renameDispatchArea,
  assignRiderToArea,
  reorderAreaOrders,
  updateDispatchAreaBinding
} from "../../shared/api/http";
import type {
  DispatchAreaBlockingOrder,
  DispatchAreaBindingResponse,
  DispatchManagedRiderResponse,
  DispatchAreaOrderItemResponse
} from "../../shared/api/types";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "../../shared/components/ui/table";
import { AppSelect } from "../../shared/components/AppSelect";
import { AdminDialog } from "../../shared/components/AdminDialog";
import { SafeInput } from "../../shared/components/SafeInput";
import { toast } from "../../shared/components/Toast";
import {
  buildDispatchAreaStats,
  DEFAULT_OPERATOR,
  hasDisplayValue,
  reorderDispatchAreaOrders,
  validateAreaName,
  mealPeriodLabel
} from "./dispatchCenterLayout.helpers";
import { useDispatchContext } from "./DispatchContext";
import { DispatchAreaAiCorrectionDialog } from "./components/DispatchAreaAiCorrectionDialog";
import { DispatchAreaDetailDialog } from "./components/DispatchAreaDetailDialog";
import { useDispatchAreasLiveData } from "./dispatchLiveData";

const selectStyle: React.CSSProperties = { width: "100%" };

type DeleteBlockedState = {
  areaCode: string;
  message: string;
  activeOrderCount: number;
  orders: DispatchAreaBlockingOrder[];
};

function getErrorMessage(error: unknown, fallback = "操作失败") {
  if (typeof error === "object" && error !== null) {
    const errorLike = error as { response?: { data?: { message?: string } }; message?: string };
    return errorLike.response?.data?.message || errorLike.message || fallback;
  }
  return typeof error === "string" ? error : fallback;
}

export function DispatchAreasPage() {
  const { serveDate, mealPeriod } = useDispatchContext();
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [savingArea, setSavingArea] = useState<string | null>(null);
  const [newArea, setNewArea] = useState({ name: "", riderId: "" });
  const [showCreateAreaErrors, setShowCreateAreaErrors] = useState(false);
  const [renamingArea, setRenamingArea] = useState<string | null>(null);
  const [renameValue, setRenameValue] = useState("");
  const [deletingArea, setDeletingArea] = useState<string | null>(null);
  const [deleteBlockedState, setDeleteBlockedState] = useState<DeleteBlockedState | null>(null);
  const [activeAreaCode, setActiveAreaCode] = useState<string | null>(null);
  const [assignRiderAreaCode, setAssignRiderAreaCode] = useState<string | null>(null);
  const [selectedRiderId, setSelectedRiderId] = useState("");
  const [riderSearch, setRiderSearch] = useState("");
  const [isReordering, setIsReordering] = useState(false);
  const [localOrders, setLocalOrders] = useState<DispatchAreaOrderItemResponse[]>([]);
  const [selectedOrderIds, setSelectedOrderIds] = useState<number[]>([]);
  const [batchMoving, setBatchMoving] = useState(false);
  const [orderDetailId, setOrderDetailId] = useState<number | null>(null);
  const [orderMoveTargetArea, setOrderMoveTargetArea] = useState("");
  const [movingOrderToArea, setMovingOrderToArea] = useState(false);
  const [showAiCorrectionDialog, setShowAiCorrectionDialog] = useState(false);
  const { bindings, riders, loadError, reload } = useDispatchAreasLiveData(
    { serveDate, mealPeriod },
    { autoRefreshBlocked: isReordering || savingArea !== null }
  );

  useEffect(() => {
    if (!loadError) {
      return;
    }
    toast(getErrorMessage(loadError, "加载区域与骑手失败"), "error");
  }, [loadError]);

  // 已绑定为某区域负责骑手的骑手及其所属区域（含当前区域），用于展示"已被XX区域使用"
  const boundRiderMap = useMemo(() => {
    const map = new Map<string, string>();
    bindings.forEach((b) => {
      if (b.defaultRiderId) {
        map.set(String(b.defaultRiderId), b.areaCode);
      }
    });
    return map;
  }, [bindings]);

  // 更换骑手弹窗里展示的骑手列表：启用/停用均展示，让商家清楚看到状态
  const riderReplaceList = useMemo(() => {
    const currentArea = assignRiderAreaCode
      ? bindings.find((b) => b.areaCode === assignRiderAreaCode)
      : null;
    const currentRiderId = currentArea?.defaultRiderId ? String(currentArea.defaultRiderId) : null;

    return riders
      .map((rider) => {
        const riderId = String(rider.riderId);
        const disabled = rider.authStatus !== "ACTIVE";
        const occupiedBy = boundRiderMap.get(riderId);
        // 已停用 或 已被其他区域占用（当前区域自己的骑手除外）→ 不可选
        const blocked = disabled || (Boolean(occupiedBy) && occupiedBy !== assignRiderAreaCode);
        let badge: { text: string; kind: "danger" | "warn" } | null = null;
        if (disabled) {
          badge = { text: "已停用", kind: "danger" };
        } else if (occupiedBy && occupiedBy !== assignRiderAreaCode) {
          badge = { text: `已被 ${occupiedBy} 使用`, kind: "warn" };
        }
        return {
          riderId,
          riderName: rider.riderName,
          phone: rider.phone || "--",
          disabled,
          occupiedBy: occupiedBy && occupiedBy !== assignRiderAreaCode ? occupiedBy : null,
          blocked,
          badge,
          isCurrent: riderId === currentRiderId
        };
      });
  }, [riders, boundRiderMap, assignRiderAreaCode, bindings]);

  const filteredRiderReplaceList = useMemo(() => {
    const kw = riderSearch.trim().toLowerCase();
    if (!kw) return riderReplaceList;
    return riderReplaceList.filter(
      (r) => r.riderName.toLowerCase().includes(kw) || r.phone.toLowerCase().includes(kw)
    );
  }, [riderReplaceList, riderSearch]);

  // 创建区域时可绑定的骑手：启用且未被任何区域占用
  const creatableRiderOptions = useMemo(
    () =>
      riders
        .filter((rider) => rider.authStatus === "ACTIVE" && !boundRiderMap.has(String(rider.riderId)))
        .map((rider) => ({ label: `${rider.riderName} (${rider.phone || "--"})`, value: String(rider.riderId) })),
    [riders, boundRiderMap]
  );

  const activeArea = useMemo(
    () => bindings.find((item) => item.areaCode === activeAreaCode) ?? null,
    [activeAreaCode, bindings]
  );

  const areaStats = useMemo(() => buildDispatchAreaStats(bindings), [bindings]);
  const createAreaNameError = showCreateAreaErrors ? validateAreaName(newArea.name) : "";

  const activeAreaOrders = activeArea?.orders ?? [];

  // 目标区域选项：当前区域以外的所有区域（订单详情"更换区域"用）
  const orderTargetAreaOptions = useMemo(
    () =>
      bindings
        .filter((item) => item.areaCode !== activeArea?.areaCode)
        .map((item) => ({ value: item.areaCode, label: item.areaCode })),
    [bindings, activeArea?.areaCode]
  );

  // 使用本地状态或原始数据
  const displayOrders = isReordering && localOrders.length > 0 ? localOrders : activeAreaOrders;
  
  // 当区域切换时重置本地状态（只在区域切换时触发，不在拖拽时触发）
  useEffect(() => {
    if (activeAreaCode) {
      setLocalOrders([]);
      setIsReordering(false);
      setSelectedOrderIds([]);
    }
  }, [activeAreaCode]); // 只依赖 activeAreaCode
  
  const orderDetail = useMemo(
    () => displayOrders.find((item) => item.orderId === orderDetailId) ?? null,
    [orderDetailId, displayOrders]
  );

  async function handleAssignRider() {
    if (!assignRiderAreaCode || !selectedRiderId) return;
    const area = bindings.find((item) => item.areaCode === assignRiderAreaCode);
    const rider = riders.find((item) => String(item.riderId) === selectedRiderId);
    if (!area || !rider) return;
    const occupiedBy = boundRiderMap.get(selectedRiderId);
    if (rider.authStatus !== "ACTIVE" || (occupiedBy && occupiedBy !== assignRiderAreaCode)) {
      toast("该骑手已停用或已被其他区域使用，无法绑定", "error");
      return;
    }
    setSavingArea(assignRiderAreaCode);
    try {
      await updateDispatchAreaBinding(assignRiderAreaCode, {
        keywords: area.keywords,
        defaultRiderId: rider.riderId
      });
      const result = await assignRiderToArea(assignRiderAreaCode, rider.riderName, mealPeriod);
      setAssignRiderAreaCode(null);
      setSelectedRiderId("");
      await reload();
      if (result.assignedCount === 0) {
        toast(`已将 ${rider.riderName} 绑定为「${assignRiderAreaCode}」的负责骑手，该区域暂无待配送订单，新订单归入后会自动指派。`);
      } else {
        toast(`已将 ${rider.riderName} 绑定到 ${assignRiderAreaCode}`);
      }
    } catch (err: any) {
      toast(getErrorMessage(err, "更换区域骑手失败"), "error");
    } finally {
      setSavingArea(null);
    }
  }

  async function handleCreateArea() {
    const nextError = validateAreaName(newArea.name);
    setShowCreateAreaErrors(true);
    if (nextError) {
      toast(nextError, "error");
      return;
    }
    setSavingArea("__new__");
    try {
      await updateDispatchAreaBinding(newArea.name.trim(), {
        keywords: newArea.name.trim(),
        defaultRiderId: newArea.riderId ? Number(newArea.riderId) : null
      });
      setNewArea({ name: "", riderId: "" });
      setShowCreateAreaErrors(false);
      setShowCreateModal(false);
      await reload();
      toast("区域已创建");
    } catch (err: any) {
      toast(getErrorMessage(err, "创建区域失败"), "error");
    } finally {
      setSavingArea(null);
    }
  }

  function toggleReorderMode() {
    if (isReordering) {
      saveOrderSequence();
    } else {
      setIsReordering(true);
      setLocalOrders([...activeAreaOrders]);
      setSelectedOrderIds([]);
    }
  }

  function cancelReorder() {
    setIsReordering(false);
    setLocalOrders([]);
    setSelectedOrderIds([]);
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
      setSelectedOrderIds([]);
      toast("区域内订单顺序已更新");
    } catch (err: any) {
      toast(getErrorMessage(err, "保存排序失败"), "error");
      // 失败回滚本地乐观更新：退出编辑模式并重新拉取服务器真实顺序，避免界面顺序与数据库不一致
      await reload();
      setIsReordering(false);
      setLocalOrders([]);
      setSelectedOrderIds([]);
    } finally {
      setSavingArea(null);
    }
  }

  async function handleBatchMoveOrders(targetAreaCode: string) {
    if (!activeArea || selectedOrderIds.length === 0 || !targetAreaCode || batchMoving) return;
    const orderIds = [...selectedOrderIds];
    const sourceAreaCode = activeArea.areaCode;
    setBatchMoving(true);
    try {
      // 必须串行：后端每个订单都是独立事务，并发更新 dispatch_assignments /
      // dispatch_batches / dispatch_batch_items / rider_profiles 会触发 InnoDB 死锁，
      // 表现为前端"系统繁忙，请稍后重试"（详见后端 CannotAcquireLockException 堆栈）。
      // 单事务走批量接口是后续优化点，这里先用串行兜底保证可用。
      const moved: number[] = [];
      const failed: number[] = [];
      for (const orderId of orderIds) {
        try {
          await moveOrderToArea(sourceAreaCode, orderId, { targetAreaCode });
          moved.push(orderId);
        } catch (err: any) {
          // 单单失败不打断后续订单：先把能移的移过去，最后统一报告失败的清单
          // eslint-disable-next-line no-console
          console.error("移区单订单失败", orderId, err);
          failed.push(orderId);
        }
      }
      setIsReordering(false);
      setLocalOrders([]);
      setSelectedOrderIds([]);
      await reload();
      if (failed.length > 0) {
        toast(`已移 ${moved.length} 单，${failed.length} 单失败：#${failed.join(" #")}`, "error");
      } else {
        toast(`已将 ${moved.length} 单移到「${targetAreaCode}」`);
      }
    } finally {
      setBatchMoving(false);
    }
  }

  // 订单详情：把单个订单从当前区域移到另一个区域
  async function handleMoveOrderToArea() {
    if (!activeArea || !orderDetailId || !orderMoveTargetArea || movingOrderToArea) return;
    if (orderMoveTargetArea === activeArea.areaCode) {
      toast("目标区域与当前区域相同，无需移动", "error");
      return;
    }
    const sourceAreaCode = activeArea.areaCode;
    const targetAreaCode = orderMoveTargetArea;
    setMovingOrderToArea(true);
    try {
      await moveOrderToArea(sourceAreaCode, orderDetailId, { targetAreaCode });
      setOrderMoveTargetArea("");
      setOrderDetailId(null);
      await reload();
      toast(`订单 #${orderDetailId} 已移到「${targetAreaCode}」，请到该区域重新指派骑手`);
    } catch (err: any) {
      toast(getErrorMessage(err, "更换区域失败"), "error");
    } finally {
      setMovingOrderToArea(false);
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

  // 触屏设备：通过上移/下移按钮调整顺序（替代拖拽）
  function handleMoveOrder(index: number, direction: -1 | 1) {
    setLocalOrders((prevItems) => {
      const targetIndex = index + direction;
      if (targetIndex < 0 || targetIndex >= prevItems.length) return prevItems;
      const newItems = Array.from(prevItems);
      const [removed] = newItems.splice(index, 1);
      newItems.splice(targetIndex, 0, removed);
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
      toast("区域名称已更新");
    } catch (err: any) {
      toast(getErrorMessage(err, "修改区域名称失败"), "error");
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
      toast("区域已删除");
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
      toast(getErrorMessage(err, "删除区域失败"), "error");
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
        defaultRiderId: Number(selectedRiderId)
      });
      setAssignRiderAreaCode(null);
      setSelectedRiderId("");
      await reload();
      toast("区域负责骑手已刷新");
    } catch (err: any) {
      toast(getErrorMessage(err, "刷新区域骑手失败"), "error");
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
            <button
              className="btn btn-primary"
              onClick={() => {
                setShowCreateAreaErrors(false);
                setShowCreateModal(true);
              }}
            >
              <PlusCircle size={16} /> 新增区域
            </button>
          </div>
        </div>
      </div>

      <div className="dispatch-summary-grid">
        <div className="dispatch-stat-card">
          <div className="admin-panel-note">区域总数</div>
          <div className="dispatch-stat-card__value">{areaStats.totalCount}</div>
        </div>
        <div className="dispatch-stat-card">
          <div className="admin-panel-note">订单总份数</div>
          <div className="dispatch-stat-card__value is-primary">{areaStats.totalOrderCount}</div>
        </div>
        <div className="dispatch-stat-card">
          <div className="admin-panel-note">待配送份数</div>
          <div className="dispatch-stat-card__value is-primary">{areaStats.dispatchingCount}</div>
        </div>
        <div className="dispatch-stat-card">
          <div className="admin-panel-note">缺骑手区域</div>
          <div className="dispatch-stat-card__value" style={{ color: "var(--error-color)" }}>{areaStats.missingRiderAreaCount}</div>
        </div>
      </div>

      {bindings.length === 0 ? (
        <div className="dispatch-empty">暂无区域，请先创建一个区域并绑定骑手。</div>
      ) : (
        <div className="border rounded-md">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>区域名称</TableHead>
                <TableHead>骑手</TableHead>
                <TableHead>订单份数</TableHead>
                <TableHead>状态</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {bindings.map((area) => (
                <TableRow
                  key={area.areaCode}
                  onClick={() => setActiveAreaCode(area.areaCode)}
                  style={{ cursor: "pointer" }}
                  className={area.missingRider ? "bg-red-50 hover:bg-red-100" : ""}
                >
                  <TableCell className="font-medium">{area.areaCode}</TableCell>
                  <TableCell>
                    {area.currentRiderName || area.defaultRiderName || "暂无骑手"}
                  </TableCell>
                  <TableCell>{area.orderCount} 份</TableCell>
                  <TableCell>
                    {area.missingRider ? (
                      <span className="tag tag-red">缺骑手</span>
                    ) : (
                      <span className="tag tag-green">正常</span>
                    )}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </div>
      )}

      <AdminDialog
        open={showCreateModal}
        title="新增区域"
        description="创建一个新区域后，可以立即绑定负责骑手。"
        onClose={() => {
          setShowCreateAreaErrors(false);
          setShowCreateModal(false);
        }}
        footer={
          <>
            <button
              className="btn btn-outline"
              onClick={() => {
                setShowCreateAreaErrors(false);
                setShowCreateModal(false);
              }}
            >
              取消
            </button>
            <button className="btn btn-primary" disabled={savingArea === "__new__"} onClick={handleCreateArea}>
              <PlusCircle size={14} /> 创建区域
            </button>
          </>
        }
      >
        <label className="admin-field">
            <span className="admin-field-label">区域名称</span>
            <SafeInput
              wrapperClassName={createAreaNameError ? "admin-input--error" : ""}
              value={newArea.name}
              prefix={<MapPin size={16} />}
              onValueChange={(value) => {
                setNewArea((prev) => ({ ...prev, name: value }));
                if (showCreateAreaErrors) {
                  setShowCreateAreaErrors(false);
                }
              }}
              placeholder="例如：万达商圈"
            />
            {createAreaNameError ? <div className="form-error">{createAreaNameError}</div> : null}
          </label>
        <label className="admin-field">
          <span className="admin-field-label">负责骑手</span>
          <AppSelect
            value={newArea.riderId}
            placeholder="可选：立即添加骑手"
            options={[{ label: "暂不添加骑手", value: "" }, ...creatableRiderOptions]}
            onChange={(value) => setNewArea((prev) => ({ ...prev, riderId: value }))}
            style={selectStyle}
          />
        </label>
      </AdminDialog>

      <DispatchAreaDetailDialog
        activeArea={activeArea}
        mealPeriod={mealPeriod}
        isReordering={isReordering}
        displayOrders={displayOrders}
        selectedOrderIds={selectedOrderIds}
        batchMoving={batchMoving}
        targetAreaOptions={bindings
          .filter((item) => item.areaCode !== activeArea?.areaCode)
          .map((item) => ({ value: item.areaCode, label: item.areaCode }))}
        onClose={() => {
          setActiveAreaCode(null);
          setIsReordering(false);
          setLocalOrders([]);
          setSelectedOrderIds([]);
        }}
        onOpenAssignRider={() => {
          if (!activeArea) return;
          setAssignRiderAreaCode(activeArea.areaCode);
          setSelectedRiderId(activeArea.defaultRiderId ? String(activeArea.defaultRiderId) : "");
          setRiderSearch("");
        }}
        onOpenAiCorrection={() => setShowAiCorrectionDialog(true)}
        onStartRename={() => activeArea && startRename(activeArea.areaCode)}
        onRequestDeleteArea={() => activeArea && setDeletingArea(activeArea.areaCode)}
        onToggleReorder={toggleReorderMode}
        onCancelReorder={cancelReorder}
        onDragEnd={handleDragEnd}
        onMoveOrder={handleMoveOrder}
        onSelectOrderDetail={setOrderDetailId}
        onToggleSelect={(orderId) =>
          setSelectedOrderIds((prev) =>
            prev.includes(orderId) ? prev.filter((id) => id !== orderId) : [...prev, orderId]
          )
        }
        onBatchMove={handleBatchMoveOrders}
      />
      <DispatchAreaAiCorrectionDialog
        open={showAiCorrectionDialog && Boolean(activeArea)}
        areaCode={activeArea?.areaCode || ""}
        originalOrders={activeAreaOrders}
        draftOrders={displayOrders}
        onClose={() => setShowAiCorrectionDialog(false)}
        onPreviewApplied={(finalOrderIds) => {
          if (!activeAreaOrders.length) {
            return;
          }
          setLocalOrders((prev) => {
            const baseOrders = prev.length ? prev : activeAreaOrders;
            return reorderDispatchAreaOrders(baseOrders, finalOrderIds);
          });
          setIsReordering(true);
        }}
        onRollbackPreview={(orderIds) => {
          if (!activeAreaOrders.length) {
            return;
          }
          setLocalOrders((prev) => {
            const baseOrders = prev.length ? prev : activeAreaOrders;
            return reorderDispatchAreaOrders(baseOrders, orderIds);
          });
          setIsReordering(true);
        }}
        onConfirmed={async () => {
          await reload();
          setIsReordering(false);
          setLocalOrders([]);
        }}
      />

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
            <SafeInput prefix={<MapPin size={16} />} value={renameValue} onValueChange={setRenameValue} autoFocus />
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
        description={assignRiderAreaCode ? `将 ${assignRiderAreaCode} 所有未配送订单（含午餐和晚餐）统一分配给骑手` : undefined}
        zOffset={10}
        onClose={() => {
          setAssignRiderAreaCode(null);
          setSelectedRiderId("");
          setRiderSearch("");
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
          <SafeInput
            value={riderSearch}
            onValueChange={setRiderSearch}
            placeholder="搜索骑手姓名或手机号"
            style={{ width: "100%", marginBottom: 8 }}
          />
          <div style={{ display: "grid", gap: "8px", maxHeight: 320, overflowY: "auto", paddingRight: 4 }}>
            {filteredRiderReplaceList.map((r) => {
              const selected = selectedRiderId === r.riderId;
              const blocked = r.blocked && !selected;
              return (
                <button
                  key={r.riderId}
                  type="button"
                  disabled={r.blocked}
                  onClick={() => {
                    if (r.blocked) return;
                    setSelectedRiderId(r.riderId);
                  }}
                  className={`rider-replace-item${selected ? " is-selected" : ""}${blocked ? " is-blocked" : ""}`}
                  style={{
                    display: "flex",
                    alignItems: "center",
                    justifyContent: "space-between",
                    gap: 10,
                    width: "100%",
                    textAlign: "left",
                    padding: "10px 12px",
                    borderRadius: 8,
                    border: selected ? "1px solid #2f6df6" : "1px solid #e3e6ee",
                    background: selected ? "#eef3ff" : blocked ? "#f6f7f9" : "#fff",
                    color: blocked ? "#9aa0ac" : "#1f2430",
                    cursor: r.blocked ? "not-allowed" : "pointer",
                    opacity: blocked ? 0.7 : 1
                  }}
                >
                  <span style={{ display: "flex", alignItems: "center", gap: 8, minWidth: 0 }}>
                    <span style={{ fontWeight: 600, whiteSpace: "nowrap" }}>{r.riderName}</span>
                    <span style={{ color: blocked ? "#aab0bc" : "#8a90a0", fontSize: 12 }}>{r.phone}</span>
                    {r.isCurrent ? <span className="tag tag-gray" style={{ fontSize: 11 }}>当前</span> : null}
                  </span>
                  <span style={{ display: "flex", alignItems: "center", gap: 6, flexShrink: 0 }}>
                    {r.badge ? (
                      <span
                        className={r.badge.kind === "danger" ? "tag tag-red" : "tag tag-amber"}
                        style={{ fontSize: 11 }}
                      >
                        {r.badge.text}
                      </span>
                    ) : null}
                    {selected ? <span className="tag tag-blue" style={{ fontSize: 11 }}>已选</span> : null}
                  </span>
                </button>
              );
            })}
            {filteredRiderReplaceList.length === 0 ? (
              riderReplaceList.length === 0 ? (
                <div className="dispatch-inline-note">当前{mealPeriodLabel(mealPeriod)}暂无可用骑手，请先在骑手管理中添加。</div>
              ) : (
                <div className="dispatch-inline-note">未找到匹配的骑手，请调整搜索关键词。</div>
              )
            ) : null}
          </div>
        </label>
        <div className="dispatch-inline-note" style={{ marginTop: 8 }}>
          同一骑手不能同时负责多个区域；<b>已停用</b>或<b>已被其他区域使用</b>的骑手不可选（灰色显示）。
        </div>
      </AdminDialog>

      <AdminDialog
        open={Boolean(orderDetail)}
        title="订单详情"
        description={orderDetail ? `订单 #${orderDetail.orderId} · ${orderDetail.customerName}` : undefined}
        zOffset={10}
        onClose={() => setOrderDetailId(null)}
        footer={
          <div style={{ display: "flex", justifyContent: "flex-end", alignItems: "center", width: "100%" }}>
            <button className="btn btn-outline" onClick={() => setOrderDetailId(null)}>关闭</button>
          </div>
        }
      >
        {orderDetail ? (
          <div style={{ display: "grid", gap: "16px" }}>
            <div className="dispatch-dialog-header">
              <div>
                <div className="dispatch-card__title">{activeArea?.areaCode || "-"}</div>
                <div className="dispatch-inline-note">当前骑手：{activeArea?.currentRiderName || activeArea?.defaultRiderName || "暂无骑手"}</div>
              </div>
              <div style={{ display: "flex", gap: "8px", alignItems: "center" }}>
                <span className={`tag ${orderDetail.deliveryStatus === "DELIVERED" ? "tag-green" : "tag-amber"}`}>
                  {orderDetail.deliveryStatus === "DELIVERED" ? "已送达" : "待配送"}
                </span>
                {orderDetail.quantity > 1 ? <span className="tag tag-gray">数量 ×{orderDetail.quantity}</span> : null}
              </div>
            </div>

            <div className="dispatch-detail-grid">
              <section className="dispatch-detail-panel">
                <div className="dispatch-section__title" style={{ marginBottom: 0, fontSize: "15px" }}>订单信息</div>
                <div className="dispatch-detail-row">
                  <div className="admin-panel-note">配送地址</div>
                  <div>{orderDetail.deliveryAddress || "-"}</div>
                </div>
                <div className="dispatch-detail-row">
                  <div className="admin-panel-note">当前骑手</div>
                  <div>{activeArea?.currentRiderName || activeArea?.defaultRiderName || "暂无骑手"}</div>
                </div>
                <div className="dispatch-detail-row dispatch-detail-row--column">
                  <div className="admin-panel-note">区域归属</div>
                  <div>
                    当前区域：<span className="tag tag-blue">{activeArea?.areaCode || "-"}</span>
                  </div>
                  {orderTargetAreaOptions.length > 0 ? (
                    <>
                      <div style={{ display: "flex", gap: "8px", alignItems: "center", flexWrap: "wrap" }}>
                        <AppSelect
                          value={orderMoveTargetArea}
                          placeholder="选择目标区域"
                          options={orderTargetAreaOptions}
                          onChange={setOrderMoveTargetArea}
                          style={{ flex: "1 1 180px", minWidth: 160 }}
                        />
                        <button
                          className="btn btn-primary btn-compact"
                          disabled={!orderMoveTargetArea || movingOrderToArea}
                          onClick={() => handleMoveOrderToArea()}
                        >
                          <MapPin size={14} /> 确认移区
                        </button>
                      </div>
                      <div className="dispatch-inline-note">
                        移区后订单将归入目标区域待派单并清空骑手，需重新指派骑手。
                      </div>
                    </>
                  ) : (
                    <div className="dispatch-inline-note">当前没有其他区域可选。</div>
                  )}
                </div>
                <div className="dispatch-detail-row">
                  <div className="admin-panel-note">用户备注</div>
                  <div>{hasDisplayValue(orderDetail.userNote) ? orderDetail.userNote : "-"}</div>
                </div>
                <div className="dispatch-detail-row">
                  <div className="admin-panel-note">商家备注</div>
                  <div>{hasDisplayValue(orderDetail.merchantRemark) ? orderDetail.merchantRemark : "-"}</div>
                </div>
              </section>

              <section className="dispatch-detail-panel">
                <div className="dispatch-section__title" style={{ marginBottom: 0, fontSize: "15px" }}>送达信息</div>
                <div className="dispatch-detail-row">
                  <div className="admin-panel-note">骑手备注</div>
                  <div>{hasDisplayValue(orderDetail.receiptNote) ? orderDetail.receiptNote : "-"}</div>
                </div>
                <div className="dispatch-detail-row">
                  <div className="admin-panel-note">送达时间</div>
                  <div>{orderDetail.deliveredAt || "-"}</div>
                </div>
              </section>
            </div>

            <div className="dispatch-image-grid">
              <section className="dispatch-detail-panel">
                <div className="admin-panel-note">地址参照图</div>
                {hasDisplayValue(orderDetail.referenceImageUrl) ? (
                  <img
                    src={orderDetail.referenceImageUrl}
                    alt="地址参照图"
                    style={{ width: "100%", borderRadius: "12px", border: "1px solid var(--border-color)", objectFit: "cover", aspectRatio: "3 / 4" }}
                    onClick={() => window.open(orderDetail.referenceImageUrl, "_blank")}
                  />
                ) : (
                  <div className="dispatch-image-empty">暂无参照图</div>
                )}
              </section>

              <section className="dispatch-detail-panel">
                <div className="admin-panel-note">本次送达图</div>
                {hasDisplayValue(orderDetail.receiptUrl) ? (
                  <img 
                    src={orderDetail.receiptUrl}
                    alt="回执照片" 
                    style={{ width: "100%", borderRadius: "12px", border: "1px solid var(--border-color)", objectFit: "cover", aspectRatio: "3 / 4", cursor: "pointer" }}
                    onClick={() => window.open(orderDetail.receiptUrl, "_blank")}
                  />
                ) : (
                  <div className="dispatch-image-empty">暂无送达图</div>
                )}
              </section>
            </div>
          </div>
        ) : null}
      </AdminDialog>
    </div>
  );
}
