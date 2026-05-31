import React, { useEffect, useState } from "react";
import { fetchOperationSettings, updateHolidayNotice, updateOrderingToggle, updateBannerImages, updatePopupAnnouncement } from "../../shared/api/http";
import type { OperationSettingsResponse } from "../../shared/api/types";
import { X, Settings2, Bell, AlertTriangle, Power, Image as ImageIcon, Megaphone } from "lucide-react";
import { buildCustomerFacingSettingHints, buildOperationRiskSummary, countBannerImages, normalizeBannerImages, resolveOrderingTone } from "./systemSettingsPage.helpers";

export function SystemSettingsPage() {
  const [settings, setSettings] = useState<OperationSettingsResponse>({
    orderingEnabled: true,
    orderingStatusLabel: "通道开启中",
    holidayNoticeTitle: "节假日/店休特殊公告",
    holidayNoticeDesc: "在小程序首页顶部展示的提示信息",
    emergencyActionLabel: "熔断：一键暂停接单 (假期店休使用)",
    bannerImages: '["../../assets/hero-new.jpg"]',
    popupAnnouncementEnabled: false,
    popupAnnouncementContent: ""
  });

  const [isNoticeOpen, setIsNoticeOpen] = useState(false);
  const [noticeForm, setNoticeForm] = useState({ title: "", description: "" });
  const [isBannerOpen, setIsBannerOpen] = useState(false);
  const [bannerForm, setBannerForm] = useState({ bannerImages: "" });
  const [isPopupOpen, setIsPopupOpen] = useState(false);
  const [popupForm, setPopupForm] = useState({ enabled: false, content: "" });
  const riskSummary = buildOperationRiskSummary(settings);
  const customerFacingHints = buildCustomerFacingSettingHints(settings);
  const bannerImages = normalizeBannerImages(settings.bannerImages);

  useEffect(() => {
    reloadSettings().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)));
  }, []);

  async function reloadSettings() {
    const response = await fetchOperationSettings();
    setSettings(response);
  }

  async function handleToggleOrdering() {
    const response = await updateOrderingToggle(!settings.orderingEnabled);
    setSettings(response);
  }

  function openNoticeForm() {
    setNoticeForm({
      title: settings.holidayNoticeTitle,
      description: settings.holidayNoticeDesc
    });
    setIsNoticeOpen(true);
  }

  async function handleNoticeSubmit() {
    if (!noticeForm.title || !noticeForm.description) return;
    const response = await updateHolidayNotice(noticeForm.title, noticeForm.description);
    setSettings(response);
    setIsNoticeOpen(false);
  }

  function openBannerForm() {
    setBannerForm({
      bannerImages: settings.bannerImages
    });
    setIsBannerOpen(true);
  }

  async function handleBannerSubmit() {
    if (!bannerForm.bannerImages) return;
    try {
      JSON.parse(bannerForm.bannerImages);
    } catch (e) {
      window.alert("轮播图配置必须是合法的 JSON 数组格式");
      return;
    }
    const response = await updateBannerImages(bannerForm.bannerImages);
    setSettings(response);
    setIsBannerOpen(false);
  }

  function openPopupForm() {
    setPopupForm({
      enabled: settings.popupAnnouncementEnabled,
      content: settings.popupAnnouncementContent
    });
    setIsPopupOpen(true);
  }

  async function handlePopupSubmit() {
    const response = await updatePopupAnnouncement(popupForm.enabled, popupForm.content);
    setSettings(response);
    setIsPopupOpen(false);
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h2 className="page-title">系统设置</h2>
          <p className="page-subtitle">接单状态、公告与营业控制</p>
        </div>
        <div className="page-header__actions">
          <button className="btn btn-outline" onClick={openNoticeForm}>
            <Bell size={16} />
            配置公告
          </button>
          <button
            className={settings.orderingEnabled ? "btn btn-danger" : "btn btn-primary"}
            onClick={() => handleToggleOrdering().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}
          >
            {settings.orderingEnabled ? <AlertTriangle size={16} /> : <Power size={16} />}
            {settings.orderingEnabled ? "立即暂停接单" : "恢复接单"}
          </button>
        </div>
      </div>
      <div className="stat-row">
        <div className="stat-card">
          <div className="stat-title">接单状态</div>
          <div className="stat-val">{settings.orderingEnabled ? "开启" : "关闭"}</div>
          <div className="stat-footer">{settings.orderingStatusLabel}</div>
        </div>
        <div className="stat-card">
          <div className="stat-title">公告配置</div>
          <div className="stat-val">1 <span>条</span></div>
          <div className="stat-footer">{settings.holidayNoticeTitle ? "当前首页展示公告已配置" : "当前未配置首页公告"}</div>
        </div>
        <div className="stat-card">
          <div className="stat-title">暂停接单</div>
          <div className="stat-val">熔断 <span>可用</span></div>
          <div className="stat-footer">适用于节假日店休或临时停业</div>
        </div>
        <div className="stat-card">
          <div className="stat-title">当前状态</div>
          <div className={`stat-val ${riskSummary.tone === "warning" ? "stat-val--warning" : "stat-val--primary"}`}>
            {riskSummary.tone === "warning" ? "注意" : "正常"}
          </div>
          <div className="stat-footer">{riskSummary.primaryHint}</div>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(320px, 1fr))", gap: "20px", marginBottom: "20px" }}>
        <div className="table-container" style={{ padding: "24px" }}>
          <div className="admin-panel-title admin-title-with-icon" style={{ marginBottom: "20px" }}>
            <Settings2 size={18} />
            营业状态控制
          </div>

          <div style={{ display: "grid", gap: "16px" }}>
            <div className="address-card" style={{ cursor: "default", minHeight: "92px" }}>
              <div className="address-content">
                <div className="address-title">接单通道</div>
                <div className="address-detail">当前接单状态</div>
                <div className="address-detail">{customerFacingHints.orderHint}</div>
              </div>
              <span className={`tag tag-${resolveOrderingTone(settings.orderingEnabled)}`}>{settings.orderingStatusLabel}</span>
            </div>

            <div className="address-card" style={{ cursor: "default", minHeight: "92px" }}>
              <div className="address-content">
                <div className="address-title">{settings.holidayNoticeTitle || "未设置公告"}</div>
                <div className="address-detail">{settings.holidayNoticeDesc || "当前没有配置前台公告。"}</div>
              </div>
              <button className="btn btn-outline" onClick={openNoticeForm}>
                <Bell size={16} />
                编辑公告
              </button>
            </div>

            <div className="address-card" style={{ cursor: "default", minHeight: "92px" }}>
              <div className="address-content">
                <div className="address-title">首页轮播图</div>
                <div className="address-detail">{countBannerImages(settings.bannerImages)} 张图片正在轮播</div>
                <div className="address-detail">{customerFacingHints.bannerHint}</div>
              </div>
              <button className="btn btn-outline" onClick={openBannerForm}>
                <ImageIcon size={16} />
                编辑轮播图
              </button>
            </div>

            <div className="address-card" style={{ cursor: "default", minHeight: "92px" }}>
              <div className="address-content">
                <div className="address-title">登录弹窗公告</div>
                <div className="address-detail">{settings.popupAnnouncementEnabled ? "已开启" : "已关闭"}</div>
                <div className="address-detail">{customerFacingHints.popupHint}</div>
              </div>
              <button className="btn btn-outline" onClick={openPopupForm}>
                <Megaphone size={16} />
                配置弹窗
              </button>
            </div>

            <div className="admin-card-section" style={{ background: "linear-gradient(180deg, rgba(255,247,237,0.78) 0%, rgba(255,255,255,0.92) 100%)", border: "1px solid #fed7aa" }}>
              <div className="admin-panel-note" style={{ color: "var(--warning-color)", fontWeight: 800 }}>当前提醒</div>
              <div style={{ color: "var(--text-main)", fontWeight: 700 }}>{riskSummary.primaryHint}</div>
              <div style={{ color: "var(--text-sub)", lineHeight: 1.7 }}>{riskSummary.secondaryHint}</div>
            </div>

            <div style={{ borderTop: "1px solid var(--border-color)", paddingTop: "18px" }}>
              <button className={settings.orderingEnabled ? "btn btn-danger" : "btn btn-primary"} style={{ width: "100%", height: "44px", fontSize: "15px" }} onClick={() => handleToggleOrdering().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}>
                {settings.orderingEnabled ? <AlertTriangle size={18} /> : <Power size={18} />}
                {settings.emergencyActionLabel}
              </button>
            </div>
          </div>
        </div>

        <div className="toolbar" style={{ margin: 0 }}>
          <div style={{ display: "grid", gridTemplateColumns: "1fr", gap: "12px" }}>
            <div className="address-card" style={{ cursor: "default", minHeight: "100px" }}>
              <div className="address-content">
                <div className="address-title">公告状态</div>
                <div className="address-detail">{settings.holidayNoticeTitle ? "已配置" : "未配置"}</div>
              </div>
            </div>
            <div className="address-card" style={{ cursor: "default", minHeight: "100px" }}>
              <div className="address-content">
                <div className="address-title">停单场景</div>
                <div className="address-detail">店休、爆单、停电、节假日</div>
              </div>
            </div>
            <div className="address-card" style={{ cursor: "default", minHeight: "100px" }}>
              <div className="address-content">
                <div className="address-title">执行检查</div>
                <div className="address-detail">接单状态、公告文案、恢复时间</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <div className="mobile-card-list">
        <div className="mobile-card">
          <div className="mobile-card-header">
            <span style={{ fontWeight: 700 }}>接单通道</span>
            <span className={`tag tag-${resolveOrderingTone(settings.orderingEnabled)}`}>{settings.orderingStatusLabel}</span>
          </div>
          <div className="mobile-card-row">
            <div className="mobile-card-label">公告</div>
            <div className="mobile-card-value">{settings.holidayNoticeTitle || "未配置公告"}</div>
          </div>
          <div className="mobile-card-row">
            <div className="mobile-card-label">状态</div>
            <div className="mobile-card-value">{riskSummary.primaryHint}</div>
          </div>
          <div className="mobile-card-footer">
            <button className="btn btn-outline" onClick={openNoticeForm}>配置公告</button>
            <button className={settings.orderingEnabled ? "btn btn-danger" : "btn btn-primary"} onClick={() => handleToggleOrdering().catch((err) => window.alert(err?.response?.data?.message || err.message || String(err)))}>
              {settings.orderingEnabled ? "暂停接单" : "恢复接单"}
            </button>
          </div>
        </div>
      </div>

      {isNoticeOpen && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">
              <span>配置首页顶部公告</span>
              <span className="modal-close" onClick={() => setIsNoticeOpen(false)}><X size={20} /></span>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label className="form-label"><span className="required">*</span>公告标题</label>
                <input className="form-control" value={noticeForm.title} onChange={e => setNoticeForm({...noticeForm, title: e.target.value})} placeholder="例如: 五一店休公告" />
              </div>
              <div className="form-group">
                <label className="form-label"><span className="required">*</span>公告内容</label>
                <textarea className="form-control" value={noticeForm.description} onChange={e => setNoticeForm({...noticeForm, description: e.target.value})} placeholder="例如: 5月1日至5月3日暂停接单，5月4日恢复。" />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={() => setIsNoticeOpen(false)}>取消</button>
              <button className="btn btn-primary" onClick={handleNoticeSubmit}>保存并展示</button>
            </div>
          </div>
        </div>
      )}

      {isBannerOpen && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">
              <span>编辑首页轮播图</span>
              <span className="modal-close" onClick={() => setIsBannerOpen(false)}><X size={20} /></span>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label className="form-label"><span className="required">*</span>图片 JSON 数组</label>
                <textarea className="form-control" style={{ height: '120px', fontFamily: 'monospace' }} value={bannerForm.bannerImages} onChange={e => setBannerForm({ bannerImages: e.target.value })} placeholder='例如: ["/assets/banner1.jpg", "/assets/banner2.jpg"]' />
                <p className="form-help">请输入合法的 JSON 数组，包含图片的完整路径或本地相对路径。当前生效 {bannerImages.length} 张。</p>
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={() => setIsBannerOpen(false)}>取消</button>
              <button className="btn btn-primary" onClick={handleBannerSubmit}>保存修改</button>
            </div>
          </div>
        </div>
      )}

      {isPopupOpen && (
        <div className="modal-overlay">
          <div className="modal-content">
            <div className="modal-header">
              <span>配置登录弹窗公告</span>
              <span className="modal-close" onClick={() => setIsPopupOpen(false)}><X size={20} /></span>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label className="form-label">启用状态</label>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                  <input type="checkbox" checked={popupForm.enabled} onChange={e => setPopupForm({ ...popupForm, enabled: e.target.checked })} />
                  <span>启用登录后自动弹窗</span>
                </div>
              </div>
              <div className="form-group">
                <label className="form-label">弹窗内容</label>
                <textarea className="form-control" style={{ height: '150px' }} value={popupForm.content} onChange={e => setPopupForm({ ...popupForm, content: e.target.value })} placeholder="输入公告详情内容，支持多行显示。" />
              </div>
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={() => setIsPopupOpen(false)}>取消</button>
              <button className="btn btn-primary" onClick={handlePopupSubmit}>保存配置</button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
