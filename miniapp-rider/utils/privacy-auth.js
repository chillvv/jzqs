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
    jsapiBlocked: message.includes('jsapi has no permission')
  };
}

function getPhonePrivacyErrorMessage(detailOrError) {
  const result = normalizePrivacyError(detailOrError);
  if (result.jsapiBlocked) {
    return '微信手机号权限未生效，请检查隐私指引后重试';
  }
  if (result.denied) {
    return '你已拒绝隐私授权，请重新点击并同意';
  }
  if (result.noPermission) {
    return '请先完成微信隐私授权，再获取手机号';
  }
  return '微信手机号授权失败，请重试';
}

function ensurePhonePrivacyPermission() {
  return new Promise((resolve, reject) => {
    if (typeof wx.requirePrivacyAuthorize !== 'function') {
      resolve({ supported: false });
      return;
    }
    wx.requirePrivacyAuthorize({
      success() {
        resolve({ supported: true });
      },
      fail(error) {
        reject(normalizePrivacyError(error));
      }
    });
  });
}

module.exports = {
  ensurePhonePrivacyPermission,
  getPhonePrivacyErrorMessage,
  normalizePrivacyError
};
