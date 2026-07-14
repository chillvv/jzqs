const { resolveServiceHeaders } = require('./api-base');

let requestCount = 0;

function showGlobalLoading() {
  if (requestCount === 0) {
    if (typeof wx.showLoading === 'function') {
      wx.showLoading({ title: '加载中', mask: true });
    }
  }
  requestCount++;
}

function hideGlobalLoading() {
  if (requestCount > 0) {
    requestCount--;
    if (requestCount === 0) {
      if (typeof wx.hideLoading === 'function') {
        wx.hideLoading();
      }
    }
  }
}

function request({ url, method = 'GET', data, header, requireAuth = true, hideLoading = false, hideErrorToast = false }) {
  const app = getApp();
  return new Promise((resolve, reject) => {
    const resolveToken = () => app.globalData.token || wx.getStorageSync('auth_token');
    
    const sendRequest = () => {
      const token = resolveToken();
      if (requireAuth && !token) {
        reject(new Error('请先完成手机号验证'));
        return;
      }
      const finalHeader = {
        ...(header || {}),
        ...resolveServiceHeaders(app.globalData.serviceHeaders)
      };
      if (token) {
        finalHeader.Authorization = `Bearer ${token}`;
      }

      if (!hideLoading) {
        showGlobalLoading();
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
          const errorMsg = body.message || '请求失败';
          if (!hideErrorToast && typeof wx.showToast === 'function') {
            wx.showToast({ title: errorMsg, icon: 'none', duration: 2000 });
          }
          reject(new Error(errorMsg));
        },
        fail(err) {
          let errorMsg = '网络请求失败';
          if (err && err.errMsg && err.errMsg.includes('fail')) {
            errorMsg = '暂时无法连接服务，请确认后端已启动';
          }
          if (!hideErrorToast && typeof wx.showToast === 'function') {
            wx.showToast({ title: errorMsg, icon: 'none', duration: 2000 });
          }
          reject(new Error(errorMsg));
        },
        complete() {
          if (!hideLoading) {
            hideGlobalLoading();
          }
        }
      });
    };

    // 等待认证就绪
    if (requireAuth && !resolveToken()) {
      app.waitForAuth()
        .then(() => {
          // 认证就绪后再次检查 token
          if (resolveToken()) {
            sendRequest();
          } else {
            reject(new Error('请先完成手机号验证'));
          }
        })
        .catch(() => reject(new Error('登录失败，请稍后重试')));
      return;
    }
    sendRequest();
  });
}
module.exports = {
  request
};
