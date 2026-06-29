function extractPrivacyMessage(detailOrError) {
  if (!detailOrError) {
    return '';
  }
  if (typeof detailOrError === 'string') {
    return detailOrError.trim();
  }
  if (detailOrError.errMsg) {
    return String(detailOrError.errMsg).trim();
  }
  if (detailOrError.message) {
    return String(detailOrError.message).trim();
  }
  return '';
}

function normalizePrivacyError(detailOrError) {
  const message = extractPrivacyMessage(detailOrError);
  return {
    message,
    denied: message.includes('deny') || message.includes('user deny'),
    noPermission: message.includes('no permission'),
    jsapiBlocked: message.includes('jsapi has no permission'),
    scopeUndeclared:
      message.includes('api scope is not declared in the privacy agreement') ||
      message.includes('privacy agreement') ||
      message.includes('errno:112')
  };
}

function getPhonePrivacyErrorMessage(detailOrError) {
  const result = normalizePrivacyError(detailOrError);
  if (result.scopeUndeclared) {
    return '小程序后台隐私保护指引还未声明手机号能力，请先补充后再使用微信一键登录';
  }
  if (result.jsapiBlocked) {
    return '微信手机号权限未生效，请检查隐私指引后重试';
  }
  if (result.denied) {
    return '你已拒绝隐私授权，请重新点击并同意';
  }
  if (result.noPermission) {
    return '微信手机号能力未生效，请检查小程序后台隐私保护指引并在真机重试';
  }
  return '微信手机号授权失败，请重试';
}

function getPrivacySetting() {
  return new Promise((resolve) => {
    if (typeof wx.getPrivacySetting !== 'function') {
      resolve({
        supported: false,
        needAuthorization: false,
        privacyContractName: '隐私保护指引'
      });
      return;
    }
    wx.getPrivacySetting({
      success(res) {
        resolve({
          supported: true,
          needAuthorization: !!res.needAuthorization,
          privacyContractName: res.privacyContractName || '隐私保护指引'
        });
      },
      fail() {
        resolve({
          supported: true,
          needAuthorization: true,
          privacyContractName: '隐私保护指引'
        });
      }
    });
  });
}

function openPrivacyContract() {
  return new Promise((resolve, reject) => {
    if (typeof wx.openPrivacyContract !== 'function') {
      reject(new Error('当前微信版本不支持打开隐私保护指引'));
      return;
    }
    wx.openPrivacyContract({
      success: resolve,
      fail(error) {
        reject(normalizePrivacyError(error));
      }
    });
  });
}

function ensurePhonePrivacyPermission() {
  return getPrivacySetting().then((setting) => {
    if (!setting.needAuthorization || typeof wx.requirePrivacyAuthorize !== 'function') {
      return setting;
    }
    return new Promise((resolve, reject) => {
      wx.requirePrivacyAuthorize({
        success() {
          resolve({
            ...setting,
            needAuthorization: false
          });
        },
        fail(error) {
          reject({
            ...normalizePrivacyError(error),
            privacyContractName: setting.privacyContractName
          });
        }
      });
    });
  });
}

module.exports = {
  ensurePhonePrivacyPermission,
  getPhonePrivacyErrorMessage,
  normalizePrivacyError,
  getPrivacySetting,
  openPrivacyContract
};
