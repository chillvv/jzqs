/**
 * 进度条组件
 */
Component({
  properties: {
    // 当前值
    current: {
      type: Number,
      value: 0
    },
    // 总值
    total: {
      type: Number,
      value: 100
    },
    // 显示文本
    showText: {
      type: Boolean,
      value: true
    },
    // 进度条颜色
    color: {
      type: String,
      value: '#3b82f6'
    },
    // 背景颜色
    backgroundColor: {
      type: String,
      value: '#e5e7eb'
    },
    // 高度
    height: {
      type: String,
      value: '16rpx'
    },
    // 圆角
    borderRadius: {
      type: String,
      value: '999rpx'
    },
    // 动画时长（ms）
    duration: {
      type: Number,
      value: 300
    }
  },

  data: {
    percentage: 0
  },

  lifetimes: {
    attached() {
      this.updatePercentage();
    }
  },

  observers: {
    'current, total': function() {
      this.updatePercentage();
    }
  },

  methods: {
    updatePercentage() {
      const { current, total } = this.data;
      const percentage = total > 0 ? Math.round((current / total) * 100) : 0;
      this.setData({ percentage: Math.min(percentage, 100) });
    }
  }
});
