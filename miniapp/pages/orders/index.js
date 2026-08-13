const { shareAppMessage, shareTimeline } = require('../../utils/share');
const { request } = require('../../utils/request');
const { mapOrderForDisplay, resolveVisibleOrders } = require('../../utils/order-list');
const { buildSupportRefundNotice } = require('../../utils/order-guards');
const { buildRejectedAftersaleDetail } = require('../../utils/aftersale');
const realtime = require('../../utils/realtime');
const guide = require('../../utils/guide');
const demo = require('../../utils/demo');
const auth = require('../../utils/auth');
const onboarding = require('../../utils/onboarding');

Page({
  onShareAppMessage: shareAppMessage,
  onShareTimeline: shareTimeline,
  data: {
    statusBarHeight: 0,
    navBarHeight: 44,
    filters: [
      { label: '全部', value: '' },
      { label: '待配送', value: 'PENDING_DISPATCH' },
      { label: '已送达', value: 'DELIVERED' }
    ],
    currentStatus: '',
    targetOrderId: null,
    showingTargetOrderOnly: false,
    items: [],
    loading: false,
    demoActive: false
  },

  onLoad(options) {
    const app = getApp();
    this.setData({
      statusBarHeight: app.globalData.statusBarHeight,
      navBarHeight: app.globalData.navBarHeight
    });
    if (options.orderId) {
      this.setData({ targetOrderId: options.orderId });
      wx.setNavigationBarTitle({ title: '关联订单' });
    }
  },

  onShow() {
    onboarding.ensureCleanDemo();
    this.startRealtimeSync();
    this.loadOrders();
  },

  onHide() {
    this.stopRealtimeSync();
  },

  onUnload() {
    this.stopRealtimeSync();
  },

  onPullDownRefresh() {
    this.loadOrders();
  },

  async loadOrders() {
    const { currentStatus, targetOrderId } = this.data;
    if (demo.isActive() && onboarding.isRunningFlow()) {
      this.applyDemoOrders();
      this.showGuide();
      return;
    }
    this.setData({ loading: true });
    try {
      const response = await request({ url: '/api/mobile/customer/orders' });
      let items = (response.items || []).map((item) => mapOrderForDisplay(item));
      if (currentStatus) {
        items = items.filter((item) => item.customerStatus === currentStatus);
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
      this.showGuide();
    }
  },

  showGuide() {
    if (onboarding.shouldRunStageHere('flow_orders')) {
      onboarding.runCurrentStage(this);
    }
  },

  applyDemoOrders() {
    const mock = demo.getMockOrderDisplay();
    this.setData({
      items: [mock],
      showingTargetOrderOnly: false,
      demoActive: true,
      loading: false
    });
  },

  startRealtimeSync() {
    this.stopRealtimeSync();
    this._unsubscribeRealtime = realtime.subscribe((message) => {
      const eventType = String((message && message.eventType) || '');
      if (!eventType.startsWith('customer.')) {
        return;
      }
      this.loadOrders();
    });
  },

  stopRealtimeSync() {
    if (this._unsubscribeRealtime) {
      this._unsubscribeRealtime();
      this._unsubscribeRealtime = null;
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

  goToOrder() {
    wx.switchTab({ url: '/pages/order/index' });
  },

  cancelOrder(e) {
    const { id } = e.currentTarget.dataset;
    wx.showModal({
      title: '取消订单',
      content: '确认取消这笔预订吗？餐次会退回到钱包余额',
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

  // 送餐当天不支持自助退款，按时间点给出不同语气的客服引导
  requestSupportRefund(e) {
    const { stage } = e.currentTarget.dataset;
    const notice = buildSupportRefundNotice(stage);
    wx.showModal({
      title: notice.title,
      content: notice.content,
      showCancel: false,
      confirmText: '我知道了',
      confirmColor: '#92AA40'
    });
  },

  viewAftersale(e) {
    wx.showModal({
      title: '售后处理中',
      content: '您的售后申请商家正在处理中，请留意最新状态或直接联系客服',
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

  callRider(e) {
    const { phone } = e.currentTarget.dataset;
    if (!phone) {
      return;
    }
    wx.makePhoneCall({
      phoneNumber: String(phone),
      fail: () => {
        wx.showToast({ title: '暂无法拨打电话', icon: 'none' });
      }
    });
  },

  changeAddress(e) {
    const { id, mode } = e.currentTarget.dataset;
    if (mode === 'CONTACT_SUPPORT') {
      wx.showModal({
        title: '送餐当天需联系商家',
        content: '这单今天就要配送啦，地址没法在系统里直接改。您可以先和商家协商下，由商家后台帮您调整，能不能改以商家反馈为准',
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
    const order = this.data.items.find((item) => item.id === id);
    const currentAddressId = order && order.addressId ? Number(order.addressId) : '';
    let url = `/pages/addresses/index?selectOrderId=${id}`;
    if (currentAddressId) {
      url += `&currentAddressId=${currentAddressId}`;
    }
    wx.navigateTo({ url });
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
