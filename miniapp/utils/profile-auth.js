function isValidPhone(phone) {
  return /^1\d{10}$/.test(String(phone || '').trim());
}

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

function getSubmitProfileError({ mode, nickname, phoneNumber }) {
  const finalMode = mode === 'login' ? 'login' : 'register';

  if (finalMode === 'register' && !String(nickname || '').trim()) {
    return '请输入姓名';
  }

  if (!String(phoneNumber || '').trim()) {
    return '请输入手机号';
  }

  if (!isValidPhone(phoneNumber)) {
    return '请输入正确的11位手机号';
  }

  return '';
}

module.exports = {
  isValidPhone,
  resolvePhoneAuthResult,
  getSubmitProfileError
};
