const AUTH_TOKEN_KEY = 'auth_token';
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

function networkError() {
  return new Error('暂时无法连接服务，请确认后端已启动');
}

function handleUnauthorized(app) {
  console.log('[请求] Token 失效，清除本地 token');
  wx.removeStorageSync(AUTH_TOKEN_KEY);
  if (app && typeof app.resetRiderAuthState === 'function') {
    app.resetRiderAuthState({
      redirect: true,
      message: '登录已过期，请重新登录'
    });
  }
}

function request({ url, method = 'GET', data, header, requireWorkbench = true, token: explicitToken, hideLoading = false, hideErrorToast = false }) {
  const app = getApp();
  return new Promise((resolve, reject) => {
    const sendRequest = () => {
      // 只有在需要工作台权限时才检查
      if (requireWorkbench && !app.canUseWorkbench()) {
        reject(new Error(app.getWorkbenchBlockMessage()));
        return;
      }
      
      // 自动添加 token 到请求头
      const token = explicitToken || wx.getStorageSync(AUTH_TOKEN_KEY);
      const requestHeader = {
        ...header,
        ...resolveServiceHeaders(app.globalData.serviceHeaders)
      };
      if (token) {
        requestHeader.Authorization = `Bearer ${token}`;
      }
      
      if (!hideLoading) {
        showGlobalLoading();
      }

      // 直接使用常规 wx.request（开发模式下可以用 HTTP/IP，正式模式需要 HTTPS/域名）
      wx.request({
        url: `${app.globalData.apiBaseUrl}${url}`,
        method,
        data,
        header: requestHeader,
        timeout: 30000, // 30秒超时
        success(res) {
          const body = res.data || {};
          
          // token 失效处理
          if (res.statusCode === 401) {
            handleUnauthorized(app);
            const errorMsg = '登录已过期，请重新登录';
            if (!hideErrorToast && typeof wx.showToast === 'function') {
              wx.showToast({ title: errorMsg, icon: 'none', duration: 2000 });
            }
            reject(new Error(errorMsg));
            return;
          }
          
          if (res.statusCode >= 200 && res.statusCode < 300 && body.code === 'OK') {
            resolve(body.data);
            return;
          }
          
          const errorMsg = body.message || '请求失败';
          if (!hideErrorToast && typeof wx.showToast === 'function') {
            wx.showToast({ title: errorMsg, icon: 'none', duration: 2000 });
          }
          reject(new Error(errorMsg));
        },
        fail(err) {
          console.error('[请求失败]', {
            url: `${app.globalData.apiBaseUrl}${url}`,
            error: err
          });
          
          let errorMsg = '网络请求失败';
          if (err.errMsg && err.errMsg.includes('timeout')) {
            errorMsg = '请求超时，请检查网络或后端服务';
          } else if (err.errMsg && err.errMsg.includes('fail')) {
            errorMsg = '无法连接服务器，请检查后端是否启动';
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

    // 不需要工作台权限的请求（如登录接口），直接发送
    if (!requireWorkbench) {
      sendRequest();
      return;
    }

    // 需要工作台权限的请求，等待认证完成
    if (app.globalData.riderAuthReady) {
      sendRequest();
      return;
    }
    app.waitForRiderAuth()
      .then(sendRequest)
      .catch(() => reject(new Error('登录失败，请稍后重试')));
  });
}

function uploadFile({ url, filePath, name = 'file', formData, requireWorkbench = true, hideLoading = false, hideErrorToast = false }) {
  const app = getApp();
  return new Promise((resolve, reject) => {
    const sendUpload = () => {
      if (requireWorkbench && !app.canUseWorkbench()) {
        reject(new Error(app.getWorkbenchBlockMessage()));
        return;
      }
      
      const token = wx.getStorageSync(AUTH_TOKEN_KEY);
      const uploadFormData = {
        ...formData
      };
      
      // 自动添加 token 到请求头
      const header = {};
      if (token) {
        header.Authorization = `Bearer ${token}`;
      }
      
      if (!hideLoading) {
        showGlobalLoading();
      }

      wx.uploadFile({
        url: `${app.globalData.apiBaseUrl}${url}`,
        filePath,
        name,
        formData: uploadFormData,
        header: header,
        timeout: 60000, // 上传文件超时 60 秒
        success(res) {
          try {
            const body = JSON.parse(res.data || '{}');
            
            // token 失效处理
            if (res.statusCode === 401) {
              handleUnauthorized(app);
              const errorMsg = '登录已过期，请重新登录';
              if (!hideErrorToast && typeof wx.showToast === 'function') {
                wx.showToast({ title: errorMsg, icon: 'none', duration: 2000 });
              }
              reject(new Error(errorMsg));
              return;
            }
            
            if (res.statusCode >= 200 && res.statusCode < 300 && body.code === 'OK') {
              resolve(body.data);
              return;
            }
            const errorMsg = body.message || '上传失败';
            if (!hideErrorToast && typeof wx.showToast === 'function') {
              wx.showToast({ title: errorMsg, icon: 'none', duration: 2000 });
            }
            reject(new Error(errorMsg));
          } catch (error) {
            if (!hideErrorToast && typeof wx.showToast === 'function') {
              wx.showToast({ title: '上传失败', icon: 'none', duration: 2000 });
            }
            reject(error);
          }
        },
        fail(err) {
          console.error('[上传失败]', {
            url: `${app.globalData.apiBaseUrl}${url}`,
            error: err
          });
          
          let errorMsg = '网络请求失败';
          if (err.errMsg && err.errMsg.includes('timeout')) {
            errorMsg = '上传超时，请检查网络';
          } else {
            errorMsg = '无法连接服务器，请检查后端是否启动';
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

    // 不需要工作台权限的上传，直接发送
    if (!requireWorkbench) {
      sendUpload();
      return;
    }

    // 需要工作台权限的上传，等待认证完成
    if (app.globalData.riderAuthReady) {
      sendUpload();
      return;
    }
    app.waitForRiderAuth()
      .then(sendUpload)
      .catch(() => reject(new Error('登录失败，请稍后重试')));
  });
}

module.exports = {
  request,
  uploadFile
};
