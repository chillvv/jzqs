Component({
  data: {
    selected: 0,
    color: "#999999",
    selectedColor: "#2563EB",
    list: [
      {
        pagePath: "/pages/queue/index",
        iconPath: "/assets/icons/queue.svg",
        selectedIconPath: "/assets/icons/queue-active.svg",
        text: "订单"
      },
      {
        pagePath: "/pages/profile/index",
        iconPath: "/assets/icons/profile.svg",
        selectedIconPath: "/assets/icons/profile-active.svg",
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
