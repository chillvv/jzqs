const AUTH_TOKEN_KEY = 'auth_token';

function networkError() {
  return new Error('暂时无法连接服务，请确认后端已启动');
}

function request({ url, method = 'GET', data, header, requireWorkbench = true }) {
  const app = getApp();
  return new Promise((resolve, reject) => {
    const sendRequest = () => {
      // 只有在需要工作台权限时才检查
      if (requireWorkbench && !app.canUseWorkbench()) {
        reject(new Error(app.getWorkbenchBlockMessage()));
        return;
      }
      
      // 自动添加 token 到请求头
      const token = wx.getStorageSync(AUTH_TOKEN_KEY);
      const requestHeader = {
        ...header,
        "X-WX-SERVICE": "tcbanyservice",
        "X-Vm-Service": "lhins-2f9aerfm"
      };
      if (token) {
        requestHeader.Authorization = `Bearer ${token}`;
      }
      
      // 优先使用云开发云托管调用
      if (wx.cloud) {
        wx.cloud.callContainer({
          config: {
            env: "cloud1-4g88w3e2dedee471"
          },
          header: requestHeader,
          path: url,
          method,
          data,
          success(res) {
            const body = res.data || {};
            // token 失效处理
            if (res.statusCode === 401) {
              console.log('[请求] Token 失效，清除本地 token');
              wx.removeStorageSync(AUTH_TOKEN_KEY);
              app.resetRiderAuthState(true);
              reject(new Error('登录已过期，请重新登录'));
              return;
            }
            if (res.statusCode >= 200 && res.statusCode < 300 && body.code === 'OK') {
              resolve(body.data);
              return;
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
            console.log('[请求] Token 失效，清除本地 token');
            wx.removeStorageSync(AUTH_TOKEN_KEY);
            app.resetRiderAuthState(true);
            reject(new Error('登录已过期，请重新登录'));
            return;
          }
          
          if (res.statusCode >= 200 && res.statusCode < 300 && body.code === 'OK') {
            resolve(body.data);
            return;
          }
          
          reject(new Error(body.message || '请求失败'));
        },
        fail(err) {
          console.error('[请求失败]', {
            url: `${app.globalData.apiBaseUrl}${url}`,
            error: err
          });
          
          if (err.errMsg && err.errMsg.includes('timeout')) {
            reject(new Error('请求超时，请检查网络或后端服务'));
          } else if (err.errMsg && err.errMsg.includes('fail')) {
            reject(new Error('无法连接服务器，请检查后端是否启动'));
          } else {
            reject(networkError());
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

function uploadFile({ url, filePath, name = 'file', formData, requireWorkbench = true }) {
  const app = getApp();
  return new Promise((resolve, reject) => {
    const sendUpload = () => {
      if (requireWorkbench && !app.canUseWorkbench()) {
        reject(new Error(app.getWorkbenchBlockMessage()));
        return;
      }
      
      // 自动添加 token 到 formData
      const token = wx.getStorageSync(AUTH_TOKEN_KEY);
      const uploadFormData = {
        ...formData
      };
      if (token) {
        uploadFormData['token'] = token;
      }
      
      // 自动添加 token 到请求头
      const header = {};
      if (token) {
        header.Authorization = `Bearer ${token}`;
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
              console.log('[上传] Token 失效，清除本地 token');
              wx.removeStorageSync(AUTH_TOKEN_KEY);
              app.resetRiderAuthState(true);
              reject(new Error('登录已过期，请重新登录'));
              return;
            }
            
            if (res.statusCode >= 200 && res.statusCode < 300 && body.code === 'OK') {
              resolve(body.data);
              return;
            }
            reject(new Error(body.message || '上传失败'));
          } catch (error) {
            reject(error);
          }
        },
        fail(err) {
          console.error('[上传失败]', {
            url: `${app.globalData.apiBaseUrl}${url}`,
            error: err
          });
          
          if (err.errMsg && err.errMsg.includes('timeout')) {
            reject(new Error('上传超时，请检查网络'));
          } else {
            reject(networkError());
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
