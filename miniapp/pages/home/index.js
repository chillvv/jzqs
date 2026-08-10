const { shareAppMessage, shareTimeline } = require('../../utils/share');
const { request } = require('../../utils/request');
const { resolveMediaUrl } = require('../../utils/media-url');
const realtime = require('../../utils/realtime');
const auth = require('../../utils/auth');
const demo = require('../../utils/demo');
const onboarding = require('../../utils/onboarding');

const MEAL_REMINDER_DISMISSED_PREFIX = 'miniapp_meal_reminder_dismissed_';

const WEEK_TABS = [
  { key: 0, label: '本周', title: '本周主厨菜单' },
  { key: 1, label: '下周', title: '下周主厨菜单' }
];

function formatShortDate(dateText) {
  return String(dateText || '').slice(5).replace('-', '.');
}

function decorateDay(day) {
  return Object.assign({}, day, {
    shortDate: formatShortDate(day.serveDate),
    isRestDay: day.slotStatus === 'REST',
    isPendingDay: day.slotStatus === 'UNCONFIGURED',
    restText: day.slotStatus === 'REST'
      ? (day.restNotice || `${day.weekdayLabel}固定店休，法定节假日不出餐`)
      : ''
  });
}

function buildWeekView(week) {
  if (!week || !week.weekStartDate) {
    return { rangeText: '', published: false, cards: [] };
  }
  return {
    rangeText: `${formatShortDate(week.weekStartDate)} - ${formatShortDate(week.weekEndDate)}`,
    // 后端未返回 published 时按「已发布」处理，避免老版本接口把菜单整周藏起来
    published: week.published !== false,
    cards: (week.days || []).map(decorateDay)
  };
}

// 明天属于下一周时（即今天是周日），首页默认展示下周，
// 这样客户在周日也能看到并提前预订下周一的餐。
function defaultWeekTab() {
  return new Date().getDay() === 0 ? 1 : 0;
}

function readMealReminderDismissed(key) {
  if (!key) {
    return false;
  }
  try {
    return !!wx.getStorageSync(`${MEAL_REMINDER_DISMISSED_PREFIX}${key}`);
  } catch (_) {
    return false;
  }
}

function persistMealReminderDismissed(key, dismissed) {
  if (!key) {
    return;
  }
  try {
    const storageKey = `${MEAL_REMINDER_DISMISSED_PREFIX}${key}`;
    if (dismissed) {
      wx.setStorageSync(storageKey, true);
      return;
    }
    wx.removeStorageSync(storageKey);
  } catch (_) {}
}

Page({
  onShareAppMessage: shareAppMessage,
  onShareTimeline: shareTimeline,
  data: {
    home: null,
    rangeText: '',
    weekCards: [],
    weekTabs: WEEK_TABS,
    weekTab: defaultWeekTab(),
    weekTitle: WEEK_TABS[defaultWeekTab()].title,
    weekUnpublished: false,
    weekViews: [],
    loading: false,
    fullscreenAnnouncementLines: [],
    showMealReminderPopup: false,
    mealReminderChecked: false,
    mealReminderKey: '',
    statusBarHeight: 0,
    navBarHeight: 44,
    demoActive: false
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
      this.getTabBar().setData({ selected: 0 });
    }
    this.startRealtimeSync();
    this.loadPageData();
  },

  async loadPageData() {
    // 演示模式：用沙盒假数据，不请求真实接口
    if (demo.isActive() && onboarding.isRunningFlow()) {
      const mock = demo.getMockHomeData();
      this.setData({
        home: mock.home,
        rangeText: mock.rangeText,
        weekCards: mock.weekCards,
        weekViews: [{ rangeText: mock.rangeText, published: true, cards: mock.weekCards }],
        weekUnpublished: false,
        loading: false,
        fullscreenAnnouncementLines: [],
        showMealReminderPopup: false,
        demoActive: true
      });
      wx.stopPullDownRefresh();
      this.showGuide();
      return;
    }

    const app = getApp();
    await app.waitForAuth();
    this.setData({ loading: true });
    try {
      const [home, currentWeek, nextWeek] = await Promise.all([
        request({ url: '/api/mobile/customer/home', requireAuth: false }),
        request({ url: '/api/mobile/customer/menu/current-week', requireAuth: false }),
        // 下周菜单：即使没配置也会返回 7 天「待配置」占位，不会失败
        request({ url: '/api/mobile/customer/menu/next-week', requireAuth: false }).catch(() => null)
      ]);
      const resolvedHome = {
        ...home,
        bannerImages: (home.bannerImages || []).map((item) => {
          if (typeof item === 'string') {
            return {
              imageUrl: resolveMediaUrl(item, app.globalData.apiBaseUrl),
              enabled: true
            };
          }
          return {
            imageUrl: resolveMediaUrl(item.imageUrl || item.url || '', app.globalData.apiBaseUrl),
            enabled: item.enabled !== false
          };
        })
      };
      const weekViews = [buildWeekView(currentWeek), buildWeekView(nextWeek)];
      this.setData({ home: resolvedHome, weekViews });
      this.applyWeekTab(this.data.weekTab);

      if (
        resolvedHome.popupAnnouncementEnabled &&
        resolvedHome.popupAnnouncementContent
      ) {
        this.setData({
          fullscreenAnnouncementLines: String(resolvedHome.popupAnnouncementContent)
            .split(/\r?\n/)
            .map((line) => line.trim())
            .filter(Boolean)
        });
        this.startAnnouncementPolling();
      } else {
        this.stopAnnouncementPolling();
        this.setData({ fullscreenAnnouncementLines: [] });
      }
      this.syncMealReminderPopup(resolvedHome);
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
      this.showGuide();
    }
  },

  applyWeekTab(tab) {
    const index = Number(tab) === 1 ? 1 : 0;
    const views = this.data.weekViews || [];
    const view = views[index] || { rangeText: '', published: true, cards: [] };
    this.setData({
      weekTab: index,
      weekTitle: WEEK_TABS[index].title,
      rangeText: view.rangeText,
      weekCards: view.cards,
      weekUnpublished: !view.published
    });
  },

  switchWeekTab(e) {
    const tab = Number(e.currentTarget.dataset.tab);
    if (tab === this.data.weekTab) {
      return;
    }
    this.applyWeekTab(tab);
  },

  onGuideDone() {},

  onGuideSkip() {},

  showGuide() {
    if (onboarding.shouldRunStageHere('flow_home')) {
      onboarding.runCurrentStage(this);
    }
  },

  onPullDownRefresh() {
    wx.vibrateShort({ type: 'light' });
    this.loadPageData();
  },

  onHide() {
    this.stopAnnouncementPolling();
    this.stopRealtimeSync();
  },

  onUnload() {
    this.stopAnnouncementPolling();
    this.stopRealtimeSync();
  },

  _pollAnnouncementTimer: null,

  startAnnouncementPolling() {
    this.stopAnnouncementPolling();
    this._pollAnnouncementTimer = setInterval(() => {
      // 兜底：页面已从栈中销毁（subPageFrame 为 null）时立即停掉轮询，
      // 避免回调里访问框架导致 Cannot read property '__subPageFrameEndTime__' of null
      if (getCurrentPages().indexOf(this) === -1) {
        this.stopAnnouncementPolling();
        return;
      }
      request({ url: '/api/mobile/customer/home', requireAuth: false })
        .then((home) => {
          if (getCurrentPages().indexOf(this) === -1) return;
          if (!home.popupAnnouncementEnabled) {
            this.stopAnnouncementPolling();
            this.loadPageData();
          }
        })
        .catch(() => {});
    }, 30000);
  },

  stopAnnouncementPolling() {
    if (this._pollAnnouncementTimer) {
      clearInterval(this._pollAnnouncementTimer);
      this._pollAnnouncementTimer = null;
    }
  },

  startRealtimeSync() {
    this.stopRealtimeSync();
    this._unsubscribeRealtime = realtime.subscribe((message) => {
      const eventType = String((message && message.eventType) || '');
      if (!eventType.startsWith('system.') && !eventType.startsWith('customer.')) {
        return;
      }
      this.loadPageData();
    });
  },

  stopRealtimeSync() {
    if (this._unsubscribeRealtime) {
      this._unsubscribeRealtime();
      this._unsubscribeRealtime = null;
    }
  },

  syncMealReminderPopup(home) {
    const mealReminderKey = String(home && home.mealReminderKey || '').trim();
    const shouldShow = Boolean(
      home
      && home.mealReminderPopupEnabled
      && mealReminderKey
      && home.mealReminderMessage
      && !readMealReminderDismissed(mealReminderKey)
      && !(home.popupAnnouncementEnabled && home.popupAnnouncementContent)
    );
    this.setData({
      showMealReminderPopup: shouldShow,
      mealReminderChecked: false,
      mealReminderKey
    });
  },

  toggleMealReminderChecked() {
    this.setData({ mealReminderChecked: !this.data.mealReminderChecked });
  },

  closeMealReminderPopup() {
    if (this.data.mealReminderChecked && this.data.mealReminderKey) {
      persistMealReminderDismissed(this.data.mealReminderKey, true);
    }
    this.setData({
      showMealReminderPopup: false,
      mealReminderChecked: false
    });
  },

  handleBannerTap(e) {
    const { index } = e.currentTarget.dataset;
    const images = ((this.data.home && this.data.home.bannerImages) || [])
      .map((item) => item.imageUrl)
      .filter(Boolean);
    if (!images.length) {
      return;
    }
    wx.previewImage({
      current: images[index] || images[0],
      urls: images
    });
  }
});
