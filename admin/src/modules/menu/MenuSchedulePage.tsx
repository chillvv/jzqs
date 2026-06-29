import React, { useEffect, useMemo, useState } from "react";
import { createNextMenuWeek, copyMenuWeekFromLastWeek, fetchCurrentMenuWeek, publishMenuWeek, saveMenuWeekDay } from "../../shared/api/http";
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

export function MenuSchedulePage() {
  const [week, setWeek] = useState<AdminMenuWeekResponse | null>(null);
  const [expandedDate, setExpandedDate] = useState<string | null>(null);
  const [drafts, setDrafts] = useState<Record<string, DayDraft>>({});
  const [loading, setLoading] = useState(false);
  const [selectedDate, setSelectedDate] = useState("");
  const [isPublishConfirmOpen, setIsPublishConfirmOpen] = useState(false);
  const [copyingLastWeek, setCopyingLastWeek] = useState(false);
  const [creatingNextWeek, setCreatingNextWeek] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [savingDay, setSavingDay] = useState<string | null>(null);

  useEffect(() => {
    reloadMenu().catch((err) => toast(err?.response?.data?.message || err.message || String(err), "error"));
  }, []);


  async function reloadMenu(targetDate?: string) {
    setLoading(true);
    const currentWeek = await fetchCurrentMenuWeek(targetDate);
    const nextDrafts: Record<string, DayDraft> = {};
    currentWeek.days.forEach((day) => {
      nextDrafts[day.serveDate] = {
        lunch: toDraft(day.lunch),
        dinner: toDraft(day.dinner)
      };
    });
    setWeek(currentWeek);
    setDrafts(nextDrafts);
    setExpandedDate(null);
    setSelectedDate(currentWeek.weekStartDate);
    setLoading(false);
  }

  async function handleCreateNextWeek() {
    if (creatingNextWeek) {
      return;
    }
    setCreatingNextWeek(true);
    try {
      await createNextMenuWeek();
      await reloadMenu(nextWeekDate());
      toast("下周空白模板已创建", "success");
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
      await reloadMenu();
      toast("已将上周菜单复制到本周", "success");
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
      await reloadMenu();
      toast(`${week.weekStartDate} ~ ${week.weekEndDate} 菜单已发布`, "success");
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

  function setDayRest(serveDate: string) {
    setDrafts((current) => ({
      ...current,
      [serveDate]: {
        lunch: { slotStatus: "REST", dishItems: [], totalCalories: null, merchantNote: "", imageUrl: "" },
        dinner: { slotStatus: "REST", dishItems: [], totalCalories: null, merchantNote: "", imageUrl: "" }
      }
    }));
  }

  function clearDay(serveDate: string) {
    setDrafts((current) => ({
      ...current,
      [serveDate]: {
        lunch: { slotStatus: "UNCONFIGURED", dishItems: [""], totalCalories: null, merchantNote: "", imageUrl: "" },
        dinner: { slotStatus: "UNCONFIGURED", dishItems: [""], totalCalories: null, merchantNote: "", imageUrl: "" }
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
      await reloadMenu(week.weekStartDate);
      setExpandedDate(serveDate);
      toast("保存成功", "success");
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

  async function handlePickWeek(targetDate: string) {
    if (!targetDate) return;
    await reloadMenu(targetDate);
  }

  const pageTitle = useMemo(() => {
    if (!week) return "周菜单管理";
    return `周菜单管理 (${week.weekStartDate} ~ ${week.weekEndDate})`;
  }, [week]);

  const weekSummary = useMemo(
    () => (week ? buildMenuWeekSummary(week) : { activeSlotCount: 0, configuredDayCount: 0, restDayCount: 0, unconfiguredSlotCount: 0, completionRate: "0%" }),
    [week]
  );

  function renderSlotEditor(serveDate: string, mealPeriod: "lunch" | "dinner", slot: SlotDraft) {
    return (
      <div className="menu-slot-editor">
        <div className="menu-slot-editor__title">{slotLabel(mealPeriod === "lunch" ? "LUNCH" : "DINNER")}</div>
        <div className="form-group">
          <label className="form-label">状态</label>
          <AppSelect
            value={slot.slotStatus}
            options={[
              { label: "正常营业", value: "ACTIVE" },
              { label: "待配置", value: "UNCONFIGURED" },
              { label: "休息", value: "REST" }
            ]}
            onChange={(value) => updateSlot(serveDate, mealPeriod, "slotStatus", value as SlotDraft["slotStatus"])}
          />
        </div>
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
          <button className="btn btn-outline" disabled={copyingLastWeek} onClick={() => handleCopyFromLastWeek().catch((err) => toast(err?.response?.data?.message || err.message || String(err), "error"))}>{copyingLastWeek ? "复制中..." : "复制上周菜单到本周"}</button>
          <button className="btn btn-outline" disabled={creatingNextWeek} onClick={() => handleCreateNextWeek().catch((err) => toast(err?.response?.data?.message || err.message || String(err), "error"))}>{creatingNextWeek ? "创建中..." : "新建下周空白模板"}</button>
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
            handlePickWeek(shiftLocalDateInputValue(selectedDate, -7)).catch((err) => toast(err?.response?.data?.message || err.message || String(err), "error"));
          }}
        >‹ 上一周</button>
        <DatePicker
          value={selectedDate}
          onChange={(date) => handlePickWeek(date).catch((err) => toast(err?.response?.data?.message || err.message || String(err), "error"))}
          showTomorrowShortcut={false}
        />
        <span className="menu-week-toolbar__range">
          {week ? `${week.weekStartDate} ~ ${week.weekEndDate}` : selectedDate}
        </span>
        <button
          className="btn btn-outline"
          onClick={() => {
            handlePickWeek(shiftLocalDateInputValue(selectedDate, 7)).catch((err) => toast(err?.response?.data?.message || err.message || String(err), "error"));
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
            <div key={day.serveDate} className={`menu-week-day-card ${expanded ? "is-expanded" : ""} ${isRest ? "menu-week-day-card--rest" : hasGap ? "menu-week-day-card--gap" : "menu-week-day-card--complete"}`}>
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
                <button className="btn btn-outline" onClick={() => setExpandedDate(expanded ? null : day.serveDate)}>{expanded ? "收起编辑" : "编辑"}</button>
              </div>
              <div className="menu-week-day-card__content">
                <div className={`menu-week-day-card__slot-preview menu-week-day-card__slot-preview--${day.lunch.slotStatus.toLowerCase()}`}>
                  <strong>午餐：</strong>{day.lunch.slotStatus === "ACTIVE" ? day.lunch.dishItems.join(" / ") : day.lunch.slotStatus === "REST" ? "休息" : "待配置"}
                </div>
                <div className={`menu-week-day-card__slot-preview menu-week-day-card__slot-preview--${day.dinner.slotStatus.toLowerCase()}`}>
                  <strong>晚餐：</strong>{day.dinner.slotStatus === "ACTIVE" ? day.dinner.dishItems.join(" / ") : day.dinner.slotStatus === "REST" ? "休息" : "待配置"}
                </div>
                {!hasMenu && !isRest && <div className="admin-empty-note">未配置</div>}
              </div>
              {expanded && draft && (
                <div className="menu-week-day-card__editor">
                  {renderSlotEditor(day.serveDate, "lunch", draft.lunch)}
                  {renderSlotEditor(day.serveDate, "dinner", draft.dinner)}
                  <div className="menu-week-day-card__actions">
                    <button className="btn btn-primary" disabled={savingDay === day.serveDate} onClick={() => handleSaveDay(day.serveDate).catch((err) => toast(err?.response?.data?.message || err.message || String(err), "error"))}>
                      {savingDay === day.serveDate ? "保存中..." : "保存当天"}
                    </button>
                    <button className="btn btn-outline" disabled={savingDay === day.serveDate} onClick={() => clearDay(day.serveDate)}>一键清空</button>
                    <button className="btn btn-outline" disabled={savingDay === day.serveDate} onClick={() => setDayRest(day.serveDate)}>设为休息</button>
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
    </>
  );
}

function nextWeekDate() {
  const date = new Date();
  date.setDate(date.getDate() + 7);
  return formatLocalDateInputValue(date);
}
