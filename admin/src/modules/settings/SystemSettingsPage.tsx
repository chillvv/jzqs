import React, { useEffect, useRef, useState } from "react";
import {
  fetchOperationSettings,
  updateBannerImages,
  updatePackageReminderSettings,
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
import { SafeInput, SafeTextarea } from "../../shared/components/SafeInput";
import { SettingsModal } from "../../shared/components/SettingsModal";
import { toast } from "../../shared/components/Toast";
import {
  buildCustomerFacingSettingHints,
  countEnabledBannerImages,
  normalizeBannerConfigs,
  serializeBannerConfigs,
  type BannerConfigItem
} from "./systemSettingsPage.helpers";
import { SystemSettingsOverview } from "./components/SystemSettingsOverview";
import { BannerPreviewModal } from "./components/BannerPreviewModal";

const EMPTY_POPUP = { title: "", description: "", enabled: false, content: "" };
const EMPTY_PACKAGE_REMINDER = {
  packageExpiryReminderDays: "7",
  packageLowBalanceThreshold: "3",
  mealReminderPopupEnabled: true,
  deliverySubscribeEnabled: true,
  deliverySubscribeLunchTime: "11:30",
  deliverySubscribeDinnerTime: "17:30"
};

function normalizeTriggerTime(value: string) {
  const normalized = String(value || "").trim();
  return /^([01]\d|2[0-3]):([0-5]\d)$/.test(normalized) ? normalized : "";
}

export function SystemSettingsPage() {
  const [settings, setSettings] = useState<OperationSettingsResponse>({
    orderingEnabled: true,
    orderingStatusLabel: "",
    holidayNoticeTitle: "锁定公告",
    holidayNoticeDesc: "用户进入后仅可查看锁定公告内容",
    emergencyActionLabel: "",
    bannerImages: "[{\"imageUrl\":\"../../assets/hero-new.jpg\",\"enabled\":true}]",
    bannerIntervalSeconds: 3,
    packageExpiryReminderDays: 7,
    packageLowBalanceThreshold: 3,
    mealReminderPopupEnabled: true,
    deliverySubscribeEnabled: true,
    deliverySubscribeLunchTime: "11:30",
    deliverySubscribeDinnerTime: "17:30",
    popupAnnouncementEnabled: false,
    popupAnnouncementContent: ""
  });

  const [popupForm, setPopupForm] = useState(EMPTY_POPUP);
  const [bannerForm, setBannerForm] = useState<BannerConfigItem[]>([]);
  const [bannerIntervalSeconds, setBannerIntervalSeconds] = useState("3");
  const [packageReminderForm, setPackageReminderForm] = useState(EMPTY_PACKAGE_REMINDER);

  const [modal, setModal] = useState<"banner" | "popup" | "packageReminder" | null>(null);
  const [settingsLoading, setSettingsLoading] = useState(true);
  const [bannerUploading, setBannerUploading] = useState(false);
  const [popupSubmitting, setPopupSubmitting] = useState(false);
  const [packageSubmitting, setPackageSubmitting] = useState(false);
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
    toast(err?.response?.data?.message || err.message || String(err), "error");
  }

  async function reloadSettings() {
    setSettingsLoading(true);
    try {
      setSettings(await fetchOperationSettings());
    } finally {
      setSettingsLoading(false);
    }
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
      toast("启用锁定公告时请至少填写主文和正文", "error");
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
      toast("锁定公告已更新");
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

  function openPackageReminder() {
    setPackageReminderForm({
      packageExpiryReminderDays: String(settings.packageExpiryReminderDays || 7),
      packageLowBalanceThreshold: String(settings.packageLowBalanceThreshold || 3),
      mealReminderPopupEnabled: settings.mealReminderPopupEnabled !== false,
      deliverySubscribeEnabled: settings.deliverySubscribeEnabled !== false,
      deliverySubscribeLunchTime: normalizeTriggerTime(settings.deliverySubscribeLunchTime || "11:30") || "11:30",
      deliverySubscribeDinnerTime: normalizeTriggerTime(settings.deliverySubscribeDinnerTime || "17:30") || "17:30"
    });
    openModal("packageReminder");
  }

  async function submitPackageReminder() {
    const packageExpiryReminderDays = Math.max(1, Number(packageReminderForm.packageExpiryReminderDays) || 0);
    const packageLowBalanceThreshold = Math.max(1, Number(packageReminderForm.packageLowBalanceThreshold) || 0);
    const deliverySubscribeLunchTime = normalizeTriggerTime(packageReminderForm.deliverySubscribeLunchTime);
    const deliverySubscribeDinnerTime = normalizeTriggerTime(packageReminderForm.deliverySubscribeDinnerTime);
    if (!packageExpiryReminderDays || !packageLowBalanceThreshold || !deliverySubscribeLunchTime || !deliverySubscribeDinnerTime) {
      toast("请填写有效的提醒阈值以及午餐、晚餐订阅时间", "error");
      return;
    }
    setPackageSubmitting(true);
    try {
      setSettings(await updatePackageReminderSettings({
        packageExpiryReminderDays,
        packageLowBalanceThreshold,
        mealReminderPopupEnabled: packageReminderForm.mealReminderPopupEnabled,
        deliverySubscribeEnabled: packageReminderForm.deliverySubscribeEnabled,
        deliverySubscribeLunchTime,
        deliverySubscribeDinnerTime
      }));
      closeModal();
      toast("餐包提醒已更新");
    } catch (err: any) {
      showError(err);
    } finally {
      setPackageSubmitting(false);
    }
  }

  async function submitBanner() {
    const validItems = bannerForm.filter((item) => item.imageUrl.trim().length > 0);
    if (!validItems.length) {
      toast("请至少保留一张轮播图", "error");
      return;
    }
    setBannerSubmitting(true);
    try {
      setSettings(await updateBannerImages(serializeBannerConfigs(validItems), Math.max(1, Number(bannerIntervalSeconds) || 3)));
      closeModal();
      toast("轮播图配置已更新");
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
      toast(`已上传 ${uploaded.length} 张轮播图`);
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

      {settingsLoading ? (
        <div className="admin-panel" style={{ marginBottom: 16, color: "var(--text-muted)" }}>
          系统设置加载中...
        </div>
      ) : null}

      <SystemSettingsOverview
        settings={settings}
        customerHints={customerHints}
        bannerConfigs={bannerConfigs}
        enabledBannerCount={enabledBannerCount}
        onOpenPopup={openPopup}
        onOpenPackageReminder={openPackageReminder}
        onOpenBanner={openBanner}
        onPreviewBanner={setPreviewBannerImage}
      />

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
          <SafeInput
            className="form-control"
            value={popupForm.title}
            onValueChange={(value) => setPopupForm({ ...popupForm, title: value })}
            placeholder="例如：门店公告通知"
          />
        </div>
        <div className="form-group">
          <label className="form-label">公告补充</label>
          <SafeTextarea
            className="form-control"
            value={popupForm.description}
            onValueChange={(value) => setPopupForm({ ...popupForm, description: value })}
            placeholder="用于后台摘要和锁定页补充展示。"
          />
        </div>
        <div className="form-group">
          <label className="form-label">锁定页正文</label>
          <SafeTextarea
            className="form-control"
            style={{ height: 150 }}
            value={popupForm.content}
            onValueChange={(value) => setPopupForm({ ...popupForm, content: value })}
            placeholder="支持多行，不填写时默认使用主文和补充拼接。"
          />
        </div>
      </SettingsModal>

      <SettingsModal
        open={modal === "packageReminder"}
        title="配置餐包提醒"
        onClose={closeModal}
        onSubmit={submitPackageReminder}
        submitLabel="保存策略"
        submitting={packageSubmitting}
      >
        <div className="form-group">
          <label className="form-label">到期提醒提前天数</label>
          <SafeInput
            className="form-control"
            type="number"
            min="1"
            value={packageReminderForm.packageExpiryReminderDays}
            onValueChange={(value) => setPackageReminderForm({ ...packageReminderForm, packageExpiryReminderDays: value })}
          />
        </div>
        <div className="form-group">
          <label className="form-label">低餐量提醒阈值</label>
          <SafeInput
            className="form-control"
            type="number"
            min="1"
            value={packageReminderForm.packageLowBalanceThreshold}
            onValueChange={(value) => setPackageReminderForm({ ...packageReminderForm, packageLowBalanceThreshold: value })}
          />
        </div>
        <div className="form-group">
          <label className="form-label">顾客上线弹窗提醒</label>
          <div className="toggle-row">
            <input
              type="checkbox"
              checked={packageReminderForm.mealReminderPopupEnabled}
              onChange={(e) => setPackageReminderForm({ ...packageReminderForm, mealReminderPopupEnabled: e.target.checked })}
            />
            <span>{packageReminderForm.mealReminderPopupEnabled ? "开启后顾客打开小程序会收到一次温和提醒" : "关闭后顾客上线时不再主动弹出提醒"}</span>
          </div>
        </div>
        <div className="form-group">
          <label className="form-label">订阅通知发送</label>
          <div className="toggle-row">
            <input
              type="checkbox"
              checked={packageReminderForm.deliverySubscribeEnabled}
              onChange={(e) => setPackageReminderForm({ ...packageReminderForm, deliverySubscribeEnabled: e.target.checked })}
            />
            <span>{packageReminderForm.deliverySubscribeEnabled ? "开启后按午餐、晚餐各自时间扫描并发送顾客订阅通知" : "关闭后不发送顾客订阅通知"}</span>
          </div>
        </div>
        <div className="form-group">
          <label className="form-label">午餐订阅时间</label>
          <SafeInput
            className="form-control"
            type="time"
            value={packageReminderForm.deliverySubscribeLunchTime}
            onValueChange={(value) => setPackageReminderForm({ ...packageReminderForm, deliverySubscribeLunchTime: value })}
          />
          <div className="settings-card__detail settings-card__detail--sub" style={{ marginTop: 8 }}>
            仅午餐订阅消息在命中该时间时发送，例如 11:30。
          </div>
        </div>
        <div className="form-group">
          <label className="form-label">晚餐订阅时间</label>
          <SafeInput
            className="form-control"
            type="time"
            value={packageReminderForm.deliverySubscribeDinnerTime}
            onValueChange={(value) => setPackageReminderForm({ ...packageReminderForm, deliverySubscribeDinnerTime: value })}
          />
          <div className="settings-card__detail settings-card__detail--sub" style={{ marginTop: 8 }}>
            仅晚餐订阅消息在命中该时间时发送，例如 17:30。
          </div>
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
          <SafeInput
            className="form-control"
            type="number"
            min="1"
            value={bannerIntervalSeconds}
            onValueChange={setBannerIntervalSeconds}
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
              <div style={{ marginTop: 12, color: "var(--text-sub)", fontSize: 12 }}>
                点击图片可预览大图；上传后会直接用于顾客端轮播展示。
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

      <BannerPreviewModal imageUrl={previewBannerImage} onClose={() => setPreviewBannerImage("")} />
    </>
  );
}
