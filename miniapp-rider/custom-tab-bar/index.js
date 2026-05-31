Component({
  data: {
    selected: 0,
    color: "#999999",
    selectedColor: "#2563EB",
    list: [
      {
        pagePath: "/pages/queue/index",
        iconPath: "/assets/tab-order.png",
        selectedIconPath: "/assets/tab-order-active.png",
        text: "订单"
      },
      {
        pagePath: "/pages/profile/index",
        iconPath: "/assets/tab-profile.png",
        selectedIconPath: "/assets/tab-profile-active.png",
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
