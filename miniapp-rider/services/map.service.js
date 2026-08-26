// 地图导航服务：提供地图选择弹窗，并拉起手机里已安装的原生地图 App（高德 / 腾讯 / 百度）
// 注意：微信小程序无法直接 openURL Scheme，因此通过一个 web-view 中转页（map-nav.html）
// 在 H5 中调用各家地图的 URL Scheme 来唤起原生 App。

// 可选地图列表（新增百度地图）
const MAP_OPTIONS = [
  { label: '高德地图', value: 'amap', color: '#1AAD19' },
  { label: '腾讯地图', value: 'tencent', color: '#07C160' },
  { label: '百度地图', value: 'baidu', color: '#2932E1' },
  { label: '复制地址', value: 'copy', color: '#999999' }
];

// 返回可跳转的中转页 URL（由 web-view 加载 H5 来拉起原生 App）
function buildNavUrl({ platform, name, addr }) {
  const app = getApp();
  const base = (app && app.globalData && app.globalData.apiBaseUrl) || 'https://jzqs.top';
  const root = String(base).replace(/\/+$/, '');
  const params = [
    'platform=' + encodeURIComponent(platform),
    'name=' + encodeURIComponent(name || ''),
    'addr=' + encodeURIComponent(addr || '')
  ].join('&');
  return '/pages/map-nav/index?' + params;
}

// 拉起导航：兼容传入 order 对象（取 deliveryAddress）或显式 name/address
function navigate(orderOrParams) {
  let name = '';
  let address = '';
  if (orderOrParams) {
    if (typeof orderOrParams === 'object') {
      name = orderOrParams.customerName || '';
      address = orderOrParams.deliveryAddress || orderOrParams.address || '';
    } else {
      name = orderOrParams.name || '';
      address = orderOrParams.address || '';
    }
  }
  openMapNavigation({ name, address });
}

// 拉起导航：弹出选择框 -> 选择后跳转到原生地图 App（而非小程序）
function openMapNavigation({ name = '', address = '' } = {}) {
  wx.showActionSheet({
    itemList: MAP_OPTIONS.map(o => o.label),
    success(res) {
      const option = MAP_OPTIONS[res.tapIndex];
      if (!option) return;

      if (option.value === 'copy') {
        wx.setClipboardData({
          data: address || name,
          success() {
            wx.showToast({ title: '地址已复制', icon: 'success' });
          }
        });
        return;
      }

      // 先把地址复制到剪贴板（带着地址去地图 App），再拉起手机里已安装的原生地图 App
      const addressText = address || name;
      if (addressText) {
        wx.setClipboardData({
          data: addressText,
          success() {
            wx.showToast({ title: '地址已复制，正在打开地图', icon: 'none' });
          }
        });
      }
      // 跳转到中转页，由 H5 拉起地图 App 并搜索该地址
      wx.navigateTo({
        url: buildNavUrl({ platform: option.value, name, addr: address })
      });
    }
  });
}

module.exports = {
  MAP_OPTIONS,
  navigate,
  openMapNavigation,
  buildNavUrl
};
