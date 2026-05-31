const assert = require('node:assert/strict');
const {
  resolvePhoneAuthResult,
  getSubmitProfileError
} = require('../utils/profile-auth');

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
    phoneNumber: '',
    message: '已取消手机号授权'
  }
);

assert.equal(
  getSubmitProfileError({
    mode: 'login',
    nickname: '',
    phoneNumber: '13800138000'
  }),
  ''
);

assert.equal(
  getSubmitProfileError({
    mode: 'register',
    nickname: '',
    phoneNumber: '13800138000'
  }),
  '请输入姓名'
);

assert.equal(
  getSubmitProfileError({
    mode: 'login',
    nickname: '林晓',
    phoneNumber: ''
  }),
  '请输入手机号'
);

assert.equal(
  getSubmitProfileError({
    mode: 'register',
    nickname: '林晓',
    phoneNumber: '123'
  }),
  '请输入正确的11位手机号'
);

assert.equal(
  getSubmitProfileError({
    mode: 'register',
    nickname: '林晓',
    phoneNumber: '13800138000'
  }),
  ''
);

console.log('profile-auth tests passed');
