const { shareAppMessage, shareTimeline } = require('../../utils/share');
const { request } = require('../../utils/request');
const { formatMonthDay, periodLabel } = require('../../utils/mobile');
const { getCheckoutMealLimitMessage, isNightOrderClosed, getNightCloseNotice } = require('../../utils/order-guards');
const demo = require('../../utils/demo');
const onboarding = require('../../utils/onboarding');
const {
  requestCombinedSubscribeAuthorization,
  saveOrderDeliverySubscription,
  saveNightlySubscription,
  cacheDeliveryAcceptResult,
  queryNightlySubscribeStatus
} = require('../../utils/delivery-subscription');
const {
  normalizeHistoryRemarkSuggestions,
  addHistoryRemark,
  composeRemark,
  resolveInitialRemark
} = require('../../utils/order-remark');
const auth = require('../../utils/auth');

function tomorrowDate() {
  const date = new Date();
  date.setHours(0, 0, 0, 0);
  date.setDate(date.getDate() + 1);
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function displayDate(dateText) {
  return dateText.slice(5).replace('-', '.');
}

function toViewItem(item, qty) {
  return {
    ...item,
    qty,
    periodText: periodLabel(item.mealPeriod)
  };
}

function openInlineAuth(page, source) {
  page.setData({
    showInlineAuth: true,
    pendingAction: source
  });
}

Page({
  onShareAppMessage: shareAppMessage,
  onShareTimeline: shareTimeline,
  data: {
    showCheckout: false,
    showAddressPopup: false,
    qty1: 0,
    qty2: 0,
    price: 1,
    serveDate: tomorrowDate(),
    serveDateText: displayDate(tomorrowDate()),
    menuItems: [],
    lunchItem: null,
    dinnerItem: null,
    checkoutItems: [],
    totalQty: 0,
    addresses: [],
    selectedAddressId: null,
    selectedAddressText: '请先选择地址',
    selectedContactText: '暂无地址',
    remark: '',
    customRemark: '',
    defaultRemark: '',
    historyRemarkSuggestions: [],
    showRemarkDropdown: false,
    // 订阅授权引导：默认未勾选（需点击勾选框并成功授权后才变为勾选）。
    // 未勾选时下单会被拦截并提示。
    subscribeConsent: false,
    // 点击勾选框后正在请求微信订阅授权（避免重复点击）
    consentingSubscribe: false,
    // 用户是否已勾选「总是」授权每晚提醒（已授权则视为已开启，无需再弹）
    nightlySubscribed: false,
    submitting: false,
    savingDefaultRemark: false,
    loading: false,
    home: null,
    isGuest: true,
    selfOrderEnabled: true,
    selfOrderNotice: '',
    canOrder: true,
    statusText: '',
    showInlineAuth: false,
    pendingAction: '',
    statusBarHeight: 0,
    navBarHeight: 44,
    showOrderSuccess: false,
    orderSuccessMsg: '',
    orderSuccessIds: [],
    demoActive: false,
    nightClosed: false,
    nightCloseDesc: '',
    nightCloseBtnText: ''
  },

  onLoad() {
    const app = getApp();
    this.setData({
      statusBarHeight: app.globalData.statusBarHeight,
      navBarHeight: app.globalData.navBarHeight
    });
  },

  onShow() {
    onboarding.ensureCleanDemo();
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({
        selected: 1
      })
    }
    this.restoreRemarkDraft();
    this.applyNightOrderState();
    this.loadOrderData();
  },

  applyNightOrderState() {
    const notice = getNightCloseNotice();
    this.setData({
      nightClosed: isNightOrderClosed(new Date()),
      nightCloseDesc: notice.desc,
      nightCloseBtnText: notice.buttonText
    });
  },

  onPullDownRefresh() {
    this.loadOrderData();
  },

  async loadOrderData() {
    // 演示模式：用沙盒假数据，不请求真实接口
    if (demo.isActive() && onboarding.isRunningFlow()) {
      const mock = demo.getMockOrderPageData();
      const qty1 = mock.qty1 || 0;
      const qty2 = mock.qty2 || 0;
      this.setData({
        home: mock.home,
        isGuest: false,
        serveDate: mock.serveDate,
        serveDateText: mock.serveDateText,
        selfOrderEnabled: true,
        selfOrderNotice: '',
        canOrder: true,
        statusText: '',
        nightClosed: false,
        nightCloseDesc: '',
        nightCloseBtnText: '',
        menuItems: mock.menuItems,
        lunchItem: mock.lunchItem,
        dinnerItem: mock.dinnerItem,
        addresses: mock.addresses,
        qty1, qty2,
        defaultRemark: '',
        historyRemarkSuggestions: [],
        selectedAddressId: 'demo-addr',
        selectedAddressText: mock.addresses[0].addressLine,
        selectedContactText: `${mock.addresses[0].contactName} ${mock.addresses[0].contactPhone}`,
        loading: false,
        demoActive: true
      });
      this.syncCheckoutState();
      wx.stopPullDownRefresh();
      this.showGuide();
      return;
    }

    const app = getApp();
    await app.waitForAuth();
    this.setData({ loading: true });
    try {
      const tasks = [
        request({ url: '/api/mobile/customer/home', requireAuth: false }),
        request({ url: '/api/mobile/customer/menu/tomorrow', requireAuth: false }),
        app.globalData.token
          ? request({ url: '/api/mobile/customer/addresses' })
          : Promise.resolve([]),
        app.globalData.token
          ? request({ url: '/api/mobile/customer/nightly-subscription/status' }).catch(() => null)
          : Promise.resolve(null)
      ];
      const [home, tomorrowMenu, addresses, nightlyStatus] = await Promise.all(tasks);
      // 新逻辑：下单不再依赖「总是保持」，而是每次下单前强制重新请求两个模板授权（见 submitOrder）。
      // nightlySubscribed 仅用于勾选框 UI 展示（后端快照是否曾授权过），不再作为下单放行条件。
      const backendSubscribed = !!(nightlyStatus && nightlyStatus.subscribed);
      this.setData({ nightlySubscribed: backendSubscribed, subscribeConsent: backendSubscribed });
      const defaultAddress = addresses.find((item) => item.isDefault) || addresses[0] || null;
      const menuItems = [tomorrowMenu.lunchItem, tomorrowMenu.dinnerItem].filter(Boolean);
      const lunchItem = tomorrowMenu.lunchItem || null;
      const dinnerItem = tomorrowMenu.dinnerItem || null;
      const fallbackCanOrder = Boolean(tomorrowMenu.selfOrderEnabled) && Boolean(lunchItem && dinnerItem);
      const resolvedCanOrder = typeof tomorrowMenu.canOrder === 'boolean'
        ? tomorrowMenu.canOrder
        : fallbackCanOrder;
      const resolvedStatusText = typeof tomorrowMenu.statusText === 'string' && tomorrowMenu.statusText
        ? tomorrowMenu.statusText
        : (resolvedCanOrder ? '' : (tomorrowMenu.selfOrderNotice || '明日菜单待发布或店休，暂不提供配送服务'));
      this.setData({
        home,
        isGuest: !app.globalData.token,
        serveDate: tomorrowMenu.serveDate,
        serveDateText: displayDate(tomorrowMenu.serveDate),
        selfOrderEnabled: tomorrowMenu.selfOrderEnabled,
        selfOrderNotice: tomorrowMenu.selfOrderNotice,
        canOrder: resolvedCanOrder,
        statusText: resolvedStatusText,
        nightClosed: isNightOrderClosed(new Date(), tomorrowMenu),
        nightCloseDesc: getNightCloseNotice(tomorrowMenu).desc,
        nightCloseBtnText: getNightCloseNotice(tomorrowMenu).buttonText,
        menuItems,
        lunchItem,
        dinnerItem,
        addresses,
        defaultRemark: home && home.defaultUserRemark ? String(home.defaultUserRemark).trim() : '',
        historyRemarkSuggestions: normalizeHistoryRemarkSuggestions(undefined, {
          customerId: home && home.customerId
        }),
        selectedAddressId: defaultAddress ? defaultAddress.id : null,
        selectedAddressText: defaultAddress ? defaultAddress.addressLine : '请先选择地址',
        selectedContactText: defaultAddress ? `${defaultAddress.contactName} ${defaultAddress.contactPhone}` : '暂无地址'
      });
      this.restoreRemarkDraft(home && home.defaultUserRemark);
      this.syncCheckoutState();
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
      this.showGuide();
    }
  },

  showGuide() {
    if (onboarding.shouldRunStageHere('flow_order')) {
      onboarding.runCurrentStage(this);
    }
  },

  updateCart1Minus() {
    if (this.data.qty1 > 0) {
      this.setData({ qty1: this.data.qty1 - 1 });
      this.syncCheckoutState();
    }
  },

  updateCart1Plus() {
    if (!this.data.lunchItem) {
      wx.showToast({ title: '当前无午餐可选', icon: 'none' });
      return;
    }
    this.setData({ qty1: this.data.qty1 + 1 });
    this.syncCheckoutState();
  },

  updateCart2Minus() {
    if (this.data.qty2 > 0) {
      this.setData({ qty2: this.data.qty2 - 1 });
      this.syncCheckoutState();
    }
  },

  updateCart2Plus() {
    if (!this.data.dinnerItem) {
      wx.showToast({ title: '当前无晚餐可选', icon: 'none' });
      return;
    }
    this.setData({ qty2: this.data.qty2 + 1 });
    this.syncCheckoutState();
  },

  onRemarkInput(e) {
    const customRemark = e.detail.value;
    this.setData({ customRemark, remark: customRemark });
    this.persistRemarkDraft(customRemark);
  },

  onRemarkFocus() {
    this.setData({ showRemarkDropdown: true });
  },

  onRemarkBlur() {
    // Delay hiding to allow tap on dropdown item to register
    setTimeout(() => {
      this.setData({ showRemarkDropdown: false });
    }, 200);
  },

  selectHistoryRemark(e) {
    const customRemark = e.currentTarget.dataset.note;
    this.setData({
      customRemark,
      remark: customRemark,
      showRemarkDropdown: false
    });
    this.persistRemarkDraft(customRemark);
  },

  async toggleDefaultRemark() {
    if (!getApp().globalData.token) {
      openInlineAuth(this, 'order');
      return;
    }
    const currentRemark = String(this.data.customRemark || this.data.remark || '').trim();
    if (currentRemark === this.data.defaultRemark) {
      return;
    }
    if (this.data.savingDefaultRemark) {
      return;
    }
    this.setData({ savingDefaultRemark: true });
    try {
      await request({
        url: '/api/mobile/customer/profile',
        method: 'POST',
        header: { 'content-type': 'application/json' },
        data: { defaultUserRemark: currentRemark }
      });
      this.setData({
        defaultRemark: currentRemark,
        home: this.data.home
          ? { ...this.data.home, defaultUserRemark: currentRemark }
          : this.data.home
      });
      if (currentRemark) {
        wx.showToast({ title: '已设置默认备注', icon: 'success' });
      } else {
        wx.showToast({ title: '已清空默认备注', icon: 'success' });
      }
    } catch (error) {
      wx.showToast({ title: error.message || '设置失败', icon: 'none' });   
    } finally {
      this.setData({ savingDefaultRemark: false });
    }
  },

  showAddressList() {
    if (!getApp().globalData.token) {
      openInlineAuth(this, 'order');
      return;
    }
    if (!this.data.addresses.length) {
      this.goToAddAddress();
      return;
    }
    this.setData({ showAddressPopup: true });
  },

  hideAddressList() {
    this.setData({ showAddressPopup: false });
  },

  selectAddress(e) {
    const id = e.currentTarget.dataset.id;
    const selected = this.data.addresses.find((item) => item.id === id);
    if (selected) {
      this.setData({
        selectedAddressId: selected.id,
        selectedAddressText: selected.addressLine,
        selectedContactText: `${selected.contactName} ${selected.contactPhone}`,
        showAddressPopup: false
      });
    }
  },

  goToAddAddress() {
    this.hideAddressList();
    wx.navigateTo({ url: '/pages/addresses/index' });
  },

  goToCheckout() {
    if (this.data.nightClosed) {
      const notice = getNightCloseNotice();
      wx.showModal({
        title: notice.title,
        content: notice.content,
        showCancel: false,
        confirmText: '我知道了',
        confirmColor: '#B8D060'
      });
      return;
    }
    if (!getApp().globalData.token) {
      openInlineAuth(this, 'order');
      return;
    }
    if (!this.data.selfOrderEnabled) {
      wx.showToast({ title: this.data.selfOrderNotice || '请联系专属客服微信', icon: 'none' });
      return;
    }
    if (this.data.totalQty === 0) {
      wx.showToast({ title: '请先选择餐食', icon: 'none' });
      return;
    }
    const remainingMeals = (this.data.home && this.data.home.remainingMeals) || 0;
    const mealLimitMessage = getCheckoutMealLimitMessage({
      totalQty: this.data.totalQty,
      remainingMeals
    });
    if (mealLimitMessage) {
      wx.showModal({
        title: '餐次余额不足',
        content: mealLimitMessage,
        confirmText: '联系商家',
        confirmColor: '#B8D060',
        cancelText: '稍后处理',
        success: (res) => {
          if (res.confirm) {
            wx.switchTab({ url: '/pages/profile/index' });
          }
        }
      });
      return;
    }
    // 不在此处强制选择地址，进入确认页后再选择
    this.setData({ showCheckout: true });
  },

  backToMenu() {
    this.setData({ showCheckout: false });
  },

  /**
   * 核心订阅授权流程：弹出微信授权框申请「取餐 + 每晚」两个模板，拿到额度即成功。
   * 不要求「总是保持」——微信一次性订阅是「授权一次 = 发一条」的额度制，因此每次下单都重新
   * 请求，确保当天能收到「取餐提醒」和「每晚菜单提醒」各一条。
   * 返回 true 表示两个模板都授权成功（各获得 1 条额度），false 表示被拒绝 / 授权失败（内部已提示）。
   */
  async requestSubscribeConsent() {
    if (this.data.consentingSubscribe) {
      return this.data.nightlySubscribed;
    }
    this.setData({ consentingSubscribe: true });
    try {
      // 一次弹窗申请「取餐 + 每晚」两个授权（此调用在用户点击栈内，必须同步触发）
      const results = await requestCombinedSubscribeAuthorization();
      const { delivery, nightly } = results || {};
      if (delivery) {
        cacheDeliveryAcceptResult(delivery);
      }
      if (!delivery || !nightly) {
        // 两个模板都要授权成功；失败时明确区分「总开关关」「取餐未授权」「优惠券未授权」，精准提示
        this.setData({ subscribeConsent: false, nightlySubscribed: false });
        const status = await queryNightlySubscribeStatus().catch(() => ({ supported: false }));
        if (status.supported && !status.mainSwitch) {
          this.promptOpenSubscribeSetting('mainSwitchOff');
        } else if (!delivery && !nightly) {
          wx.showToast({ title: '需允许接收「取餐提醒」和「优惠券过期提醒」才能下单', icon: 'none' });
        } else if (!delivery) {
          wx.showToast({ title: '需允许接收「取餐提醒」才能下单', icon: 'none' });
        } else if (status.supported && status.rejected) {
          this.promptOpenSubscribeSetting('rejected');
        } else {
          wx.showToast({ title: '需允许接收「优惠券过期提醒」才能下单', icon: 'none' });
        }
        return false;
      }
      // 「每晚提醒」授权成功（用户点了允许，获得 1 条额度），保存后端记录
      await saveNightlySubscription(nightly);
      this.setData({ nightlySubscribed: true, subscribeConsent: true });
      wx.showToast({ title: '订阅授权成功', icon: 'success' });
      return true;
    } catch (error) {
      this.setData({ subscribeConsent: false, nightlySubscribed: false });
      wx.showToast({ title: error.message || '订阅授权失败', icon: 'none' });
      return false;
    } finally {
      this.setData({ consentingSubscribe: false });
    }
  },

  /**
   * 引导用户去微信「设置 → 订阅消息」里重新开启。
   * reason 区分两种卡死场景，给出精准指引，避免用户开错开关而反复循环：
   * - 'mainSwitchOff'：订阅消息总开关被关闭（用户可能开了模板却没开总开关）；
   * - 'rejected'：该「每晚提醒」模板被用户「总是拒绝」（用户可能开了总开关却没开模板）。
   */
  promptOpenSubscribeSetting(reason) {
    const content = reason === 'mainSwitchOff'
      ? '微信「订阅消息」总开关被关闭了，这会导致「取餐提醒」和「优惠券过期提醒」都无法送达。请前往设置，在「订阅消息」页面最顶部打开「接收订阅消息」总开关（注意：是最顶部的总开关，不是下面单个模板的开关）。现在前往设置？'
      : '你之前拒绝过「优惠券过期提醒」订阅，微信不会再弹出授权框。请前往设置 → 订阅消息 → 找到「简知轻食」，把「优惠券过期提醒」重新打开为「接收」后再回来。现在前往设置？';
    wx.showModal({
      title: reason === 'mainSwitchOff' ? '开启订阅总开关' : '开启优惠券提醒',
      content,
      confirmText: '去设置',
      cancelText: '取消',
      confirmColor: '#B8D060',
      success: (res) => {
        if (res.confirm) {
          wx.openSetting({
            // 让设置页尽量展示「订阅消息」区域，方便用户找到开关
            withSubscriptions: true,
            success: () => {
              this.refreshNightlySubscribeFromWx(reason);
            }
          });
        }
      }
    });
  },

  /** 从微信设置页返回后，重新校验真实状态，确认用户是否解决了卡点。 */
  async refreshNightlySubscribeFromWx(reason) {
    const status = await queryNightlySubscribeStatus().catch(() => ({ supported: false, mainSwitch: false, rejected: false }));
    // 根据之前卡点的原因判断是否已解决（总开关已开 / 模板已不再是拒绝状态）
    const solved = reason === 'mainSwitchOff'
      ? (status.supported && status.mainSwitch)
      : (status.supported && !status.rejected);
    if (solved) {
      wx.showToast({ title: '已开启，请重新下单', icon: 'success' });
      return;
    }
    // 从设置返回后仍未解决：明确反馈具体哪里没开对，避免用户以为已开但实际未生效而反复困惑
    if (reason === 'mainSwitchOff') {
      wx.showModal({
        title: '总开关仍未打开',
        content: '检测到微信「订阅消息」总开关仍处于关闭状态，「取餐提醒」和「优惠券过期提醒」依然无法送达。请重新进入设置，在「订阅消息」页面最顶部打开「接收订阅消息」总开关（最顶部的总开关，不是单个模板的开关）。',
        showCancel: false,
        confirmText: '我知道了',
        confirmColor: '#B8D060'
      });
    } else {
      wx.showModal({
        title: '优惠券提醒仍未开启',
        content: '检测到「优惠券过期提醒」仍处于拒绝接收状态。请重新进入设置 → 订阅消息 → 「简知轻食」，把「优惠券过期提醒」打开为「接收」。',
        showCancel: false,
        confirmText: '我知道了',
        confirmColor: '#B8D060'
      });
    }
  },

  async submitOrder() {
    if (demo.isActive()) {
      wx.showToast({ title: '演示下单成功（未真实提交）', icon: 'none' });
      this.setData({
        showCheckout: false,
        showOrderSuccess: true,
        orderSuccessMsg: '演示下单成功，未真实提交任何数据',
        orderSuccessIds: ['demo-order']
      });
      return;
    }
    if (this.data.nightClosed) {
      const notice = getNightCloseNotice();
      wx.showModal({
        title: notice.title,
        content: notice.content,
        showCancel: false,
        confirmText: '我知道了',
        confirmColor: '#B8D060'
      });
      return;
    }
    if (this.data.submitting) {
      return;
    }
    if (!this.data.selfOrderEnabled) {
      wx.showToast({ title: this.data.selfOrderNotice || '请联系专属客服微信', icon: 'none' });
      return;
    }
    const selectedAddress = this.data.addresses.find((item) => item.id === this.data.selectedAddressId);
    if (!selectedAddress) {
      wx.showToast({ title: '请先选择配送地址', icon: 'none' });
      return;
    }
    const requests = [];
    if (this.data.qty1 > 0 && this.data.lunchItem) {
      requests.push(request({
        url: '/api/mobile/customer/orders',
        method: 'POST',
        header: { 'content-type': 'application/json' },
        data: {
          serveDate: this.data.serveDate,
          mealPeriod: 'LUNCH',
          deliveryAddress: selectedAddress.addressLine,
          note: this.data.remark,
          quantity: this.data.qty1
        }
      }));
    }
    if (this.data.qty2 > 0 && this.data.dinnerItem) {
      requests.push(request({
        url: '/api/mobile/customer/orders',
        method: 'POST',
        header: { 'content-type': 'application/json' },
        data: {
          serveDate: this.data.serveDate,
          mealPeriod: 'DINNER',
          deliveryAddress: selectedAddress.addressLine,
          note: this.data.remark,
          quantity: this.data.qty2
        }
      }));
    }
    if (!requests.length) {
      wx.showToast({ title: '请先选择餐食', icon: 'none' });
      return;
    }
    // 防重复提交：进入本方法即锁定提交按钮（含微信授权弹窗期间，避免授权中二次点击触发重复下单）。
    this.setData({ submitting: true });
    // 下单前强制请求「取餐 + 每晚」两个模板授权，各获得 1 条当天额度（不要求「总是保持」）。
    // 必须在任何 await 之前同步触发 requestSubscribeMessage，否则会脱离用户点击栈被微信拦截
    // （报 "can only be invoked by user TAP gesture"）。
    const subscribed = await this.requestSubscribeConsent();
    if (!subscribed) {
      this.setData({ submitting: false });
      return;
    }
    try {
      // 送达授权结果已在 requestSubscribeConsent 时写入缓存，此处随订单保存送达订阅；
      // 每晚提醒授权也已在 requestSubscribeConsent 时保存后端（AUTHORIZED），无需重复处理。
      const orderResults = await Promise.all(requests);
      const mergedCount = orderResults.filter((item) => item && item.status === 'MERGED').length;
      // 后端短时间重复下单拦截：ALREADY_RESERVED 表示该餐次已有订单且刚下过单，
      // 本次未重复扣餐，仅返回已有订单，避免"繁忙重试"导致数量翻倍。
      const alreadyReservedCount = orderResults.filter(
        (item) => item && item.walletAction === 'ALREADY_RESERVED'
      ).length;
      const orderIds = [...new Set(orderResults
        .map((item) => item && item.orderId)
        .filter(Boolean))];
      await saveOrderDeliverySubscription(orderIds);

      // Save remark to history
      if (this.data.remark) {
        addHistoryRemark(this.data.remark, {
          customerId: this.data.home && this.data.home.customerId
        });
      }

      let successMsg;
      if (alreadyReservedCount > 0) {
        successMsg = '检测到刚提交过相同餐次的订单，为避免重复扣餐，本次未再扣减，请勿重复点击下单';
      } else if (mergedCount > 0) {
        successMsg = `同地址餐次已自动合并到原订单，共扣减 ${this.data.totalQty} 餐`;
      } else {
        successMsg = `已成功预订明天的餐食，共扣减 ${this.data.totalQty} 餐`;
      }
      this.setData({
        showOrderSuccess: true,
        orderSuccessMsg: successMsg,
        orderSuccessIds: orderIds
      });
    } catch (error) {
      if (error.message && (error.message.includes('不足') || error.message.includes('INSUFFICIENT_MEALS'))) {
        wx.showModal({
          title: '餐次不足',
          content: '您的套餐剩余餐次不足，请联系专属客服充值',
          confirmText: '联系客服',
          confirmColor: '#B8D060',
          cancelText: '取消',
          success: (res) => {
            if (res.confirm) {
              wx.switchTab({ url: '/pages/profile/index' });
            }
          }
        });
      } else {
        // 网络/系统繁忙类失败：订单可能已提交成功但响应丢失，若直接重试会导致同餐次数量叠加。
        // 明确引导先到订单列表确认，避免重复提交多扣餐次。
        wx.showModal({
          title: '提交结果未确认',
          content: '网络或系统繁忙，订单可能已提交成功。请先到「我的订单」确认是否已下单，若未下单再点击重试，切勿连续点击。',
          confirmText: '去订单列表',
          confirmColor: '#B8D060',
          cancelText: '稍后再试',
          success: (res) => {
            if (res.confirm) {
              wx.navigateTo({ url: '/pages/orders/index' });
            }
          }
        });
      }
    } finally {
      this.setData({ submitting: false });
    }
  },

  goAddressManage() {
    if (!getApp().globalData.token) {
      openInlineAuth(this, 'order');
      return;
    }
    wx.navigateTo({ url: '/pages/addresses/index' });
  },

  closeInlineAuth() {
    this.setData({
      showInlineAuth: false,
      pendingAction: ''
    });
  },

  goProfileAuth() {
    this.closeInlineAuth();
    wx.switchTab({ url: '/pages/profile/index' });
  },

  noop() {},

  confirmOrderSuccess() {
    const orderIds = this.data.orderSuccessIds || [];
    this.setData({
      showOrderSuccess: false,
      showCheckout: false,
      qty1: 0,
      qty2: 0
    });
    this.syncCheckoutState();
    this.loadOrderData();
    wx.navigateTo({
      url: orderIds.length
        ? `/pages/orders/index?orderId=${orderIds[0]}`
        : '/pages/orders/index'
    });
  },

  closeOrderSuccess() {
    this.setData({
      showOrderSuccess: false,
      showCheckout: false,
      qty1: 0,
      qty2: 0
    });
    this.syncCheckoutState();
    this.loadOrderData();
  },

  promptAuth() {
    openInlineAuth(this, 'order');
  },

  syncCheckoutState() {
    const checkoutItems = [];
    if (this.data.qty1 > 0 && this.data.lunchItem) {
      checkoutItems.push(toViewItem(this.data.lunchItem, this.data.qty1));
    }
    if (this.data.qty2 > 0 && this.data.dinnerItem) {
      checkoutItems.push(toViewItem(this.data.dinnerItem, this.data.qty2));
    }
    this.setData({
      totalQty: this.data.qty1 + this.data.qty2,
      checkoutItems
    });
  },

  restoreRemarkDraft(preferredRemark) {
    const initialRemark = resolveInitialRemark(
      wx.getStorageSync('orderRemarkDraft') || '',
      preferredRemark || ''
    );
    this.setData({
      customRemark: initialRemark,
      remark: initialRemark
    });
    wx.setStorageSync('orderRemarkDraft', initialRemark);
  },

  persistRemarkDraft(remark) {
    wx.setStorageSync('orderRemarkDraft', remark);
  },

  previewDate() {
    return formatMonthDay(this.data.serveDate);
  }
});
