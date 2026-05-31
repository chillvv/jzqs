import fs from 'fs';
import path from 'path';

const filePath = path.resolve('d:/Code/jzqs/admin/src/modules/dispatch/DispatchAreasPage.tsx');
let content = fs.readFileSync(filePath, 'utf-8');

// 1. Add missing rider warning to the card header
const cardHeaderOld = `<div className="dispatch-card__header">
                <div>
                  <div className="dispatch-card__title">{group.areaCode}</div>`;
const cardHeaderNew = `<div className="dispatch-card__header">
                <div>
                  <div className="dispatch-card__title">{group.areaCode}</div>`;

content = content.replace(cardHeaderOld, cardHeaderNew); // Just to test replacement

// 2. Add assign rider state and API imports
content = content.replace('updateDispatchAreaBinding', 'updateDispatchAreaBinding,\n  assignRiderToArea,\n  assignRiderToAreaOrder');

const statesOld = `const [removingBinding, setRemovingBinding] = useState<{ areaCode: string; riderId: number; riderName: string } | null>(null);`;
const statesNew = `const [removingBinding, setRemovingBinding] = useState<{ areaCode: string; riderId: number; riderName: string } | null>(null);\n  const [assignRiderState, setAssignRiderState] = useState<{ areaCode: string; orderId?: number } | null>(null);`;
content = content.replace(statesOld, statesNew);

// 3. Add assignRider handle
const handleRenameOld = `async function handleRename() {`;
const handleAssignNew = `async function handleAssignRider() {
    if (!assignRiderState || !selectedRiderId) return;
    setSavingArea(assignRiderState.areaCode);
    try {
      const riderName = riderOptions.find(r => r.value === selectedRiderId)?.label?.split(' ')[0] || selectedRiderId;
      if (assignRiderState.orderId) {
        await assignRiderToAreaOrder(assignRiderState.areaCode, assignRiderState.orderId, riderName);
      } else {
        await assignRiderToArea(assignRiderState.areaCode, riderName);
      }
      setAssignRiderState(null);
      setSelectedRiderId("");
      await reload();
    } catch (err: any) {
      window.alert(err?.response?.data?.message || err.message || String(err));
    } finally {
      setSavingArea(null);
    }
  }

  async function handleRename() {`;
content = content.replace(handleRenameOld, handleAssignNew);

// 4. Update the Warning for Missing Riders
const chipListOld = `<div className="dispatch-chip-list">`;
const chipListNew = `{group.riders.length === 0 && group.activeOrders.length > 0 ? (
                <div className="dispatch-area-warning" style={{ margin: "0 0 12px" }}>
                  <div className="dispatch-area-warning__title">该区域有 {group.activeOrders.length} 单待配送，请尽快分配骑手</div>
                </div>
              ) : null}
              <div className="dispatch-chip-list">`;
content = content.replace(chipListOld, chipListNew);

// 5. Update the orders list to add "分配" button for AREA_ASSIGNED
const ordersItemOld = `<strong>订单 #{order.orderId}</strong>
                            <span className={\`tag \${order.deliveryStatus === "DISPATCHING" ? "tag-amber" : "tag-gray"}\`}>
                              {order.deliveryStatus}
                            </span>
                          </div>
                          <div>{order.customerName}</div>
                          <div className="dispatch-order-item__meta">{order.deliveryAddress}</div>`;

const ordersItemNew = `<strong>订单 #{order.orderId}</strong>
                            <span className={\`tag \${order.deliveryStatus === "DISPATCHING" ? "tag-blue" : order.deliveryStatus === "AREA_ASSIGNED" ? "tag-amber" : "tag-gray"}\`}>
                              {order.deliveryStatus === "AREA_ASSIGNED" ? "待分配" : order.deliveryStatus === "DISPATCHING" ? "配送中" : order.deliveryStatus}
                            </span>
                          </div>
                          <div>{order.customerName}</div>
                          <div className="dispatch-order-item__meta" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                            <span>{order.deliveryAddress}</span>
                            {order.deliveryStatus === "AREA_ASSIGNED" ? (
                              <button className="btn btn-outline btn-compact" onClick={() => {
                                setAssignRiderState({ areaCode: group.areaCode, orderId: order.orderId });
                                setSelectedRiderId("");
                              }}>
                                分配骑手
                              </button>
                            ) : null}
                          </div>`;
content = content.replace(new RegExp(ordersItemOld.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g'), ordersItemNew);

// 6. Add "分配骑手" button at the card footer
const actionButtonsOld = `<button
                  className="btn btn-outline btn-compact"
                  disabled={savingArea === group.areaCode}
                  onClick={() => {
                    setAddRiderArea(group.areaCode);
                    setSelectedRiderId("");
                  }}
                >
                  <UserPlus size={14} /> 添加骑手
                </button>`;

const actionButtonsNew = `<button
                  className="btn btn-primary btn-compact"
                  disabled={savingArea === group.areaCode}
                  onClick={() => {
                    setAssignRiderState({ areaCode: group.areaCode });
                    setSelectedRiderId("");
                  }}
                >
                  <UserPlus size={14} /> 分配骑手
                </button>
                <button
                  className="btn btn-outline btn-compact"
                  disabled={savingArea === group.areaCode}
                  onClick={() => {
                    setAddRiderArea(group.areaCode);
                    setSelectedRiderId("");
                  }}
                >
                  <UserPlus size={14} /> 添加长期骑手
                </button>`;
content = content.replace(actionButtonsOld, actionButtonsNew);

// 7. Add Assign Rider Dialog
const assignDialog = `      <AdminDialog
        open={Boolean(assignRiderState)}
        title="分配骑手"
        description={assignRiderState ? (assignRiderState.orderId ? \`将订单 #\${assignRiderState.orderId} 分配给骑手\` : \`将 \${assignRiderState.areaCode} 区域的待分配订单分配给骑手\`) : undefined}
        onClose={() => {
          setAssignRiderState(null);
          setSelectedRiderId("");
        }}
        footer={
          <>
            <button className="btn btn-outline" onClick={() => {
              setAssignRiderState(null);
              setSelectedRiderId("");
            }}>取消</button>
            <button
              className="btn btn-primary"
              disabled={!selectedRiderId || !assignRiderState || savingArea === assignRiderState?.areaCode}
              onClick={handleAssignRider}
            >
              确认分配
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
      </AdminDialog>`;

content = content.replace('</AdminDialog>\n    </div>', '</AdminDialog>\n\n' + assignDialog + '\n    </div>');

// 8. Fix the area card border color for warning
content = content.replace(/className="dispatch-card"/g, 'className={`dispatch-card ${group.riders.length === 0 && group.activeOrders.length > 0 ? "has-warning" : ""}`}');

fs.writeFileSync(filePath, content, 'utf-8');
console.log('Done');
