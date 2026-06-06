const { request } = require('../../utils/request');
const { mapOrderForDisplay, resolveVisibleOrders } = require('../../utils/order-list');
const { buildOrderStatusGuidance } = require('../../utils/customer-order-flow');
const { buildRejectedAftersaleDetail } = require('../../utils/aftersale');

Page({
  data: {
    filters: [
      { label: '全部', value: '' },
      { label: '待配送', value: 'PENDING_DISPATCH' },
      { label: '已送达', value: 'DELIVERED' }
    ],
    currentStatus: '',
    targetOrderId: null,
    showingTargetOrderOnly: false,
    items: [],
    loading: false
  },

  onLoad(options) {
    if (options.orderId) {
      this.setData({ targetOrderId: options.orderId });
      wx.setNavigationBarTitle({ title: '关联订单' });
    }
  },

  onShow() {
    this.loadOrders();
  },

  onPullDownRefresh() {
    this.loadOrders();
  },

  async loadOrders() {
    const { currentStatus, targetOrderId } = this.data;
    this.setData({ loading: true });
    try {
      const query = [];
      if (currentStatus) {
        query.push(`status=${currentStatus}`);
      }
      const response = await request({ url: `/api/mobile/customer/orders${query.length ? `?${query.join('&')}` : ''}` });
      let items = (response.items || []).map((item) => {
        const displayItem = mapOrderForDisplay(item);
        return {
          ...displayItem,
          guidanceText: buildOrderStatusGuidance(displayItem.status)
        };
      });
      items = resolveVisibleOrders(items, targetOrderId);

      this.setData({
        items,
        showingTargetOrderOnly: Boolean(targetOrderId)
      });
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  onFilterTap(e) {
    const { status } = e.currentTarget.dataset;
    if (status === this.data.currentStatus) {
      return;
    }
    this.setData({ currentStatus: status, items: [] });
    this.loadOrders();
  },

  viewAllOrders() {
    if (!this.data.targetOrderId) {
      return;
    }
    this.setData({
      targetOrderId: null,
      showingTargetOrderOnly: false,
      items: []
    });
    wx.setNavigationBarTitle({ title: '我的订单' });
    this.loadOrders();
  },

  cancelOrder(e) {
    const { id } = e.currentTarget.dataset;
    wx.showModal({
      title: '取消订单',
      content: '确认取消这笔预订吗？餐次会退回到钱包余额。',
      success: async ({ confirm }) => {
        if (!confirm) {
          return;
        }
        try {
          await request({
            url: `/api/mobile/customer/orders/${id}/cancel`,
            method: 'POST'
          });
          wx.showToast({ title: '已取消', icon: 'success' });
          this.loadOrders();
        } catch (error) {
          wx.showToast({ title: error.message || '取消失败', icon: 'none' });
        }
      }
    });
  },

  openAftersale(e) {
    const { id } = e.currentTarget.dataset;
    wx.navigateTo({
      url: `/pages/aftersale-apply/index?orderId=${id}`
    });
  },

  openReceipt(e) {
    const { id } = e.currentTarget.dataset;
    wx.navigateTo({
      url: `/pages/receipts/index?orderId=${id}`
    });
  },

  showStatusDetail(e) {
    const { item } = e.currentTarget.dataset;
    if (item.afterSaleStatus === 'REJECTED') {
      wx.showModal({
        title: '售后处理详情',
        content: buildRejectedAftersaleDetail(item.afterSaleAdminRemark),
        showCancel: false,
        confirmText: '我知道了',
        confirmColor: '#92AA40'
      });
    }
  }
});
