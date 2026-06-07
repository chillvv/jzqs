const { request } = require('../utils/request');

/**
 * 骑手注册（手机号+姓名+微信绑定）
 */
async function riderRegister(phone, name, openid) {
  return await request({
    url: '/api/auth/register-phone',
    method: 'POST',
    data: { phone, nickname: name, openid, userType: 'rider' },
    header: { 'content-type': 'application/json' },
    requireWorkbench: false
  });
}

/**
 * 骑手微信一键登录（微信手机号动态令牌）
 */
async function riderWechatLogin(code) {
  return await request({
    url: '/api/rider/wechat-login',
    method: 'POST',
    data: { code },
    header: { 'content-type': 'application/json' },
    requireWorkbench: false
  });
}

/**
 * 骑手手机号登录
 */
async function riderPhoneLogin(phone, openid) {
  return await request({
    url: '/api/auth/phone-login',
    method: 'POST',
    data: { phone, openid, userType: 'rider' },
    header: { 'content-type': 'application/json' },
    requireWorkbench: false
  });
}

/**
 * 骑手混合登录（手机号+姓名+微信绑定）
 * @deprecated 使用 riderRegister 或 riderPhoneLogin 替代
 */
async function riderMixedLogin(phone, name, openid) {
  return await request({
    url: '/api/rider/login',
    method: 'POST',
    data: { phone, name, openid },
    header: { 'content-type': 'application/json' },
    requireWorkbench: false
  });
}

/**
 * 获取骑手个人信息
 */
async function getRiderProfile(riderName) {
  return await request({
    url: `/api/mobile/rider-auth/me?riderName=${encodeURIComponent(riderName)}`,
    requireWorkbench: false
  });
}

/**
 * 微信登录（保留旧接口）
 */
async function wxLogin(code) {
  return await request({
    url: '/api/mobile/rider-auth/wx-login',
    method: 'POST',
    data: { code },
    header: { 'content-type': 'application/json' },
    requireWorkbench: false
  });
}

/**
 * 绑定手机号（保留旧接口）
 */
async function bindPhone(openid, phone, nickname) {
  return await request({
    url: '/api/mobile/rider-auth/bind-phone',
    method: 'POST',
    data: { openid, phone, nickname },
    header: { 'content-type': 'application/json' },
    requireWorkbench: false
  });
}

/**
 * 密码登录（保留旧接口）
 */
async function passwordLogin(phone, password) {
  return await request({
    url: '/api/mobile/rider-auth/login',
    method: 'POST',
    data: { phone, password },
    header: { 'content-type': 'application/json' },
    requireWorkbench: false
  });
}

module.exports = {
  riderRegister,
  riderWechatLogin,
  riderPhoneLogin,
  riderMixedLogin,
  getRiderProfile,
  wxLogin,
  bindPhone,
  passwordLogin
};
