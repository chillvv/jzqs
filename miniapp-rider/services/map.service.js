// 地图导航服务：点一下直接调起地图进行导航。
// 由于微信小程序限制 + 后端订单未返回经纬度，采用 wx.openLocation 直接出微信内置地图，
// 无需 web-view 中转页（避免真机 file not found），用户可直接在地图内导航，
// 并在底部选择「在 XX 地图中打开」跳转到手机已安装的原生地图 App。

// 项目所在城市兜底坐标（长沙），没有精确经纬度时先把地图定位到该城市，再按地址名搜索
const FALLBACK_CITY = { latitude: 28.2282, longitude: 112.9388 };

// 拉起导航：兼容传入 order 对象（取 deliveryAddress）或显式 name/address
function navigate(orderOrParams) {
  let name = '';
  let address = '';
  if (orderOrParams) {
    if (typeof orderOrParams === 'object') {
      name = orderOrParams.customerName || orderOrParams.name || '';
      address = orderOrParams.deliveryAddress || orderOrParams.address || '';
    } else if (typeof orderOrParams === 'string') {
      address = orderOrParams;
    }
  }

  if (!address && !name) {
    wx.showToast({ title: '暂无地址信息', icon: 'none' });
    return;
  }

  // 直接调起微信地图（无需中转页）。微信地图可导航，并支持跳转到手机原生地图 App。
  wx.openLocation({
    latitude: FALLBACK_CITY.latitude,
    longitude: FALLBACK_CITY.longitude,
    name: name || '配送地址',
    address: address,
    scale: 16,
    fail() {
      // 兜底：复制地址，让用户自行粘贴到地图 App
      wx.setClipboardData({
        data: address || name,
        success() {
          wx.showToast({ title: '地址已复制，请粘贴到地图', icon: 'none' });
        }
      });
    }
  });
}

module.exports = {
  navigate
};
