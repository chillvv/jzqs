const { shareAppMessage, shareTimeline } = require('../../utils/share');
const { request } = require('../../utils/request');
const { maskPhone } = require('../../utils/mobile');
const {
  DELIVERY_TEMPLATE_ID,
  NIGHTLY_TEMPLATE_ID,
  requestDeliverySubscribeAuthorization,
  requestNightlySubscribeAuthorization,
  sendSubscribeMessageTest
} = require('../../utils/delivery-subscription');
const auth = require('../../utils/auth');
const onb = require('../../utils/onboarding');

function displayName(name) {
  if (!name || name.startsWith('微信用户-') || name.startsWith('待完善-')) {
    return '用户';
  }
  return name;
}

Page({
  onShareAppMessage: shareAppMessage,
  onShareTimeline: shareTimeline,
  data: {
    home: null,
    maskedPhone: '',
    displayName: '游客',
    loading: false,
    onboarding: true,
    sendingDelivery: false,
    sendingNightly: false,
    statusBarHeight: 0,
    navBarHeight: 44,
    // 是否展示「测试订阅」内部入口（仅测试环境，正式版隐藏）
    showTestSubscription: false
  },

  onLoad() {
    const app = getApp();
    this.setData({
      statusBarHeight: app.globalData.statusBarHeight,
      navBarHeight: app.globalData.navBarHeight
    });
  },

  goLoginPage() {
    wx.navigateTo({ url: '/pages/login/index' });
  },

  onShow() {
    onb.ensureCleanDemo();
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({
        selected: 2
      })
    }

    this.refreshPage();
  },

  async refreshPage() {
    const app = getApp();
    await app.waitForAuth();
    const onboarding = !app.globalData.token;
    this.setData({
      onboarding,
      showTestSubscription: !!(app.globalData && app.globalData.isTestEnv)
    });
    if (onboarding) {
      this.setData({
        home: null,
        maskedPhone: '',
        displayName: '游客'
      });
      wx.stopPullDownRefresh();
      return;
    }
    this.loadProfile();
  },

  async loadProfile() {
    const app = getApp();
    if (!app.globalData.token) {
      this.setData({ loading: false });
      return;
    }
    this.setData({ loading: true });
    try {
      const home = await request({ url: '/api/mobile/customer/home' });
      const finalName = displayName(home.name);
      this.setData({
        home,
        displayName: finalName,
        maskedPhone: maskPhone(home.phone)
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
    if (onb.shouldRunStageHere('flow_profile')) {
      onb.runCurrentStage(this);
    }
  },

  onPullDownRefresh() {
    this.refreshPage();
  },

  handleLogout() {
    wx.showModal({
      title: '退出登录',
      content: '确定要退出当前账号吗？',
      success: async (res) => {
        if (res.confirm) {
          await auth.logout();
          this.refreshPage();
          wx.showToast({ title: '已退出', icon: 'success' });
        }
      }
    });
  },

  goEditProfile() {
    if (this.data.onboarding) return;
    wx.showModal({
      title: '修改姓名',
      editable: true,
      placeholderText: '请输入您的姓名',
      content: this.data.home ? this.data.home.name : '',
      success: async (res) => {
        if (res.confirm && res.content) {
          const trimmedName = String(res.content || '').trim();
          if (!/^[\u4e00-\u9fa5·]{2,20}$/.test(trimmedName)) {
            wx.showToast({ title: '姓名仅支持汉字（2-20个字符）', icon: 'none' });
            return;
          }
          try {
            await request({
              url: '/api/mobile/customer/profile',
              method: 'POST',
              data: { name: trimmedName }
            });
            wx.showToast({ title: '修改成功', icon: 'success' });
            this.loadProfile();
          } catch (error) {
            wx.showToast({ title: error.message || '修改失败', icon: 'none' });
          }
        }
      }
    });
  },
  async guardMemberAction(targetUrl) {
    const app = getApp();
    await app.waitForAuth();
    if (!app.globalData.token) {
      wx.showToast({ title: '先完成手机号验证，再查看会员服务', icon: 'none' });
      this.goLoginPage();
      return;
    }
    if (this.data.onboarding) {
      this.setData({ onboarding: false });
    }
    wx.navigateTo({ url: targetUrl });
  },

  goOrders() {
    this.guardMemberAction('/pages/orders/index');
  },

  goReceipts() {
    this.guardMemberAction('/pages/receipts/index');
  },

  goAddresses() {
    this.guardMemberAction('/pages/addresses/index');
  },

  goWallet() {
    this.guardMemberAction('/pages/wallet/index');
  },

  async contactService() {
    const app = getApp();
    await app.waitForAuth();
    if (!app.globalData.token) {
      wx.showToast({ title: '验证后可同步会员服务与订单支持', icon: 'none' });
      this.goLoginPage();
      return;
    }
    wx.showModal({
      title: '联系专属客服',
      content: '可在"联系客服"入口直接发起会话，或联系运营同事处理',
      showCancel: false
    });
  },

  onNewbieGuideTap() {
    wx.showModal({
      title: '新手指引',
      content: '将用演示数据带你完整走一遍「看菜单 → 点餐 → 查预订」的流程，约 1 分钟。确认开始吗？',
      confirmText: '确认开始',
      success: (res) => {
        if (res.confirm) {
          onb.startFromProfile();
        }
      }
    });
  },

  onGuideDone() {},
  onGuideSkip() {},

  async sendSubscriptionTest(e) {
    if (this.data.onboarding) {
      wx.showToast({ title: '请先登录后再测试订阅', icon: 'none' });
      return;
    }
    const template = (e && e.currentTarget && e.currentTarget.dataset && e.currentTarget.dataset.template) || 'delivery';
    const isDelivery = template === 'delivery';
    const sendingKey = isDelivery ? 'sendingDelivery' : 'sendingNightly';
    if (this.data[sendingKey]) {
      return;
    }
    const templateId = isDelivery ? DELIVERY_TEMPLATE_ID : NIGHTLY_TEMPLATE_ID;
    const label = isDelivery ? '送达' : '提醒';
    this.setData({ [sendingKey]: true });
    try {
      // 各自只弹自己这一个模板的授权框
      const acceptResult = isDelivery
        ? await requestDeliverySubscribeAuthorization()
        : await requestNightlySubscribeAuthorization();
      if (!acceptResult) {
        wx.showToast({ title: `未授权${label}订阅，未发送`, icon: 'none' });
        return;
      }
      const type = isDelivery ? 'delivery' : 'nightly';
      const res = await sendSubscribeMessageTest(templateId, acceptResult, type);
      wx.showToast({ title: `${label}订阅测试已发送`, icon: 'none' });
    } catch (error) {
      wx.showToast({ title: error.message || '订阅测试失败', icon: 'none' });
    } finally {
      this.setData({ [sendingKey]: false });
    }
  }
});
