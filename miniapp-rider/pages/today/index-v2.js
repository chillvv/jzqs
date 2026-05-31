/**
 * 今日任务页面 V2.0
 * 重构版本：使用服务层、进度条、优化UI
 */

const taskService = require('../../services/task.service');
const { getMealPeriodLabel, getBatchStatusLabel, calculateProgress } = require('../../utils/formatter');

Page({
  data: {
    loading: false,
    viewState: 'checking',
    summary: {
      totalCount: 0,
      deliveredCount: 0,
      remainingCount: 0,
      riderName: ''
    },
    progress: 0,
    lunchBatch: null,
    dinnerBatch: null,
    hasAnyBatch: false
  },

  async onShow() {
    const app = getApp();
    await app.waitForRiderAuth();

    if (app.globalData.riderRegistered) {
      try {
        await app.refreshRiderProfile();
      } catch (error) {
        console.warn('[今日页面] 刷新资料失败', error);
      }
    }

    const viewState = app.getRiderViewState();
    this.setData({ viewState });

    if (viewState !== 'active') {
      this.resetSummary();
      return;
    }

    this.loadSummary();
  },

  onPullDownRefresh() {
    if (this.data.viewState !== 'active') {
      this.onShow();
      return;
    }
    this.loadSummary();
  },

  /**
   * 加载今日概览
   */
  async loadSummary() {
    const app = getApp();
    const riderName = app.getActiveRiderName();

    if (!riderName) {
      wx.showToast({ title: '骑手信息未就绪', icon: 'none' });
      wx.stopPullDownRefresh();
      return;
    }

    this.setData({ loading: true });

    try {
      const summary = await taskService.getTodaySummary(riderName);

      // 更新全局骑手信息
      app.globalData.riderProfile.riderName = summary.riderName || riderName;
      app.globalData.riderProfile.completedCount = summary.deliveredCount || 0;

      // 处理批次数据
      const lunchBatch = this.normalizeBatch(summary.lunch);
      const dinnerBatch = this.normalizeBatch(summary.dinner);
      const hasAnyBatch = !!(lunchBatch || dinnerBatch);

      // 计算总体进度
      const progress = calculateProgress(summary.deliveredCount, summary.totalCount);

      this.setData({
        summary: {
          totalCount: summary.totalCount || 0,
          deliveredCount: summary.deliveredCount || 0,
          remainingCount: summary.remainingCount || 0,
          riderName: summary.riderName || riderName
        },
        progress,
        lunchBatch,
        dinnerBatch,
        hasAnyBatch
      });
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  /**
   * 规范化批次数据
   */
  normalizeBatch(batch) {
    if (!batch) return null;

    return {
      ...batch,
      mealLabel: getMealPeriodLabel(batch.mealPeriod),
      batchStatusLabel: getBatchStatusLabel(batch.batchStatus),
      progress: calculateProgress(batch.deliveredCount, batch.totalCount),
      currentCustomerName: batch.currentCustomerName || '暂无',
      nextCustomerName: batch.nextCustomerName || '暂无'
    };
  },

  /**
   * 重置概览数据
   */
  resetSummary() {
    this.setData({
      loading: false,
      summary: {
        totalCount: 0,
        deliveredCount: 0,
        remainingCount: 0,
        riderName: ''
      },
      progress: 0,
      lunchBatch: null,
      dinnerBatch: null,
      hasAnyBatch: false
    });
  },

  /**
   * 进入配送队列
   */
  openQueue() {
    const app = getApp();
    if (app.getRiderViewState() !== 'active') {
      wx.switchTab({ url: '/pages/profile/index' });
      return;
    }
    wx.switchTab({ url: '/pages/queue/index' });
  },

  /**
   * 进入指定餐期的配送队列
   */
  openQueueWithMeal(e) {
    const meal = e.currentTarget.dataset.meal;
    const app = getApp();
    
    if (app.getRiderViewState() !== 'active') {
      wx.switchTab({ url: '/pages/profile/index' });
      return;
    }
    
    // 保存餐期筛选参数到全局
    app.globalData.queueMealFilter = meal;
    
    wx.switchTab({ url: '/pages/queue/index' });
  },

  /**
   * 查看已完成
   */
  viewCompleted() {
    wx.navigateTo({ url: '/pages/completed/index' });
  },

});
