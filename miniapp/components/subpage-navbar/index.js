Component({
  options: {
    addGlobalClass: true
  },
  properties: {
    title: {
      type: String,
      value: ''
    }
  },

  data: {
    statusBarHeight: 0,
    navBarHeight: 44
  },

  lifetimes: {
    attached: function () {
      const app = getApp();
      this.setData({
        statusBarHeight: app.globalData.statusBarHeight || 0,
        navBarHeight: app.globalData.navBarHeight || 44
      });
    }
  },

  methods: {
    goBack: function () {
      wx.navigateBack({
        fail: function () {
          wx.switchTab({ url: '/pages/home/index' });
        }
      });
    }
  }
});
