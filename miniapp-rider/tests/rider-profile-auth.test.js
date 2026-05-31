const assert = require('node:assert/strict');
const {
  resolvePhoneAuthResult,
  getSubmitProfileError
} = require('../utils/rider-profile-auth');

assert.deepEqual(
  resolvePhoneAuthResult({
    errMsg: 'getPhoneNumber:ok',
    phoneNumber: '13800138000'
  }),
  {
    ok: true,
    phoneNumber: '13800138000'
  }
);

assert.deepEqual(
  resolvePhoneAuthResult({
    errMsg: 'getPhoneNumber:fail user deny'
  }),
  {
    ok: false,
    message: '已取消手机号授权'
  }
);

assert.equal(
  getSubmitProfileError({
    agreed: false,
    nickname: '骑手小李',
    phoneNumber: '13800138000'
  }),
  '请先阅读并同意骑手服务协议与隐私说明'
);

assert.equal(
  getSubmitProfileError({
    agreed: true,
    nickname: '',
    phoneNumber: '13800138000'
  }),
  ''
);

assert.equal(
  getSubmitProfileError({
    agreed: true,
    nickname: '骑手小李',
    phoneNumber: ''
  }),
  '请输入手机号'
);

assert.equal(
  getSubmitProfileError({
    agreed: true,
    nickname: '骑手小李',
    phoneNumber: '13800138000'
  }),
  ''
);

console.log('rider-profile-auth tests passed');
