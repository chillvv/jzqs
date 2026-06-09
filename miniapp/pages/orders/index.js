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
      const response = await request({ url: '/api/mobile/customer/orders' });
      let items = (response.items || []).map((item) => {
        const displayItem = mapOrderForDisplay(item);
        return {
          ...displayItem,
          guidanceText: buildOrderStatusGuidance(displayItem.userVisibleStatus || displayItem.status)
        };
      });
      if (currentStatus) {
        items = items.filter((item) => (item.userVisibleStatus || item.status) === currentStatus);
      }
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

  viewAftersale(e) {
    wx.showModal({
      title: '售后处理中',
      content: '您的售后申请商家正在处理中，请留意最新状态或直接联系客服。',
      showCancel: false,
      confirmText: '我知道了',
      confirmColor: '#92AA40'
    });
  },

  openReceipt(e) {
    const { id } = e.currentTarget.dataset;
    wx.navigateTo({
      url: `/pages/receipts/index?orderId=${id}`
    });
  },

  changeAddress(e) {
    const { id, mode } = e.currentTarget.dataset;
    if (mode === 'CONTACT_SUPPORT') {
      wx.showModal({
        title: '联系客服修改',
        content: '送餐当天请联系客服微信，由商家后台手动修改地址。',
        showCancel: false,
        confirmText: '我知道了',
        confirmColor: '#92AA40'
      });
      return;
    }
    if (mode !== 'SELF_SERVICE') {
      wx.showToast({ title: '当前订单不可修改地址', icon: 'none' });
      return;
    }
    wx.navigateTo({
      url: `/pages/addresses/index?selectOrderId=${id}`
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
