/**
 * 地图导航服务
 * 提供多种地图导航方式
 */

const { request } = require('../utils/request');

/**
 * 地图导航选项
 */
const MAP_OPTIONS = [
  { name: '微信地图导航（推荐）', value: 'wechat' },
  { name: '高德地图', value: 'amap' },
  { name: '腾讯地图', value: 'tencent' },
  { name: '复制地址', value: 'copy' }
];

/**
 * 高德地图小程序AppID
 */
const AMAP_APPID = 'wxe2b370c2d7c7b2e5';

/**
 * 腾讯地图小程序AppID（需确认）
 */
const TENCENT_MAP_APPID = 'wx6d9c0a3c3e5d5e5e';

/**
 * 显示地图导航选项
 * @param {Object} item - 配送任务项
 * @param {Function} callback - 选择回调
 */
function showMapOptions(item, callback) {
  if (!item || !item.deliveryAddress) {
    wx.showToast({ title: '地址信息不完整', icon: 'none' });
    return;
  }

  wx.showActionSheet({
    itemList: MAP_OPTIONS.map(opt => opt.name),
    success: (res) => {
      const option = MAP_OPTIONS[res.tapIndex];
      if (callback) {
        callback(option.value, item);
      }
    }
  });
}

/**
 * 微信地图导航
 * @param {Object} item - 配送任务项
 */
async function openWechatMap(item) {
  wx.showLoading({ title: '正在定位...' });

  try {
    const app = getApp();
    const riderName = app.getActiveRiderName();

    // 调用后台地理编码接口
    const geocode = await request({
      url: `/api/mobile/rider/geocode?riderName=${encodeURIComponent(riderName)}&address=${encodeURIComponent(item.deliveryAddress)}`
    });

    wx.hideLoading();

    // 打开微信地图
    wx.openLocation({
      latitude: geocode.latitude,
      longitude: geocode.longitude,
      name: item.customerName || '配送地址',
      address: item.deliveryAddress,
      scale: 18
    });

    // 记录导航日志
    console.log('[地图导航] 微信地图', {
      fromCache: geocode.fromCache,
      confidence: geocode.confidence
    });

  } catch (error) {
    wx.hideLoading();
    console.error('[地图导航] 失败', error);

    // 降级到复制地址
    wx.showModal({
      title: '定位失败',
      content: error.message || '无法获取地址坐标，是否复制地址手动导航？',
      success: (res) => {
        if (res.confirm) {
          copyAddress(item);
        }
      }
    });
  }
}

/**
 * 高德地图导航
 * @param {Object} item - 配送任务项
 */
function openAmapNavigation(item) {
  wx.navigateToMiniProgram({
    appId: AMAP_APPID,
    path: `pages/index/index?keyword=${encodeURIComponent(item.deliveryAddress)}`,
    success: () => {
      console.log('[地图导航] 高德地图成功');
    },
    fail: (error) => {
      console.error('[地图导航] 高德地图失败', error);
      wx.showModal({
        title: '跳转失败',
        content: '未安装高德地图小程序，是否复制地址？',
        success: (res) => {
          if (res.confirm) {
            copyAddress(item);
          }
        }
      });
    }
  });
}

/**
 * 腾讯地图导航
 * @param {Object} item - 配送任务项
 */
function openTencentMapNavigation(item) {
  wx.navigateToMiniProgram({
    appId: TENCENT_MAP_APPID,
    path: `pages/index/index?keyword=${encodeURIComponent(item.deliveryAddress)}`,
    success: () => {
      console.log('[地图导航] 腾讯地图成功');
    },
    fail: (error) => {
      console.error('[地图导航] 腾讯地图失败', error);
      wx.showModal({
        title: '跳转失败',
        content: '未安装腾讯地图小程序，是否复制地址？',
        success: (res) => {
          if (res.confirm) {
            copyAddress(item);
          }
        }
      });
    }
  });
}

/**
 * 复制地址
 * @param {Object} item - 配送任务项
 */
function copyAddress(item) {
  if (!item || !item.deliveryAddress) {
    wx.showToast({ title: '地址信息不完整', icon: 'none' });
    return;
  }

  wx.setClipboardData({
    data: item.deliveryAddress,
    success: () => {
      wx.showToast({
        title: '地址已复制',
        icon: 'success',
        duration: 2000
      });
    }
  });
}

/**
 * 统一导航入口
 * @param {Object} item - 配送任务项
 */
function navigate(item) {
  showMapOptions(item, (type, taskItem) => {
    switch (type) {
      case 'wechat':
        openWechatMap(taskItem);
        break;
      case 'amap':
        openAmapNavigation(taskItem);
        break;
      case 'tencent':
        openTencentMapNavigation(taskItem);
        break;
      case 'copy':
        copyAddress(taskItem);
        break;
      default:
        copyAddress(taskItem);
    }
  });
}

module.exports = {
  navigate,
  showMapOptions,
  openWechatMap,
  openAmapNavigation,
  openTencentMapNavigation,
  copyAddress
};
