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

      // 优先使用云开发云托管调用
      if (wx.cloud) {
        wx.cloud.callContainer({
          config: {
            env: "cloud1-4g88w3e2dedee471"
          },
          header: finalHeader,
          path: url,
          method,
          data,
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
            console.error('[云托管请求失败]', err);
            reject(new Error('暂时无法连接服务，请确认云托管已启动'));
          }
        });
        return;
      }

      // 备选方案：常规 wx.request
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
