// @vitest-environment jsdom

import React from "react";
import { act } from "react";
import { createRoot } from "react-dom/client";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { SystemSettingsSectionPage } from "./SystemSettingsSectionPage";

(globalThis as typeof globalThis & { IS_REACT_ACT_ENVIRONMENT?: boolean }).IS_REACT_ACT_ENVIRONMENT = true;

function deferred<T>() {
  let resolve!: (value: T | PromiseLike<T>) => void;
  let reject!: (reason?: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

const {
  dispatchAiWorkbenchResponse,
  deleteDispatchAreaMemory,
  fetchDispatchAreaCodes,
  fetchDispatchAreaMemories,
  fetchDispatchAreaMemorySources,
  fetchDispatchAiWorkbench,
  fetchDispatchAiJobLog,
  fetchMaintenanceLogs,
  fetchMaintenanceOverview,
  swrFetcher,
  fetchOperationSettings,
  maintenanceLogsResponse,
  maintenanceOverviewResponse,
  runDispatchAiNow,
  settingsResponse,
  updateDispatchRouteWorkbench,
  updateDispatchAiWorkbench,
  updateDispatchAreaMemory,
  updatePackageReminderSettings
} = vi.hoisted(() => ({
  fetchDispatchAreaCodes: vi.fn(),
  fetchDispatchAreaMemories: vi.fn(),
  fetchDispatchAreaMemorySources: vi.fn(),
  fetchOperationSettings: vi.fn(),
  fetchDispatchAiWorkbench: vi.fn(),
  fetchDispatchAiJobLog: vi.fn(),
  fetchMaintenanceOverview: vi.fn(),
  fetchMaintenanceLogs: vi.fn(),
  swrFetcher: vi.fn(),
  runDispatchAiNow: vi.fn(),
  updateDispatchRouteWorkbench: vi.fn(),
  updateDispatchAiWorkbench: vi.fn(),
  updateDispatchAreaMemory: vi.fn(),
  deleteDispatchAreaMemory: vi.fn(),
  settingsResponse: {
    orderingEnabled: true,
    orderingStatusLabel: "",
    holidayNoticeTitle: "锁定公告",
    holidayNoticeDesc: "用户进入后仅可查看锁定公告内容",
    emergencyActionLabel: "",
    bannerImages: "[{\"imageUrl\":\"/uploads/banner.jpg\",\"enabled\":true}]",
    bannerIntervalSeconds: 3,
    packageExpiryReminderDays: 7,
    packageLowBalanceThreshold: 3,
    mealReminderPopupEnabled: true,
    deliverySubscribeEnabled: true,
    deliverySubscribeLunchTime: "11:30",
    deliverySubscribeDinnerTime: "17:30",
    popupAnnouncementEnabled: false,
    popupAnnouncementContent: "",
    restNoticeTemplate: ""
  },
  dispatchAiWorkbenchResponse: {
    settings: {
      autoScheduleEnabled: true,
      autoScheduleTime: "00:05",
      defaultStrategyMode: "NEAR_TO_FAR",
      anchorName: "五环天地",
      anchorAddress: "五环天地",
      aiEnabled: true,
      apiBaseUrl: "https://api.deepseek.com",
      maskedApiKey: "sk-****1234",
      aiModel: "deepseek-chat",
      aiPromptTemplate: "prompt",
      balanceCurrency: "CNY",
      balanceAvailable: true,
      totalBalance: "88.00",
      grantedBalance: "8.00",
      toppedUpBalance: "80.00",
      balanceCheckedAt: "2026-06-30 10:00:00",
      lowBalanceThreshold: "20.00"
    },
    recentLogs: [
      {
        id: 1,
        runType: "PRODUCTION",
        triggerSource: "MANUAL",
        serveDate: "2026-07-01",
        mealPeriod: "LUNCH",
        areaCode: "A01",
        suggestionId: 9,
        status: "SUCCESS",
        suggestionSource: "AI_ONLY",
        reasonSummary: "先处理软件园，再收回五环天地周边",
        message: "执行完成",
        metadataJson: "{\"runStatusCode\":\"AI_SUCCESS\",\"runStatusLabel\":\"AI 已完成排线\",\"runStatusDescription\":\"先处理软件园，再收回五环天地周边\",\"summary\":\"先处理软件园，再收回五环天地周边\",\"confidence\":0.92,\"analysisSteps\":[{\"type\":\"context_read\",\"title\":\"读取区域上下文\",\"message\":\"当前以软件园片区为主\"},{\"type\":\"sequencing\",\"title\":\"生成最终顺序\",\"message\":\"先聚合同片区，再回到锚点周边\"}],\"groups\":[{\"groupName\":\"软件园片区\",\"orderIds\":[1,2]}],\"finalOrderIds\":[1,2],\"perOrderReasons\":[{\"orderId\":1,\"reason\":\"与 2 同片区，适合作为起送点\"},{\"orderId\":2,\"reason\":\"紧接 1 处理，减少折返\"}],\"items\":[{\"orderId\":1,\"suggestedSequence\":1,\"aiAdjusted\":true,\"ruleSequence\":1,\"addressLabel\":\"A栋101\",\"clusterName\":\"A栋\",\"buildingName\":\"1单元\",\"roadName\":\"主路\",\"distanceBand\":\"\",\"neighborCount\":2,\"adjustmentReason\":\"与 2 同片区，适合作为起送点\"},{\"orderId\":2,\"suggestedSequence\":2,\"aiAdjusted\":true,\"ruleSequence\":2,\"addressLabel\":\"A栋102\",\"clusterName\":\"A栋\",\"buildingName\":\"1单元\",\"roadName\":\"主路\",\"distanceBand\":\"\",\"neighborCount\":1,\"adjustmentReason\":\"紧接 1 处理，减少折返\"}]}",
        executedBy: "admin",
        startedAt: "2026-06-30 10:00:00",
        finishedAt: "2026-06-30 10:00:05"
      }
    ]
  },
  maintenanceOverviewResponse: {
    latestManual: null,
    latestAuto: null,
    latestCloudReceipt: null,
    latestCloudStorage: null,
    cleanupRules: [],
    nextAutoRunLabel: "每日 03:00"
  },
  maintenanceLogsResponse: [],
  updatePackageReminderSettings: vi.fn()
}));

vi.mock("../../shared/api/http", () => ({
  fetchDispatchAiWorkbench,
  fetchDispatchAreaCodes,
  fetchDispatchAreaMemories,
  fetchDispatchAreaMemorySources,
  fetchDispatchAiJobLog,
  fetchMaintenanceLogs,
  fetchMaintenanceOverview,
  swrFetcher,
  fetchOperationSettings,
  refreshDispatchAiBalance: vi.fn(),
  runDispatchAiNow,
  simulateRouteLab: vi.fn(),
  triggerDataCleanup: vi.fn(),
  triggerMaintenanceModuleCleanup: vi.fn(),
  updateDispatchRouteWorkbench,
  updateDispatchAiWorkbench,
  updateDispatchAreaMemory,
  deleteDispatchAreaMemory,
  updateMaintenanceCleanupSettings: vi.fn(),
  updateBannerImages: vi.fn(),
  updatePackageReminderSettings,
  updatePopupAnnouncement: vi.fn(),
  uploadBannerImage: vi.fn()
}));

vi.mock("../../shared/components/Toast", () => ({
  toast: vi.fn()
}));

function renderIntoDom(element: React.ReactElement) {
  const container = document.createElement("div");
  document.body.appendChild(container);
  const root = createRoot(container);
  act(() => {
    root.render(element);
  });
  return {
    container,
    unmount() {
      act(() => {
        root.unmount();
      });
      container.remove();
    }
  };
}

function renderAtPath(path: string) {
  return renderIntoDom(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/settings/:section" element={<SystemSettingsSectionPage />} />
      </Routes>
    </MemoryRouter>
  );
}

function clickByText(container: HTMLElement, text: string) {
  const element = Array.from(container.querySelectorAll("button"))
    .find((node) => node.textContent?.includes(text));
  if (!element) {
    throw new Error(`未找到按钮: ${text}`);
  }
  act(() => {
    element.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
}

function findFieldGroup(container: HTMLElement, labelText: string) {
  const label = Array.from(container.querySelectorAll("label"))
    .find((node) => node.textContent?.includes(labelText));
  if (!label?.parentElement) {
    throw new Error(`未找到字段: ${labelText}`);
  }
  return label.parentElement;
}

function setInputValue(container: HTMLElement, labelText: string, value: string) {
  const fieldGroup = findFieldGroup(container, labelText);
  const input = fieldGroup.querySelector("input");
  if (!input) {
    throw new Error(`字段 ${labelText} 未找到 input`);
  }
  act(() => {
    const setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, "value")?.set;
    setter?.call(input, value);
    input.dispatchEvent(new Event("input", { bubbles: true }));
    input.dispatchEvent(new Event("change", { bubbles: true }));
  });
}

function getSelectValue(container: HTMLElement, labelText: string) {
  const fieldGroup = findFieldGroup(container, labelText);
  const select = fieldGroup.querySelector("select");
  if (!select) {
    throw new Error(`字段 ${labelText} 未找到 select`);
  }
  return select.value;
}

function selectAppSelectOption(container: HTMLElement, labelText: string, optionText: string) {
  const fieldGroup = findFieldGroup(container, labelText);
  const trigger = fieldGroup.querySelector(".app-select-trigger");
  if (!trigger) {
    throw new Error(`字段 ${labelText} 未找到 AppSelect`);
  }
  act(() => {
    trigger.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
  // AppSelect 用 createPortal 把下拉菜单渲染到 document.body，因此必须从 document 查找选项
  const option = Array.from(document.querySelectorAll(".app-select-option"))
    .find((node) => node.textContent?.includes(optionText));
  if (!option) {
    throw new Error(`字段 ${labelText} 未找到选项: ${optionText}`);
  }
  act(() => {
    option.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
}

beforeEach(() => {
  fetchOperationSettings.mockResolvedValue(settingsResponse);
  fetchDispatchAiWorkbench.mockResolvedValue(dispatchAiWorkbenchResponse);
  fetchDispatchAiJobLog.mockResolvedValue(dispatchAiWorkbenchResponse.recentLogs[0]);
  fetchDispatchAreaCodes.mockResolvedValue({
    areaCodes: ["B02", "A01"]
  });
  fetchDispatchAreaMemories.mockResolvedValue({
    areaCode: "B02",
    items: [
      {
        id: 1,
        memoryType: "ROUTE_PREFERENCE",
        title: "午餐先写字楼",
        summary: "A 区午餐高峰先写字楼后住宅",
        applicableScene: "ALL",
        weight: 2,
        status: "ACTIVE",
        updatedAt: "2026-07-04 11:00:00"
      }
    ]
  });
  fetchDispatchAreaMemorySources.mockResolvedValue({
    areaCode: "B02",
    memoryId: 1,
    memoryTitle: "午餐先写字楼",
    items: [
      {
        correctionId: 88,
        correctionMode: "MIXED",
        merchantInstruction: "午餐高峰先送写字楼",
        merchantReasonSummary: "A 区午餐先写字楼后住宅",
        aiInterpretationSummary: "AI 已理解午餐先写字楼再收住宅",
        replanStatus: "SUCCESS",
        confirmedAt: "2026-07-04 11:08:00"
      }
    ]
  });
  runDispatchAiNow.mockResolvedValue({ message: "执行完成" });
    fetchMaintenanceOverview.mockResolvedValue(maintenanceOverviewResponse);
    fetchMaintenanceLogs.mockResolvedValue(maintenanceLogsResponse);
    swrFetcher.mockImplementation(async (url: string) => {
      if (url === "/api/admin/maintenance/logs") {
        return { data: await fetchMaintenanceLogs() };
      }
      if (url === "/api/admin/maintenance/overview") {
        return { data: await fetchMaintenanceOverview() };
      }
      if (url.startsWith("/api/admin/dispatch/job-logs/")) {
        return await fetchDispatchAiJobLog();
      }
      return { data: null };
    });
  updateDispatchRouteWorkbench.mockReset();
  updateDispatchRouteWorkbench.mockResolvedValue(dispatchAiWorkbenchResponse);
  updateDispatchAiWorkbench.mockReset();
  updateDispatchAiWorkbench.mockResolvedValue(dispatchAiWorkbenchResponse);
  updateDispatchAreaMemory.mockReset();
  updateDispatchAreaMemory.mockResolvedValue({
    areaCode: "B02",
    items: [
      {
        id: 1,
        memoryType: "ROUTE_PREFERENCE",
        title: "午餐先写字楼",
        summary: "A 区午餐高峰先写字楼，再收住宅",
        applicableScene: "ALL",
        weight: 2,
        status: "ACTIVE",
        updatedAt: "2026-07-04 11:05:00"
      }
    ]
  });
  deleteDispatchAreaMemory.mockReset();
  deleteDispatchAreaMemory.mockResolvedValue({
    areaCode: "B02",
    items: []
  });
  runDispatchAiNow.mockReset();
  runDispatchAiNow.mockResolvedValue({ message: "执行完成" });
  updatePackageReminderSettings.mockReset();
});

afterEach(() => {
  document.body.innerHTML = "";
  vi.clearAllMocks();
});

describe("SystemSettingsPage", () => {
  it("shows loading state before settings are loaded", async () => {
    const view = renderAtPath("/settings/basic");

    expect(view.container.textContent).toContain("系统设置加载中...");
    expect(view.container.textContent).toContain("基础设置");
    expect(view.container.textContent).toContain("AI 智能调度");
    expect(view.container.textContent).toContain("系统维护");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(view.container.textContent).toContain("配置餐包提醒");
    view.unmount();
  });

  it("renders ai-dispatch section without basic overview cards", async () => {
    const view = renderAtPath("/settings/ai-dispatch");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(view.container.textContent).toContain("路线排版控制台");
    expect(view.container.textContent).toContain("排版规则");
    expect(view.container.textContent).toContain("排版详情");
    expect(view.container.textContent).toContain("运行历史");
    expect(view.container.textContent).not.toContain("配置餐包提醒");
    view.unmount();
  });

  it("renders maintenance section through the extracted reusable content", async () => {
    const view = renderAtPath("/settings/maintenance");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(view.container.textContent).toContain("系统维护");
    expect(view.container.textContent).toContain("清理中心总览");
    expect(view.container.textContent).not.toContain("运行日志");
    view.unmount();
  });

  it("renders area ai memory section and loads memory items", async () => {
    const view = renderAtPath("/settings/ai-dispatch");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    clickByText(view.container, "区域记忆");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(fetchDispatchAreaCodes).toHaveBeenCalledTimes(1);
    expect(fetchDispatchAreaMemories).toHaveBeenCalledWith("B02");
    expect(view.container.textContent).toContain("区域 AI 记忆");
    expect(view.container.textContent).toContain("午餐先写字楼");
    expect(view.container.textContent).toContain("A 区午餐高峰先写字楼后住宅");
    view.unmount();
  });

  it("shows memory sources in the area ai memory section", async () => {
    const view = renderAtPath("/settings/ai-dispatch");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    clickByText(view.container, "区域记忆");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    clickByText(view.container, "查看来源");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(fetchDispatchAreaMemorySources).toHaveBeenCalledWith(1);
    expect(view.container.textContent).toContain("来源纠偏");
    expect(view.container.textContent).toContain("纠偏 #88");
    expect(view.container.textContent).toContain("午餐高峰先送写字楼");
    view.unmount();
  });

  it("deletes memory from the area ai memory section", async () => {
    const confirmSpy = vi.spyOn(window, "confirm").mockReturnValue(true);
    const view = renderAtPath("/settings/ai-dispatch");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    clickByText(view.container, "区域记忆");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    clickByText(view.container, "删除");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(deleteDispatchAreaMemory).toHaveBeenCalledWith(1);
    expect(view.container.textContent).not.toContain("午餐先写字楼");
    confirmSpy.mockRestore();
    view.unmount();
  });

  it("uses a dedicated submitting state for package reminder modal", async () => {
    const pending = deferred<typeof settingsResponse>();
    updatePackageReminderSettings.mockReturnValueOnce(pending.promise);
    const view = renderAtPath("/settings/basic");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    clickByText(view.container, "配置餐包提醒");
    clickByText(view.container, "保存策略");

    expect(view.container.textContent).toContain("加载中...");

    pending.resolve(settingsResponse);
    await act(async () => {
      await pending.promise;
    });

    view.unmount();
  });

  it("allows saving dispatch settings when auto schedule is disabled", async () => {
    fetchDispatchAiWorkbench.mockResolvedValueOnce({
      settings: {
        autoScheduleEnabled: false,
        autoScheduleTime: "",
        defaultStrategyMode: "NEAR_TO_FAR",
        anchorName: "五环天地",
        anchorAddress: "五环天地",
        aiEnabled: false,
        apiBaseUrl: "",
        maskedApiKey: "",
        aiModel: "",
        aiPromptTemplate: "",
        balanceCurrency: "CNY",
        balanceAvailable: false,
        totalBalance: "0",
        grantedBalance: "0",
        toppedUpBalance: "0",
        balanceCheckedAt: "",
        lowBalanceThreshold: "20.00"
      },
      recentLogs: []
    });
    const view = renderAtPath("/settings/ai-dispatch");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    clickByText(view.container, "排版规则");
    clickByText(view.container, "保存路线配置");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(updateDispatchRouteWorkbench).toHaveBeenCalledTimes(1);
    expect(updateDispatchRouteWorkbench).toHaveBeenCalledWith(expect.objectContaining({
      autoScheduleEnabled: false,
      autoScheduleTime: "00:05",
      defaultStrategyMode: "NEAR_TO_FAR",
      anchorAddress: "五环天地"
    }));

    view.unmount();
  });

  it("renders latest production result from recent logs", async () => {
    const view = renderAtPath("/settings/ai-dispatch");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(view.container.textContent).toContain("AI 智能调度");
    expect(view.container.textContent).toContain("最终顺序");
    expect(view.container.textContent).toContain("AI已参与");
    expect(view.container.textContent).toContain("AI 已完成排线");
    view.unmount();
  });

  it("renders ai thinking page as conversation style debugger", async () => {
    fetchDispatchAiWorkbench.mockResolvedValueOnce({
      ...dispatchAiWorkbenchResponse,
      recentLogs: [
        {
          id: 2,
          runType: "TEST",
          status: "SUCCESS",
          message: "执行完成",
          metadataJson: "{\"runStatusCode\":\"AI_SUCCESS\",\"runStatusLabel\":\"AI 已完成排线\",\"currentPhase\":\"排线结论\",\"summary\":\"先处理软件园，再收回五环天地周边\",\"analysisSteps\":[{\"type\":\"context_read\",\"title\":\"读取区域上下文\",\"message\":\"当前以软件园片区为主\"}],\"items\":[]}"
        }
      ]
    });
    fetchDispatchAiJobLog.mockResolvedValueOnce({
      id: 2,
      runType: "TEST",
      status: "SUCCESS",
      message: "执行完成",
      metadataJson: "{\"runStatusCode\":\"AI_SUCCESS\",\"runStatusLabel\":\"AI 已完成排线\",\"currentPhase\":\"排线结论\",\"summary\":\"先处理软件园，再收回五环天地周边\",\"analysisSteps\":[{\"type\":\"context_read\",\"title\":\"读取区域上下文\",\"message\":\"当前以软件园片区为主\"}],\"items\":[]}"
    });

    const view = renderAtPath("/settings/ai-dispatch");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    clickByText(view.container, "测试实验");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(view.container.textContent).toContain("AI 对话记录");
    expect(view.container.textContent).toContain("读取区域上下文");
    expect(view.container.textContent).toContain("排线结论");
    expect(view.container.textContent).toContain("先处理软件园，再收回五环天地周边");
    view.unmount();
  });

  it("runs production dispatch through the run-now modal", async () => {
    const view = renderAtPath("/settings/ai-dispatch");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    await act(async () => {
      clickByText(view.container, "立刻执行");
      await new Promise((resolve) => setTimeout(resolve, 0));
      // ensureDispatchAreaCodesLoaded 是异步的：fetch 后再 setState 触发重渲染，需要多等一个 tick
      await new Promise((resolve) => setTimeout(resolve, 0));
    });
    setInputValue(view.container, "执行日期", "2026-07-01");
    selectAppSelectOption(view.container, "执行区域", "A01");
    clickByText(view.container, "确认执行");

    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 0));
    });

    expect(runDispatchAiNow).toHaveBeenCalledTimes(1);
    expect(runDispatchAiNow).toHaveBeenCalledWith(expect.objectContaining({
      serveDate: "2026-07-01",
      areaCode: "A01"
    }));
    view.unmount();
  });
});
