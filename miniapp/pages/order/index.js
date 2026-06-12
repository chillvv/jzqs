const { request } = require('../../utils/request');
const { formatMonthDay, periodLabel } = require('../../utils/mobile');
const { getCheckoutMealLimitMessage } = require('../../utils/order-guards');
const {
  normalizeHistoryRemarkSuggestions,
  addHistoryRemark,
  composeRemark,
  resolveInitialRemark
} = require('../../utils/order-remark');

const DELIVERY_TEMPLATE_ID = 'LUxfZUE3i9iv2MyRpMmY-8jShHTAJhntznUFfEigZrA';
const DELIVERY_SUBSCRIPTION_HINT_KEY = 'deliverySubscriptionHintShown';

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

function showDeliverySubscriptionHint() {
  return new Promise((resolve) => {
    wx.showModal({
      title: '送达后提醒你',
      content: '用于在骑手送达并上传回执后，第一时间通知你查看送达结果。',
      confirmText: '继续',
      cancelText: '跳过',
      success(res) {
        wx.setStorageSync(DELIVERY_SUBSCRIPTION_HINT_KEY, true);
        resolve(Boolean(res.confirm));
      },
      fail() {
        resolve(false);
      }
    });
  });
}

async function requestDeliverySubscription(orderIds) {
  if (!Array.isArray(orderIds) || !orderIds.length) {
    return;
  }
  if (typeof wx.requestSubscribeMessage !== 'function') {
    return;
  }
  const shouldExplain = !wx.getStorageSync(DELIVERY_SUBSCRIPTION_HINT_KEY);
  if (shouldExplain) {
    const confirmed = await showDeliverySubscriptionHint();
    if (!confirmed) {
      return;
    }
  }
  const subscribeResult = await new Promise((resolve) => {
    wx.requestSubscribeMessage({
      tmplIds: [DELIVERY_TEMPLATE_ID],
      success: resolve,
      fail() {
        resolve({});
      }
    });
  });
  if (subscribeResult[DELIVERY_TEMPLATE_ID] !== 'accept') {
    return;
  }
  await Promise.all(orderIds.map((orderId) => request({
    url: `/api/mobile/customer/orders/${orderId}/delivery-subscription`,
    method: 'POST',
    header: { 'content-type': 'application/json' },
    data: {
      templateId: DELIVERY_TEMPLATE_ID,
      acceptResult: 'accept'
    }
  }).catch(() => null)));
}

Page({
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
    historyRemarkSuggestions: [],
    showRemarkDropdown: false,
    submitting: false,
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
    navBarHeight: 44
  },

  onLoad() {
    const app = getApp();
    this.setData({
      statusBarHeight: app.globalData.statusBarHeight,
      navBarHeight: app.globalData.navBarHeight
    });
  },

  onShow() {
    if (typeof this.getTabBar === 'function' && this.getTabBar()) {
      this.getTabBar().setData({
        selected: 1
      })
    }
    this.restoreRemarkDraft();
    this.loadOrderData();
  },

  onPullDownRefresh() {
    this.loadOrderData();
  },

  async loadOrderData() {
    const app = getApp();
    await app.waitForAuth();
    this.setData({ loading: true });
    try {
      const tasks = [
        request({ url: '/api/mobile/customer/home', requireAuth: false }),
        request({ url: '/api/mobile/customer/menu/tomorrow', requireAuth: false }),
        app.globalData.token
          ? request({ url: '/api/mobile/customer/addresses' })
          : Promise.resolve([])
      ];
      const [home, tomorrowMenu, addresses] = await Promise.all(tasks);
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
        menuItems,
        lunchItem,
        dinnerItem,
        addresses,
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
    this.setData({ qty1: 1 });
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
    this.setData({ qty2: 1 });
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
        title: '套餐余额不足',
        content: mealLimitMessage,
        confirmText: '联系顾问',
        confirmColor: '#B8D060',
        cancelText: '暂不需要',
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

  async submitOrder() {
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
          note: this.data.remark
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
          note: this.data.remark
        }
      }));
    }
    if (!requests.length) {
      wx.showToast({ title: '请先选择餐食', icon: 'none' });
      return;
    }
    this.setData({ submitting: true });
    try {
      const orderResults = await Promise.all(requests);
      const orderIds = orderResults
        .map((item) => item && item.orderId)
        .filter(Boolean);
      
      // Save remark to history
      if (this.data.remark) {
        addHistoryRemark(this.data.remark, {
          customerId: this.data.home && this.data.home.customerId
        });
      }
      
      wx.showModal({
        title: '下单成功',
        content: `已成功预订明天的餐食，共扣减 ${this.data.totalQty} 餐。`,
        showCancel: false,
        confirmText: '查看预订',
        confirmColor: '#B8D060',
        success: async () => {
          this.setData({
            showCheckout: false,
            qty1: 0,
            qty2: 0
          });
          this.syncCheckoutState();
          this.loadOrderData();
          await requestDeliverySubscription(orderIds);
          wx.navigateTo({
            url: orderIds.length
              ? `/pages/orders/index?orderId=${orderIds[0]}`
              : '/pages/orders/index'
          });
        }
      });
    } catch (error) {
      if (error.message && (error.message.includes('不足') || error.message.includes('INSUFFICIENT_MEALS'))) {
        wx.showModal({
          title: '餐次不足',
          content: '您的套餐剩余餐次不足，请联系专属客服充值。',
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
