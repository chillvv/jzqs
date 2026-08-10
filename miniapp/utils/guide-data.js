// 顾客端新手指引步骤配置
// 幻灯片式，用户只需点「下一步」观看

const STEPS = [
  {
    mockType: 'home',
    tipTitle: '查看本周菜单',
    tipDesc: '首页展示本周主厨精心搭配的每日菜单，午晚餐一目了然',
    tipPlacement: 'bottom',
    tipTop: '440rpx',
    highlights: [
      { top: '280rpx', left: '24rpx', width: '702rpx', height: '340rpx', borderColor: '#b8d060' }
    ]
  },
  {
    mockType: 'order',
    tipTitle: '预订明日餐食',
    tipDesc: '选择午/晚餐，查看菜品详情和卡路里，按需增减份数',
    tipPlacement: 'bottom',
    tipTop: '280rpx',
    highlights: [
      { top: '180rpx', left: '24rpx', width: '702rpx', height: '340rpx', borderColor: '#b8d060' }
    ]
  },
  {
    mockType: 'order',
    tipTitle: '点击 + 加份',
    tipDesc: '每人每餐可预订多份，点 + 增加份数，点 - 减少',
    tipPlacement: 'bottom',
    tipTop: '420rpx',
    highlights: [
      { top: '530rpx', left: '520rpx', width: '116rpx', height: '56rpx', borderColor: '#92aa40' }
    ]
  },
  {
    mockType: 'checkout',
    tipTitle: '确认订单信息',
    tipDesc: '核对收货地址、餐食明细和份数，填写备注后确认预订',
    tipPlacement: 'top',
    tipBottom: '240rpx',
    highlights: [
      { top: '90rpx', left: '24rpx', width: '702rpx', height: '240rpx', borderColor: '#b8d060' }
    ]
  },
  {
    mockType: 'success',
    tipTitle: '预订成功',
    tipDesc: '提交后显示成功确认，可点击查看预订详情',
    tipPlacement: 'bottom',
    tipTop: '420rpx',
    highlights: [
      { top: '100rpx', left: '120rpx', width: '510rpx', height: '420rpx', borderColor: '#b8d060' }
    ]
  },
  {
    mockType: 'orders',
    tipTitle: '查看我的预订',
    tipDesc: '在「我的预订」里随时查看所有订单和配送状态',
    tipPlacement: 'center',
    highlights: [
      { top: '260rpx', left: '24rpx', width: '702rpx', height: '260rpx', borderColor: '#b8d060' }
    ]
  }
];

// 用于首页自动触发
const HOME_STORAGE_KEY = 'guide_v2_home_shown';

function shouldAutoShow() {
  try {
    if (wx.getStorageSync('guide_v2_done')) return false;
    if (wx.getStorageSync('guide_v2_dismiss')) return false;
    if (wx.getStorageSync(HOME_STORAGE_KEY)) return false;
    return true;
  } catch (e) {
    return false;
  }
}

function markHomeShown() {
  try { wx.setStorageSync(HOME_STORAGE_KEY, true); } catch (e) {}
}

module.exports = {
  STEPS,
  shouldAutoShow,
  markHomeShown
};
