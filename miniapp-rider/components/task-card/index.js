Component({
  options: {
    multipleSlots: true
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
    // 客户电话
    customerPhone: {
      type: String,
      value: ''
    },
    // 配送地址
    deliveryAddress: {
      type: String,
      value: ''
    },
    // 餐期标签
    mealLabel: {
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
    // 备注
    note: {
      type: String,
      value: ''
    },
    // 是否高亮显示
    highlight: {
      type: Boolean,
      value: false
    },
    // 是否显示电话
    showPhone: {
      type: Boolean,
      value: true
    },
    // 是否显示操作按钮
    showActions: {
      type: Boolean,
      value: false
    },
    // 自定义样式类
    className: {
      type: String,
      value: ''
    }
  }
});
