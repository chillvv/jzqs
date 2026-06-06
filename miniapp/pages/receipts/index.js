const { request } = require('../../utils/request');
const { mapReceiptRecord } = require('../../utils/receipt-display');

Page({
  data: {
    items: [],
    loading: false,
    targetOrderId: ''
  },

  onLoad(options) {
    if (options.orderId) {
      this.setData({ targetOrderId: String(options.orderId) });
      wx.setNavigationBarTitle({ title: '订单详情' });
      return;
    }
    wx.setNavigationBarTitle({ title: '送达回执' });
  },

  onShow() {
    this.loadReceipts();
  },

  onPullDownRefresh() {
    this.loadReceipts();
  },

  async loadReceipts() {
    const { targetOrderId } = this.data;
    const app = getApp();
    this.setData({ loading: true });
    try {
      const response = await request({
        url: '/api/mobile/customer/orders?status=DELIVERED'
      });
      let items = (response.items || [])
        .filter((item) => item.receiptUrl || item.receiptNote || item.deliveredAt)
        .map((item) => mapReceiptRecord(item, app.globalData.apiBaseUrl));
      if (targetOrderId) {
        items = items.filter((item) => String(item.id) === targetOrderId);
      }
      this.setData({ items });
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
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
  },

  openOrderDetail(e) {
    const { id } = e.currentTarget.dataset;
    wx.navigateTo({
      url: `/pages/receipts/index?orderId=${id}`
    });
  }
});
