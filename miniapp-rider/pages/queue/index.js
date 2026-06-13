/**
 * 骑手小程序 - 订单列表页
 * 拖拽排序 v2：插入索引 + 平移动画
 */
const taskService = require('../../services/task.service');

Page({
  data: {
    statusBarHeight: 0,
    navBarHeight: 0,
    loading: false,
    viewState: 'checking',
    isEditMode: false,
    batchReferenceMode: false,
    batchSubmitting: false,
    selectedReferenceItemIds: [],

    // 拖拽状态
    dragging: false,
    dragIndex: -1,
    dragOriginIndex: -1,
    dragStartY: 0,
    dragTranslateY: 0,

    refresherTriggered: false,

    // 当前选中的餐期
    currentMealPeriod: 'LUNCH',
    currentStatusFilter: 'PENDING',

    // 数据
    allItems: [],
    currentMealItems: [],

    // 统计
    lunchStats: { totalCount: 0, deliveredCount: 0, remainingCount: 0 },
    dinnerStats: { totalCount: 0, deliveredCount: 0, remainingCount: 0 }
  },

  async onShow() {
    const app = getApp();
    this.setData({ 
      statusBarHeight: app.globalData.statusBarHeight,
      navBarHeight: app.globalData.navBarHeight
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

    this.loadQueue();
  },

  onScrollRefresh() {
    if (this.data.viewState !== 'active') { this.onShow(); return; }
    this.loadQueue().finally(() => {
      this.setData({ refresherTriggered: false });
    });
  },

  async loadQueue() {
    const app = getApp();
    const riderName = app.getActiveRiderName();
    if (!riderName) {
      wx.showToast({ title: '骑手信息未就绪', icon: 'none' });
      return;
    }
    this.setData({ loading: true });
    try {
      const page = await taskService.getQueue(riderName);
      const items = page.items || [];
      this.setData({ allItems: items });
      this.calculateMealStats(items);
      this.filterCurrentMealItems();
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
    }
  },

  calculateMealStats(items) {
    const lunch = items.filter(i => i.mealPeriod === 'LUNCH');
    const dinner = items.filter(i => i.mealPeriod === 'DINNER');
    const ld = lunch.filter(i => i.itemStatus === 'DELIVERED').length;
    const dd = dinner.filter(i => i.itemStatus === 'DELIVERED').length;
    this.setData({
      lunchStats: { totalCount: lunch.length, deliveredCount: ld, remainingCount: lunch.length - ld },
      dinnerStats: { totalCount: dinner.length, deliveredCount: dd, remainingCount: dinner.length - dd }
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
      if (seen.has(i.batchItemId)) return false;
      seen.add(i.batchItemId);
      return true;
    });
    const selectedSet = new Set(selectedReferenceItemIds);
    const normalizedItems = items.map(item => ({
      ...item,
      specialSummary: item.specialSummary || [item.specialTag, item.note && item.note !== '-' ? '用户备注' : '', item.adminNote ? '商家备注' : '']
        .filter(Boolean)
        .join(' / '),
      hasRemark: Boolean(
        (item.note && item.note !== '-')
        || (item.adminNote && item.adminNote !== '-')
        || (item.customerNote && item.customerNote !== '-')
        || (item.merchantNote && item.merchantNote !== '-')
      ),
      batchSelected: selectedSet.has(item.batchItemId)
    }));
    const visibleIds = new Set(normalizedItems.map(item => item.batchItemId));
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
      dragTranslateY: 0
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

  toggleBatchItemSelection(batchItemId) {
    const selected = new Set(this.data.selectedReferenceItemIds);
    if (selected.has(batchItemId)) {
      selected.delete(batchItemId);
    } else {
      selected.add(batchItemId);
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
        .filter(item => selectedSet.has(item.batchItemId))
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
      const uploadResult = await taskService.uploadReceipt(riderName, tempFilePath);
      const referenceImageUrl = uploadResult.fileKey || uploadResult.previewUrl;
      await taskService.saveBatchAddressReferenceImage(riderName, addressIds, referenceImageUrl);
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
      this._activateDrag(index, touch.pageY);
    }, 300);
  },

  // 激活拖拽状态
  _activateDrag(index, startY) {
    // 测量卡片高度（列表位置不再需要）
    const query = wx.createSelectorQuery();
    query.select('.order-card').boundingClientRect();
    query.exec((res) => {
      if (res[0] && res[0].height) {
        this._itemHeight = res[0].height;
      }
    });

    this.setData({
      dragging: true,
      dragIndex: index,
      dragOriginIndex: index,
      dragStartY: startY,
      dragTranslateY: 0
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

    // 节流：限制 setData 频率 ~30fps，减少渲染压力
    const now = Date.now();
    if (this._lastDragFrame && now - this._lastDragFrame < 33) return;
    this._lastDragFrame = now;

    const currentY = e.touches[0].pageY;
    const totalDeltaY = currentY - this.data.dragStartY;
    const { dragIndex, dragOriginIndex, currentMealItems } = this.data;
    const itemHeight = this._itemHeight || 100;

    // 基于累计偏移量计算目标位置（dragOriginIndex + 手指移动的卡片数）
    const positionsShifted = Math.round(totalDeltaY / itemHeight);
    const targetIndex = Math.max(0, Math.min(
      dragOriginIndex + positionsShifted,
      currentMealItems.length - 1
    ));

    if (targetIndex !== dragIndex) {
      const newItems = [...currentMealItems];
      const [item] = newItems.splice(dragIndex, 1);
      newItems.splice(targetIndex, 0, item);

      this.setData({
        currentMealItems: newItems,
        dragIndex: targetIndex,
        dragTranslateY: totalDeltaY - ((targetIndex - dragOriginIndex) * itemHeight)
      });
    } else {
      this.setData({
        dragTranslateY: totalDeltaY - ((dragIndex - dragOriginIndex) * itemHeight)
      });
    }
  },

  // 结束拖拽
  onTouchEnd() {
    this._cancelLongPress();
    this._lastDragFrame = null;
    if (!this.data.dragging) return;

    // 先过渡到目标位置
    this.setData({ dragTranslateY: 0 });

    // 等待过渡动画完成后再清理
    setTimeout(() => {
      this.setData({
        dragging: false,
        dragIndex: -1,
        dragOriginIndex: -1,
        dragStartY: 0,
        dragTranslateY: 0
      });
    }, 250);
  },

  async saveOrderSequence() {
    const { currentMealItems, currentMealPeriod } = this.data;
    const app = getApp();
    const riderName = app.getActiveRiderName();
    const ids = currentMealItems.filter(i => i.itemStatus !== 'DELIVERED').map(i => i.batchItemId);
    if (ids.length === 0) return;
    try {
      await taskService.saveOrderSequence(riderName, currentMealPeriod, ids);
      wx.showToast({ title: '顺序已保存', icon: 'success' });
      await this.loadQueue();
    } catch (error) {
      wx.showToast({ title: error.message || '保存失败', icon: 'none' });
    }
  },

  handleOrderTap(e) {
    if (this.data.isEditMode || this.data.dragging) {
      return;
    }
    
    const itemId = Number(e.currentTarget.dataset.itemId);
    const item = this.data.currentMealItems.find(i => i.batchItemId === itemId);
    if (!item) return;

    if (this.data.batchReferenceMode) {
      this.toggleBatchItemSelection(item.batchItemId);
      return;
    }
    
    wx.navigateTo({
      url: `/pages/order-detail/index?batchItemId=${item.batchItemId}&mealSlotOrderId=${item.mealSlotOrderId}`
    });
  },

  resetQueueState() {
    this.setData({
      loading: false, isEditMode: false, allItems: [], currentMealItems: [],
      batchReferenceMode: false, batchSubmitting: false, selectedReferenceItemIds: [],
      dragging: false, dragIndex: -1, dragOriginIndex: -1, dragTranslateY: 0,
      lunchStats: { totalCount: 0, deliveredCount: 0, remainingCount: 0 },
      dinnerStats: { totalCount: 0, deliveredCount: 0, remainingCount: 0 }
    });
  }
});
