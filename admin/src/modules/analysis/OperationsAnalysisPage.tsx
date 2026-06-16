import { useEffect, useMemo, useState } from "react";
import { createCostEntry, fetchAnalysisOverview, fetchCostEntries } from "../../shared/api/http";
import type { AnalysisOverviewResponse, CostEntryItem } from "../../shared/api/types";
import { X, RotateCcw } from "lucide-react";
import { buildAnalysisInsights, formatMoney } from "./operationsAnalysisPage.helpers";
import { formatDateLabel } from "../../shared/utils/dateTime";
import { AppSelect } from "../../shared/components/AppSelect";
import { RemarkField } from "../../shared/components/RemarkField";
import { toast } from "../../shared/components/Toast";

const emptyOverview: AnalysisOverviewResponse = {
  date: "",
  totalSales: 0,
  totalCost: 0,
  totalProfit: 0,
  totalOrders: 0,
  totalMeals: 0,
  aftersaleCount: 0
};

export function OperationsAnalysisPage() {
  const [overview, setOverview] = useState<AnalysisOverviewResponse>(emptyOverview);
  const [costEntries, setCostEntries] = useState<CostEntryItem[]>([]);
  const [isAddCostOpen, setIsAddCostOpen] = useState(false);
  const [submittingAddCost, setSubmittingAddCost] = useState(false);
  const [costForm, setCostForm] = useState({
    costDate: new Date().toISOString().slice(0, 10),
    costCategory: "INGREDIENT",
    amount: "",
    remark: "",
    recordedBy: "后台客服"
  });

  const insights = useMemo(() => buildAnalysisInsights(overview), [overview]);
  const topCostEntries = useMemo(
    () => [...costEntries].sort((a, b) => Number(b.amount) - Number(a.amount)).slice(0, 3),
    [costEntries]
  );

  useEffect(() => {
    reload().catch((err) => toast(err?.response?.data?.message || err.message || String(err), "error"));
  }, []);

  async function reload() {
    const [overviewData, costData] = await Promise.all([
      fetchAnalysisOverview(),
      fetchCostEntries()
    ]);
    setOverview(overviewData);
    setCostEntries(costData);
  }

  function handleOpenAddCost() {
    setCostForm({
      costDate: new Date().toISOString().slice(0, 10),
      costCategory: "INGREDIENT",
      amount: "",
      remark: "",
      recordedBy: "后台客服"
    });
    setIsAddCostOpen(true);
  }

  async function submitAddCost() {
    if (!costForm.costDate || !costForm.amount || isNaN(Number(costForm.amount))) {
      toast("请输入正确的日期和金额", "error");
      return;
    }
    if (submittingAddCost) {
      return;
    }
    setSubmittingAddCost(true);
    try {
      await createCostEntry({
        costDate: costForm.costDate,
        costCategory: costForm.costCategory,
        amount: Number(costForm.amount),
        remark: costForm.remark,
        recordedBy: costForm.recordedBy
      });
      setIsAddCostOpen(false);
      await reload();
      toast("成本已录入");
    } catch (err: any) {
      toast(err?.response?.data?.message || err?.message || "录入成本失败", "error");
    } finally {
      setSubmittingAddCost(false);
    }
  }

  const resolveCategoryLabel = (category: string) => {
    switch (category) {
      case "INGREDIENT": return "食材采购";
      case "PACKAGING": return "包装耗材";
      case "LABOR": return "人工成本";
      case "UTILITY": return "水电租金";
      case "MARKETING": return "营销推广";
      case "OTHER": return "其他";
      default: return category;
    }
  };

  return (
    <>
      <div className="page-header">
        <div>
          <h2 className="page-title">经营分析</h2>
          <p className="page-subtitle">销售、成本、毛利与售后概览</p>
        </div>
        <div className="page-header__actions">
          <button className="btn btn-outline" onClick={() => reload()}>
            <RotateCcw size={16} /> 刷新数据
          </button>
          <button className="btn btn-primary" onClick={handleOpenAddCost}>新增成本录入</button>
        </div>
      </div>
        <div className="stat-row">
          <div className="stat-card">
            <div className="stat-title">总销售额</div>
            <div className="stat-val stat-val--primary">¥ {formatMoney(overview.totalSales)}</div>
          </div>
          <div className="stat-card">
            <div className="stat-title">总成本</div>
            <div className="stat-val stat-val--danger">¥ {formatMoney(overview.totalCost)}</div>
          </div>
          <div className="stat-card">
            <div className="stat-title">毛利</div>
            <div className="stat-val stat-val--success">¥ {formatMoney(overview.totalProfit)}</div>
            <div className="stat-footer">毛利率 {insights.grossMarginRate}</div>
          </div>
          <div className="stat-card">
            <div className="stat-title">总餐数 / 售后单</div>
            <div className="stat-val">{overview.totalMeals} <span style={{ fontSize: 18, margin: "0 8px" }}>/</span> {overview.aftersaleCount}</div>
            <div className="stat-footer">售后率 {insights.aftersaleRate}</div>
          </div>
        </div>

        <div className="toolbar">
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: "12px" }}>
            <div className="address-card" style={{ cursor: "default", minHeight: "100px" }}>
              <div className="address-content">
                <div className="address-title">平均客单价</div>
                <div className="metric-card-value">¥ {insights.avgOrderValue}</div>
                <div className="address-detail">总销售额 / 总订单数</div>
              </div>
            </div>
            <div className="address-card" style={{ cursor: "default", minHeight: "100px" }}>
              <div className="address-content">
                <div className="address-title">平均餐数</div>
                <div className="metric-card-value stat-val--warning">
                  {(overview.totalMeals / Math.max(overview.totalOrders, 1)).toFixed(1)}
                </div>
                <div className="address-detail">总餐数 / 总订单数</div>
              </div>
            </div>
            <div className="address-card" style={{ cursor: "default", minHeight: "100px" }}>
              <div className="address-content">
                <div className="address-title">售后率</div>
                <div className="metric-card-value stat-val--warning">{insights.aftersaleRate}</div>
                <div className="address-detail">售后单 / 总订单数</div>
              </div>
            </div>
            <div className="address-card" style={{ cursor: "default", minHeight: "100px" }}>
              <div className="address-content">
                <div className="address-title">高额成本</div>
                <div className="address-detail">{topCostEntries.length} 项</div>
              </div>
            </div>
          </div>
        </div>

        <div className="toolbar">
          <div className="admin-panel-header">
            <div className="admin-panel-title">重点成本项</div>
            <div className="admin-panel-note">按金额排序</div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(220px, 1fr))", gap: "12px" }}>
            {topCostEntries.length === 0 && <div className="empty-state" style={{ padding: "18px 0" }}>当前还没有成本数据</div>}
            {topCostEntries.map((item) => (
              <div key={item.id} className="address-card" style={{ cursor: "default", minHeight: "96px" }}>
                <div className="address-content">
                  <div className="address-title">{resolveCategoryLabel(item.costCategory)}</div>
                  <div className="metric-card-value stat-val--danger">¥ {formatMoney(item.amount)}</div>
                  <div className="address-detail">{formatDateLabel(item.costDate)} / {item.remark || "无备注"}</div>
                </div>
              </div>
            ))}
          </div>
        </div>

        <div className="table-container">
          <div className="table-header-toolbar">
            <span>成本台账 ({costEntries.length})</span>
            <span style={{ color: "var(--text-sub)", fontSize: "13px" }}>查看全部成本记录</span>
          </div>
          <div className="table-responsive">
          <table>
            <thead>
              <tr>
                <th>日期</th>
                <th>支出分类</th>
                <th>金额</th>
                <th>备注</th>
                <th>录入人</th>
              </tr>
            </thead>
            <tbody>
              {costEntries.map((item) => (
                <tr key={item.id}>
                  <td>{formatDateLabel(item.costDate)}</td>
                  <td>
                    <span className="tag tag-gray">{resolveCategoryLabel(item.costCategory)}</span>
                  </td>
                  <td style={{ fontWeight: 600 }}>¥ {item.amount}</td>
                  <td>{item.remark || "-"}</td>
                  <td style={{ color: "var(--text-sub)" }}>{item.recordedBy}</td>
                </tr>
              ))}
              {costEntries.length === 0 && (
                <tr>
                  <td colSpan={5}>
                    <div className="empty-state">暂无成本记录</div>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
          </div>

          <div className="mobile-card-list">
            {costEntries.length === 0 && <div className="empty-state">暂无成本记录</div>}
            {costEntries.map((item) => (
              <div className="mobile-card" key={item.id}>
                <div className="mobile-card-header">
                  <span style={{ fontWeight: 700 }}>{resolveCategoryLabel(item.costCategory)}</span>
                  <span className="tag tag-gray">{formatDateLabel(item.costDate)}</span>
                </div>
                <div className="mobile-card-row">
                  <div className="mobile-card-label">金额</div>
                  <div className="mobile-card-value" style={{ fontWeight: 800, color: "var(--error-color)" }}>
                    ¥ {formatMoney(item.amount)}
                  </div>
                </div>
                <div className="mobile-card-row">
                  <div className="mobile-card-label">备注</div>
                  <div className="mobile-card-value">{item.remark || "-"}</div>
                </div>
                <div className="mobile-card-row">
                  <div className="mobile-card-label">录入人</div>
                  <div className="mobile-card-value">{item.recordedBy}</div>
                </div>
              </div>
            ))}
          </div>
        </div>

      {isAddCostOpen && (
        <div className="modal-overlay">
          <div className="modal-content" style={{ maxWidth: 460 }}>
            <div className="modal-header">
              新增成本录入
              <div className="modal-close" onClick={() => setIsAddCostOpen(false)}><X size={18} /></div>
            </div>
            <div className="modal-body">
              <div className="form-group" style={{ display: "flex", gap: 16 }}>
                <div style={{ flex: 1 }}>
                  <label className="form-label"><span className="required">*</span>产生日期</label>
                  <input type="date" className="form-control" value={costForm.costDate} onChange={e => setCostForm({ ...costForm, costDate: e.target.value })} />
                </div>
                <div style={{ flex: 1 }}>
                  <label className="form-label"><span className="required">*</span>支出金额 (元)</label>
                  <input type="number" step="0.01" min="0" className="form-control" placeholder="0.00" value={costForm.amount} onChange={e => setCostForm({ ...costForm, amount: e.target.value })} />
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">支出分类</label>
                <AppSelect
                  value={costForm.costCategory}
                  options={[
                    { label: "食材采购", value: "INGREDIENT" },
                    { label: "包装耗材", value: "PACKAGING" },
                    { label: "人工成本", value: "LABOR" },
                    { label: "水电租金", value: "UTILITY" },
                    { label: "营销推广", value: "MARKETING" },
                    { label: "其他支出", value: "OTHER" }
                  ]}
                  onChange={(value) => setCostForm({ ...costForm, costCategory: value })}
                />
              </div>
              <RemarkField
                label="支出备注"
                value={costForm.remark}
                onChange={(value) => setCostForm({ ...costForm, remark: value })}
                placeholder="记录具体的支出名目..."
                scene="COST_REMARK"
                multiline
              />
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" disabled={submittingAddCost} onClick={() => setIsAddCostOpen(false)}>取消</button>
              <button className="btn btn-primary" disabled={submittingAddCost} onClick={submitAddCost}>{submittingAddCost ? "提交中..." : "确认录入"}</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
