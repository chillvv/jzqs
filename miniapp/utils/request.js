function request({ url, method = 'GET', data, header, requireAuth = true }) {
  const app = getApp();
  return new Promise((resolve, reject) => {
    const sendRequest = () => {
      const token = app.globalData.token;
      if (requireAuth && !token) {
        if (app.globalData.requireProfile) {
          wx.switchTab({ url: '/pages/profile/index' });
        }
        reject(new Error('请先完成手机号验证'));
        return;
      }
      const finalHeader = {
        ...(header || {}),
        "X-WX-SERVICE": "tcbanyservice",
        "X-Vm-Service": "lhins-2f9aerfm"
      };
      if (token) {
        finalHeader.Authorization = `Bearer ${token}`;
      }

      // 直接使用常规 wx.request（开发模式下可以用 HTTP/IP，正式模式需要 HTTPS/域名）
      wx.request({
        url: `${app.globalData.apiBaseUrl}${url}`,
        method,
        data,
        header: finalHeader,
        success(res) {
          const body = res.data || {};
          if (res.statusCode >= 200 && res.statusCode < 300 && body.code === 'OK') {
            resolve(body.data);
            return;
          }
          if (body.code === 'UNAUTHORIZED') {
            app.handleUnauthorized();
          }
          reject(new Error(body.message || '请求失败'));
        },
        fail(err) {
          if (err && err.errMsg && err.errMsg.includes('fail')) {
            reject(new Error('暂时无法连接服务，请确认后端已启动'));
            return;
          }
          reject(err);
        }
      });
    };

    if (requireAuth && !app.globalData.token && app.authPromise) {
      app.authPromise
        .then(sendRequest)
        .catch(() => reject(new Error('登录失败，请稍后重试')));
      return;
    }
    sendRequest();
  });
}
module.exports = {
  request
};
