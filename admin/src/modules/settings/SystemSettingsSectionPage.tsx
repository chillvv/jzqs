import React, { useEffect, useRef, useState } from "react";
import useSWR from "swr";
import { NavLink, useParams } from "react-router-dom";
import {
  deleteDispatchAreaMemory,
  fetchDispatchAiWorkbench,
  fetchDispatchAreaCodes,
  fetchDispatchAreaMemories,
  fetchDispatchAreaMemorySources,
  fetchOperationSettings,
  refreshDispatchAiBalance,
  runDispatchAiNow,
  swrFetcher,
  updateBannerImages,
  updateDispatchAreaMemory,
  updateDispatchRouteWorkbench,
  updateDispatchAiWorkbench,
  updatePackageReminderSettings,
  updatePopupAnnouncement,
  uploadBannerImage,
  simulateRouteLab,
  deleteDispatchAiJobLogs
} from "../../shared/api/http";
import type {
  DispatchAiWorkbenchResponse,
  DispatchAreaCodeListResponse,
  DispatchAreaMemoryItem,
  DispatchAreaMemoryListResponse,
  DispatchAreaMemorySourceListResponse,
  OperationSettingsResponse
} from "../../shared/api/types";
import {
  Bot,
  Image as ImageIcon,
  MapPinned,
  PlayCircle,
  Megaphone,
  Loader2,
  Settings2
} from "lucide-react";
import { AppSelect } from "../../shared/components/AppSelect";
import { SafeInput, SafeTextarea } from "../../shared/components/SafeInput";
import { AdminDialog } from "../../shared/components/AdminDialog";
import { EmptyStateCard } from "../../shared/components/EmptyStateCard";
import { SettingsModal } from "../../shared/components/SettingsModal";
import { toast } from "../../shared/components/Toast";
import {
  buildDispatchAiConversation,
  buildCustomerFacingSettingHints,
  countEnabledBannerImages,
  handleAdminImageFallback,
  normalizeBannerConfigs,
  parseDispatchAiLogMetadata,
  resolveAdminMediaUrl,
  resolveDispatchRunStatusDescription,
  resolveDispatchRunStatusLabel,
  resolveDispatchAiOrderReasonMap,
  resolveDispatchRouteStatusTone,
  serializeBannerConfigs,
  summarizeDispatchAiLogMetadata,
  type BannerConfigItem
} from "./systemSettingsPage.helpers";
import { SystemSettingsOverview } from "./components/SystemSettingsOverview";
import { BannerPreviewModal } from "./components/BannerPreviewModal";
import { MaintenanceSectionContent } from "../maintenance/MaintenanceSectionContent";
import {
  buildSettingsSectionPath,
  DEFAULT_SETTINGS_SECTION,
  isSettingsSection,
  SETTINGS_SECTION_META,
  SETTINGS_SECTION,
  type SettingsSection
} from "./settingsSections";

const EMPTY_POPUP = { title: "", description: "", enabled: false, content: "" };
const EMPTY_PACKAGE_REMINDER = {
  packageExpiryReminderDays: "7",
  packageLowBalanceThreshold: "3",
  mealReminderPopupEnabled: true,
  deliverySubscribeEnabled: true,
  deliverySubscribeLunchTime: "11:30",
  deliverySubscribeDinnerTime: "17:30"
};
const EMPTY_ROUTE_WORKBENCH_FORM = {
  autoScheduleEnabled: false,
  autoScheduleTime: "00:05",
  defaultStrategyMode: "NEAR_TO_FAR",
  anchorAddress: "五环天地"
};
const EMPTY_AI_CONFIG_FORM = {
  aiEnabled: true,
  apiBaseUrl: "https://api.deepseek.com",
  apiKey: "",
  aiModel: "deepseek-chat",
  aiPromptTemplate: "",
  lowBalanceThreshold: "20.00"
};
const EMPTY_PRODUCTION_RUN_FORM = {
  serveDate: "",
  mealPeriod: "",
  areaCode: ""
};
const EMPTY_ROUTE_LAB_FORM = {
  addressesText: "",
  strategyMode: "NEAR_TO_FAR"
};
const EMPTY_AREA_MEMORY_FORM = {
  id: 0,
  title: "",
  summary: "",
  applicableScene: "ALL",
  status: "ACTIVE"
};
const MEAL_PERIOD_OPTIONS = [
  { label: "全部餐期", value: "" },
  { label: "午餐", value: "LUNCH" },
  { label: "晚餐", value: "DINNER" }
];
const STRATEGY_MODE_OPTIONS = [
  { label: "近 -> 远", value: "NEAR_TO_FAR" },
  { label: "远 -> 近", value: "FAR_TO_NEAR" }
];
const ROUTE_LAB_CALL_OUT = "路线实验室已经从真实运行工作台剥离，后续专门接手工地址推演，不再混入生产执行。";

const SETTINGS_SECTION_TABS: Array<{
  key: SettingsSection;
  label: string;
  description: string;
}> = (Object.values(SETTINGS_SECTION) as SettingsSection[]).map((key) => ({
  key,
  label: SETTINGS_SECTION_META[key].label,
  description: SETTINGS_SECTION_META[key].description
}));

function normalizeTriggerTime(value: string) {
  const normalized = String(value || "").trim();
  return /^([01]\d|2[0-3]):([0-5]\d)$/.test(normalized) ? normalized : "";
}

export function SystemSettingsSectionPage() {
  const { section } = useParams();
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
  const [routeWorkbenchForm, setRouteWorkbenchForm] = useState(EMPTY_ROUTE_WORKBENCH_FORM);
  const [aiConfigForm, setAiConfigForm] = useState(EMPTY_AI_CONFIG_FORM);
  const [productionRunForm, setProductionRunForm] = useState(EMPTY_PRODUCTION_RUN_FORM);
  const [routeLabForm, setRouteLabForm] = useState(EMPTY_ROUTE_LAB_FORM);
  const [selectedMemoryAreaCode, setSelectedMemoryAreaCode] = useState("");
  const [dispatchAreaCodes, setDispatchAreaCodes] = useState<DispatchAreaCodeListResponse["areaCodes"]>([]);
  const [areaMemoryData, setAreaMemoryData] = useState<DispatchAreaMemoryListResponse | null>(null);
  const [areaMemoryForm, setAreaMemoryForm] = useState(EMPTY_AREA_MEMORY_FORM);
  const [areaMemorySourceData, setAreaMemorySourceData] = useState<DispatchAreaMemorySourceListResponse | null>(null);

  const [modal, setModal] = useState<"banner" | "popup" | "packageReminder" | "routeWorkbench" | "aiConfig" | "productionRun" | "routeLab" | "areaMemoryHub" | "areaMemory" | "areaMemorySources" | null>(null);
  const [settingsLoading, setSettingsLoading] = useState(true);
  const [bannerUploading, setBannerUploading] = useState(false);
  const [popupSubmitting, setPopupSubmitting] = useState(false);
  const [packageSubmitting, setPackageSubmitting] = useState(false);
  const [bannerSubmitting, setBannerSubmitting] = useState(false);
  const [dispatchAiLoading, setDispatchAiLoading] = useState(true);
  const [routeWorkbenchSubmitting, setRouteWorkbenchSubmitting] = useState(false);
  const [aiConfigSubmitting, setAiConfigSubmitting] = useState(false);
  const [dispatchAiBalanceRefreshing, setDispatchAiBalanceRefreshing] = useState(false);
  const [dispatchAiRunSubmitting, setDispatchAiRunSubmitting] = useState(false);
  const [routeLabSubmitting, setRouteLabSubmitting] = useState(false);
  const [dispatchAreaCodesLoading, setDispatchAreaCodesLoading] = useState(false);
  const [areaMemoryLoading, setAreaMemoryLoading] = useState(false);
  const [areaMemorySubmitting, setAreaMemorySubmitting] = useState(false);
  const [areaMemorySourceLoading, setAreaMemorySourceLoading] = useState(false);
  const [selectedLogId, setSelectedLogId] = useState<number | null>(null);
  const [runTypeFilter, setRunTypeFilter] = useState<"PRODUCTION" | "TEST">("PRODUCTION");
  const [previewBannerImage, setPreviewBannerImage] = useState("");
  const bannerInputRef = useRef<HTMLInputElement | null>(null);

  const customerHints = buildCustomerFacingSettingHints(settings);
  const bannerConfigs = normalizeBannerConfigs(settings.bannerImages);
  const enabledBannerCount = countEnabledBannerImages(settings.bannerImages);
  const activeSection = isSettingsSection(section) ? section : DEFAULT_SETTINGS_SECTION;
  const activeSectionMeta = SETTINGS_SECTION_TABS.find((item) => item.key === activeSection) || SETTINGS_SECTION_TABS[0];
  const thinkingLogId = runTypeFilter === "TEST"
    ? (selectedLogId || dispatchAiWorkbench?.recentLogs?.find((log) => log.runType === "TEST")?.id || null)
    : null;

  const selectedLogForThinking = thinkingLogId ? dispatchAiWorkbench?.recentLogs?.find(l => l.id === thinkingLogId) : null;
  const isTestLogSelected = activeSection === SETTINGS_SECTION.AI_DISPATCH && selectedLogForThinking && selectedLogForThinking.runType === "TEST";

  const { data: aiThinkingLog = null, isLoading: aiThinkingLoading } = useSWR<DispatchAiWorkbenchResponse["recentLogs"][number]>(
    isTestLogSelected ? `/api/admin/dispatch/job-logs/${thinkingLogId}` : null,
    swrFetcher,
    {
      refreshInterval: (data) => (data?.status === "RUNNING" ? 1500 : 0)
    }
  );

  useEffect(() => {
    reloadSettings().catch(showError);
    reloadDispatchAiWorkbench().catch(showError);
  }, []);

  useEffect(() => {
    if (activeSection !== SETTINGS_SECTION.AI_DISPATCH) {
      return;
    }
    reloadDispatchAreaCodes().catch(showError);
  }, [activeSection]);

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

  async function reloadAreaMemories(areaCode: string) {
    const normalized = areaCode.trim();
    if (!normalized) {
      setAreaMemoryData(null);
      return;
    }
    setAreaMemoryLoading(true);
    try {
      setAreaMemoryData(await fetchDispatchAreaMemories(normalized));
    } finally {
      setAreaMemoryLoading(false);
    }
  }

  async function reloadDispatchAreaCodes() {
    setDispatchAreaCodesLoading(true);
    try {
      const response = await fetchDispatchAreaCodes();
      const nextAreaCodes = response.areaCodes || [];
      setDispatchAreaCodes(nextAreaCodes);
      const nextSelectedAreaCode = nextAreaCodes.includes(selectedMemoryAreaCode)
        ? selectedMemoryAreaCode
        : (nextAreaCodes[0] || "");
      setSelectedMemoryAreaCode(nextSelectedAreaCode);
      if (nextSelectedAreaCode) {
        await reloadAreaMemories(nextSelectedAreaCode);
      } else {
        setAreaMemoryData(null);
      }
    } finally {
      setDispatchAreaCodesLoading(false);
    }
  }

  function handleMemoryAreaCodeChange(value: string) {
    setSelectedMemoryAreaCode(value);
    reloadAreaMemories(value).catch(showError);
  }

  async function ensureDispatchAreaCodesLoaded() {
    if (dispatchAreaCodes.length) {
      return dispatchAreaCodes;
    }
    const response = await fetchDispatchAreaCodes();
    const nextAreaCodes = response.areaCodes || [];
    setDispatchAreaCodes(nextAreaCodes);
    return nextAreaCodes;
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

  function openRouteWorkbench() {
    const settings = dispatchAiWorkbench?.settings;
    setRouteWorkbenchForm(settings ? {
      autoScheduleEnabled: settings.autoScheduleEnabled,
      autoScheduleTime: settings.autoScheduleTime || "00:05",
      defaultStrategyMode: settings.defaultStrategyMode || "NEAR_TO_FAR",
      anchorAddress: settings.anchorAddress || ""
    } : EMPTY_ROUTE_WORKBENCH_FORM);
    openModal("routeWorkbench");
  }

  function openAiConfig() {
    const settings = dispatchAiWorkbench?.settings;
    setAiConfigForm(settings ? {
      aiEnabled: true,
      apiBaseUrl: settings.apiBaseUrl || "https://api.deepseek.com",
      apiKey: "",
      aiModel: settings.aiModel || "deepseek-chat",
      aiPromptTemplate: settings.aiPromptTemplate || "",
      lowBalanceThreshold: settings.lowBalanceThreshold || "20.00"
    } : EMPTY_AI_CONFIG_FORM);
    openModal("aiConfig");
  }

  async function openProductionRunModal() {
    try {
      const nextAreaCodes = await ensureDispatchAreaCodesLoaded();
      setProductionRunForm({
        serveDate: "",
        mealPeriod: "",
        areaCode: nextAreaCodes[0] || ""
      });
      openModal("productionRun");
    } catch (err: any) {
      showError(err);
    }
  }

  function openRouteLab() {
    setRouteLabForm((current) => ({
      ...EMPTY_ROUTE_LAB_FORM,
      strategyMode: dispatchAiWorkbench?.settings.defaultStrategyMode || current.strategyMode || "NEAR_TO_FAR"
    }));
    openModal("routeLab");
  }

  async function openAreaMemoryHub() {
    try {
      const nextAreaCodes = await ensureDispatchAreaCodesLoaded();
      const nextAreaCode = nextAreaCodes.includes(selectedMemoryAreaCode)
        ? selectedMemoryAreaCode
        : (nextAreaCodes[0] || "");
      setSelectedMemoryAreaCode(nextAreaCode);
      openModal("areaMemoryHub");
      if (nextAreaCode) {
        await reloadAreaMemories(nextAreaCode);
      } else {
        setAreaMemoryData(null);
      }
    } catch (err: any) {
      showError(err);
    }
  }

  function openAreaMemoryEditor(item: DispatchAreaMemoryItem) {
    setAreaMemoryForm({
      id: item.id,
      title: item.title,
      summary: item.summary,
      applicableScene: item.applicableScene || "ALL",
      status: item.status || "ACTIVE"
    });
    openModal("areaMemory");
  }

  async function openAreaMemorySources(item: DispatchAreaMemoryItem) {
    setAreaMemorySourceLoading(true);
    setAreaMemorySourceData(null);
    openModal("areaMemorySources");
    try {
      setAreaMemorySourceData(await fetchDispatchAreaMemorySources(item.id));
    } catch (err: any) {
      closeModal();
      showError(err);
    } finally {
      setAreaMemorySourceLoading(false);
    }
  }

  async function handleDeleteAreaMemory(item: DispatchAreaMemoryItem) {
    if (!window.confirm(`确定删除记忆“${item.title}”吗？删除后不会再参与后续 AI 上下文。`)) {
      return;
    }
    setAreaMemorySubmitting(true);
    try {
      const next = await deleteDispatchAreaMemory(item.id);
      setAreaMemoryData(next);
      toast("区域记忆已删除");
    } catch (err: any) {
      showError(err);
    } finally {
      setAreaMemorySubmitting(false);
    }
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

  async function submitRouteWorkbench() {
    const autoScheduleTime = normalizeTriggerTime(routeWorkbenchForm.autoScheduleTime);
    const anchorAddress = routeWorkbenchForm.anchorAddress.trim();
    if (!anchorAddress) {
      toast("请完整填写锚点地址", "error");
      return;
    }
    if (routeWorkbenchForm.autoScheduleEnabled && !autoScheduleTime) {
      toast("启用自动预排时请填写有效的执行时间", "error");
      return;
    }
    setRouteWorkbenchSubmitting(true);
    try {
      const next = await updateDispatchRouteWorkbench({
        autoScheduleEnabled: routeWorkbenchForm.autoScheduleEnabled,
        autoScheduleTime: autoScheduleTime || "00:05",
        defaultStrategyMode: routeWorkbenchForm.defaultStrategyMode,
        anchorAddress
      });
      setDispatchAiWorkbench(next);
      closeModal();
      toast("路线工作台配置已更新");
    } catch (err: any) {
      showError(err);
    } finally {
      setRouteWorkbenchSubmitting(false);
    }
  }

  async function submitAiConfig() {
    const routeSettings = dispatchAiWorkbench?.settings;
    if (!routeSettings) {
      toast("AI 配置尚未加载完成", "error");
      return;
    }
    const apiBaseUrl = aiConfigForm.apiBaseUrl.trim();
    const aiModel = aiConfigForm.aiModel.trim();
    const aiPromptTemplate = aiConfigForm.aiPromptTemplate.trim();
    if (!apiBaseUrl || !aiModel || !aiPromptTemplate) {
      toast("AI 为必经阶段，请完整填写接口地址、模型和提示词", "error");
      return;
    }
    setAiConfigSubmitting(true);
    try {
      const next = await updateDispatchAiWorkbench({
        autoScheduleEnabled: routeSettings.autoScheduleEnabled,
        autoScheduleTime: routeSettings.autoScheduleTime || "00:05",
        defaultStrategyMode: routeSettings.defaultStrategyMode,
        anchorName: routeSettings.anchorAddress,
        anchorAddress: routeSettings.anchorAddress,
        aiEnabled: true,
        apiBaseUrl,
        apiKey: aiConfigForm.apiKey.trim(),
        aiModel,
        aiPromptTemplate,
        lowBalanceThreshold: aiConfigForm.lowBalanceThreshold.trim() || "20.00"
      });
      setDispatchAiWorkbench(next);
      closeModal();
      toast("AI 管理配置已更新");
    } catch (err: any) {
      showError(err);
    } finally {
      setAiConfigSubmitting(false);
    }
  }

  async function submitAreaMemory() {
    if (!areaMemoryForm.id) {
      toast("请选择要编辑的记忆", "error");
      return;
    }
    if (!areaMemoryForm.title.trim() || !areaMemoryForm.summary.trim()) {
      toast("请完整填写记忆标题和摘要", "error");
      return;
    }
    setAreaMemorySubmitting(true);
    try {
      const next = await updateDispatchAreaMemory(areaMemoryForm.id, {
        title: areaMemoryForm.title.trim(),
        summary: areaMemoryForm.summary.trim(),
        applicableScene: areaMemoryForm.applicableScene,
        status: areaMemoryForm.status
      });
      setAreaMemoryData(next);
      closeModal();
      toast("区域记忆已更新");
    } catch (err: any) {
      showError(err);
    } finally {
      setAreaMemorySubmitting(false);
    }
  }

  function renderAreaAiMemorySection() {
    return (
      <div style={{ display: "grid", gap: 16 }}>
        <div style={{ display: "flex", gap: 8, alignItems: "center", justifyContent: "space-between", flexWrap: "wrap" }}>
          <div>
            <div style={{ fontSize: 16, fontWeight: 700, color: "var(--text-strong)" }}>区域 AI 记忆</div>
            <div style={{ fontSize: 13, color: "var(--text-sub)", marginTop: 4 }}>按真实区域查看和修订长期纠偏经验。</div>
          </div>
          <div style={{ display: "flex", gap: 8, alignItems: "center", flexWrap: "wrap" }}>
            <AppSelect
              value={selectedMemoryAreaCode}
              options={dispatchAreaCodes.map((areaCode) => ({
                label: areaCode,
                value: areaCode
              }))}
              onChange={handleMemoryAreaCodeChange}
              placeholder={dispatchAreaCodesLoading ? "真实区域加载中..." : "请选择真实区域"}
              disabled={dispatchAreaCodesLoading || !dispatchAreaCodes.length}
              style={{ minWidth: 180 }}
            />
            <button
              className="btn btn-outline"
              disabled={dispatchAreaCodesLoading}
              onClick={() => reloadDispatchAreaCodes().catch(showError)}
            >
              刷新区域
            </button>
          </div>
        </div>
        {dispatchAreaCodesLoading || areaMemoryLoading ? (
          <div className="admin-panel-note">区域记忆加载中...</div>
        ) : !dispatchAreaCodes.length ? (
          <EmptyStateCard title="暂无真实区域" description="系统暂未发现可用于 AI 记忆管理的真实区域，请先检查区域绑定或派单数据。" />
        ) : areaMemoryData?.items?.length ? (
          <div style={{ display: "grid", gap: 12 }}>
            {areaMemoryData.items.map((item) => (
              <div key={item.id} style={{ border: "1px solid var(--border-soft)", borderRadius: 12, padding: 16, background: "#fff" }}>
                <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "flex-start" }}>
                  <div style={{ display: "grid", gap: 8 }}>
                    <div style={{ fontSize: 15, fontWeight: 700 }}>{item.title}</div>
                    <div style={{ fontSize: 13, color: "var(--text-sub)" }}>{item.summary}</div>
                    <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                      <span className="tag tag-blue">{item.memoryType}</span>
                      <span className="tag tag-gray">场景 {item.applicableScene}</span>
                      <span className={item.status === "ACTIVE" ? "tag tag-green" : "tag tag-amber"}>{item.status}</span>
                      <span className="tag tag-gray">权重 {item.weight}</span>
                    </div>
                  </div>
                  <div style={{ display: "flex", gap: 8, flexWrap: "wrap", justifyContent: "flex-end" }}>
                    <button className="btn btn-outline btn-compact" onClick={() => openAreaMemorySources(item)}>
                      查看来源
                    </button>
                    <button className="btn btn-outline btn-compact" onClick={() => openAreaMemoryEditor(item)}>
                      编辑
                    </button>
                    <button className="btn-delete btn-compact" disabled={areaMemorySubmitting} onClick={() => handleDeleteAreaMemory(item)}>
                      删除
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <EmptyStateCard title="当前区域暂无长期记忆" description={`${selectedMemoryAreaCode || "当前区域"} 还没有已沉淀的 AI 记忆，先在真实区域管理里做 AI 纠偏并确认，系统才会生成可编辑经验。`} />
        )}
      </div>
    );
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
    const currentSettings = dispatchAiWorkbench?.settings;
    if (!currentSettings) {
      toast("路线工作台尚未加载完成", "error");
      return;
    }
    setDispatchAiRunSubmitting(true);
    try {
      const result = await runDispatchAiNow({
        serveDate: productionRunForm.serveDate || undefined,
        mealPeriod: productionRunForm.mealPeriod || undefined,
        areaCode: productionRunForm.areaCode.trim() || undefined
      });
      await reloadDispatchAiWorkbench();
      closeModal();
      toast(result.message || "立刻执行完成");
      setSelectedLogId(null); // Reset selection so it selects the new run
    } catch (err: any) {
      showError(err);
    } finally {
      setDispatchAiRunSubmitting(false);
    }
  }

  async function handleRunRouteLabTest() {
    const addresses = routeLabForm.addressesText
      .split("\n")
      .map((line) => line.trim())
      .filter(Boolean);
    if (addresses.length < 2) {
      toast("至少需要提供 2 个有效地址", "error");
      return;
    }
    const currentSettings = dispatchAiWorkbench?.settings;
    if (!currentSettings) {
      toast("路线工作台尚未加载完成", "error");
      return;
    }

    setRouteLabSubmitting(true);
    try {
      const result = await simulateRouteLab({
        addresses,
        strategyMode: routeLabForm.strategyMode,
        anchorAddress: currentSettings.anchorAddress || ""
      });
      await reloadDispatchAiWorkbench();
      closeModal();
      setRunTypeFilter("TEST");
      setSelectedLogId(result.logId);
      toast(result.message || "推演已启动，正在进入 AI 思考阶段");
    } catch (err: any) {
      showError(err);
    } finally {
      setRouteLabSubmitting(false);
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
  function renderBasicSection() {
    return (
      <>
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
      </>
    );
  }
  function renderAiDispatchSection() {
    const workbenchSettings = dispatchAiWorkbench?.settings;
    const productionLogs = (dispatchAiWorkbench?.recentLogs || []).filter((log) =>
      runTypeFilter === "TEST" ? log.runType === "TEST" : log.runType !== "TEST"
    );

    const activeLogId = selectedLogId || (productionLogs.length > 0 ? productionLogs[0].id : null);
    const activeLog = productionLogs.find((log) => log.id === activeLogId) || productionLogs[0];
    const activeMetadata = activeLog ? parseDispatchAiLogMetadata(activeLog.metadataJson || "") : null;
    const routeItems = activeMetadata?.items || [];
    const thinkingLog = aiThinkingLog;
    const thinkingMetadata = thinkingLog ? parseDispatchAiLogMetadata(thinkingLog.metadataJson || "") : null;
    const conversation = thinkingMetadata ? buildDispatchAiConversation(thinkingMetadata) : [];
    const orderReasonMap = thinkingMetadata ? resolveDispatchAiOrderReasonMap(thinkingMetadata) : new Map<number, string>();
    const thinkingRouteItems = thinkingMetadata?.items || [];

    return (
      <div style={{ display: "grid", gap: 20 }}>
        <div className="settings-card" style={{ padding: 20, marginBottom: 0 }}>
          <div style={{ display: "flex", justifyContent: "space-between", gap: 16, alignItems: "flex-start", flexWrap: "wrap" }}>
            <div>
              <div style={{ fontSize: 18, fontWeight: 700, color: "var(--text-strong)", display: "flex", alignItems: "center", gap: 8 }}>
                <Settings2 size={18} className="text-primary" /> AI 智能调度
              </div>
              <div style={{ marginTop: 6, fontSize: 13, color: "var(--text-sub)" }}>
                主页面只保留控制台、运行历史和测试。AI 配置与区域记忆按入口展开，不再常驻占位。
              </div>
            </div>
            <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
              <button className="btn btn-outline" onClick={handleRefreshDispatchAiBalance} disabled={dispatchAiBalanceRefreshing}>
                {dispatchAiBalanceRefreshing ? "刷新中..." : "刷新额度"}
              </button>
              <button className="btn btn-outline" onClick={openAiConfig}>
                AI 配置
              </button>
              <button className="btn btn-outline" onClick={() => { void openAreaMemoryHub(); }}>
                区域记忆
              </button>
            </div>
          </div>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fit, minmax(180px, 1fr))", gap: 12, marginTop: 16 }}>
            <div className="stat-card" style={{ marginBottom: 0 }}>
              <div className="stat-title">当前模型</div>
              <div className="stat-val" style={{ fontSize: 20 }}>{workbenchSettings?.aiModel || "未配置"}</div>
              <div className="stat-footer">{workbenchSettings?.apiBaseUrl || "未配置接口地址"}</div>
            </div>
            <div className="stat-card" style={{ marginBottom: 0 }}>
              <div className="stat-title">接口状态</div>
              <div className="stat-val" style={{ fontSize: 20 }}>{workbenchSettings?.maskedApiKey ? "已配置" : "未配置"}</div>
              <div className="stat-footer">AI 为必经阶段</div>
            </div>
            <div className="stat-card" style={{ marginBottom: 0 }}>
              <div className="stat-title">可用额度</div>
              <div className="stat-val" style={{ fontSize: 20 }}>{workbenchSettings?.totalBalance || "0.00"}</div>
              <div className="stat-footer">{workbenchSettings?.balanceCurrency || "CNY"}</div>
            </div>
            <div className="stat-card" style={{ marginBottom: 0 }}>
              <div className="stat-title">默认策略</div>
              <div className="stat-val" style={{ fontSize: 20 }}>{workbenchSettings?.defaultStrategyMode === "FAR_TO_NEAR" ? "远到近" : "近到远"}</div>
              <div className="stat-footer">{workbenchSettings?.anchorAddress || "未配置锚点"}</div>
            </div>
          </div>
        </div>

        <div className="settings-card" style={{ padding: 24, marginBottom: 0 }}>
          <div className="toolbar" style={{ display: "flex", flexDirection: "row", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 16 }}>
            <div>
              <h3 style={{ fontSize: 18, fontWeight: 700, margin: 0, display: "flex", alignItems: "center", gap: 8, color: "var(--text-strong)" }}>
                <Bot size={20} className="text-primary" /> 路线排版控制台
              </h3>
              <p style={{ margin: "6px 0 0 0", color: "var(--text-sub)", fontSize: 13 }}>
                自动预排：{workbenchSettings?.autoScheduleEnabled ? `每天 ${workbenchSettings.autoScheduleTime}` : "已关闭"} · 策略：{workbenchSettings?.defaultStrategyMode === "FAR_TO_NEAR" ? "远到近" : "近到远"} · 锚点：{workbenchSettings?.anchorAddress || "五环天地"}
              </p>
            </div>
            <div style={{ display: "flex", gap: 12 }}>
              <button className="btn btn-outline" onClick={openRouteWorkbench}>排版规则</button>
              <button className="btn btn-outline" onClick={openRouteLab}>打开实验室</button>
              <button className="btn btn-primary" onClick={openProductionRunModal} disabled={dispatchAiLoading}>
                <PlayCircle size={16} /> 立刻执行
              </button>
            </div>
          </div>

          <div style={{ display: "grid", gridTemplateColumns: "minmax(280px, 320px) 1fr", gap: 20, alignItems: "start", marginTop: 24 }}>
            <div className="settings-card" style={{ marginBottom: 0, padding: 0, overflow: "hidden", border: "1px solid var(--border-soft)" }}>
              <div className="settings-card__header" style={{ padding: "16px 20px", borderBottom: "1px solid var(--border-soft)", background: "linear-gradient(180deg, rgba(255,255,255,0.99) 0%, rgba(249,251,255,0.96) 100%)" }}>
                <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                  <h4 style={{ margin: 0, fontSize: 15, fontWeight: 700, color: "var(--text-strong)" }}>运行历史</h4>
                  {productionLogs.length > 0 && (
                    <button
                      className="btn"
                      onClick={async (e) => {
                        e.stopPropagation();
                        if (window.confirm(`确定要删除当前的 ${productionLogs.length} 条${runTypeFilter === "TEST" ? "测试" : "真实"}运行记录吗？`)) {
                          try {
                            await deleteDispatchAiJobLogs({ ids: productionLogs.map((log) => log.id) });
                            await reloadDispatchAiWorkbench();
                            setSelectedLogId(null);
                            toast("已清空当前列表的记录");
                          } catch (err: any) {
                            showError(err);
                          }
                        }
                      }}
                      style={{ padding: "2px 8px", fontSize: 12, height: 24, minHeight: 0, background: "var(--bg-muted)", border: "none", borderRadius: 4, cursor: "pointer", color: "var(--text-sub)" }}
                    >
                      批量清空
                    </button>
                  )}
                </div>
                <div className="segmented-control" style={{ marginTop: 12, marginBottom: 0, padding: 2 }}>
                  <button
                    className={`segmented-control__item ${runTypeFilter === "PRODUCTION" ? "is-active" : ""}`}
                    onClick={() => { setRunTypeFilter("PRODUCTION"); setSelectedLogId(null); }}
                    style={{ padding: "4px 8px", fontSize: 13 }}
                  >
                    真实运行
                  </button>
                  <button
                    className={`segmented-control__item ${runTypeFilter === "TEST" ? "is-active" : ""}`}
                    onClick={() => { setRunTypeFilter("TEST"); setSelectedLogId(null); }}
                    style={{ padding: "4px 8px", fontSize: 13 }}
                  >
                    测试实验
                  </button>
                </div>
              </div>
              <div style={{ maxHeight: 590, overflowY: "auto" }}>
                {productionLogs.length > 0 ? productionLogs.map((log) => {
                  const summary = summarizeDispatchAiLogMetadata(log.metadataJson || "");
                  const statusCode = summary?.runStatusCode || log.status;
                  const statusLabel = resolveDispatchRunStatusLabel(statusCode, summary?.runStatusLabel || log.status);
                  const isActive = activeLogId === log.id;
                  return (
                    <div
                      key={log.id}
                      onClick={() => setSelectedLogId(log.id)}
                      style={{
                        padding: "16px 20px",
                        borderBottom: "1px solid var(--border-soft)",
                        cursor: "pointer",
                        background: isActive ? "linear-gradient(90deg, rgba(59,130,246,0.06) 0%, transparent 100%)" : "transparent",
                        borderLeft: isActive ? "3px solid var(--primary-color)" : "3px solid transparent",
                        transition: "all 0.2s ease"
                      }}
                    >
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginBottom: 8 }}>
                        <span className={`tag ${resolveDispatchRouteStatusTone(statusCode)}`}>
                          {statusLabel}
                        </span>
                        <span style={{ fontSize: 12, color: "var(--text-subtle)" }}>{log.finishedAt ? log.finishedAt.split(" ")[1] : "-"}</span>
                      </div>
                      <div style={{ fontSize: 13, fontWeight: 600, color: "var(--text-body)", marginBottom: 4 }}>
                        {log.runType === "TEST" ? "路线推演测试" : `区域 ${log.areaCode || "全部"} · ${log.mealPeriod === "LUNCH" ? "午餐" : log.mealPeriod === "DINNER" ? "晚餐" : "全餐期"}`}
                      </div>
                      <div style={{ fontSize: 12, color: "var(--text-sub)" }}>
                        {summary
                          ? `${summary.orderCount} 单 · ${summary.aiAdjustedCount > 0 ? `AI 已调整 ${summary.aiAdjustedCount} 单` : "AI 已复核，无需调整"}`
                          : "暂无详情"}
                      </div>
                    </div>
                  );
                }) : (
                  <div style={{ padding: 32, textAlign: "center", color: "var(--text-sub)" }}>暂无运行记录</div>
                )}
              </div>
            </div>

            <div className="settings-card" style={{ marginBottom: 0, padding: 0, overflow: "hidden", border: "1px solid var(--border-soft)" }}>
              {activeLog && activeMetadata ? (
                runTypeFilter === "TEST" ? (
                  <div style={{ display: "flex", flexDirection: "column", height: "100%" }}>
                    <div style={{ padding: "16px 24px", borderBottom: "1px solid var(--border-soft)", background: "linear-gradient(180deg, rgba(255,255,255,0.99) 0%, rgba(249,251,255,0.96) 100%)" }}>
                      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                        <div>
                          <h4 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: "var(--text-strong)" }}>测试日志 #{activeLog.id}</h4>
                          <p style={{ margin: "4px 0 0 0", fontSize: 13, color: "var(--text-sub)" }}>
                            {thinkingMetadata?.currentPhase || thinkingLog?.message || "等待执行"}
                          </p>
                        </div>
                        <span className={`tag ${thinkingLog?.status === "FAILED" ? "tag-red" : thinkingLog?.status === "RUNNING" ? "tag-blue" : "tag-green"}`}>
                          {thinkingLog?.status === "RUNNING" ? "生成中" : thinkingLog?.status === "FAILED" ? "执行失败" : "已完成"}
                        </span>
                      </div>
                    </div>
                    <div style={{ padding: 24, overflowY: "auto", maxHeight: 640 }}>
                      {aiThinkingLoading && !thinkingLog ? (
                        <div style={{ textAlign: "center", color: "var(--text-sub)", padding: 40 }}>AI 思考页加载中...</div>
                      ) : thinkingLog ? (
                        <div style={{ display: "grid", gap: 20 }}>
                          <div style={{ display: "grid", gridTemplateColumns: "repeat(2, minmax(0, 1fr))", gap: 12 }}>
                            <div className="stat-card" style={{ padding: 16 }}>
                              <div className="stat-title">当前状态</div>
                              <div className="stat-val" style={{ fontSize: 18 }}>{resolveDispatchRunStatusLabel(thinkingMetadata?.runStatusCode || thinkingLog.status || "", thinkingMetadata?.runStatusLabel || thinkingLog.status || "--")}</div>
                            </div>
                            <div className="stat-card" style={{ padding: 16 }}>
                              <div className="stat-title">模型置信度</div>
                              <div className="stat-val" style={{ fontSize: 18 }}>{thinkingMetadata?.confidence ? `${Math.round(thinkingMetadata.confidence * 100)}%` : "--"}</div>
                            </div>
                          </div>

                          <div style={{ border: "1px solid var(--border-soft)", borderRadius: 16, overflow: "hidden", background: "linear-gradient(180deg, rgba(255,255,255,0.98) 0%, rgba(247,250,255,0.96) 100%)" }}>
                            <div style={{ padding: "16px 20px", borderBottom: "1px solid var(--border-soft)" }}>
                              <div style={{ fontSize: 14, fontWeight: 700, color: "var(--text-strong)" }}>AI 对话记录</div>
                              <div style={{ fontSize: 12, color: "var(--text-sub)", marginTop: 4 }}>
                                {resolveDispatchRunStatusDescription(thinkingMetadata?.runStatusCode || "", thinkingMetadata?.runStatusDescription || thinkingLog.message || "正在等待 AI 返回结论", thinkingMetadata?.aiAdjustedCount || 0)}
                              </div>
                            </div>
                            <div style={{ padding: 16, display: "grid", gap: 12 }}>
                              {conversation.map((message) => (
                                <div
                                  key={message.id}
                                  style={{
                                    border: message.role === "error" ? "1px solid rgba(239,68,68,0.18)" : "1px solid var(--border-soft)",
                                    borderRadius: 12,
                                    padding: 12,
                                    background: message.role === "system" ? "rgba(241,245,249,0.88)" : message.role === "error" ? "rgba(254,242,242,0.96)" : "rgba(255,255,255,0.88)"
                                  }}
                                >
                                  <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "center", marginBottom: 6 }}>
                                    <div style={{ fontSize: 13, fontWeight: 700, color: "var(--text-strong)" }}>{message.title}</div>
                                    <span className={`tag ${message.role === "error" ? "tag-red" : message.role === "system" ? "tag-gray" : "tag-blue"}`}>
                                      {message.role === "system" ? "系统" : message.role === "error" ? "错误" : "AI"}
                                    </span>
                                  </div>
                                  <div style={{ fontSize: 12, color: message.role === "error" ? "var(--error-color-dark)" : "var(--text-body)", whiteSpace: "pre-wrap" }}>
                                    {message.content}
                                  </div>
                                </div>
                              ))}
                              {thinkingMetadata?.providerError ? (
                                <div style={{ border: "1px solid rgba(239,68,68,0.18)", background: "rgba(254,242,242,0.96)", color: "var(--error-color-dark)", borderRadius: 12, padding: 14, fontSize: 13 }}>
                                  AI 错误信息：{thinkingMetadata.providerError}
                                </div>
                              ) : null}
                            </div>
                          </div>
                          {thinkingMetadata?.summary || thinkingRouteItems.length > 0 ? (
                            <div style={{ border: "1px solid var(--border-soft)", borderRadius: 16, overflow: "hidden" }}>
                              <div style={{ padding: "16px 20px", borderBottom: "1px solid var(--border-soft)", background: "#f8fafc" }}>
                                <h4 style={{ margin: 0, fontSize: 14, fontWeight: 700 }}>最终结果</h4>
                              </div>
                              <div style={{ padding: 16, display: "grid", gap: 16 }}>
                                <div style={{ border: "1px solid var(--border-soft)", borderRadius: 12, padding: 14, background: "rgba(248,250,252,0.72)" }}>
                                  <div style={{ fontSize: 12, color: "var(--text-sub)", fontWeight: 700, marginBottom: 8 }}>AI 总结</div>
                                  <div style={{ fontSize: 13, color: "var(--text-body)", lineHeight: 1.6 }}>
                                    {thinkingMetadata?.summary || "AI 已返回测试结果。"}
                                  </div>
                                </div>
                                {thinkingRouteItems.length ? (
                                  <div style={{ border: "1px solid var(--border-soft)", borderRadius: 12, overflow: "hidden" }}>
                                    {thinkingRouteItems.map((item, index) => (
                                      <div key={item.orderId} style={{ display: "flex", gap: 12, padding: "12px 16px", borderBottom: index < thinkingRouteItems.length - 1 ? "1px solid var(--border-soft)" : "none" }}>
                                        <div style={{ width: 28, textAlign: "center", fontWeight: 800, color: "var(--text-sub)" }}>{item.suggestedSequence}</div>
                                        <div style={{ flex: 1 }}>
                                          <div style={{ fontSize: 13, fontWeight: 600, color: "var(--text-body)" }}>{item.addressLabel}</div>
                                          <div style={{ fontSize: 12, color: "var(--text-sub)", marginTop: 4 }}>{orderReasonMap.get(item.orderId) || item.adjustmentReason || "AI 已生成当前顺序"}</div>
                                        </div>
                                      </div>
                                    ))}
                                  </div>
                                ) : null}
                              </div>
                            </div>
                          ) : null}
                        </div>
                      ) : null}
                    </div>
                  </div>
                ) : (
                  <>
                    <div className="settings-card__header" style={{ padding: "16px 24px", borderBottom: "1px solid var(--border-soft)", display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 12, background: "linear-gradient(180deg, rgba(255,255,255,0.99) 0%, rgba(249,251,255,0.96) 100%)" }}>
                      <div>
                        <h4 style={{ margin: 0, fontSize: 16, fontWeight: 700, color: "var(--text-strong)" }}>排版详情 #{activeLog.id}</h4>
                        <p style={{ margin: "4px 0 0 0", fontSize: 13, color: "var(--text-sub)" }}>
                          由 {activeLog.executedBy || "-"} 触发 · {resolveDispatchRunStatusDescription(activeMetadata.runStatusCode, activeMetadata.runStatusDescription || activeLog.reasonSummary || "运行完成", activeMetadata.aiAdjustedCount)}
                        </p>
                      </div>
                      <div style={{ display: "flex", gap: 20 }}>
                        <div style={{ textAlign: "right" }}>
                          <div style={{ fontSize: 12, color: "var(--text-sub)", fontWeight: 600 }}>总订单</div>
                          <div style={{ fontSize: 18, fontWeight: 800, color: "var(--text-strong)" }}>{activeMetadata.orderCount}</div>
                        </div>
                        <div style={{ textAlign: "right" }}>
                          <div style={{ fontSize: 12, color: "var(--text-sub)", fontWeight: 600 }}>AI阶段</div>
                          <div style={{ fontSize: 18, fontWeight: 800, color: "var(--text-strong)" }}>已执行</div>
                        </div>
                      </div>
                    </div>
                    <div style={{ maxHeight: 640, overflowY: "auto", padding: 24, display: "grid", gap: 16 }}>
                      <div style={{ border: "1px solid var(--border-soft)", borderRadius: 12, padding: 14, background: "rgba(248,250,252,0.72)" }}>
                        <div style={{ fontSize: 12, color: "var(--text-sub)", fontWeight: 700, marginBottom: 8 }}>AI 结果概览</div>
                        <div style={{ fontSize: 13, color: "var(--text-body)", lineHeight: 1.6 }}>
                          {activeMetadata.summary || activeLog.reasonSummary || "AI 已完成当前批次排线。"}
                        </div>
                      </div>
                      <div>
                        <h5 style={{ margin: "0 0 12px 0", fontSize: 14, fontWeight: 700, color: "var(--text-strong)" }}>最终顺序</h5>
                        <div style={{ border: "1px solid var(--border-soft)", borderRadius: 12, overflow: "hidden" }}>
                          {routeItems.map((item, index) => (
                            <div key={item.orderId} style={{ display: "flex", gap: 16, padding: "12px 16px", borderBottom: index < routeItems.length - 1 ? "1px solid var(--border-soft)" : "none", background: "transparent" }}>
                              <div style={{ fontWeight: 800, fontSize: 16, color: "var(--text-sub)", width: 28, textAlign: "center" }}>
                                {item.suggestedSequence}
                              </div>
                              <div style={{ flex: 1 }}>
                                <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 4, flexWrap: "wrap" }}>
                                  <span style={{ fontWeight: 600, fontSize: 14, color: "var(--text-body)" }}>{item.addressLabel}</span>
                                  <span className="tag tag-green" style={{ padding: "0 6px", fontSize: 11 }}>AI已参与</span>
                                </div>
                                <div style={{ fontSize: 12, color: "var(--text-sub)" }}>
                                  {item.adjustmentReason || "AI 已生成当前顺序。"}
                                </div>
                              </div>
                            </div>
                          ))}
                        </div>
                      </div>
                    </div>
                  </>
                )
              ) : (
                <EmptyStateCard
                  title="未选择运行记录"
                  description="在左侧选择一条历史记录查看详情，或点击上方立刻执行。"
                  primaryActionText="立刻执行"
                  onPrimaryAction={openProductionRunModal}
                />
              )}
            </div>
          </div>
        </div>
      </div>
    );
  }


  function renderAiConfigSection() {
    const aiSettings = dispatchAiWorkbench?.settings;
    return (
      <div style={{ display: "grid", gap: 16 }}>
        <div className="form-group">
          <label className="form-label">AI 执行策略</label>
          <div className="settings-form-callout">
            当前系统固定为“AI 主排”模式，不能关闭 AI。若 DeepSeek 请求失败，会直接返回错误信息用于诊断，不会再降级到本地排序。
          </div>
        </div>
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
          <div>
            <div style={{ fontSize: 13, color: "var(--text-sub)", marginBottom: 4 }}>当前模型</div>
            <div style={{ fontSize: 14, fontWeight: 600, color: "var(--text-body)" }}>{aiSettings?.aiModel || "-"}</div>
          </div>
          <div>
            <div style={{ fontSize: 13, color: "var(--text-sub)", marginBottom: 4 }}>Base URL</div>
            <div style={{ fontSize: 14, fontWeight: 600, color: "var(--text-body)" }}>{aiSettings?.apiBaseUrl || "-"}</div>
          </div>
          <div>
            <div style={{ fontSize: 13, color: "var(--text-sub)", marginBottom: 4 }}>低余额提醒阈值</div>
            <div style={{ fontSize: 14, fontWeight: 600, color: "var(--text-body)" }}>{aiSettings?.lowBalanceThreshold || "-"}</div>
          </div>
          <div>
            <div style={{ fontSize: 13, color: "var(--text-sub)", marginBottom: 4 }}>余额</div>
            <div style={{ fontSize: 14, fontWeight: 600, color: "var(--text-body)" }}>
              {(aiSettings?.totalBalance || "0.00")} {aiSettings?.balanceCurrency || "CNY"}
            </div>
          </div>
        </div>
        <div style={{ background: "#f8fafc", borderRadius: 12, padding: 16, border: "1px solid var(--border-soft)" }}>
          <div style={{ display: "flex", justifyContent: "space-between", marginBottom: 8 }}>
            <span style={{ fontSize: 13, color: "var(--text-sub)", fontWeight: 700 }}>系统提示词 (Prompt)</span>
            <span style={{ fontSize: 12, color: "var(--text-subtle)" }}>{aiSettings?.aiPromptTemplate?.length || 0} 字符</span>
          </div>
          <div style={{ fontSize: 13, color: "var(--text-body)", whiteSpace: "pre-wrap", wordBreak: "break-word", maxHeight: 180, overflowY: "auto" }}>
            {aiSettings?.aiPromptTemplate || "未配置提示词"}
          </div>
        </div>
      </div>
    );
  }
  return (
    <>
      <div className="page-header">
        <div>
          <h2 className="page-title">系统设置</h2>
          <p className="page-subtitle">{activeSectionMeta.description}</p>
        </div>
        <div className="page-header__actions">
          {activeSection === SETTINGS_SECTION.BASIC ? (
            <button className="btn btn-outline" onClick={openPopup}>
              <Megaphone size={16} /> 锁定公告
            </button>
          ) : null}
          {activeSection === SETTINGS_SECTION.AI_DISPATCH ? (
            <>
              <button className="btn btn-outline" onClick={openAiConfig}>
                AI 配置
              </button>
              <button className="btn btn-outline" onClick={() => { void openAreaMemoryHub(); }}>
                <MapPinned size={16} /> 区域记忆
              </button>
            </>
          ) : null}
        </div>
      </div>
      <div className="admin-panel" style={{ padding: 16, marginBottom: 16 }}>
        <div className="segmented-control" role="tablist" aria-label="系统设置分组切换">
          {SETTINGS_SECTION_TABS.map((tab) => (
            <NavLink
              key={tab.key}
              to={buildSettingsSectionPath(tab.key)}
              className={({ isActive }) => "segmented-control__item " + (isActive ? "is-active" : "")}
            >
              {tab.label}
            </NavLink>
          ))}
        </div>
        <div style={{ marginTop: 10, color: "var(--text-sub)", fontSize: 13 }}>
          {activeSectionMeta.description}
        </div>
      </div>
      {activeSection === SETTINGS_SECTION.BASIC ? renderBasicSection() : null}
      {activeSection === SETTINGS_SECTION.AI_DISPATCH ? renderAiDispatchSection() : null}
      {activeSection === SETTINGS_SECTION.MAINTENANCE ? <MaintenanceSectionContent embedded /> : null}
      <AdminDialog
        open={modal === "areaMemoryHub"}
        title="区域 AI 记忆"
        description="这里集中查看和修订真实区域的长期纠偏经验。"
        onClose={closeModal}
        footer={<button className="btn btn-outline" onClick={closeModal}>关闭</button>}
      >
        {renderAreaAiMemorySection()}
      </AdminDialog>
      <SettingsModal
        open={modal === "areaMemory"}
        title="编辑区域记忆"
        onClose={closeModal}
        onSubmit={submitAreaMemory}
        submitLabel="保存记忆"
        submitting={areaMemorySubmitting}
      >
        <div className="form-group">
          <label className="form-label">记忆标题</label>
          <SafeInput className="form-control" value={areaMemoryForm.title} onValueChange={(value) => setAreaMemoryForm({ ...areaMemoryForm, title: value })} />
        </div>
        <div className="form-group">
          <label className="form-label">记忆摘要</label>
          <SafeTextarea className="form-control" rows={4} value={areaMemoryForm.summary} onValueChange={(value) => setAreaMemoryForm({ ...areaMemoryForm, summary: value })} />
        </div>
        <div className="form-group">
          <label className="form-label">适用场景</label>
          <AppSelect
            value={areaMemoryForm.applicableScene}
            options={[
              { label: "全部场景", value: "ALL" },
              { label: "午餐", value: "LUNCH" },
              { label: "晚餐", value: "DINNER" }
            ]}
            onChange={(value) => setAreaMemoryForm({ ...areaMemoryForm, applicableScene: value })}
          />
        </div>
        <div className="form-group">
          <label className="form-label">状态</label>
          <AppSelect
            value={areaMemoryForm.status}
            options={[
              { label: "启用", value: "ACTIVE" },
              { label: "暂停", value: "PAUSED" },
              { label: "删除", value: "DELETED" }
            ]}
            onChange={(value) => setAreaMemoryForm({ ...areaMemoryForm, status: value })}
          />
        </div>
      </SettingsModal>
      <AdminDialog
        open={modal === "areaMemorySources"}
        title={areaMemorySourceData ? `来源纠偏 · ${areaMemorySourceData.memoryTitle}` : "来源纠偏"}
        description={areaMemorySourceData ? `${areaMemorySourceData.areaCode} · 查看该记忆沉淀自哪些已确认纠偏` : undefined}
        onClose={closeModal}
        footer={<button className="btn btn-outline" onClick={closeModal}>关闭</button>}
      >
        {areaMemorySourceLoading ? (
          <div className="admin-panel-note">来源纠偏加载中...</div>
        ) : areaMemorySourceData?.items?.length ? (
          <div style={{ display: "grid", gap: 12 }}>
            {areaMemorySourceData.items.map((item) => (
              <div key={item.correctionId} style={{ border: "1px solid var(--border-soft)", borderRadius: 12, padding: 16, background: "#fff" }}>
                <div style={{ display: "flex", justifyContent: "space-between", gap: 12, alignItems: "center", marginBottom: 8 }}>
                  <div style={{ fontSize: 14, fontWeight: 700 }}>纠偏 #{item.correctionId}</div>
                  <div style={{ display: "flex", gap: 8, flexWrap: "wrap" }}>
                    <span className="tag tag-blue">{item.correctionMode}</span>
                    <span className={item.replanStatus === "SUCCESS" ? "tag tag-green" : "tag tag-amber"}>{item.replanStatus}</span>
                  </div>
                </div>
                <div style={{ display: "grid", gap: 6, fontSize: 13, color: "var(--text-body)" }}>
                  <div><strong>商家说明：</strong>{item.merchantInstruction || "-"}</div>
                  <div><strong>记忆摘要：</strong>{item.merchantReasonSummary || "-"}</div>
                  <div><strong>AI 理解：</strong>{item.aiInterpretationSummary || "-"}</div>
                  <div style={{ color: "var(--text-sub)" }}><strong>确认时间：</strong>{item.confirmedAt || "-"}</div>
                </div>
              </div>
            ))}
          </div>
        ) : (
          <EmptyStateCard title="暂无来源纠偏" description="当前记忆还没有可展示的来源记录。" />
        )}
      </AdminDialog>
      <SettingsModal
        open={modal === "routeWorkbench"}
        title="路线工作台配置"
        onClose={closeModal}
        onSubmit={submitRouteWorkbench}
        submitLabel="保存路线配置"
        submitting={routeWorkbenchSubmitting}
      >
        <div className="form-group">
          <label className="form-label">启用自动预排</label>
          <div className="toggle-row">
            <input type="checkbox" checked={routeWorkbenchForm.autoScheduleEnabled} onChange={(e) => setRouteWorkbenchForm({ ...routeWorkbenchForm, autoScheduleEnabled: e.target.checked })} />
            <span>{routeWorkbenchForm.autoScheduleEnabled ? "到点后自动为次日派单排序" : "关闭后仅保留手动测试与商家拖拽确认"}</span>
          </div>
        </div>
        <div className="form-group">
          <label className="form-label">自动执行时间</label>
          <SafeInput className="form-control" type="time" value={routeWorkbenchForm.autoScheduleTime} onValueChange={(value) => setRouteWorkbenchForm({ ...routeWorkbenchForm, autoScheduleTime: value })} />
        </div>
        <div className="form-group">
          <label className="form-label">默认策略</label>
          <AppSelect
            value={routeWorkbenchForm.defaultStrategyMode}
            options={STRATEGY_MODE_OPTIONS}
            onChange={(value) => setRouteWorkbenchForm({ ...routeWorkbenchForm, defaultStrategyMode: value })}
            placeholder="请选择默认策略"
          />
        </div>
        <div className="form-group">
          <label className="form-label">锚点详细地址</label>
          <SafeTextarea className="form-control" value={routeWorkbenchForm.anchorAddress} onValueChange={(value) => setRouteWorkbenchForm({ ...routeWorkbenchForm, anchorAddress: value })} />
        </div>
      </SettingsModal>

      <SettingsModal
        open={modal === "productionRun"}
        title="立刻执行"
        onClose={closeModal}
        onSubmit={handleRunDispatchAiNow}
        submitLabel="确认执行"
        submitting={dispatchAiRunSubmitting}
      >
        <div className="settings-form-callout">
          这里执行的是真实订单，不是测试。留空时按系统默认范围执行；需要时可手动指定日期、餐期或区域。
        </div>
        <div className="form-group">
          <label className="form-label">执行日期</label>
          <SafeInput
            className="form-control"
            type="date"
            value={productionRunForm.serveDate}
            onValueChange={(value) => setProductionRunForm({ ...productionRunForm, serveDate: value })}
          />
        </div>
        <div className="form-group">
          <label className="form-label">执行餐期</label>
          <AppSelect
            value={productionRunForm.mealPeriod}
            options={MEAL_PERIOD_OPTIONS}
            onChange={(value) => setProductionRunForm({ ...productionRunForm, mealPeriod: value })}
            placeholder="默认全部餐期"
          />
        </div>
        <div className="form-group">
          <label className="form-label">执行区域</label>
          <AppSelect
            value={productionRunForm.areaCode}
            options={[
              { label: "全部区域", value: "" },
              ...dispatchAreaCodes.map((areaCode) => ({
                label: areaCode,
                value: areaCode
              }))
            ]}
            onChange={(value) => setProductionRunForm({ ...productionRunForm, areaCode: value })}
            placeholder={dispatchAreaCodesLoading ? "真实区域加载中..." : "默认全部区域"}
            disabled={dispatchAreaCodesLoading}
          />
        </div>
      </SettingsModal>

      <SettingsModal
        open={modal === "routeLab"}
        title="路线实验室"
        onClose={closeModal}
        onSubmit={handleRunRouteLabTest}
        submitLabel="开始推演"
        submitting={routeLabSubmitting}
      >
        <div className="settings-form-callout">
          {ROUTE_LAB_CALL_OUT}
        </div>
        <div className="form-group">
          <label className="form-label">实验策略</label>
          <AppSelect
            value={routeLabForm.strategyMode}
            options={STRATEGY_MODE_OPTIONS}
            onChange={(value) => setRouteLabForm({ ...routeLabForm, strategyMode: value })}
            placeholder="请选择实验策略"
          />
        </div>
        <div className="form-group">
          <label className="form-label">测试地址</label>
          <SafeTextarea
            className="form-control"
            style={{ height: 180 }}
            value={routeLabForm.addressesText}
            onValueChange={(value) => setRouteLabForm({ ...routeLabForm, addressesText: value })}
            placeholder={"后续这里直接输入测试地址，每行一条。\n例如：\n五环天地A栋101\n五环天地B栋202"}
          />
        </div>
      </SettingsModal>

      <SettingsModal
        open={modal === "aiConfig"}
        title="AI 管理配置"
        onClose={closeModal}
        onSubmit={submitAiConfig}
        submitLabel="保存 AI 配置"
        submitting={aiConfigSubmitting}
      >
        {renderAiConfigSection()}
        <div className="form-group">
          <label className="form-label">API Base URL</label>
          <SafeInput className="form-control" value={aiConfigForm.apiBaseUrl} onValueChange={(value) => setAiConfigForm({ ...aiConfigForm, apiBaseUrl: value })} />
        </div>
        <div className="form-group">
          <label className="form-label">API Key</label>
          <SafeInput className="form-control" value={aiConfigForm.apiKey} onValueChange={(value) => setAiConfigForm({ ...aiConfigForm, apiKey: value })} placeholder={dispatchAiWorkbench?.settings.maskedApiKey ? `已配置：${dispatchAiWorkbench.settings.maskedApiKey}，留空表示不修改` : "请输入 API Key"} />
        </div>
        <div className="form-group">
          <label className="form-label">模型名</label>
          <SafeInput className="form-control" value={aiConfigForm.aiModel} onValueChange={(value) => setAiConfigForm({ ...aiConfigForm, aiModel: value })} />
        </div>
        <div className="form-group">
          <label className="form-label">低余额提醒阈值</label>
          <SafeInput className="form-control" value={aiConfigForm.lowBalanceThreshold} onValueChange={(value) => setAiConfigForm({ ...aiConfigForm, lowBalanceThreshold: value })} />
        </div>
        <div className="form-group">
          <label className="form-label">AI 提示词</label>
          <SafeTextarea className="form-control" style={{ height: 180 }} value={aiConfigForm.aiPromptTemplate} onValueChange={(value) => setAiConfigForm({ ...aiConfigForm, aiPromptTemplate: value })} />
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
