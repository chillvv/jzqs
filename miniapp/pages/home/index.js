const { request } = require('../../utils/request');
const { resolveMediaUrl } = require('../../utils/media-url');

Page({
  data: {
    home: null,
    rangeText: '',
    weekCards: [],
    loading: false,
    heroExpanded: false,
    showAnnouncementModal: false,
    announcementTitle: '',
    announcementLines: [],
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
        bannerImages: (home.bannerImages || []).map((item) =>
          resolveMediaUrl(item, app.globalData.apiBaseUrl)
        )
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
        app.globalData.token &&
        resolvedHome.popupAnnouncementEnabled &&
        resolvedHome.popupAnnouncementContent &&
        !app.globalData.announcementShown
      ) {
        app.globalData.announcementShown = true;
        this.setData({
          showAnnouncementModal: true,
          announcementTitle: resolvedHome.holidayNoticeTitle || '系统公告',
          announcementLines: String(resolvedHome.popupAnnouncementContent)
            .split(/\r?\n/)
            .map((line) => line.trim())
            .filter(Boolean)
        });
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

  toggleHero() {
    this.setData({ heroExpanded: !this.data.heroExpanded });
  },

  closeAnnouncementModal() {
    this.setData({
      showAnnouncementModal: false
    });
  }
});
