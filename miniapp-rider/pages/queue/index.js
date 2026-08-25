const { shareAppMessage, shareTimeline } = require('../../utils/share');
/**
 * 骑手小程序 - 订单列表页
 * 拖拽排序 v2：插入索引 + 平移动画
 */
const taskService = require('../../services/task.service');
const { createWorkbenchDateOptions, formatDateYMD } = require('../../utils/formatter');
const { resolveQueueItemIdentity, resolveQueueItemRequestId } = require('../../utils/rider-queue');
const { resolveMediaUrl } = require('../../utils/media-url');
const realtime = require('../../utils/realtime');
const guide = require('../../utils/guide');
const demo = require('../../utils/demo');
const onboarding = require('../../utils/onboarding');
const AUTO_REFRESH_MS = 8000;

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
    batchReferenceMode: false,
    batchSubmitting: false,
    selectedReferenceItemIds: [],
    currentDateLabel: '',
    currentDateTitle: '今天',
    selectedDate: '',
    showDatePicker: false,
    dateOptions: [],

    // 拖拽状态
    dragging: false,
    dragIndex: -1,
    dragOriginIndex: -1,
    dragCurrentIndex: -1,
    dragStartY: 0,
    dragTranslateY: 0,
    itemHeight: 100,
    listOffset: 0,            // 拖拽时列表内容的 translateY（模拟滚动，绕开 scroll-view）

    // 拖拽自动滚动所需状态
    dragScrollTop: 0,        // 拖拽开始时的滚动位置
    scrollTop: 0,            // 当前 scroll-view 滚动位置（受控）
    scrollViewHeight: 0,    // scroll-view 可视高度
    scrollViewTop: 0,       // scroll-view 在视口中的绝对 top（边缘判定用）
    scrollContentHeight: 0, // scroll-view 内部内容总高度
    scrollAnimating: false,  // 是否在自动滚动态（避免动画抢断手指）
    autoScrollTimer: null,   // 边缘自动滚动定时器
    autoScrollDir: 0,        // 1=向下 -1=向上 0=停
    dragTouchClientY: 0,     // 当前手指在 scroll-view 内的相对 Y（用于边缘判定）
    scrollIntoView: '',      // scroll-into-view 目标 id（程序滚动）

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
    await this.loadQueue();
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
    this.loadQueue().finally(() => {
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
        || this.data.dragging
        || this.data.batchReferenceMode
        || this.data.batchSubmitting
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
      if (this.data.viewState !== 'active' || this.data.isEditMode || this.data.dragging || this.data.batchSubmitting) {
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
    const { allItems, currentMealPeriod, currentStatusFilter, selectedReferenceItemIds } = this.data;
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
    const selectedSet = new Set(selectedReferenceItemIds);
    const app = getApp();
    const apiBaseUrl = (app && app.globalData && app.globalData.apiBaseUrl) || '';
    const normalizedItems = items.map(item => {
      const attentionSources = Array.isArray(item.attentionSources)
        ? item.attentionSources.filter(Boolean)
        : [];
      const fallbackNeedAttention = Boolean(
        (item.note && item.note !== '-')
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

      return {
        ...item,
        referenceImageUrl: resolvedReferenceUrl,
        receiptUrl: resolvedReceiptUrl,
        displayThumbUrl,
        displayThumbLabel,
        queueItemIdentity: resolveQueueItemIdentity(item),
        detailItemId: resolveQueueItemRequestId(item.batchItemId, item.mealSlotOrderId),
        attentionSources,
        attentionLabel: item.attentionLabel || (needAttention ? '有备注' : ''),
        needAttention,
        hasRemark: needAttention,
        batchSelected: selectedSet.has(resolveQueueItemIdentity(item))
      };
    });
    const visibleIds = new Set(normalizedItems.map(item => item.queueItemIdentity));
    this.setData({
      currentMealItems: normalizedItems,
      selectedReferenceItemIds: selectedReferenceItemIds.filter(id => visibleIds.has(id))
    });
  },

  switchStatusFilter(e) {
    const { filter } = e.currentTarget.dataset;
    if (filter === this.data.currentStatusFilter) return;
    this.setData({
      currentStatusFilter: filter,
      isEditMode: false,
      batchReferenceMode: false,
      selectedReferenceItemIds: []
    }, () => this.filterCurrentMealItems());
  },

  switchMealPeriod(e) {
    const { period } = e.currentTarget.dataset;
    this.setData({
      currentMealPeriod: period,
      isEditMode: false,
      batchReferenceMode: false,
      selectedReferenceItemIds: []
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
    await this.loadQueue();
  },

  // ========== 拖拽排序（v2 - 插入索引 + 平移动画）==========

  toggleEditMode() {
    if (this.data.batchReferenceMode) {
      wx.showToast({ title: '请先完成批量参考图', icon: 'none' });
      return;
    }
    const isEditMode = !this.data.isEditMode;
    this.setData({
      isEditMode,
      dragging: false,
      dragIndex: -1,
      dragOriginIndex: -1,
      dragCurrentIndex: -1,
      dragTranslateY: 0,
      listOffset: 0
    });
    if (!isEditMode) {
      this.saveOrderSequence();
    }
  },

  toggleBatchReferenceMode() {
    if (this.data.isEditMode) {
      wx.showToast({ title: '请先完成排序', icon: 'none' });
      return;
    }
    const batchReferenceMode = !this.data.batchReferenceMode;
    this.setData({
      batchReferenceMode,
      selectedReferenceItemIds: []
    }, () => this.filterCurrentMealItems());
  },

  toggleBatchItemSelection(queueItemIdentity) {
    const selected = new Set(this.data.selectedReferenceItemIds);
    if (selected.has(queueItemIdentity)) {
      selected.delete(queueItemIdentity);
    } else {
      selected.add(queueItemIdentity);
    }
    this.setData({
      selectedReferenceItemIds: Array.from(selected)
    }, () => this.filterCurrentMealItems());
  },

  chooseReferenceImage() {
    return new Promise((resolve, reject) => {
      wx.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['camera', 'album'],
        success: (res) => resolve((res.tempFilePaths || [])[0] || ''),
        fail: reject
      });
    });
  },

  async handleBatchReferenceUpload() {
    if (demo.isActive()) {
      wx.showToast({ title: '演示模式：批量传图仅展示，不会真实上传', icon: 'none' });
      return;
    }
    const { batchSubmitting, selectedReferenceItemIds, currentMealItems } = this.data;
    if (batchSubmitting) return;
    if (!selectedReferenceItemIds.length) {
      wx.showToast({ title: '请先勾选订单', icon: 'none' });
      return;
    }

    const app = getApp();
    const riderName = app.getActiveRiderName();
    if (!riderName) {
      wx.showToast({ title: '骑手信息未就绪', icon: 'none' });
      return;
    }

    const selectedSet = new Set(selectedReferenceItemIds);
    const addressIds = [...new Set(
      currentMealItems
        .filter(item => selectedSet.has(item.queueItemIdentity))
        .map(item => Number(item.addressId))
        .filter(id => id > 0)
    )];

    if (!addressIds.length) {
      wx.showToast({ title: '所选订单缺少有效地址', icon: 'none' });
      return;
    }

    this.setData({ batchSubmitting: true });
    try {
      const tempFilePath = await this.chooseReferenceImage();
      if (!tempFilePath) {
        this.setData({ batchSubmitting: false });
        return;
      }
      wx.showLoading({ title: '上传中...', mask: true });
      const uploadResult = await taskService.uploadReceipt(tempFilePath);
      const referenceImageUrl = uploadResult.fileKey || uploadResult.previewUrl;
      await taskService.saveBatchAddressReferenceImage(addressIds, referenceImageUrl);
      wx.hideLoading();
      wx.showToast({ title: `已更新${addressIds.length}个地点`, icon: 'success' });
      this.setData({
        batchReferenceMode: false,
        batchSubmitting: false,
        selectedReferenceItemIds: []
      });
      await this.loadQueue();
    } catch (error) {
      wx.hideLoading();
      if (error && error.errMsg && error.errMsg.includes('cancel')) {
        this.setData({ batchSubmitting: false });
        return;
      }
      this.setData({ batchSubmitting: false });
      wx.showToast({ title: error.message || '设置失败', icon: 'none' });
    }
  },

  // 触摸开始 - 启动长按定时器（替代 bindlongpress）
  onTouchStart(e) {
    if (!this.data.isEditMode) return;
    const { index, status } = e.currentTarget.dataset;
    if (status === 'DELIVERED') return;
    if (!e.touches || !e.touches[0]) return;

    const touch = e.touches[0];
    this._longPressData = {
      startY: touch.pageY,
      startX: touch.pageX,
      index: index
    };

    this._cancelLongPress();
    this._longPressTimer = setTimeout(() => {
      this._activateDrag(index, touch.clientY);
    }, 300);
  },

  // 激活拖拽状态
  _activateDrag(index, startY) {
    const query = wx.createSelectorQuery();
    query.select('.order-card').boundingClientRect();
    query.select('.scroll-area').boundingClientRect();
    query.select('.orders-list').boundingClientRect();
    query.exec((res) => {
      if (res[0] && res[0].height) {
        this._itemHeight = res[0].height;
      }
      // scroll-area 的 top 是它在视口中的绝对 Y 坐标，用于边缘判定
      if (res[1] && res[1].height) {
        this.setData({
          scrollViewHeight: res[1].height,
          scrollViewTop: res[1].top
        });
      }
      if (res[2] && res[2].height) {
        this.setData({ scrollContentHeight: res[2].height });
      }
      // 同步 itemHeight 到 data 供 wxs 视图层计算位移
      if (this._itemHeight) {
        this.setData({ itemHeight: this._itemHeight });
      }
    });

    // 记录拖拽起始的滚动位置，便于后续联合位移计算
    const currentScrollTop = this.data.scrollTop || 0;

    this.setData({
      dragging: true,
      dragIndex: index,
      dragOriginIndex: index,
      dragCurrentIndex: index,
      dragStartY: startY,
      dragScrollTop: currentScrollTop,
      dragTranslateY: 0,
      listOffset: 0,
      scrollAnimating: false
    });
  },

  // scroll-view 滚动时同步 scrollTop（受控）
  onScroll(e) {
    if (typeof e.detail.scrollTop === 'number') {
      this.data.scrollTop = e.detail.scrollTop;
    }
  },

  // 当手指靠近列表上/下边缘时，自动滚动列表，使拖拽能延伸到屏幕外的项
  // 注意：clientY 是相对"视口顶部"的，scroll-view 在视口中有顶部偏移（header+toggle），
  // 必须用 scroll-view 的绝对 top 来做边缘判定，否则永远判不到底部边缘。
  _checkAutoScroll(clientY) {
    const edgePx = 120;                      // 边缘触发阈值（px），放宽更易触发
    const viewH = this.data.scrollViewHeight || 0;
    const viewTop = this.data.scrollViewTop || 0;
    if (!viewH) return;

    const viewBottom = viewTop + viewH;       // scroll-view 底部在视口中的绝对 Y
    let dir = 0;
    if (clientY < viewTop + edgePx) {
      // 手指靠近 scroll-view 顶部边缘 → 向上滚
      dir = -1;
    } else if (clientY > viewBottom - edgePx) {
      // 手指靠近 scroll-view 底部边缘 → 向下滚
      dir = 1;
    }

    if (dir !== this.data.autoScrollDir) {
      this.data.autoScrollDir = dir;
      this._stopAutoScroll();
      if (dir !== 0) {
        this._startAutoScroll(dir);
      }
    }
  },

  _startAutoScroll(dir) {
    // 用 transform 移动 main-content 模拟滚动（绕开 scroll-view，拖拽时 scroll-y=false 屏幕不滚）
    // dir=1 向下：listOffset 减小（负值，内容上移露出后面的项）
    // dir=-1 向上：listOffset 增大（正值，内容下移露前面的项）
    const step = 6;
    const interval = 20;
    this.data.autoScrollTimer = setInterval(() => {
      if (!this.data.dragging) {
        this._stopAutoScroll();
        return;
      }
      const itemH = this._itemHeight || this.data.itemHeight || 100;
      let contentH = this.data.scrollContentHeight || 0;
      if (!contentH) {
        contentH = (this.data.currentMealItems || []).length * itemH * 1.15;
      }
      const curScrollTop = this.data.dragScrollTop || 0;
      const maxDown = Math.max(0, contentH - curScrollTop);
      const maxUp = Math.max(0, curScrollTop);
      let next = (this.data.listOffset || 0) - dir * step;
      next = Math.max(-maxDown, Math.min(maxUp, next));
      if (next === (this.data.listOffset || 0)) return; // 到边界不动（不停定时器，避免卡死）
      // 合并 setData：listOffset + dragCurrentIndex + dragTranslateY 一次设置，减少卡顿
      const deltaY = (this.data.dragTouchClientY - this.data.dragStartY) - next;
      const shift = Math.round(deltaY / itemH);
      const dragCurrentIndex = Math.max(0, Math.min(
        this.data.dragOriginIndex + shift,
        (this.data.currentMealItems || []).length - 1
      ));
      this.setData({
        listOffset: next,
        dragCurrentIndex: dragCurrentIndex,
        dragTranslateY: deltaY
      });
    }, interval);
  },

  _stopAutoScroll() {
    if (this.data.autoScrollTimer) {
      clearInterval(this.data.autoScrollTimer);
      this.data.autoScrollTimer = null;
    }
    this.data.autoScrollDir = 0;
  },

  // FLIP 模式：只算被拖卡片视觉当前位置 + 跟手指位移，不重排数组
  // 联合位移 = 手指位移 - listOffset（listOffset 为负表示列表上移露出后面的项）
  _recalcDragTarget() {
    if (!this.data.dragging) return;
    const currentY = this.data.dragTouchClientY;
    const deltaY = (currentY - this.data.dragStartY) - (this.data.listOffset || 0);
    const itemHeight = this._itemHeight || this.data.itemHeight || 100;
    const { dragOriginIndex, currentMealItems } = this.data;

    const shift = Math.round(deltaY / itemHeight);
    const dragCurrentIndex = Math.max(0, Math.min(
      dragOriginIndex + shift,
      currentMealItems.length - 1
    ));

    this.setData({
      dragCurrentIndex: dragCurrentIndex,
      dragTranslateY: deltaY
    });
  },

  // 取消长按（滚动或提前松手）
  _cancelLongPress() {
    if (this._longPressTimer) {
      clearTimeout(this._longPressTimer);
      this._longPressTimer = null;
    }
    this._longPressData = null;
  },

  // 拖拽移动
  onTouchMove(e) {
    const lp = this._longPressData;

    // 未进入拖拽：检查是否取消长按（手指移动超过阈值=滚动）
    if (!this.data.dragging) {
      if (lp && e.touches && e.touches[0]) {
        const dx = Math.abs(e.touches[0].pageX - lp.startX);
        const dy = Math.abs(e.touches[0].pageY - lp.startY);
        if (dx > 10 || dy > 10) {
          this._cancelLongPress();
        }
      }
      return;
    }

    if (!e.touches || !e.touches[0]) return;
    const touch = e.touches[0];
    this.data.dragTouchClientY = touch.clientY;

    // 节流：FLIP 模式只 setData 两个数字（轻量），16ms ~60fps 更丝滑
    const now = Date.now();
    if (this._lastDragFrame && now - this._lastDragFrame < 16) {
      // 仍要做边缘检测，保证手指刚进边缘就能触发自动滚动
      this._checkAutoScroll(touch.clientY);
      return;
    }
    this._lastDragFrame = now;

    // 联合位移 = 手指位移 + 滚动位移（拖拽中列表自动滚动的部分也要计入）
    this._recalcDragTarget();

    // 边缘自动滚动检测
    this._checkAutoScroll(touch.clientY);
  },

  // 结束拖拽（FLIP：先过渡被拖卡片到目标位置，再 splice 重排归位 + 同步 scroll-view 位置）
  onTouchEnd() {
    this._cancelLongPress();
    this._lastDragFrame = null;
    this._stopAutoScroll();
    if (!this.data.dragging) return;

    const { dragOriginIndex, dragCurrentIndex, currentMealItems, dragScrollTop, listOffset } = this.data;
    const itemHeight = this._itemHeight || this.data.itemHeight || 100;
    const finalIndex = dragCurrentIndex >= 0 ? dragCurrentIndex : dragOriginIndex;

    // 第一阶段：被拖卡片过渡到目标位置（视觉上落在 dragCurrentIndex）
    const targetTranslate = (finalIndex - dragOriginIndex) * itemHeight;
    this.setData({ dragTranslateY: targetTranslate });

    // 第二阶段：过渡完成后 splice 重排数组 + 同步 scroll-view 位置 + 清零
    // listOffset 归 0（列表 transform 归位），scrollTop 设为 dragScrollTop - listOffset（scroll-view 滚到对应位置）
    // 两者变化量抵消，视觉无跳变
    const syncScrollTop = (dragScrollTop || 0) - (listOffset || 0);
    setTimeout(() => {
      let newItems = currentMealItems;
      if (finalIndex !== dragOriginIndex && finalIndex >= 0 && finalIndex < currentMealItems.length) {
        newItems = [...currentMealItems];
        const [item] = newItems.splice(dragOriginIndex, 1);
        newItems.splice(finalIndex, 0, item);
      }
      this.setData({
        currentMealItems: newItems,
        dragging: false,
        dragIndex: -1,
        dragOriginIndex: -1,
        dragCurrentIndex: -1,
        dragStartY: 0,
        dragTranslateY: 0,
        dragScrollTop: 0,
        dragTouchClientY: 0,
        listOffset: 0,
        scrollTop: Math.max(0, syncScrollTop),
        scrollAnimating: false
      });
    }, 200);
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
      await this.loadQueue();
    } catch (error) {
      wx.showToast({ title: error.message || '保存失败', icon: 'none' });
      // 失败回滚本地乐观更新：重新拉取服务器真实顺序，避免界面顺序与数据库不一致
      await this.loadQueue();
    }
  },

  handleOrderTap(e) {
    if (this.data.isEditMode || this.data.dragging) {
      return;
    }

    if (demo.isActive()) {
      this.hideGuideMask();
    }

    const itemId = Number(e.currentTarget.dataset.itemId);
    const item = this.data.currentMealItems.find(i => i.detailItemId === itemId);
    if (!item) return;

    if (this.data.batchReferenceMode) {
      this.toggleBatchItemSelection(item.queueItemIdentity);
      return;
    }
    
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
    this.setData({
      loading: false, isEditMode: false, allItems: [], currentMealItems: [],
      batchReferenceMode: false, batchSubmitting: false, selectedReferenceItemIds: [],
      dragging: false, dragIndex: -1, dragOriginIndex: -1, dragCurrentIndex: -1, dragTranslateY: 0, listOffset: 0,
      showDatePicker: false,
      queueError: false, queueErrorMessage: '',
      lunchStats: { totalCount: 0, deliveredCount: 0, remainingCount: 0 },
      dinnerStats: { totalCount: 0, deliveredCount: 0, remainingCount: 0 }
    });
  }
});
