const { shareAppMessage, shareTimeline } = require('../../utils/share');
/**
 * 骑手小程序 - 订单列表页
 * 拖拽排序 v2：插入索引 + 平移动画
 */
const taskService = require('../../services/task.service');
const { createWorkbenchDateOptions, formatDateYMD } = require('../../utils/formatter');
const { resolveQueueItemIdentity, resolveQueueItemRequestId } = require('../../utils/rider-queue');
const { resolveMediaUrl } = require('../../utils/media-url');
const { splitAddress } = require('../../utils/address');
const realtime = require('../../utils/realtime');
const guide = require('../../utils/guide');
const demo = require('../../utils/demo');
const onboarding = require('../../utils/onboarding');
const AUTO_REFRESH_MS = 8000;

// 备注归一化：null / "-" 视为无备注
function normalizeRemark(value) {
  if (typeof value !== 'string') return '';
  const t = value.trim();
  return t === '-' ? '' : t;
}

// 用户备注 + 商家备注用中文逗号拼接（与后端 OrderNoteTexts.SEPARATOR 一致）
function buildRemarkSummary(item) {
  return [normalizeRemark(item.note), normalizeRemark(item.merchantRemark)]
    .filter(Boolean)
    .join('，');
}

function buildWorkbenchDateState(selectedDate) {
  const dateOptions = createWorkbenchDateOptions().map((item) => ({
    ...item,
    active: item.value === selectedDate
  }));
  const activeOption = dateOptions.find((item) => item.active) || dateOptions[1];
  return {
    selectedDate: activeOption.value,
    currentDateLabel: activeOption.shortLabel,
    currentDateTitle: activeOption.label,
    dateOptions
  };
}

Page({
  onShareAppMessage: shareAppMessage,
  onShareTimeline: shareTimeline,
  data: {
    statusBarHeight: 0,
    navBarHeight: 0,
    loading: false,
    viewState: 'checking',
    isEditMode: false,
    showReferenceImage: true,
    currentDateLabel: '',
    currentDateTitle: '今天',
    selectedDate: '',
    showDatePicker: false,
    dateOptions: [],

    refresherTriggered: false,

    // 当前选中的餐期
    currentMealPeriod: 'LUNCH',
    currentStatusFilter: 'PENDING',

    // 数据
    allItems: [],
    currentMealItems: [],

    // 加载失败提示（区别于空列表）
    queueError: false,
    queueErrorMessage: '',

    // 统计
    lunchStats: { totalCount: 0, deliveredCount: 0, remainingCount: 0 },
    dinnerStats: { totalCount: 0, deliveredCount: 0, remainingCount: 0 }
  },

  async onShow() {
    const app = getApp();
    onboarding.ensureCleanDemo();
    // 迁移清理：移除旧版本的本地造数标记，避免残留测试假单
    try {
      if (wx.getStorageSync('demo_local_gen')) wx.removeStorageSync('demo_local_gen');
      if (wx.getStorageSync('demo_queue_count')) wx.removeStorageSync('demo_queue_count');
    } catch (e) {}
    const nextSelectedDate = app.getWorkbenchDate() || this.data.selectedDate || formatDateYMD();
    
    this.setData({ 
      statusBarHeight: app.globalData.statusBarHeight,
      navBarHeight: app.globalData.navBarHeight,
      ...buildWorkbenchDateState(nextSelectedDate),
      showDatePicker: false
    });
    
    // 更新 tabBar 选中状态
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({
        selected: 0
      });
    }
    await app.waitForRiderAuth();

    const viewState = app.getRiderViewState();
    this.setData({ viewState });

    if (viewState !== 'active') {
      this.resetQueueState();
      wx.switchTab({ url: '/pages/profile/index' });
      return;
    }

    if (app.globalData.queueMealFilter) {
      this.setData({ currentMealPeriod: app.globalData.queueMealFilter });
      app.globalData.queueMealFilter = null;
    }

    onboarding.maybeAutoStart();
    this.startAutoRefresh();
    this.startRealtimeSync();
    await this.loadQueue({ silent: true });
    this.showGuide();
  },

  showGuide() {
    if (onboarding.shouldRunStageHere('flow_queue')) {
      onboarding.runCurrentStage(this);
    }
  },

  hideGuideMask() {
    const comp = this.selectComponent('#guideMask');
    if (comp) comp.hide();
  },

  onHide() {
    this.stopAutoRefresh();
    this.stopRealtimeSync();
  },

  onUnload() {
    this.stopAutoRefresh();
    this.stopRealtimeSync();
  },

  onScrollRefresh() {
    if (this.data.viewState !== 'active') { this.onShow(); return; }
    this.loadQueue({ silent: true }).finally(() => {
      this.setData({ refresherTriggered: false });
    });
  },

  _syncCurrentDateLabel() {
    const supportedDates = createWorkbenchDateOptions().map((item) => item.value);
    if (supportedDates.includes(this.data.selectedDate)) {
      return false;
    }
    const todayDate = formatDateYMD();
    const app = getApp();
    app.resetWorkbenchDate();
    this.resetQueueState();
    this.setData({ ...buildWorkbenchDateState(todayDate) });
    return true;
  },

  async loadQueue(options = {}) {
    const { silent = false } = options;
    const app = getApp();
    const riderName = app.getActiveRiderName();
    const serveDate = this.data.selectedDate || formatDateYMD();
    if (!riderName) {
      if (!silent) {
        wx.showToast({ title: '骑手信息未就绪', icon: 'none' });
      }
      return;
    }
    if (this._queueLoading) {
      return;
    }
    this._queueLoading = true;
    if (!silent) {
      this.setData({ loading: true });
    }
    try {
      const page = await taskService.getQueue(serveDate);
      const items = page.items || [];
      this.setData({
        allItems: items,
        queueError: false,
        queueErrorMessage: ''
      });
      this.calculateMealStats(items);
      this.filterCurrentMealItems();
    } catch (error) {
      this.setData({
        queueError: true,
        queueErrorMessage: error.message || '加载失败，请检查网络后重试'
      });
      if (!silent) {
        wx.showToast({ title: error.message || '加载失败', icon: 'none' });
      }
    } finally {
      this._queueLoading = false;
      if (!silent) {
        this.setData({ loading: false });
      }
    }
  },

  startAutoRefresh() {
    this.stopAutoRefresh();
    this._autoRefreshTimer = setInterval(() => {
      this._syncCurrentDateLabel();
      if (
        this.data.viewState !== 'active'
        || this.data.isEditMode
      ) {
        return;
      }
      this.loadQueue({ silent: true });
    }, AUTO_REFRESH_MS);
  },

  stopAutoRefresh() {
    if (this._autoRefreshTimer) {
      clearInterval(this._autoRefreshTimer);
      this._autoRefreshTimer = null;
    }
  },

  startRealtimeSync() {
    this.stopRealtimeSync();
    this._unsubscribeRealtime = realtime.subscribe((message) => {
      if (!message || !message.eventType || !String(message.eventType).startsWith('dispatch.')) {
        return;
      }
      if (this.data.viewState !== 'active' || this.data.isEditMode) {
        return;
      }
      this.loadQueue({ silent: true });
    });
  },

  stopRealtimeSync() {
    if (this._unsubscribeRealtime) {
      this._unsubscribeRealtime();
      this._unsubscribeRealtime = null;
    }
  },

  calculateMealStats(items) {
    const lunch = items.filter(i => i.mealPeriod === 'LUNCH');
    const dinner = items.filter(i => i.mealPeriod === 'DINNER');
    const sumQuantity = list => list.reduce((sum, item) => sum + (item.quantity || 1), 0);
    const ld = sumQuantity(lunch.filter(i => i.itemStatus === 'DELIVERED'));
    const dd = sumQuantity(dinner.filter(i => i.itemStatus === 'DELIVERED'));
    this.setData({
      lunchStats: { totalCount: sumQuantity(lunch), deliveredCount: ld, remainingCount: sumQuantity(lunch) - ld },
      dinnerStats: { totalCount: sumQuantity(dinner), deliveredCount: dd, remainingCount: sumQuantity(dinner) - dd }
    });
  },

  filterCurrentMealItems() {
    const { allItems, currentMealPeriod, currentStatusFilter } = this.data;
    let items = allItems.filter(i => i.mealPeriod === currentMealPeriod);
    if (currentStatusFilter === 'PENDING') {
      items = items.filter(i => i.itemStatus !== 'DELIVERED');
    } else if (currentStatusFilter === 'DELIVERED') {
      items = items.filter(i => i.itemStatus === 'DELIVERED');
    }
    const seen = new Set();
    items = items.filter(i => {
      const queueItemIdentity = resolveQueueItemIdentity(i);
      if (seen.has(queueItemIdentity)) return false;
      seen.add(queueItemIdentity);
      return true;
    });
    const app = getApp();
    const apiBaseUrl = (app && app.globalData && app.globalData.apiBaseUrl) || '';
    const normalizedItems = items.map(item => {
      const attentionSources = Array.isArray(item.attentionSources)
        ? item.attentionSources.filter(Boolean)
        : [];
      const fallbackNeedAttention = Boolean(
        (item.note && item.note !== '-')
        || (item.merchantRemark && item.merchantRemark !== '-')
        || (item.adminNote && item.adminNote !== '-')
        || (item.customerNote && item.customerNote !== '-')
        || (item.merchantNote && item.merchantNote !== '-')
        || (item.receiptNote && item.receiptNote !== '-')
      );
      const needAttention = typeof item.hasAttentionMark === 'boolean'
        ? item.hasAttentionMark
        : fallbackNeedAttention;

      const resolvedReferenceUrl = item.referenceImageUrl
        ? resolveMediaUrl(item.referenceImageUrl, apiBaseUrl)
        : '';
      const resolvedReceiptUrl = item.receiptUrl
        ? resolveMediaUrl(item.receiptUrl, apiBaseUrl)
        : '';
      const showDeliveryPhoto = item.itemStatus === 'DELIVERED' && resolvedReceiptUrl;
      const displayThumbUrl = showDeliveryPhoto ? resolvedReceiptUrl : resolvedReferenceUrl;
      const displayThumbLabel = showDeliveryPhoto ? '送达图' : '参考图';
      const addrSplit = splitAddress(item.deliveryAddress);
      const identity = resolveQueueItemIdentity(item);
      let remarkSummary = buildRemarkSummary(item);
      // 粘性：后端 note 字段在实时刷新时可能短暂抖动（有值 ↔ "-"），
      // 一旦取到过备注就保持，避免备注行反复出现/消失导致布局跳动。
      if (!this._remarkCache) this._remarkCache = {};
      if (remarkSummary) {
        this._remarkCache[identity] = remarkSummary;
      } else if (this._remarkCache[identity]) {
        remarkSummary = this._remarkCache[identity];
      }

      return {
        ...item,
        ...addrSplit,
        remarkSummary,
        referenceImageUrl: resolvedReferenceUrl,
        receiptUrl: resolvedReceiptUrl,
        displayThumbUrl,
        displayThumbLabel,
        queueItemIdentity: resolveQueueItemIdentity(item),
        detailItemId: resolveQueueItemRequestId(item.batchItemId, item.mealSlotOrderId),
        attentionSources,
        attentionLabel: item.attentionLabel || (needAttention ? '有备注' : ''),
        needAttention,
        hasRemark: needAttention
      };
    });
    this.setData({
      currentMealItems: normalizedItems
    });
  },

  switchStatusFilter(e) {
    const { filter } = e.currentTarget.dataset;
    if (filter === this.data.currentStatusFilter) return;
    this.setData({
      currentStatusFilter: filter,
      isEditMode: false
    }, () => this.filterCurrentMealItems());
  },

  switchMealPeriod(e) {
    const { period } = e.currentTarget.dataset;
    this.setData({
      currentMealPeriod: period,
      isEditMode: false
    }, () => this.filterCurrentMealItems());
  },

  openDatePicker() {
    this.setData({ showDatePicker: true });
  },

  closeDatePicker() {
    this.setData({ showDatePicker: false });
  },

  async selectWorkbenchDate(e) {
    const { date } = e.currentTarget.dataset;
    if (!date || date === this.data.selectedDate) {
      this.closeDatePicker();
      return;
    }
    const app = getApp();
    app.setWorkbenchDate(date);
    this.resetQueueState();
    this.setData({
      ...buildWorkbenchDateState(date),
      showDatePicker: false
    });
    await this.loadQueue({ silent: true });
  },

  // ========== 排序（v4 - 上移/下移按钮）==========

  toggleEditMode() {
    if (this.data.isEditMode) {
      this.exitEditMode();
    } else {
      this.enterEditMode();
    }
  },

  enterEditMode() {
    this.setData({
      isEditMode: true,
      currentMealItems: this._withSortFlags(this.data.currentMealItems)
    });
  },

  exitEditMode() {
    this.setData({ isEditMode: false });
    this.saveOrderSequence();
  },

  moveItemUp(e) {
    this._moveItem(Number(e.currentTarget.dataset.index), -1);
  },

  moveItemDown(e) {
    this._moveItem(Number(e.currentTarget.dataset.index), 1);
  },

  // 上移/下移一格，跳过已送达项
  _moveItem(index, dir) {
    const list = this.data.currentMealItems;
    const from = list[index];
    if (!from || from.itemStatus === 'DELIVERED') return;
    let target = index + dir;
    while (target >= 0 && target < list.length && list[target].itemStatus === 'DELIVERED') {
      target += dir;
    }
    if (target < 0 || target >= list.length) return;
    const items = [...list];
    [items[index], items[target]] = [items[target], items[index]];
    this.setData({ currentMealItems: this._withSortFlags(items) });
  },

  // 计算每项可上移/可下移标记（已送达项不可移）
  _withSortFlags(list) {
    return list.map((it, i) => {
      const movable = it.itemStatus !== 'DELIVERED';
      let canMoveUp = false;
      let canMoveDown = false;
      if (movable) {
        for (let j = i - 1; j >= 0; j--) {
          if (list[j].itemStatus !== 'DELIVERED') { canMoveUp = true; break; }
        }
        for (let j = i + 1; j < list.length; j++) {
          if (list[j].itemStatus !== 'DELIVERED') { canMoveDown = true; break; }
        }
      }
      return { ...it, canMoveUp, canMoveDown };
    });
  },

  toggleShowReferenceImage() {
    this.setData({ showReferenceImage: !this.data.showReferenceImage });
  },

  async saveOrderSequence() {
    if (demo.isActive()) {
      wx.showToast({ title: '演示模式：排序仅展示，不会真实保存', icon: 'none' });
      return;
    }
    const { currentMealItems, currentMealPeriod } = this.data;
    const app = getApp();
    const riderName = app.getActiveRiderName();
    const ids = currentMealItems
      .filter(i => i.itemStatus !== 'DELIVERED')
      .map(i => Number(i.batchItemId))
      .filter(id => id > 0);
    if (ids.length === 0) return;
    try {
      await taskService.saveOrderSequence(ids);
      wx.showToast({ title: '顺序已保存', icon: 'success' });
      await this.loadQueue({ silent: true });
    } catch (error) {
      wx.showToast({ title: error.message || '保存失败', icon: 'none' });
      // 失败回滚本地乐观更新：重新拉取服务器真实顺序，避免界面顺序与数据库不一致
      await this.loadQueue({ silent: true });
    }
  },

  handleOrderTap(e) {
    if (this.data.isEditMode) {
      return;
    }

    if (demo.isActive()) {
      this.hideGuideMask();
    }

    const itemId = Number(e.currentTarget.dataset.itemId);
    const item = this.data.currentMealItems.find(i => i.detailItemId === itemId);
    if (!item) return;

    wx.navigateTo({
      url: `/pages/order-detail/index?batchItemId=${item.detailItemId}&mealSlotOrderId=${item.mealSlotOrderId}`
    });
  },

  previewRefImage(e) {
    const url = e.currentTarget.dataset.url;
    if (!url) return;
    wx.previewImage({ urls: [url], current: url });
  },

  resetQueueState() {
    this._remarkCache = {};
    this.setData({
      loading: false, isEditMode: false, allItems: [], currentMealItems: [],
      showDatePicker: false,
      queueError: false, queueErrorMessage: '',
      lunchStats: { totalCount: 0, deliveredCount: 0, remainingCount: 0 },
      dinnerStats: { totalCount: 0, deliveredCount: 0, remainingCount: 0 }
    });
  }
});
