import fs from 'fs';
import path from 'path';

const filePath = path.resolve('d:/Code/jzqs/admin/src/modules/dispatch/DispatchHomePage.tsx');
let content = fs.readFileSync(filePath, 'utf-8');

// 1. Remove stat-row
content = content.replace(/<div className="stat-row">[\s\S]*?<\/div>\s*<\/div>\s*<\/div>/, '');

// 2. Remove heroMetrics from imports and declarations
content = content.replace(/import \{ buildDispatchHeroMetrics, normalizeDispatchOverview \} from "\.\/dispatchCenterPage\.helpers";/, 'import { normalizeDispatchOverview } from "./dispatchCenterPage.helpers";');
content = content.replace(/const heroMetrics = useMemo\(\(\) => buildDispatchHeroMetrics\(overview\), \[overview\]\);\n/, '');

// 3. Simplify pending items table columns
const oldThead = `                <thead>
                  <tr>
                    <th>
                      <input type="checkbox" checked={allVisibleSelected} onChange={toggleAllVisible} />
                    </th>
                    <th>订单号</th>
                    <th>客户</th>
                    <th>配送地址</th>
                    <th>推荐区域/骑手</th>
                    <th>当前问题</th>
                    <th>带入处理栏</th>
                  </tr>
                </thead>`;

const newThead = `                <thead>
                  <tr>
                    <th style={{ width: '40px' }}>
                      <input type="checkbox" checked={allVisibleSelected} onChange={toggleAllVisible} />
                    </th>
                    <th>订单号</th>
                    <th>客户</th>
                    <th>配送地址</th>
                    <th>区域</th>
                  </tr>
                </thead>`;
content = content.replace(oldThead, newThead);

// 4. Update table body
const oldTbody = `                      <td>
                        <div className="dispatch-chip-list">
                          <span className={\`tag \${item.suggestedAreaCode ? "tag-blue" : "tag-gray"}\`}>{item.suggestedAreaCode || "未推荐区域"}</span>
                          <span className={\`tag \${item.suggestedRiderName ? "tag-green" : "tag-gray"}\`}>{item.suggestedRiderName || "未推荐骑手"}</span>
                        </div>
                      </td>
                      <td>
                        <div className="admin-table-cell">
                          <span>{item.reason}</span>
                          <span className="admin-table-cell__meta">{item.exceptionType}</span>
                        </div>
                      </td>
                      <td>
                        <div className="dispatch-pending-actions">
                          <button className="btn btn-outline btn-compact" onClick={() => primeSingleOrder(item)}>
                            <MapPin size={12} /> 带入处理栏
                          </button>
                        </div>
                      </td>`;

const newTbody = `                      <td>
                        {item.autoResolved ? (
                          <span className="tag tag-blue">{item.suggestedAreaCode} ✓</span>
                        ) : (
                          <AppSelect
                            value={inlineAreas[item.orderId] || ""}
                            options={[{ label: "选择区域 ▾", value: "" }, ...areaOptions]}
                            onChange={(val) => setInlineAreas(prev => ({ ...prev, [item.orderId]: val }))}
                            style={{ minWidth: '120px' }}
                          />
                        )}
                      </td>`;
content = content.replace(oldTbody, newTbody);

// 5. Add inlineAreas state
content = content.replace(/const \[selectedPendingIds, setSelectedPendingIds\] = useState<number\[\]>\(\[\]\);/, `const [selectedPendingIds, setSelectedPendingIds] = useState<number[]>([]);\n  const [inlineAreas, setInlineAreas] = useState<Record<number, string>>({});`);

// 6. Simplify bottom bar
const oldBottomBar = `          <div className="dispatch-bulk-bar__controls">
            <button className="btn btn-outline btn-compact" disabled={selectedPendingIds.length === 0} onClick={() => setSelectedPendingIds([])}>
              清空选择
            </button>
            <div className="dispatch-bulk-bar__field">
              <AppSelect
                value={batchAreaCode}
                options={[{ label: "请选择区域", value: "" }, ...areaOptions]}
                placeholder="批量选择区域"
                onChange={setBatchAreaCode}
                style={selectStyle}
              />
            </div>
            <div className="dispatch-bulk-bar__field">
              <AppSelect
                value={batchRiderName}
                options={[{ label: "暂不指定骑手", value: "" }, ...riderOptions]}
                placeholder="可选指定骑手"
                onChange={setBatchRiderName}
                style={selectStyle}
              />
            </div>
            <button
              className="btn btn-outline"
              disabled={!selectedPendingIds.length || !batchAreaCode.trim() || batchAssigning !== null}
              onClick={() => handleBatchAssign("AREA_ONLY").catch(() => {})}
            >
              仅归区域
            </button>
            <button
              className="btn btn-primary"
              disabled={!selectedPendingIds.length || !batchAreaCode.trim() || !batchRiderName.trim() || batchAssigning !== null}
              onClick={() => handleBatchAssign("AREA_AND_RIDER").catch(() => {})}
            >
              区域+骑手一起分配
            </button>
          </div>`;

const newBottomBar = `          <div className="dispatch-bulk-bar__controls">
            <div className="dispatch-bulk-bar__field">
              <AppSelect
                value={batchAreaCode}
                options={[{ label: "分配区域 ▾", value: "" }, ...areaOptions]}
                onChange={setBatchAreaCode}
                style={selectStyle}
              />
            </div>
            <div className="dispatch-bulk-bar__field">
              <AppSelect
                value={batchRiderName}
                options={[{ label: "分配骑手 ▾", value: "" }, ...riderOptions]}
                onChange={setBatchRiderName}
                style={selectStyle}
              />
            </div>
            <button
              className="btn btn-primary"
              disabled={!selectedPendingIds.length || !batchAreaCode.trim() || batchAssigning !== null}
              onClick={() => handleBatchAssign("AREA_AND_RIDER").catch(() => {})}
            >
              分配并派单
            </button>
          </div>`;
content = content.replace(oldBottomBar, newBottomBar);

// 7. Remove primeSingleOrder
content = content.replace(/  function primeSingleOrder[\s\S]*?\}\n\n/, '');

// 8. Fix matchesPendingSearch
content = content.replace(/    item\.suggestedRiderName \|\| "",\n    item\.reason/, '    item.autoResolved ? "已匹配" : "未匹配"');

// 9. Remove hasDispatchAssignment usage if any
content = content.replace(/\{item\.hasDispatchAssignment \? <span className="tag tag-blue">已有派单<\/span> : <span className="tag tag-amber">待处理<\/span>\}/, '<span className="tag tag-amber">待分配</span>');

fs.writeFileSync(filePath, content, 'utf-8');
console.log('Done');
