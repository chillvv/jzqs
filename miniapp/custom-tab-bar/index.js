Component({
  data: {
    selected: 0,
    color: "#999999",
    selectedColor: "#92AA40",
    list: [
      { pagePath: "/pages/home/index", text: "首页" },
      { pagePath: "/pages/order/index", text: "点餐" },
      { pagePath: "/pages/profile/index", text: "我的" }
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