function resolvePhoneAuthResult(detail) {
  if (!detail || detail.errMsg !== 'getPhoneNumber:ok') {
    return {
      ok: false,
      code: '',
      message: '已取消手机号授权'
    };
  }

  return {
    ok: true,
    code: String(detail.code || '').trim()
  };
}

function getSubmitProfileError({ agreed, phoneNumber }) {
  if (!agreed) {
    return '请先阅读并同意骑手服务协议与隐私说明';
  }
  if (!String(phoneNumber || '').trim()) {
    return '请输入手机号';
  }

  return '';
}

module.exports = {
  resolvePhoneAuthResult,
  getSubmitProfileError
};
