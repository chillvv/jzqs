const { request } = require('../../utils/request');

Page({
  data: {
    home: null,
    rangeText: '',
    weekCards: [],
    loading: false,
    heroExpanded: false
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
      this.setData({
        home,
        rangeText: `${currentWeek.weekStartDate.slice(5).replace('-', '.')} - ${currentWeek.weekEndDate.slice(5).replace('-', '.')}`,
        weekCards: currentWeek.days.map((day) => ({
          ...day,
          shortDate: day.serveDate.slice(5).replace('-', '.'),
          isRestDay: day.slotStatus === 'REST',
          isPendingDay: day.slotStatus === 'UNCONFIGURED'
        }))
      });

      if (app.globalData.token && home.popupAnnouncementEnabled && home.popupAnnouncementContent && !app.globalData.announcementShown) {
        wx.showModal({
          title: '系统公告',
          content: home.popupAnnouncementContent,
          showCancel: false,
          confirmText: '我知道了',
          confirmColor: '#92AA40',
          success: () => {
            app.globalData.announcementShown = true;
          }
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
  }
});
