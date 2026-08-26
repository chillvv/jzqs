// 地图导航中转页：用 web-view 加载 H5，由 H5 负责拉起手机里已安装的原生地图 App
Page({
  data: {
    webUrl: ''
  },

  onLoad(options) {
    const app = getApp();
    const base = (app && app.globalData && app.globalData.apiBaseUrl) || 'https://jzqs.top';
    const root = String(base).replace(/\/+$/, '');

    const { platform, name, lat, lng, addr } = options;
    const params = [
      'platform=' + encodeURIComponent(platform || ''),
      'name=' + encodeURIComponent(name || ''),
      'lat=' + encodeURIComponent(lat || ''),
      'lng=' + encodeURIComponent(lng || ''),
      'addr=' + encodeURIComponent(addr || '')
    ].join('&');

    this.setData({
      webUrl: root + '/map-nav.html?' + params
    });
  }
});
