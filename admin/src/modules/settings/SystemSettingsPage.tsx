import React, { useEffect, useRef, useState } from "react";
import {
  fetchDispatchAiWorkbench,
  fetchOperationSettings,
  refreshDispatchAiBalance,
  runDispatchAiNow,
  updateBannerImages,
  updateDispatchAiWorkbench,
  updatePackageReminderSettings,
  updatePopupAnnouncement,
  uploadBannerImage
} from "../../shared/api/http";
import type { DispatchAiWorkbenchResponse, OperationSettingsResponse } from "../../shared/api/types";
import {
  Bot,
  Clock3,
  Image as ImageIcon,
  PlayCircle,
  Megaphone,
  Loader2,
  Wallet,
  X
} from "lucide-react";
import { SafeInput, SafeTextarea } from "../../shared/components/SafeInput";
import { SettingsModal } from "../../shared/components/SettingsModal";
import { toast } from "../../shared/components/Toast";
import {
  buildCustomerFacingSettingHints,
  countEnabledBannerImages,
  handleAdminImageFallback,
  normalizeBannerConfigs,
  resolveAdminMediaUrl,
  serializeBannerConfigs,
  summarizeDispatchAiLogMetadata,
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
const EMPTY_DISPATCH_AI_FORM = {
  autoScheduleEnabled: false,
  autoScheduleTime: "00:05",
  defaultStrategyMode: "NEAR_TO_FAR",
  anchorName: "五环天地",
  anchorAddress: "湖北省武汉市硚口区荟聚中心",
  aiEnabled: false,
  apiBaseUrl: "https://api.deepseek.com",
  apiKey: "",
  aiModel: "deepseek-chat",
  aiPromptTemplate: "",
  lowBalanceThreshold: "20.00"
};
const EMPTY_RUN_NOW_FORM = {
  serveDate: "",
  mealPeriod: "",
  areaCode: ""
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
  const [dispatchAiWorkbench, setDispatchAiWorkbench] = useState<DispatchAiWorkbenchResponse | null>(null);
  const [dispatchAiForm, setDispatchAiForm] = useState(EMPTY_DISPATCH_AI_FORM);
  const [runNowForm, setRunNowForm] = useState(EMPTY_RUN_NOW_FORM);

  const [modal, setModal] = useState<"banner" | "popup" | "packageReminder" | "dispatchAi" | null>(null);
  const [settingsLoading, setSettingsLoading] = useState(true);
  const [bannerUploading, setBannerUploading] = useState(false);
  const [popupSubmitting, setPopupSubmitting] = useState(false);
  const [packageSubmitting, setPackageSubmitting] = useState(false);
  const [bannerSubmitting, setBannerSubmitting] = useState(false);
  const [dispatchAiLoading, setDispatchAiLoading] = useState(true);
  const [dispatchAiSubmitting, setDispatchAiSubmitting] = useState(false);
  const [dispatchAiBalanceRefreshing, setDispatchAiBalanceRefreshing] = useState(false);
  const [dispatchAiRunSubmitting, setDispatchAiRunSubmitting] = useState(false);
  const [previewBannerImage, setPreviewBannerImage] = useState("");
  const bannerInputRef = useRef<HTMLInputElement | null>(null);

  const customerHints = buildCustomerFacingSettingHints(settings);
  const bannerConfigs = normalizeBannerConfigs(settings.bannerImages);
  const enabledBannerCount = countEnabledBannerImages(settings.bannerImages);

  useEffect(() => {
    reloadSettings().catch(showError);
    reloadDispatchAiWorkbench().catch(showError);
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

  async function reloadDispatchAiWorkbench() {
    setDispatchAiLoading(true);
    try {
      setDispatchAiWorkbench(await fetchDispatchAiWorkbench());
    } finally {
      setDispatchAiLoading(false);
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

  function openDispatchAi() {
    const settings = dispatchAiWorkbench?.settings;
    setDispatchAiForm(settings ? {
      autoScheduleEnabled: settings.autoScheduleEnabled,
      autoScheduleTime: settings.autoScheduleTime || "00:05",
      defaultStrategyMode: settings.defaultStrategyMode || "NEAR_TO_FAR",
      anchorName: settings.anchorName || "五环天地",
      anchorAddress: settings.anchorAddress || "",
      aiEnabled: settings.aiEnabled,
      apiBaseUrl: settings.apiBaseUrl || "https://api.deepseek.com",
      apiKey: "",
      aiModel: settings.aiModel || "deepseek-chat",
      aiPromptTemplate: settings.aiPromptTemplate || "",
      lowBalanceThreshold: settings.lowBalanceThreshold || "20.00"
    } : EMPTY_DISPATCH_AI_FORM);
    openModal("dispatchAi");
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

  async function submitDispatchAi() {
    const autoScheduleTime = normalizeTriggerTime(dispatchAiForm.autoScheduleTime);
    if (!autoScheduleTime || !dispatchAiForm.anchorName.trim() || !dispatchAiForm.anchorAddress.trim() || !dispatchAiForm.apiBaseUrl.trim() || !dispatchAiForm.aiModel.trim() || !dispatchAiForm.aiPromptTemplate.trim()) {
      toast("请完整填写自动时间、锚点、接口地址、模型和提示词", "error");
      return;
    }
    setDispatchAiSubmitting(true);
    try {
      const next = await updateDispatchAiWorkbench({
        autoScheduleEnabled: dispatchAiForm.autoScheduleEnabled,
        autoScheduleTime,
        defaultStrategyMode: dispatchAiForm.defaultStrategyMode,
        anchorName: dispatchAiForm.anchorName.trim(),
        anchorAddress: dispatchAiForm.anchorAddress.trim(),
        aiEnabled: dispatchAiForm.aiEnabled,
        apiBaseUrl: dispatchAiForm.apiBaseUrl.trim(),
        apiKey: dispatchAiForm.apiKey.trim(),
        aiModel: dispatchAiForm.aiModel.trim(),
        aiPromptTemplate: dispatchAiForm.aiPromptTemplate.trim(),
        lowBalanceThreshold: dispatchAiForm.lowBalanceThreshold.trim() || "20.00"
      });
      setDispatchAiWorkbench(next);
      closeModal();
      toast("AI 排线工作台配置已更新");
    } catch (err: any) {
      showError(err);
    } finally {
      setDispatchAiSubmitting(false);
    }
  }

  async function handleRefreshDispatchAiBalance() {
    setDispatchAiBalanceRefreshing(true);
    try {
      setDispatchAiWorkbench(await refreshDispatchAiBalance());
      toast("额度信息已刷新");
    } catch (err: any) {
      showError(err);
    } finally {
      setDispatchAiBalanceRefreshing(false);
    }
  }

  async function handleRunDispatchAiNow() {
    setDispatchAiRunSubmitting(true);
    try {
      const result = await runDispatchAiNow({
        serveDate: runNowForm.serveDate || undefined,
        mealPeriod: runNowForm.mealPeriod || undefined,
        areaCode: runNowForm.areaCode.trim() || undefined
      });
      await reloadDispatchAiWorkbench();
      toast(`${result.message}，成功 ${result.successCount} 个区域`);
    } catch (err: any) {
      showError(err);
    } finally {
      setDispatchAiRunSubmitting(false);
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
          <button className="btn btn-outline" onClick={openDispatchAi}>
            <Bot size={16} /> AI排线工作台
          </button>
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

      <div className="settings-cards" style={{ marginTop: 16 }}>
        <div className="settings-card settings-card--highlight">
          <div className="settings-card__title">
            <Bot size={18} /> AI 排线工作台
          </div>
          <div className="settings-card__body">
            {dispatchAiLoading ? (
              <div className="settings-card__detail">AI 排线工作台加载中...</div>
            ) : (
              <>
                <div className="settings-card__detail">
                  自动预排 {dispatchAiWorkbench?.settings.autoScheduleEnabled ? "已开启" : "已关闭"} · 每天 {dispatchAiWorkbench?.settings.autoScheduleTime || "00:05"} 执行
                </div>
                <div className="settings-card__detail settings-card__detail--sub">
                  默认策略 {dispatchAiWorkbench?.settings.defaultStrategyMode === "FAR_TO_NEAR" ? "远→近" : "近→远"} · 锚点 {dispatchAiWorkbench?.settings.anchorName || "-"}
                </div>
                <div className="settings-card__detail settings-card__detail--sub">
                  AI {dispatchAiWorkbench?.settings.aiEnabled ? "已启用" : "已关闭"} · 模型 {dispatchAiWorkbench?.settings.aiModel || "-"}
                </div>
                <div className="settings-card__detail settings-card__detail--sub">
                  Key {dispatchAiWorkbench?.settings.maskedApiKey || "未配置"}
                </div>
              </>
            )}
          </div>
          <div className="settings-card__actions" style={{ display: "flex", gap: 8 }}>
            <button className="btn btn-outline" style={{ flex: 1 }} onClick={openDispatchAi}>
              配置工作台
            </button>
            <button className="btn btn-primary" style={{ flex: 1 }} onClick={handleRunDispatchAiNow} disabled={dispatchAiRunSubmitting}>
              <PlayCircle size={16} /> {dispatchAiRunSubmitting ? "执行中..." : "立刻执行"}
            </button>
          </div>
        </div>

        <div className="settings-card">
          <div className="settings-card__title">
            <Wallet size={18} /> API 额度
          </div>
          <div className="settings-card__body">
            <div className="settings-card__detail">
              可用状态：{dispatchAiWorkbench?.settings.balanceAvailable ? "可调用" : "余额不足或未刷新"}
            </div>
            <div className="settings-card__detail settings-card__detail--sub">
              总余额 {dispatchAiWorkbench?.settings.totalBalance || "0"} {dispatchAiWorkbench?.settings.balanceCurrency || "CNY"}
            </div>
            <div className="settings-card__detail settings-card__detail--sub">
              充值余额 {dispatchAiWorkbench?.settings.toppedUpBalance || "0"} · 赠送余额 {dispatchAiWorkbench?.settings.grantedBalance || "0"}
            </div>
            <div className="settings-card__detail settings-card__detail--sub">
              最近刷新 {dispatchAiWorkbench?.settings.balanceCheckedAt || "未刷新"}
            </div>
          </div>
          <div className="settings-card__actions">
            <button className="btn btn-outline" style={{ width: "100%" }} onClick={handleRefreshDispatchAiBalance} disabled={dispatchAiBalanceRefreshing}>
              {dispatchAiBalanceRefreshing ? "刷新中..." : "刷新额度"}
            </button>
          </div>
        </div>

        <div className="settings-card">
          <div className="settings-card__title">
            <Clock3 size={18} /> 测试执行
          </div>
          <div className="settings-card__body">
            <div className="form-group">
              <label className="form-label">执行日期</label>
              <SafeInput className="form-control" type="date" value={runNowForm.serveDate} onValueChange={(value) => setRunNowForm({ ...runNowForm, serveDate: value })} />
            </div>
            <div className="form-group">
              <label className="form-label">餐期</label>
              <select className="form-control" value={runNowForm.mealPeriod} onChange={(e) => setRunNowForm({ ...runNowForm, mealPeriod: e.target.value })}>
                <option value="">午餐+晚餐</option>
                <option value="LUNCH">午餐</option>
                <option value="DINNER">晚餐</option>
              </select>
            </div>
            <div className="form-group">
              <label className="form-label">指定区域</label>
              <SafeInput className="form-control" value={runNowForm.areaCode} onValueChange={(value) => setRunNowForm({ ...runNowForm, areaCode: value })} placeholder="留空表示全部区域" />
            </div>
          </div>
          <div className="settings-card__actions">
            <button className="btn btn-primary" style={{ width: "100%" }} onClick={handleRunDispatchAiNow} disabled={dispatchAiRunSubmitting}>
              <PlayCircle size={16} /> {dispatchAiRunSubmitting ? "执行中..." : "立刻执行测试"}
            </button>
          </div>
        </div>
      </div>

      <div className="admin-panel" style={{ marginTop: 16 }}>
        <div className="page-header" style={{ marginBottom: 12 }}>
          <div>
            <h3 className="page-title" style={{ fontSize: 18 }}>AI 排线日志</h3>
            <p className="page-subtitle">查看每天什么时候开始排、怎么排、结果是否成功。</p>
          </div>
        </div>
        <div style={{ display: "grid", gap: 12 }}>
          {(dispatchAiWorkbench?.recentLogs || []).map((log) => (
            (() => {
              const metadataSummary = summarizeDispatchAiLogMetadata(log.metadataJson || "");
              return (
                <div key={log.id} className="settings-card" style={{ marginBottom: 0 }}>
                  <div className="settings-card__body">
                    <div className="settings-card__detail">
                      #{log.id} · {log.triggerSource} · {log.serveDate || "-"} {log.mealPeriod || "-"} · 区域 {log.areaCode || "全部"}
                    </div>
                    <div className="settings-card__detail settings-card__detail--sub">
                      状态 {log.status} · 来源 {log.suggestionSource || "-"} · 执行人 {log.executedBy || "-"}
                    </div>
                    <div className="settings-card__detail settings-card__detail--sub">
                      {log.message || "无消息"}{log.reasonSummary ? ` · ${log.reasonSummary}` : ""}
                    </div>
                    {metadataSummary ? (
                      <div className="settings-card__detail settings-card__detail--sub">
                        共 {metadataSummary.orderCount} 单 · AI 修正阶段已执行
                      </div>
                    ) : null}
                    <div className="settings-card__detail settings-card__detail--sub">
                      开始 {log.startedAt || "-"} · 结束 {log.finishedAt || "-"}
                    </div>
                  </div>
                </div>
              );
            })()
          ))}
          {!dispatchAiLoading && !(dispatchAiWorkbench?.recentLogs || []).length ? (
            <div className="settings-card__detail">暂无 AI 排线日志</div>
          ) : null}
        </div>
      </div>

      <SettingsModal
        open={modal === "dispatchAi"}
        title="AI 排线工作台"
        onClose={closeModal}
        onSubmit={submitDispatchAi}
        submitLabel="保存配置"
        submitting={dispatchAiSubmitting}
      >
        <div className="form-group">
          <label className="form-label">启用自动预排</label>
          <div className="toggle-row">
            <input type="checkbox" checked={dispatchAiForm.autoScheduleEnabled} onChange={(e) => setDispatchAiForm({ ...dispatchAiForm, autoScheduleEnabled: e.target.checked })} />
            <span>{dispatchAiForm.autoScheduleEnabled ? "到点后自动为次日派单排序" : "关闭后仅可手动立刻执行"}</span>
          </div>
        </div>
        <div className="form-group">
          <label className="form-label">自动执行时间</label>
          <SafeInput className="form-control" type="time" value={dispatchAiForm.autoScheduleTime} onValueChange={(value) => setDispatchAiForm({ ...dispatchAiForm, autoScheduleTime: value })} />
        </div>
        <div className="form-group">
          <label className="form-label">默认策略</label>
          <select className="form-control" value={dispatchAiForm.defaultStrategyMode} onChange={(e) => setDispatchAiForm({ ...dispatchAiForm, defaultStrategyMode: e.target.value })}>
            <option value="NEAR_TO_FAR">近→远</option>
            <option value="FAR_TO_NEAR">远→近</option>
          </select>
        </div>
        <div className="form-group">
          <label className="form-label">锚点名称</label>
          <SafeInput className="form-control" value={dispatchAiForm.anchorName} onValueChange={(value) => setDispatchAiForm({ ...dispatchAiForm, anchorName: value })} />
        </div>
        <div className="form-group">
          <label className="form-label">锚点地址</label>
          <SafeTextarea className="form-control" value={dispatchAiForm.anchorAddress} onValueChange={(value) => setDispatchAiForm({ ...dispatchAiForm, anchorAddress: value })} />
        </div>
        <div className="form-group">
          <label className="form-label">启用 AI 修正</label>
          <div className="toggle-row">
            <input type="checkbox" checked={dispatchAiForm.aiEnabled} onChange={(e) => setDispatchAiForm({ ...dispatchAiForm, aiEnabled: e.target.checked })} />
            <span>{dispatchAiForm.aiEnabled ? "规则算法后会再走一次大模型轻量修正" : "仅使用规则排序"}</span>
          </div>
        </div>
        <div className="form-group">
          <label className="form-label">API Base URL</label>
          <SafeInput className="form-control" value={dispatchAiForm.apiBaseUrl} onValueChange={(value) => setDispatchAiForm({ ...dispatchAiForm, apiBaseUrl: value })} />
        </div>
        <div className="form-group">
          <label className="form-label">API Key</label>
          <SafeInput className="form-control" value={dispatchAiForm.apiKey} onValueChange={(value) => setDispatchAiForm({ ...dispatchAiForm, apiKey: value })} placeholder={dispatchAiWorkbench?.settings.maskedApiKey ? `已配置：${dispatchAiWorkbench.settings.maskedApiKey}，留空表示不修改` : "请输入 API Key"} />
        </div>
        <div className="form-group">
          <label className="form-label">模型名</label>
          <SafeInput className="form-control" value={dispatchAiForm.aiModel} onValueChange={(value) => setDispatchAiForm({ ...dispatchAiForm, aiModel: value })} />
        </div>
        <div className="form-group">
          <label className="form-label">低余额提醒阈值</label>
          <SafeInput className="form-control" value={dispatchAiForm.lowBalanceThreshold} onValueChange={(value) => setDispatchAiForm({ ...dispatchAiForm, lowBalanceThreshold: value })} />
        </div>
        <div className="form-group">
          <label className="form-label">AI 提示词</label>
          <SafeTextarea className="form-control" style={{ height: 180 }} value={dispatchAiForm.aiPromptTemplate} onValueChange={(value) => setDispatchAiForm({ ...dispatchAiForm, aiPromptTemplate: value })} />
        </div>
      </SettingsModal>

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
                onError={handleAdminImageFallback}
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
