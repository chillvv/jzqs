// 地图导航服务：把骑手带到客户地址。
//
// 顾客保存地址时通过微信地图选点（wx.chooseLocation）采集坐标随地址落库，
// 骑手端订单已带 latitude/longitude，导航直接 wx.openLocation 精准定位。
// 旧地址未选点（无坐标）时退化：复制地址 + 提示骑手自行搜索，绝不写死坐标误导。

function showToast(title) {
  if (typeof wx !== 'undefined' && typeof wx.showToast === 'function') {
    wx.showToast({ title, icon: 'none', duration: 2500 });
  }
}

function resolveAddress(orderOrParams) {
  if (!orderOrParams) return '';
  if (typeof orderOrParams === 'object') {
    return orderOrParams.deliveryAddress || orderOrParams.address || '';
  }
  if (typeof orderOrParams === 'string') return orderOrParams;
  return '';
}

function normalizeCoords(value) {
  const num = Number(value);
  return Number.isFinite(num) && num !== 0 ? num : null;
}

function copyAddress(text) {
  if (!text || typeof wx === 'undefined' || typeof wx.setClipboardData !== 'function') {
    return;
  }
  wx.setClipboardData({ data: text });
}

// 拉起导航：订单有坐标（顾客选点）→ 精准导航；无坐标（旧地址）→ 复制地址提示
function navigate(orderOrParams) {
  const address = resolveAddress(orderOrParams);
  if (!address) {
    showToast('暂无地址信息');
    return;
  }

  const latitude = orderOrParams && typeof orderOrParams === 'object'
    ? normalizeCoords(orderOrParams.latitude)
    : null;
  const longitude = orderOrParams && typeof orderOrParams === 'object'
    ? normalizeCoords(orderOrParams.longitude)
    : null;

  if (latitude !== null && longitude !== null) {
    // 订单自带坐标（顾客选点落库）：直接精确定位
    // name 只用于地图标注标题，应显示地址而非客户姓名
    wx.openLocation({
      latitude,
      longitude,
      name: address,
      address,
      scale: 16
    });
    return;
  }

  // 旧地址无坐标：复制地址 + 提示骑手自行搜索
  copyAddress(address);
  showToast('该地址暂无定位，地址已复制，可到地图 App 搜索');
}

module.exports = { navigate };
