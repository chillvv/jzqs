Component({
  data: {
    selected: 0,
    color: "#999999",
    selectedColor: "#1A1A1A",
    list: [
      {
        pagePath: "/pages/home/index",
        iconPath: "/assets/icons/home.png",
        selectedIconPath: "/assets/icons/home-active.png",
        text: "首页"
      },
      {
        pagePath: "/pages/order/index",
        iconPath: "/assets/icons/order.png",
        selectedIconPath: "/assets/icons/order-active.png",
        text: "点餐"
      },
      {
        pagePath: "/pages/profile/index",
        iconPath: "/assets/icons/profile.png",
        selectedIconPath: "/assets/icons/profile-active.png",
        text: "我的"
      }
    ]
  },
  attached() {
  },
  methods: {
    switchTab(e) {
      const data = e.currentTarget.dataset;
      const url = data.path;
      wx.switchTab({ url });
    }
  }
})