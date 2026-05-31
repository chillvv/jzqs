const { request } = require('../../utils/request');

function mapDeliveryStatusLabel(status) {
  const map = {
    DISPATCHING: '配送中',
    PENDING_DISPATCH: '待取餐',
    DELIVERED: '已送达'
  };
  return map[status] || status;
}

function mapReceiptStatusLabel(status) {
  return status === 'UPLOADED' ? '已回传' : '待回传';
}

function statusClass(status) {
  if (status === 'DELIVERED') {
    return 'tag-green';
  }
  if (status === 'DISPATCHING') {
    return 'tag-blue';
  }
  return 'tag-gray';
}

function formatCurrentDateTime() {
  const date = new Date();
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hour = String(date.getHours()).padStart(2, '0');
  const minute = String(date.getMinutes()).padStart(2, '0');
  const second = String(date.getSeconds()).padStart(2, '0');
  return `${year}-${month}-${day}T${hour}:${minute}:${second}`;
}

Page({
  data: {
    riderName: '',
    filters: [
      { label: '全部', value: 'ALL' },
      { label: '进行中', value: 'ACTIVE' },
      { label: '已完成', value: 'DELIVERED' }
    ],
    currentFilter: 'ALL',
    items: [],
    loading: false,
    submittingTaskId: null
  },
  onShow() {
    const app = getApp();
    this.setData({ riderName: app.globalData.riderName });
    this.loadData();
  },
  onPullDownRefresh() {
    this.loadData();
  },
  async loadData() {
    const app = getApp();
    this.setData({ loading: true });
    try {
      const page = await request({ url: `/api/mobile/rider/tasks?riderName=${app.globalData.riderName}` });
      const items = (page.items || []).map((item) => ({
        ...item,
        mealLabel: item.mealPeriod === 'LUNCH' ? '午餐' : '晚餐',
        deliveryStatusLabel: mapDeliveryStatusLabel(item.deliveryStatus),
        receiptStatusLabel: mapReceiptStatusLabel(item.receiptStatus),
        statusClass: statusClass(item.deliveryStatus),
        canSubmit: item.deliveryStatus !== 'DELIVERED',
        receiptNoteDraft: '',
        receiptUrlDraft: item.receiptUrl || ''
      }));
      this.setData({ items: this.filterItems(items) });
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },
  filterItems(items) {
    if (this.data.currentFilter === 'ACTIVE') {
      return items.filter((item) => item.deliveryStatus !== 'DELIVERED');
    }
    if (this.data.currentFilter === 'DELIVERED') {
      return items.filter((item) => item.deliveryStatus === 'DELIVERED');
    }
    return items;
  },
  onFilterTap(e) {
    const { filter } = e.currentTarget.dataset;
    if (filter === this.data.currentFilter) {
      return;
    }
    this.setData({ currentFilter: filter }, () => {
      this.loadData();
    });
  },
  onReceiptNoteInput(e) {
    const { index } = e.currentTarget.dataset;
    this.setData({
      [`items[${index}].receiptNoteDraft`]: e.detail.value
    });
  },
  onReceiptUrlInput(e) {
    const { index } = e.currentTarget.dataset;
    this.setData({
      [`items[${index}].receiptUrlDraft`]: e.detail.value
    });
  },
  async submitReceipt(e) {
    const id = e.currentTarget.dataset.id;
    const index = e.currentTarget.dataset.index;
    const app = getApp();
    const item = this.data.items[index];
    if (!item) {
      return;
    }
    this.setData({ submittingTaskId: id });
    try {
      await request({
        url: `/api/mobile/rider/tasks/${id}/receipt`,
        method: 'POST',
        data: {
          riderName: app.globalData.riderName,
          receiptUrl: item.receiptUrlDraft,
          receiptNote: item.receiptNoteDraft || '骑手小程序确认送达',
          deliveredAt: formatCurrentDateTime()
        },
        header: { 'content-type': 'application/json' }
      });
      wx.showToast({ title: '送达已提交', icon: 'success' });
      this.loadData();
    } catch (error) {
      wx.showToast({ title: error.message || '提交失败', icon: 'none' });
    } finally {
      this.setData({ submittingTaskId: null });
    }
  },
  previewReceipt(e) {
    const { url } = e.currentTarget.dataset;
    if (!url) {
      wx.showToast({ title: '暂无回执图片', icon: 'none' });
      return;
    }
    wx.previewImage({
      urls: [url],
      current: url
    });
  }
});
