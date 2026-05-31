Component({
  options: {
    multipleSlots: false
  },
  
  properties: {
    // 序号
    sequence: {
      type: Number,
      value: 1
    },
    // 客户姓名
    customerName: {
      type: String,
      value: ''
    },
    // 配送地址
    address: {
      type: String,
      value: ''
    },
    // 餐期标签
    mealLabel: {
      type: String,
      value: ''
    },
    // 备注
    note: {
      type: String,
      value: ''
    },
    // 状态标签
    statusLabel: {
      type: String,
      value: ''
    },
    // 状态样式类
    statusClass: {
      type: String,
      value: 'tag-gray'
    },
    // 是否高亮显示（当前订单）
    highlight: {
      type: Boolean,
      value: false
    },
    // 是否已送达
    delivered: {
      type: Boolean,
      value: false
    },
    // 批次项ID
    batchItemId: {
      type: String,
      value: ''
    },
    // 餐期订单ID
    mealSlotOrderId: {
      type: String,
      value: ''
    }
  },

  methods: {
    /**
     * 点击卡片 - 导航到详情页
     */
    onTap() {
      this.triggerEvent('tap', {
        batchItemId: this.data.batchItemId,
        mealSlotOrderId: this.data.mealSlotOrderId,
        sequence: this.data.sequence
      });
    },

    /**
     * 长按卡片 - 进入拖拽模式
     */
    onLongPress() {
      // 触发震动反馈
      wx.vibrateShort({
        type: 'medium'
      });
      
      this.triggerEvent('longpress', {
        batchItemId: this.data.batchItemId,
        mealSlotOrderId: this.data.mealSlotOrderId,
        sequence: this.data.sequence
      });
    },

    /**
     * 导航按钮
     */
    onNavigate(e) {
      // 阻止事件冒泡到卡片的tap事件
      e.stopPropagation();
      
      this.triggerEvent('navigate', {
        batchItemId: this.data.batchItemId,
        mealSlotOrderId: this.data.mealSlotOrderId,
        address: this.data.address
      });
    },

    /**
     * 拨打电话按钮
     */
    onCall(e) {
      // 阻止事件冒泡到卡片的tap事件
      e.stopPropagation();
      
      this.triggerEvent('call', {
        batchItemId: this.data.batchItemId,
        mealSlotOrderId: this.data.mealSlotOrderId,
        customerName: this.data.customerName
      });
    },

    /**
     * 标记完成按钮
     */
    onMarkDone(e) {
      // 阻止事件冒泡到卡片的tap事件
      e.stopPropagation();
      
      this.triggerEvent('markdone', {
        batchItemId: this.data.batchItemId,
        mealSlotOrderId: this.data.mealSlotOrderId,
        sequence: this.data.sequence
      });
    }
  }
});
