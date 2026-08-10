const { shareAppMessage, shareTimeline } = require('../../utils/share');
const { request } = require('../../utils/request');
const { formatWalletTransaction } = require('../../utils/aftersale');
const { buildWalletHint } = require('../../utils/customer-order-flow');
const realtime = require('../../utils/realtime');

Page({
  onShareAppMessage: shareAppMessage,
  onShareTimeline: shareTimeline,
  data: {
    statusBarHeight: 0,
    navBarHeight: 44,
    home: null,
    items: [],
    loading: false
  },

  onLoad() {
    const app = getApp();
    this.setData({
      statusBarHeight: app.globalData.statusBarHeight,
      navBarHeight: app.globalData.navBarHeight
    });
  },

  onShow() {
    this.startRealtimeSync();
    this.loadWalletData();
  },

  onHide() {
    this.stopRealtimeSync();
  },

  onUnload() {
    this.stopRealtimeSync();
  },

  onPullDownRefresh() {
    this.loadWalletData();
  },

  async loadWalletData() {
    this.setData({ loading: true });
    try {
      const [home, response] = await Promise.all([
        request({ url: '/api/mobile/customer/home' }),
        request({ url: '/api/mobile/customer/wallet-transactions' })
      ]);
      this.setData({
        home,
        items: (response.items || []).map((item) => {
          const displayItem = formatWalletTransaction(item);
          return {
            ...displayItem,
            operatorName: this.resolveOperatorName(displayItem, home),
            flowHint: buildWalletHint({ walletDelta: displayItem.mealDelta })
          };
        })
      });
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  startRealtimeSync() {
    this.stopRealtimeSync();
    this._unsubscribeRealtime = realtime.subscribe((message) => {
      const eventType = String((message && message.eventType) || '');
      if (!eventType.startsWith('customer.wallet.') && eventType !== 'customer.wallet.changed') {
        return;
      }
      this.loadWalletData();
    });
  },

  stopRealtimeSync() {
    if (this._unsubscribeRealtime) {
      this._unsubscribeRealtime();
      this._unsubscribeRealtime = null;
    }
  },

  goToRelatedOrder(e) {
    const { id } = e.currentTarget.dataset;
    if (!id) return;
    wx.navigateTo({
      url: `/pages/orders/index?orderId=${id}`
    });
  },

  resolveOperatorName(item, home) {
    const raw = item.operatorName || '';
    const remark = item.remarkText || item.remark || '';
    const customerName = home && home.name ? home.name : '';
    const isSelfOrder = raw === '小程序' || (raw === '系统' && remark.indexOf('用户自主下单') !== -1);
    if (isSelfOrder && customerName) {
      return customerName;
    }
    return raw;
  },

});
