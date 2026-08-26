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
  syncNightlySubscription,
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

/**
 * 判断「每晚用餐提醒」是否真的开启了「总是保持」：
 * - 微信侧能查到状态（supported=true）→ 以微信侧为准：后端快照 + 微信实时都成立才放行，
 *   避免用户在微信设置里关闭订阅后，库里的 AUTHORIZED 快照造成「假成功」；
 * - 微信侧查不到（supported=false，极老基础库 / 调用失败）→ 降级信任后端快照，避免误拦老用户。
 */
function resolveNightlySubscribed(backendSubscribed, realStatus) {
  if (!realStatus || !realStatus.supported) {
    return backendSubscribed;
  }
  return backendSubscribed && realStatus.accepted;
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
          : Promise.resolve(null),
        app.globalData.token
          ? queryNightlySubscribeStatus().catch(() => ({ supported: false, accepted: false }))
          : Promise.resolve({ supported: false, accepted: false })
      ];
      const [home, tomorrowMenu, addresses, nightlyStatus, nightlyRealStatus] = await Promise.all(tasks);
      // 是否已开启「总是保持」的每晚用餐提醒，是下单的唯一放行条件（见 submitOrder）。
      // 关键：不能只信后端落库状态（用户在微信设置里关闭订阅后，后端不会感知，库里的
      // AUTHORIZED 是历史快照），必须与微信侧实时查询结果同时成立，避免「假成功」。
      // 未授权、仅单次授权或已被用户关闭的均视为未开启，需点击勾选框并完成「总是保持」授权后才能下单。
      const backendSubscribed = !!(nightlyStatus && nightlyStatus.subscribed);
      // 微信侧能查到真实状态且与后端快照不一致时，静默回传同步后端：
      // 用户重新打开 → 恢复 AUTHORIZED（避免「重新打开后失效」）；用户关闭 → 置 CANCELLED（纠正假成功）。
      if (nightlyRealStatus.supported && backendSubscribed !== nightlyRealStatus.accepted) {
        syncNightlySubscription(nightlyRealStatus.accepted);
      }
      const nightlySubscribed = resolveNightlySubscribed(backendSubscribed, nightlyRealStatus);
      this.setData({ nightlySubscribed, subscribeConsent: nightlySubscribed });
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
   * 核心订阅授权流程：弹出微信授权框申请「送达 + 每晚提醒」，确认真实「总是保持」后返回结果。
   * 返回 true 表示已开启「总是保持」，false 表示未开启 / 被拒绝 / 授权失败（内部已给出提示）。
   */
  async requestSubscribeConsent() {
    if (this.data.consentingSubscribe) {
      return this.data.nightlySubscribed;
    }
    this.setData({ consentingSubscribe: true });
    try {
      // 一次弹窗申请「送达 + 每晚提醒」两个授权
      const results = await requestCombinedSubscribeAuthorization();
      const { delivery, nightly } = results || {};
      if (delivery) {
        cacheDeliveryAcceptResult(delivery);
      }
      // 只有「每晚提醒」授权成功，才写入后端，并用微信侧实时状态确认真实开启了「总是保持」。
      // requestSubscribeMessage 的返回值对「单次授权」和「总是保持」都是 'accept'，无法区分，
      // 必须用 wx.getSetting 实时校验 itemSettings 是否为 'accept'，以微信侧真实状态为准。
      if (nightly) {
        await saveNightlySubscription(nightly);
        // 刚授权成功，后端已落库 AUTHORIZED；微信侧能查到则以「总是保持」实时状态为准，
        // 查不到则信任刚落库的成功结果。
        const realStatus = await queryNightlySubscribeStatus().catch(() => ({ supported: false, accepted: false }));
        const nowSubscribed = resolveNightlySubscribed(true, realStatus);
        this.setData({
          nightlySubscribed: nowSubscribed,
          subscribeConsent: nowSubscribed
        });
        if (nowSubscribed) {
          wx.showToast({ title: '已开启总是用餐提醒', icon: 'success' });
        } else {
          // 微信侧未确认为「总是保持」：不计入放行，提示用户需勾选「总是保持」保持开启
          this.setData({ subscribeConsent: false });
          wx.showToast({ title: '需开启「总是保持」提醒才能下单', icon: 'none' });
        }
        return nowSubscribed;
      }
      // 未授权每晚提醒：不视为已开启
      this.setData({ subscribeConsent: false });
      wx.showToast({ title: '未开启提醒，无法下单', icon: 'none' });
      return false;
    } catch (error) {
      this.setData({ subscribeConsent: false, nightlySubscribed: false });
      wx.showToast({ title: error.message || '订阅授权失败', icon: 'none' });
      return false;
    } finally {
      this.setData({ consentingSubscribe: false });
    }
  },

  /**
   * 点击「同意接收订单通知」勾选框：立即请求微信订阅授权。
   * 只有授权成功，勾选框才真正变为勾选；授权失败/取消则保持未勾选。
   * 若用户之前「总是拒绝」或关闭了总开关（微信不会再弹授权框），则引导去设置页手动开启。
   */
  async toggleSubscribeConsent() {
    // 已开启「总是保持」每晚提醒的用户，视为已开启，不可再取消
    if (this.data.nightlySubscribed) {
      return;
    }
    await this.attemptEnableSubscribe();
  },

  /**
   * 尝试开启订阅（复用给勾选框点击与下单前自动校验）。
   * 先查微信侧真实状态，按状态分流：
   * - 已 accept：直接标记成功并同步后端；
   * - 已「总是拒绝」或总开关关闭：requestSubscribeMessage 不会再弹出，引导去设置页开启；
   * - 其余（未设置/查不到）：正常弹出微信授权框。
   * 返回 true 表示已开启，false 表示未开启（内部已提示）。
   */
  async attemptEnableSubscribe(preStatus) {
    const status = preStatus || await queryNightlySubscribeStatus().catch(() => ({ supported: false, accepted: false, mainSwitch: false, rejected: false }));
    if (status.supported && status.accepted) {
      this.setData({ nightlySubscribed: true, subscribeConsent: true });
      syncNightlySubscription(true);
      wx.showToast({ title: '已开启总是用餐提醒', icon: 'success' });
      return true;
    }
    if (status.supported && (status.rejected || !status.mainSwitch)) {
      this.promptOpenSubscribeSetting();
      return false;
    }
    return this.requestSubscribeConsent();
  },

  /**
   * 引导用户去微信「设置 → 订阅消息」里重新开启。
   * 触发条件：用户「总是拒绝」或关闭了订阅消息总开关，此时 requestSubscribeMessage 不会再弹窗。
   */
  promptOpenSubscribeSetting() {
    wx.showModal({
      title: '开启用餐提醒',
      content: '你之前关闭了订阅消息，需要到「设置 → 订阅消息」里重新开启「总是保持」后才能下单。现在前往设置？',
      confirmText: '去设置',
      cancelText: '取消',
      confirmColor: '#B8D060',
      success: (res) => {
        if (res.confirm) {
          wx.openSetting({
            success: () => {
              this.refreshNightlySubscribeFromWx();
            }
          });
        }
      }
    });
  },

  /** 从微信设置页返回后，重新校验真实状态并更新 UI（用户可能已手动开启）。 */
  async refreshNightlySubscribeFromWx() {
    const status = await queryNightlySubscribeStatus().catch(() => ({ supported: false, accepted: false, mainSwitch: false, rejected: false }));
    if (status.supported && status.accepted) {
      this.setData({ nightlySubscribed: true, subscribeConsent: true });
      syncNightlySubscription(true);
      wx.showToast({ title: '已开启总是用餐提醒', icon: 'success' });
    }
  },

  /**
   * 下单前确保「每晚提醒」已开启「总是保持」。
   * 实时查询微信侧真实状态；若出现异常（页面加载后用户关闭、后端假成功、或从未开启），
   * 自动弹窗重新授权或引导去设置。返回 true 表示已开启，false 表示未开启（内部已提示）。
   */
  async ensureNightlySubscriptionForCheckout() {
    const realStatus = await queryNightlySubscribeStatus().catch(() => ({ supported: false, accepted: false }));
    if (resolveNightlySubscribed(this.data.nightlySubscribed, realStatus)) {
      return true;
    }
    // 检测到异常：清状态，然后按真实状态决定弹窗还是引导去设置
    this.setData({ nightlySubscribed: false, subscribeConsent: false });
    return this.attemptEnableSubscribe(realStatus);
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
    // 订阅放行校验：实时查询微信侧真实状态；未开启或出现异常（页面加载后用户关闭、
    // 后端假成功等）时自动弹出微信授权框让用户重新授权。授权成功才继续下单。
    const subscribed = await this.ensureNightlySubscriptionForCheckout();
    if (!subscribed) {
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
    this.setData({ submitting: true });
    try {
      // 订阅授权已在勾选「同意接收订单通知」时完成（见 toggleSubscribeConsent），
      // 送达授权结果已写入缓存，此处直接随订单保存送达订阅。
      // 每晚提醒也已在勾选时保存（AUTHORIZED），无需重复请求。
      const orderResults = await Promise.all(requests);
      const mergedCount = orderResults.filter((item) => item && item.status === 'MERGED').length;
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

      const successMsg = mergedCount > 0
        ? `同地址餐次已自动合并到原订单，共扣减 ${this.data.totalQty} 餐`
        : `已成功预订明天的餐食，共扣减 ${this.data.totalQty} 餐`;
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
        wx.showToast({ title: error.message || '下单失败', icon: 'none' });
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
