import React, { useEffect, useMemo, useRef, useState } from "react";
import useSWR from "swr";
import { useBlocker } from "react-router-dom";
import { swrFetcher, createNextMenuWeek, copyMenuWeekFromLastWeek, publishMenuWeek, saveMenuWeekDay } from "../../shared/api/http";
import type { AdminMenuWeekDay, AdminMenuWeekResponse, AdminMenuWeekSlot } from "../../shared/api/types";
import { buildMenuWeekSummary, resolveWeekStatusLabel } from "./menuSchedulePage.helpers";
import { formatDateLabel, formatLocalDateInputValue, shiftLocalDateInputValue } from "../../shared/utils/dateTime";
import { AppSelect } from "../../shared/components/AppSelect";
import { AsyncContentView, type AsyncContentViewStatus } from "../../shared/components/AsyncContentView";
import { AdminDialog } from "../../shared/components/AdminDialog";
import { RemarkField } from "../../shared/components/RemarkField";
import { SafeInput } from "../../shared/components/SafeInput";
import { DatePicker } from "../../shared/components/DatePicker";
import { toast } from "../../shared/components/Toast";

type SlotDraft = {
  slotStatus: "ACTIVE" | "REST" | "UNCONFIGURED";
  dishItems: string[];
  totalCalories: number | null;
  merchantNote: string;
  imageUrl: string;
};

type DayDraft = {
  lunch: SlotDraft;
  dinner: SlotDraft;
};

type DayStatus = SlotDraft["slotStatus"];

const DAY_STATUS_OPTIONS = [
  { label: "正常营业", value: "ACTIVE" },
  { label: "待配置", value: "UNCONFIGURED" },
  { label: "休息", value: "REST" }
];

function toDraft(slot: AdminMenuWeekSlot): SlotDraft {
  return {
    slotStatus: slot.slotStatus,
    dishItems: slot.dishItems?.length ? slot.dishItems : [""],
    totalCalories: slot.totalCalories ?? null,
    merchantNote: slot.merchantNote || "",
    imageUrl: slot.imageUrl || ""
  };
}

function slotLabel(period: "LUNCH" | "DINNER") {
  return period === "LUNCH" ? "午餐" : "晚餐";
}

function cloneSlot(slot: SlotDraft): SlotDraft {
  return {
    slotStatus: slot.slotStatus,
    dishItems: [...slot.dishItems],
    totalCalories: slot.totalCalories,
    merchantNote: slot.merchantNote,
    imageUrl: slot.imageUrl
  };
}

function buildEmptySlot(status: DayStatus): SlotDraft {
  return {
    slotStatus: status,
    dishItems: [""],
    totalCalories: null,
    merchantNote: "",
    imageUrl: ""
  };
}

export function MenuSchedulePage() {
  const [targetDate, setTargetDate] = useState("");
  const [expandedDate, setExpandedDate] = useState<string | null>(null);
  const [drafts, setDrafts] = useState<Record<string, DayDraft>>({});
  const [currentWeekId, setCurrentWeekId] = useState<number | null>(null);

  const [isPublishConfirmOpen, setIsPublishConfirmOpen] = useState(false);
  const [copyingLastWeek, setCopyingLastWeek] = useState(false);
  const [creatingNextWeek, setCreatingNextWeek] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [savingDay, setSavingDay] = useState<string | null>(null);
  const [savingDirtyDays, setSavingDirtyDays] = useState(false);
  const [unsavedDialog, setUnsavedDialog] = useState<{
    onConfirm: (mode: "save" | "discard" | "cancel") => void;
  } | null>(null);

  const url = targetDate
    ? `/api/admin/menu-weeks/current?targetDate=${encodeURIComponent(targetDate)}`
    : "/api/admin/menu-weeks/current";

  const { data: response, error, isLoading: loading, mutate } = useSWR(
    url,
    swrFetcher,
    { revalidateOnFocus: false }
  );

  const week = response?.data as AdminMenuWeekResponse | undefined | null;
  const selectedDate = targetDate || week?.weekStartDate || "";

  useEffect(() => {
    if (error) {
      toast(error?.response?.data?.message || error.message || String(error), "error");
    }
  }, [error]);

  useEffect(() => {
    if (week) {
      const nextDrafts: Record<string, DayDraft> = {};
      week.days.forEach((day) => {
        nextDrafts[day.serveDate] = {
          lunch: toDraft(day.lunch),
          dinner: toDraft(day.dinner)
        };
      });
      setDrafts(nextDrafts);

      if (week.weekId !== currentWeekId) {
        setExpandedDate(null);
        setCurrentWeekId(week.weekId);
      }
    }
  }, [week, currentWeekId]);

  const dirtyDates = useMemo(() => {
    if (!week) {
      return [];
    }
    return week.days
      .filter((day) => {
        const draft = drafts[day.serveDate];
        if (!draft) {
          return false;
        }
        const baseline = {
          lunch: toDraft(day.lunch),
          dinner: toDraft(day.dinner)
        };
        return JSON.stringify(draft) !== JSON.stringify(baseline);
      })
      .map((day) => day.serveDate);
  }, [week, drafts]);

  const dirtyRef = useRef<string[]>([]);
  useEffect(() => {
    dirtyRef.current = dirtyDates;
  }, [dirtyDates]);

  // 刷新 / 关闭页面前提示
  useEffect(() => {
    const handler = (event: BeforeUnloadEvent) => {
      if (dirtyRef.current.length > 0) {
        event.preventDefault();
        event.returnValue = "";
      }
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, []);

  async function saveDirtyDays() {
    if (!week || dirtyDates.length === 0) {
      return;
    }
    await Promise.all(
      dirtyDates.map((serveDate) => saveMenuWeekDay(week.weekId, serveDate, drafts[serveDate]))
    );
  }

  function guardUnsaved(action: () => void) {
    if (dirtyDates.length === 0) {
      action();
      return;
    }
    setUnsavedDialog({
      onConfirm: (mode) => {
        if (mode === "cancel") {
          setUnsavedDialog(null);
          return;
        }
        if (mode === "discard") {
          setUnsavedDialog(null);
          action();
          return;
        }
        setSavingDirtyDays(true);
        saveDirtyDays()
          .then(() => mutate())
          .then(() => {
            setUnsavedDialog(null);
            toast("修改已保存", "success");
            action();
          })
          .catch((err: any) => {
            toast(err?.response?.data?.message || err.message || String(err), "error");
          })
          .finally(() => {
            setSavingDirtyDays(false);
          });
      }
    });
  }

  // 路由离开页面拦截（侧边栏切换菜单等）
  const blocker = useBlocker(dirtyDates.length > 0);
  useEffect(() => {
    if (blocker.state !== "blocked") {
      return;
    }
    setUnsavedDialog({
      onConfirm: (mode) => {
        if (mode === "cancel") {
          setUnsavedDialog(null);
          blocker.reset();
          return;
        }
        if (mode === "discard") {
          setUnsavedDialog(null);
          blocker.proceed();
          return;
        }
        setSavingDirtyDays(true);
        saveDirtyDays()
          .then(() => mutate())
          .then(() => {
            setUnsavedDialog(null);
            toast("修改已保存", "success");
            blocker.proceed();
          })
          .catch((err: any) => {
            toast(err?.response?.data?.message || err.message || String(err), "error");
            blocker.reset();
          })
          .finally(() => {
            setSavingDirtyDays(false);
          });
      }
    });
  }, [blocker, week, dirtyDates, drafts]);

  async function handleCreateNextWeek() {
    if (creatingNextWeek) {
      return;
    }
    setCreatingNextWeek(true);
    try {
      await createNextMenuWeek();
      setTargetDate(nextWeekDate());
      toast("下周空白模板已创建", "success");
    } catch (err: any) {
      toast(err?.response?.data?.message || err.message || String(err), "error");
    } finally {
      setCreatingNextWeek(false);
    }
  }

  async function handleCopyFromLastWeek() {
    if (copyingLastWeek) {
      return;
    }
    setCopyingLastWeek(true);
    try {
      await copyMenuWeekFromLastWeek();
      await mutate();
      toast("已将上周菜单复制到本周", "success");
    } catch (err: any) {
      toast(err?.response?.data?.message || err.message || String(err), "error");
    } finally {
      setCopyingLastWeek(false);
    }
  }

  async function handlePublish() {
    if (!week) return;
    if (publishing) {
      return;
    }
    setPublishing(true);
    try {
      setIsPublishConfirmOpen(false);
      await persistDraftDaysBeforePublish(week.weekId);
      await publishMenuWeek(week.weekId);
      await mutate();
      toast(`${week.weekStartDate} ~ ${week.weekEndDate} 菜单已发布`, "success");
    } catch (err: any) {
      toast(err?.response?.data?.message || err.message || String(err), "error");
    } finally {
      setPublishing(false);
    }
  }

  const isPublished = week?.status === "PUBLISHED";
  const publishButtonText = isPublished ? "更新菜单" : "发布菜单";
  const publishConfirmTitle = isPublished ? "确认更新菜单" : "确认发布菜单";
  const publishConfirmMessage = isPublished
    ? `确认更新「${week?.weekStartDate} ~ ${week?.weekEndDate}」的菜单？\n小程序将立即展示更新后的内容。`
    : `确认发布「${week?.weekStartDate} ~ ${week?.weekEndDate}」的菜单？\n发布后小程序将立即展示新菜单。`;

  function updateSlot(serveDate: string, mealPeriod: "lunch" | "dinner", key: keyof SlotDraft, value: SlotDraft[keyof SlotDraft]) {
    setDrafts((current) => ({
      ...current,
      [serveDate]: {
        ...current[serveDate],
        [mealPeriod]: {
          ...current[serveDate][mealPeriod],
          [key]: value
        }
      }
    }));
  }

  function setDayStatus(serveDate: string, status: DayStatus) {
    setDrafts((current) => {
      const day = current[serveDate];
      return {
        ...current,
        [serveDate]: {
          lunch: { ...day.lunch, slotStatus: status },
          dinner: { ...day.dinner, slotStatus: status }
        }
      };
    });
  }

  function setDayRest(serveDate: string) {
    setDrafts((current) => ({
      ...current,
      [serveDate]: {
        lunch: { ...cloneSlot(current[serveDate].lunch), slotStatus: "REST", dishItems: [], totalCalories: null, merchantNote: "", imageUrl: "" },
        dinner: { ...cloneSlot(current[serveDate].dinner), slotStatus: "REST", dishItems: [], totalCalories: null, merchantNote: "", imageUrl: "" }
      }
    }));
  }

  function clearDay(serveDate: string) {
    setDrafts((current) => ({
      ...current,
      [serveDate]: {
        lunch: buildEmptySlot("UNCONFIGURED"),
        dinner: buildEmptySlot("UNCONFIGURED")
      }
    }));
  }

  function updateDishItem(serveDate: string, mealPeriod: "lunch" | "dinner", index: number, value: string) {
    setDrafts((current) => {
      const nextItems = [...current[serveDate][mealPeriod].dishItems];
      nextItems[index] = value;
      return {
        ...current,
        [serveDate]: {
          ...current[serveDate],
          [mealPeriod]: {
            ...current[serveDate][mealPeriod],
            dishItems: nextItems
          }
        }
      };
    });
  }

  function addDishItem(serveDate: string, mealPeriod: "lunch" | "dinner") {
    setDrafts((current) => ({
      ...current,
      [serveDate]: {
        ...current[serveDate],
        [mealPeriod]: {
          ...current[serveDate][mealPeriod],
          dishItems: [...current[serveDate][mealPeriod].dishItems, ""]
        }
      }
    }));
  }

  function removeDishItem(serveDate: string, mealPeriod: "lunch" | "dinner", index: number) {
    setDrafts((current) => {
      const nextItems = current[serveDate][mealPeriod].dishItems.filter((_, itemIndex) => itemIndex !== index);
      return {
        ...current,
        [serveDate]: {
          ...current[serveDate],
          [mealPeriod]: {
            ...current[serveDate][mealPeriod],
            dishItems: nextItems.length ? nextItems : [""]
          }
        }
      };
    });
  }

  async function handleSaveDay(serveDate: string) {
    if (!week) return;
    if (savingDay === serveDate) {
      return;
    }
    setSavingDay(serveDate);
    try {
      await saveMenuWeekDay(week.weekId, serveDate, drafts[serveDate]);
      await mutate();
      setExpandedDate(serveDate);
      toast("保存成功", "success");
    } catch (err: any) {
      toast(err?.response?.data?.message || err.message || String(err), "error");
    } finally {
      setSavingDay(null);
    }
  }

  async function persistDraftDaysBeforePublish(weekId: number) {
    const draftEntries = Object.entries(drafts);
    if (draftEntries.length === 0) {
      return;
    }
    await Promise.all(
      draftEntries.map(([serveDate, draft]) => saveMenuWeekDay(weekId, serveDate, draft))
    );
  }

  function handlePickWeek(targetDate: string) {
    if (!targetDate) return;
    setTargetDate(targetDate);
  }

  function handlePickWeekGuarded(targetDate: string) {
    guardUnsaved(() => handlePickWeek(targetDate));
  }

  function handleToggleDay(serveDate: string) {
    if (expandedDate === serveDate) {
      setExpandedDate(null);
      return;
    }
    guardUnsaved(() => setExpandedDate(serveDate));
  }

  const pageTitle = useMemo(() => {
    if (!week) return "周菜单管理";
    return `周菜单管理 (${week.weekStartDate} ~ ${week.weekEndDate})`;
  }, [week]);

  const weekSummary = useMemo(
    () => (week ? buildMenuWeekSummary(week) : { activeSlotCount: 0, configuredDayCount: 0, restDayCount: 0, unconfiguredSlotCount: 0, completionRate: "0%" }),
    [week]
  );

  function renderDishEditor(serveDate: string, mealPeriod: "lunch" | "dinner", slot: SlotDraft) {
    return (
      <div className="menu-slot-editor">
        <div className="menu-slot-editor__title">{slotLabel(mealPeriod === "lunch" ? "LUNCH" : "DINNER")}</div>
        <div className="form-group">
          <label className="form-label">菜品列表</label>
          {slot.dishItems.map((dish, index) => (
            <div key={`${mealPeriod}-${index}`} className="menu-slot-editor__dish-row">
              <SafeInput className="form-control" value={dish} onValueChange={(value) => updateDishItem(serveDate, mealPeriod, index, value)} placeholder={`第 ${index + 1} 道菜`} />
              <button className="btn btn-outline" type="button" onClick={() => removeDishItem(serveDate, mealPeriod, index)}>删掉</button>
            </div>
          ))}
          <button className="btn btn-outline" type="button" onClick={() => addDishItem(serveDate, mealPeriod)}>添加一道菜</button>
        </div>
        <div className="menu-slot-editor__split">
          <div className="form-group menu-slot-editor__field">
            <label className="form-label">总热量</label>
            <SafeInput className="form-control" type="number" value={slot.totalCalories ?? ""} onValueChange={(value) => updateSlot(serveDate, mealPeriod, "totalCalories", value ? Number(value) : null)} />
          </div>
          <div className="form-group menu-slot-editor__field">
            <RemarkField
              label="备注"
              value={slot.merchantNote}
              onChange={(value) => updateSlot(serveDate, mealPeriod, "merchantNote", value)}
              scene="MENU_NOTE"
            />
          </div>
        </div>
      </div>
    );
  }

  const menuBoardStatus: AsyncContentViewStatus = loading
    ? "loading"
    : !week?.days.length
      ? "empty"
      : "success";

  return (
    <>
      <div className="page-header">
        <div>
          <h2 className="page-title">{pageTitle}</h2>
          <p className="page-subtitle">周菜单配置、休息日与发布状态</p>
        </div>
        <div className="page-header__actions">
          <button className="btn btn-outline" disabled={copyingLastWeek} onClick={() => guardUnsaved(() => { handleCopyFromLastWeek().catch((err) => toast(err?.response?.data?.message || err.message || String(err), "error")); })}>{copyingLastWeek ? "复制中..." : "复制上周菜单到本周"}</button>
          <button className="btn btn-outline" disabled={creatingNextWeek} onClick={() => guardUnsaved(() => { handleCreateNextWeek().catch((err) => toast(err?.response?.data?.message || err.message || String(err), "error")); })}>{creatingNextWeek ? "创建中..." : "新建下周空白模板"}</button>
          <button className="btn btn-primary" onClick={() => setIsPublishConfirmOpen(true)} disabled={!week || publishing}>{publishing ? (isPublished ? "更新中..." : "发布中...") : publishButtonText}</button>
        </div>
      </div>
      <div className="stat-row">
        <div className="stat-card">
          <div className="stat-title">已配置餐槽</div>
          <div className="stat-val stat-val--success">{weekSummary.activeSlotCount} <span>个</span></div>
          <div className="stat-footer">菜单完整度 {weekSummary.completionRate}</div>
        </div>
        <div className="stat-card">
          <div className="stat-title">待配置餐槽</div>
          <div className="stat-val stat-val--warning">{weekSummary.unconfiguredSlotCount} <span>个</span></div>
          <div className="stat-footer">未配置</div>
        </div>
        <div className="stat-card">
          <div className="stat-title">已完成天数</div>
          <div className="stat-val stat-val--primary">{weekSummary.configuredDayCount} <span>天</span></div>
          <div className="stat-footer">含休息日 {weekSummary.restDayCount} 天</div>
        </div>
        <div className="stat-card">
          <div className="stat-title">周状态</div>
          <div className={`stat-val ${isPublished ? "stat-val--success" : "stat-val--warning"}`} style={{ fontSize: "22px", fontWeight: 800 }}>
            {week ? resolveWeekStatusLabel(week.status) : "加载中"}
          </div>
          <div className="stat-footer">{week ? `${week.weekStartDate} ~ ${week.weekEndDate}` : "等待数据"}</div>
        </div>
      </div>

      <div className="table-container menu-week-toolbar">
        <button
          className="btn btn-outline"
          onClick={() => {
            handlePickWeekGuarded(shiftLocalDateInputValue(selectedDate, -7));
          }}
        >‹ 上一周</button>
        <DatePicker
          value={selectedDate}
          onChange={(date) => handlePickWeekGuarded(date)}
          showTomorrowShortcut={false}
        />
        <span className="menu-week-toolbar__range">
          {week ? `${week.weekStartDate} ~ ${week.weekEndDate}` : selectedDate}
        </span>
        <button
          className="btn btn-outline"
          onClick={() => {
            handlePickWeekGuarded(shiftLocalDateInputValue(selectedDate, 7));
          }}
        >下一周 ›</button>
      </div>
      <div className="table-container menu-week-board">
        {menuBoardStatus !== "success" ? (
          <AsyncContentView
            status={menuBoardStatus}
            loadingText="菜单加载中..."
            emptyText="当前没有可编辑的周菜单"
          />
        ) : (
          week?.days.map((day: AdminMenuWeekDay) => {
          const draft = drafts[day.serveDate];
          const expanded = expandedDate === day.serveDate;
          const hasMenu = day.lunch.slotStatus === "ACTIVE" || day.dinner.slotStatus === "ACTIVE";
          const isRest = day.lunch.slotStatus === "REST" && day.dinner.slotStatus === "REST";
          const hasGap = day.lunch.slotStatus === "UNCONFIGURED" || day.dinner.slotStatus === "UNCONFIGURED";
          return (
            <div
              key={day.serveDate}
              className={`menu-week-day-card ${expanded ? "is-expanded" : ""} ${isRest ? "menu-week-day-card--rest" : hasGap ? "menu-week-day-card--gap" : "menu-week-day-card--complete"}`}
              onClick={() => handleToggleDay(day.serveDate)}
            >
              <div className="menu-week-day-card__header">
                <div>
                  <div className="menu-week-day-card__title-row">
                    <span>{day.weekdayLabel}</span>
                    {isRest && <span className="tag tag-gray">休息日</span>}
                    {!isRest && hasGap && <span className="tag tag-orange">待补齐</span>}
                    {!isRest && !hasGap && <span className="tag tag-green">已完善</span>}
                  </div>
                  <div className="menu-week-day-card__date">{formatDateLabel(day.serveDate)}</div>
                </div>
                <button className="btn btn-outline" onClick={(event) => { event.stopPropagation(); handleToggleDay(day.serveDate); }}>{expanded ? "收起编辑" : "编辑"}</button>
              </div>
              <div className="menu-week-day-card__content">
                {isRest ? (
                  <div className="menu-week-day-card__slot-preview menu-week-day-card__slot-preview--rest">
                    <strong>今日状态：</strong>全天休息
                  </div>
                ) : (
                  <>
                    <div className={`menu-week-day-card__slot-preview menu-week-day-card__slot-preview--${day.lunch.slotStatus.toLowerCase()}`}>
                      <strong>午餐：</strong>{day.lunch.slotStatus === "ACTIVE" ? day.lunch.dishItems.join(" / ") : day.lunch.slotStatus === "REST" ? "休息" : "待配置"}
                    </div>
                    <div className={`menu-week-day-card__slot-preview menu-week-day-card__slot-preview--${day.dinner.slotStatus.toLowerCase()}`}>
                      <strong>晚餐：</strong>{day.dinner.slotStatus === "ACTIVE" ? day.dinner.dishItems.join(" / ") : day.dinner.slotStatus === "REST" ? "休息" : "待配置"}
                    </div>
                  </>
                )}
                {!hasMenu && !isRest && <div className="admin-empty-note">未配置</div>}
              </div>
              {expanded && draft && (
                <div className="menu-week-day-card__editor" onClick={(event) => event.stopPropagation()}>
                  <div className="menu-day-status-editor">
                    <label className="form-label">全天状态</label>
                    <AppSelect
                      value={draft.lunch.slotStatus}
                      options={DAY_STATUS_OPTIONS}
                      onChange={(value) => setDayStatus(day.serveDate, value as DayStatus)}
                    />
                    <span className="menu-day-status-editor__hint">{draft.lunch.slotStatus === "REST" ? "休息则全天休息，午餐和晚餐都不出餐" : "状态同时应用于午餐和晚餐"}</span>
                  </div>
                  {draft.lunch.slotStatus === "REST" && draft.dinner.slotStatus === "REST" ? (
                    <div className="menu-day-rest-hint">🌿 今日休息，不提供餐食</div>
                  ) : (
                    <>
                      {renderDishEditor(day.serveDate, "lunch", draft.lunch)}
                      {renderDishEditor(day.serveDate, "dinner", draft.dinner)}
                    </>
                  )}
                  <div className="menu-week-day-card__actions">
                    <button className="btn btn-primary" disabled={savingDay === day.serveDate} onClick={(event) => { event.stopPropagation(); handleSaveDay(day.serveDate).catch((err) => toast(err?.response?.data?.message || err.message || String(err), "error")); }}>
                      {savingDay === day.serveDate ? "保存中..." : "保存"}
                    </button>
                    <button className="btn btn-outline" disabled={savingDay === day.serveDate} onClick={(event) => { event.stopPropagation(); clearDay(day.serveDate); }}>一键清空</button>
                    <button className="btn btn-outline" disabled={savingDay === day.serveDate} onClick={(event) => { event.stopPropagation(); setDayRest(day.serveDate); }}>设为休息</button>
                  </div>
                </div>
              )}
            </div>
          );
        }))}
      </div>

      {isPublishConfirmOpen && (
        <AdminDialog
          open={isPublishConfirmOpen}
          title={publishConfirmTitle}
          width={460}
          disableOverlayClose={publishing}
          closeDisabled={publishing}
          onClose={() => setIsPublishConfirmOpen(false)}
          footer={
            <>
              <button className="btn btn-outline" disabled={publishing} onClick={() => setIsPublishConfirmOpen(false)}>取消</button>
              <button className="btn btn-primary" disabled={publishing} onClick={() => handlePublish().catch((err) => toast(err?.response?.data?.message || err.message || String(err), "error"))}>
                {publishing ? (isPublished ? "更新中..." : "发布中...") : isPublished ? "确认更新" : "确认发布"}
              </button>
            </>
          }
        >
          <p style={{ whiteSpace: "pre-line", lineHeight: 1.6, margin: 0 }}>{publishConfirmMessage}</p>
        </AdminDialog>
      )}

      {unsavedDialog && (
        <AdminDialog
          open
          title="有未保存的修改"
          width={460}
          disableOverlayClose={savingDirtyDays}
          closeDisabled={savingDirtyDays}
          onClose={() => unsavedDialog.onConfirm("cancel")}
          footer={
            <>
              <button className="btn btn-outline" disabled={savingDirtyDays} onClick={() => unsavedDialog.onConfirm("cancel")}>取消</button>
              <button className="btn btn-outline" disabled={savingDirtyDays} onClick={() => unsavedDialog.onConfirm("discard")}>不保存</button>
              <button className="btn btn-primary" disabled={savingDirtyDays} onClick={() => unsavedDialog.onConfirm("save")}>
                {savingDirtyDays ? "保存中..." : "保存"}
              </button>
            </>
          }
        >
          <p style={{ whiteSpace: "pre-line", lineHeight: 1.6, margin: 0 }}>
            {`您有未保存的菜单修改${dirtyDates.length > 1 ? `（${dirtyDates.length} 天）` : ""}，是否保存后再继续？`}
          </p>
        </AdminDialog>
      )}
    </>
  );
}

function nextWeekDate() {
  const date = new Date();
  date.setDate(date.getDate() + 7);
  return formatLocalDateInputValue(date);
}
