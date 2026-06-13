const { request } = require('../../utils/request');
const { formatWalletTransaction } = require('../../utils/aftersale');
const { buildWalletHint } = require('../../utils/customer-order-flow');

Page({
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
    this.loadWalletData();
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

  goToRelatedOrder(e) {
    const { id } = e.currentTarget.dataset;
    if (!id) return;
    wx.navigateTo({
      url: `/pages/orders/index?orderId=${id}`
    });
  },

  goBack() {
    if (getCurrentPages().length > 1) {
      wx.navigateBack();
      return;
    }
    wx.switchTab({ url: '/pages/profile/index' });
  }
});
