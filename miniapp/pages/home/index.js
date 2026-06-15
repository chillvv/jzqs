const { request } = require('../../utils/request');
const { resolveMediaUrl } = require('../../utils/media-url');
const realtime = require('../../utils/realtime');

Page({
  data: {
    home: null,
    rangeText: '',
    weekCards: [],
    loading: false,
    fullscreenAnnouncementLines: [],
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
        selected: 0
      })
    }
    this.startRealtimeSync();
    this.loadPageData();
  },

  async loadPageData() {
    const app = getApp();
    await app.waitForAuth();
    this.setData({ loading: true });
    try {
      const [home, currentWeek] = await Promise.all([
        request({ url: '/api/mobile/customer/home', requireAuth: false }),
        request({ url: '/api/mobile/customer/menu/current-week', requireAuth: false })
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
      this.setData({
        home: resolvedHome,
        rangeText: `${currentWeek.weekStartDate.slice(5).replace('-', '.')} - ${currentWeek.weekEndDate.slice(5).replace('-', '.')}`,
        weekCards: currentWeek.days.map((day) => ({
          ...day,
          shortDate: day.serveDate.slice(5).replace('-', '.'),
          isRestDay: day.slotStatus === 'REST',
          isPendingDay: day.slotStatus === 'UNCONFIGURED'
        }))
      });

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
    } catch (error) {
      wx.showToast({ title: error.message || '加载失败', icon: 'none' });
    } finally {
      this.setData({ loading: false });
      wx.stopPullDownRefresh();
    }
  },

  onPullDownRefresh() {
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
      request({ url: '/api/mobile/customer/home', requireAuth: false })
        .then((home) => {
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
