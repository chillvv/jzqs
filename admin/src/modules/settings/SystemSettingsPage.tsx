import React, { useEffect, useRef, useState } from "react";
import {
  fetchOperationSettings,
  updateBannerImages,
  updatePopupAnnouncement,
  uploadBannerImage
} from "../../shared/api/http";
import type { OperationSettingsResponse } from "../../shared/api/types";
import {
  Image as ImageIcon,
  Megaphone,
  Loader2,
  X
} from "lucide-react";
import { SettingsModal } from "../../shared/components/SettingsModal";
import {
  buildCustomerFacingSettingHints,
  countBannerImages,
  countEnabledBannerImages,
  normalizeBannerConfigs,
  resolveAdminMediaUrl,
  serializeBannerConfigs,
  type BannerConfigItem
} from "./systemSettingsPage.helpers";

const EMPTY_POPUP = { title: "", description: "", enabled: false, content: "" };

export function SystemSettingsPage() {
  const [settings, setSettings] = useState<OperationSettingsResponse>({
    orderingEnabled: true,
    orderingStatusLabel: "",
    holidayNoticeTitle: "锁定公告",
    holidayNoticeDesc: "用户进入后仅可查看锁定公告内容",
    emergencyActionLabel: "",
    bannerImages: "[{\"imageUrl\":\"../../assets/hero-new.jpg\",\"enabled\":true}]",
    bannerIntervalSeconds: 3,
    popupAnnouncementEnabled: false,
    popupAnnouncementContent: ""
  });

  const [popupForm, setPopupForm] = useState(EMPTY_POPUP);
  const [bannerForm, setBannerForm] = useState<BannerConfigItem[]>([]);
  const [bannerIntervalSeconds, setBannerIntervalSeconds] = useState("3");

  const [modal, setModal] = useState<"banner" | "popup" | null>(null);
  const [bannerUploading, setBannerUploading] = useState(false);
  const [popupSubmitting, setPopupSubmitting] = useState(false);
  const [bannerSubmitting, setBannerSubmitting] = useState(false);
  const [previewBannerImage, setPreviewBannerImage] = useState("");
  const bannerInputRef = useRef<HTMLInputElement | null>(null);

  const customerHints = buildCustomerFacingSettingHints(settings);
  const bannerConfigs = normalizeBannerConfigs(settings.bannerImages);
  const enabledBannerCount = countEnabledBannerImages(settings.bannerImages);

  useEffect(() => {
    reloadSettings().catch(showError);
  }, []);

  function showError(err: any) {
    window.alert(err?.response?.data?.message || err.message || String(err));
  }

  async function reloadSettings() {
    setSettings(await fetchOperationSettings());
  }

  function openModal(name: typeof modal) {
    setModal(name);
  }

  function closeModal() {
    setModal(null);
  }

  function openPopup() {
    setPopupForm({
      title: settings.holidayNoticeTitle || "",
      description: settings.holidayNoticeDesc || "",
      enabled: settings.popupAnnouncementEnabled,
      content: settings.popupAnnouncementContent || ""
    });
    openModal("popup");
  }

  async function submitPopup() {
    const title = popupForm.title.trim();
    const description = popupForm.description.trim();
    const content = popupForm.content.trim();
    if (popupForm.enabled && (!title || (!description && !content))) {
      window.alert("启用锁定公告时请至少填写主文和正文");
      return;
    }
    setPopupSubmitting(true);
    try {
      setSettings(await updatePopupAnnouncement({
        title,
        description,
        enabled: popupForm.enabled,
        content
      }));
      closeModal();
    } catch (err: any) {
      showError(err);
    } finally {
      setPopupSubmitting(false);
    }
  }

  function openBanner() {
    setBannerForm(normalizeBannerConfigs(settings.bannerImages));
    setBannerIntervalSeconds(String(settings.bannerIntervalSeconds || 3));
    openModal("banner");
  }

  async function submitBanner() {
    const validItems = bannerForm.filter((item) => item.imageUrl.trim().length > 0);
    if (!validItems.length) {
      window.alert("请至少保留一张轮播图");
      return;
    }
    setBannerSubmitting(true);
    try {
      setSettings(await updateBannerImages(serializeBannerConfigs(validItems), Math.max(1, Number(bannerIntervalSeconds) || 3)));
      closeModal();
    } catch (err: any) {
      showError(err);
    } finally {
      setBannerSubmitting(false);
    }
  }

  async function handleBannerFilesChange(event: React.ChangeEvent<HTMLInputElement>) {
    const files = Array.from(event.target.files || []);
    if (!files.length) {
      return;
    }
    setBannerUploading(true);
    try {
      const uploaded = await Promise.all(files.map((file) => uploadBannerImage(file)));
      setBannerForm((prev) => [
        ...prev,
        ...uploaded.map((item) => ({
          imageUrl: item.url,
          enabled: true
        }))
      ]);
    } catch (err: any) {
      showError(err);
    } finally {
      setBannerUploading(false);
      event.target.value = "";
    }
  }

  function updateBannerField<K extends keyof BannerConfigItem>(index: number, field: K, value: BannerConfigItem[K]) {
    setBannerForm((prev) => prev.map((item, currentIndex) => (
      currentIndex === index
        ? { ...item, [field]: value }
        : item
    )));
  }

  function moveBanner(index: number, direction: -1 | 1) {
    setBannerForm((prev) => {
      const targetIndex = index + direction;
      if (targetIndex < 0 || targetIndex >= prev.length) {
        return prev;
      }
      const next = [...prev];
      const [current] = next.splice(index, 1);
      next.splice(targetIndex, 0, current);
      return next;
    });
  }

  function removeBannerImage(index: number) {
    setBannerForm((prev) => prev.filter((_, currentIndex) => currentIndex !== index));
  }

  return (
    <>
      <div className="page-header">
        <div>
          <h2 className="page-title">系统设置</h2>
          <p className="page-subtitle">锁定公告 · 轮播图</p>
        </div>
        <div className="page-header__actions">
          <button className="btn btn-outline" onClick={openPopup}>
            <Megaphone size={16} /> 锁定公告
          </button>
        </div>
      </div>

      <div className="stat-row">
        <div className="stat-card">
          <div className="stat-title">锁定公告</div>
          <div className="stat-val">
            <span className={settings.popupAnnouncementEnabled ? "tag tag-red" : "tag tag-gray"}>
              {settings.popupAnnouncementEnabled ? "已锁定" : "未启用"}
            </span>
          </div>
          <div className="stat-footer">
            {settings.popupAnnouncementEnabled
              ? settings.holidayNoticeTitle || "用户当前只能查看公告"
              : "用户可正常使用小程序"}
          </div>
        </div>
        <div className="stat-card">
          <div className="stat-title">轮播图</div>
          <div className="stat-val">{countBannerImages(settings.bannerImages)} <span>张</span></div>
          <div className="stat-footer">启用中 {enabledBannerCount} 张</div>
        </div>
      </div>

      <div className="settings-cards">
        <div className="settings-card settings-card--highlight">
          <div className="settings-card__title">
            <Megaphone size={18} /> 锁定公告
          </div>
          <div className="settings-card__body">
            <span className={`tag ${settings.popupAnnouncementEnabled ? "tag-red" : "tag-gray"}`}>
              {settings.popupAnnouncementEnabled ? "用户仅可查看公告" : "当前未锁定"}
            </span>
            <div className="settings-card__detail">
              {settings.holidayNoticeTitle || "未设置锁定公告主文"}
            </div>
            <div className="settings-card__detail settings-card__detail--sub">
              {settings.holidayNoticeDesc || "未设置锁定公告摘要"}
            </div>
            <span className="settings-card__hint">{customerHints.popupHint}</span>
          </div>
          <div className="settings-card__actions">
            <button className="btn btn-outline" style={{ width: "100%" }} onClick={openPopup}>
              配置锁定公告
            </button>
          </div>
        </div>

        <div className="settings-card">
          <div className="settings-card__title">
            <ImageIcon size={18} /> 首页轮播图
          </div>
          <div className="settings-card__body">
            <div className="banner-preview-grid">
              {bannerConfigs.slice(0, 3).map((item, index) => (
                <div key={`${item.imageUrl}-${index}`} className="banner-preview-card">
                  <img
                    src={resolveAdminMediaUrl(item.imageUrl)}
                    className="banner-preview-thumb"
                    alt={`轮播图 ${index + 1}`}
                    onClick={() => setPreviewBannerImage(item.imageUrl)}
                  />
                  <div className="banner-preview-card__meta">
                    <div className="banner-preview-card__title">{`轮播图 ${index + 1}`}</div>
                    <div className="banner-preview-card__hint">{item.enabled ? "启用中 · 点击图片查看大图" : "已停用 · 点击图片查看大图"}</div>
                  </div>
                </div>
              ))}
            </div>
            <div className="settings-card__detail">
              启用中 {enabledBannerCount} 张，轮播间隔 {Math.max(1, settings.bannerIntervalSeconds || 3)} 秒
            </div>
            <div className="settings-card__detail settings-card__detail--sub">
              首页只展示图片，用户点击后仅预览大图。
            </div>
            <span className="settings-card__hint">
              {customerHints.bannerHint}
            </span>
          </div>
          <div className="settings-card__actions">
            <button className="btn btn-outline" style={{ width: "100%" }} onClick={openBanner}>
              管理轮播图
            </button>
          </div>
        </div>
      </div>

      <SettingsModal
        open={modal === "popup"}
        title="配置锁定公告"
        onClose={closeModal}
        onSubmit={submitPopup}
        submitLabel="保存配置"
        submitting={popupSubmitting}
      >
        <div className="form-group">
          <label className="form-label">启用锁定公告</label>
          <div className="toggle-row">
            <input
              type="checkbox"
              checked={popupForm.enabled}
              onChange={(e) => setPopupForm({ ...popupForm, enabled: e.target.checked })}
            />
            <span>
              {popupForm.enabled
                ? "开启后用户进入小程序时只能查看公告"
                : "关闭后用户可正常浏览和下单"}
            </span>
          </div>
          {popupForm.enabled ? (
            <button
              className="btn btn-outline"
              style={{ marginTop: 12 }}
              onClick={() => setPopupForm((current) => ({ ...current, enabled: false }))}
            >
              关闭公告
            </button>
          ) : null}
        </div>
        <div className="form-group">
          <label className="form-label">
            <span className="required">*</span>公告主文
          </label>
          <input
            className="form-control"
            value={popupForm.title}
            onChange={(e) => setPopupForm({ ...popupForm, title: e.target.value })}
            placeholder="例如：门店公告通知"
          />
        </div>
        <div className="form-group">
          <label className="form-label">公告补充</label>
          <textarea
            className="form-control"
            value={popupForm.description}
            onChange={(e) => setPopupForm({ ...popupForm, description: e.target.value })}
            placeholder="用于后台摘要和锁定页补充展示。"
          ></textarea>
        </div>
        <div className="form-group">
          <label className="form-label">锁定页正文</label>
          <textarea
            className="form-control"
            style={{ height: 150 }}
            value={popupForm.content}
            onChange={(e) => setPopupForm({ ...popupForm, content: e.target.value })}
            placeholder="支持多行，不填写时默认使用主文和补充拼接。"
          ></textarea>
        </div>
      </SettingsModal>

      <SettingsModal
        open={modal === "banner"}
        title="管理首页轮播图"
        onClose={closeModal}
        onSubmit={submitBanner}
        submitLabel="保存修改"
        submitting={bannerSubmitting}
      >
        <div className="settings-form-callout">
          轮播图只保留图片、启用状态、顺序和轮播秒数；顾客点击后仅预览大图。点击图片查看大图。
        </div>
        <div className="form-group">
          <label className="form-label">轮播秒数</label>
          <input
            className="form-control"
            type="number"
            min="1"
            value={bannerIntervalSeconds}
            onChange={(e) => setBannerIntervalSeconds(e.target.value)}
          />
        </div>
        <div className="form-group">
          <label className="form-label">
            <span className="required">*</span>上传轮播图
          </label>
          <input
            ref={bannerInputRef}
            type="file"
            accept="image/*"
            multiple
            style={{ display: "none" }}
            onChange={handleBannerFilesChange}
          />
          <div style={{ display: "flex", gap: 12, alignItems: "center", flexWrap: "wrap" }}>
            <button
              className="btn btn-outline"
              onClick={() => bannerInputRef.current?.click()}
              disabled={bannerUploading}
            >
              {bannerUploading ? <Loader2 size={16} /> : <ImageIcon size={16} />}
              {bannerUploading ? "上传中..." : "选择图片"}
            </button>
            <span style={{ color: "var(--text-sub)", fontSize: 13 }}>
              支持多张，建议横图；可配置启用状态和顺序
            </span>
          </div>
        </div>
        <div className="banner-grid">
          {bannerForm.map((item, index) => (
            <div key={`${item.imageUrl}-${index}`} className="banner-grid__item">
              <img
                src={resolveAdminMediaUrl(item.imageUrl)}
                alt={`轮播图 ${index + 1}`}
                onClick={() => setPreviewBannerImage(item.imageUrl)}
              />
              <div className="banner-grid__item-footer">
                <span>第 {index + 1} 张</span>
                <span className={`tag ${item.enabled ? "tag-green" : "tag-gray"}`}>
                  {item.enabled ? "启用" : "停用"}
                </span>
              </div>
              <div className="form-group" style={{ marginTop: 12 }}>
                <label className="form-label">图片地址</label>
                <input
                  className="form-control"
                  value={item.imageUrl}
                  onChange={(e) => updateBannerField(index, "imageUrl", e.target.value)}
                  placeholder="/uploads/settings-banners/..."
                />
              </div>
              <div className="toggle-row" style={{ marginBottom: 12 }}>
                <input
                  type="checkbox"
                  checked={item.enabled}
                  onChange={(e) => updateBannerField(index, "enabled", e.target.checked)}
                />
                <span>启用这张轮播图</span>
              </div>
              <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                <button className="btn btn-outline btn-sm" onClick={() => moveBanner(index, -1)} disabled={index === 0}>
                  上移
                </button>
                <button className="btn btn-outline btn-sm" onClick={() => moveBanner(index, 1)} disabled={index === bannerForm.length - 1}>
                  下移
                </button>
                <button className="btn btn-outline btn-sm" onClick={() => removeBannerImage(index)}>
                  删除
                </button>
              </div>
            </div>
          ))}
          {!bannerForm.length && (
            <div className="banner-grid__empty">暂未上传轮播图</div>
          )}
        </div>
      </SettingsModal>

      {previewBannerImage ? (
        <div className="modal-overlay" onClick={() => setPreviewBannerImage("")}>
          <div className="modal-content modal-content--banner-preview" onClick={(event) => event.stopPropagation()}>
            <div className="modal-header">
              <span>轮播图大图预览</span>
              <span className="modal-close" onClick={() => setPreviewBannerImage("")}><X size={18} /></span>
            </div>
            <div className="modal-body">
              <img
                src={resolveAdminMediaUrl(previewBannerImage)}
                alt="轮播图大图预览"
                className="banner-preview-dialog__image"
              />
            </div>
            <div className="modal-footer">
              <button className="btn btn-outline" onClick={() => setPreviewBannerImage("")}>关闭预览</button>
            </div>
          </div>
        </div>
      ) : null}
    </>
  );
}
