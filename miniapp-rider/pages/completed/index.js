/**
 * 今日已完成页面
 * 显示今日已送达的订单列表
 */

const taskService = require('../../services/task.service');
const { formatDateTime, getMealPeriodLabel, formatCurrentDateTime } = require('../../utils/formatter');
const imageUtil = require('../../utils/image');
const { resolveMediaUrl } = require('../../utils/media-url');

Page({
  data: {
    loading: false,
    items: [],
    totalCount: 0,
    editingItem: null,
    showEditModal: false,
    receiptTempFilePath: '',
    receiptPreviewUrl: '',
    receiptNote: '',
    submitting: false
  },

  async onLoad() {
    await this.loadCompletedTasks();
  },

  onPullDownRefresh() {
    this.loadCompletedTasks();
  },

  /**
   * 加载今日已完成任务
   */
  async loadCompletedTasks() {
    const app = getApp();
    const riderName = app.getActiveRiderName();

    if (!riderName) {
      wx.showToast({ title: '骑手信息未就绪', icon: 'none' });
      wx.stopPullDownRefresh();
      return;
    }

    this.setData({ loading: true });

    try {
      const page = await taskService.getCompletedToday(riderName);
      const items = (page.items || []).map(item => ({
        ...item,
        receiptUrl: resolveMediaUrl(item.receiptUrl, app.globalData.apiBaseUrl),
        mealLabel: getMealPeriodLabel(item.mealPeriod),
        deliveredAtFormatted: formatDateTime(item.deliveredAt)
      }));

      this.setData({
        items,
        totalCount: items.length
      });
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  /**
   * 查看回执图片
   */
  viewReceipt(e) {
    const { url } = e.currentTarget.dataset;
    if (!url) {
      wx.showToast({ title: '暂无回执图片', icon: 'none' });
      return;
    }

    imageUtil.previewImage([url]);
  },

  /**
   * 编辑回执
   */
  editReceipt(e) {
    const { index } = e.currentTarget.dataset;
    const item = this.data.items[index];
    
    if (!item) return;

    this.setData({
      editingItem: item,
      showEditModal: true,
      receiptPreviewUrl: item.receiptUrl || '',
      receiptNote: item.receiptNote || '',
      receiptTempFilePath: ''
    });
  },

  /**
   * 关闭编辑弹窗
   */
  closeEditModal() {
    this.setData({
      showEditModal: false,
      editingItem: null,
      receiptTempFilePath: '',
      receiptPreviewUrl: '',
      receiptNote: ''
    });
  },

  /**
   * 选择新图片
   */
  async chooseNewImage() {
    try {
      const paths = await imageUtil.chooseAndCompressImage({
        count: 1,
        quality: 80
      });

      if (paths.length === 0) return;

      const path = paths[0];
      this.setData({
        receiptTempFilePath: path,
        receiptPreviewUrl: path
      });
    } catch (error) {
      wx.showToast({ title: error.message || '选择图片失败', icon: 'none' });
    }
  },

  /**
   * 预览图片
   */
  previewImage() {
    if (!this.data.receiptPreviewUrl) return;
    imageUtil.previewImage([this.data.receiptPreviewUrl]);
  },

  /**
   * 输入回执说明
   */
  onReceiptNoteInput(e) {
    this.setData({ receiptNote: e.detail.value });
  },

  /**
   * 提交更新
   */
  async submitUpdate() {
    const { editingItem, receiptTempFilePath, receiptNote } = this.data;
    const app = getApp();

    if (!editingItem) return;

    // 如果没有选择新图片且没有原图片，提示错误
    if (!receiptTempFilePath && !editingItem.receiptUrl) {
      wx.showToast({ title: '请先选择图片', icon: 'none' });
      return;
    }

    this.setData({ submitting: true });

    try {
      const riderName = app.getActiveRiderName();
      let fileKey = '';

      // 如果选择了新图片，先上传
      if (receiptTempFilePath) {
        const upload = await taskService.uploadReceipt(riderName, receiptTempFilePath);
        fileKey = upload.fileKey;
      } else {
        fileKey = editingItem.receiptUrl || '';
      }

      // 更新回执
      await taskService.updateReceipt(
        riderName,
        editingItem.mealSlotOrderId,
        fileKey,
        receiptNote || '骑手确认送达',
        formatCurrentDateTime()
      );

      wx.showToast({ title: '更新成功', icon: 'success' });
      
      this.closeEditModal();
      this.loadCompletedTasks();
    } catch (error) {
      wx.showToast({ title: error.message || '更新失败', icon: 'none' });
    } finally {
      this.setData({ submitting: false });
    }
  },

  /**
   * 拨打客户电话
   */
  callCustomer(e) {
    const { phone } = e.currentTarget.dataset;
    if (!phone) {
      wx.showToast({ title: '暂无联系电话', icon: 'none' });
      return;
    }

    wx.makePhoneCall({
      phoneNumber: phone
    });
  },

  /**
   * 返回队列页面
   */
  backToQueue() {
    wx.switchTab({ url: '/pages/queue/index' });
  }
});
