const { request } = require('../utils/request');

async function riderRegister(phone, name, openid) {
  return request({
    url: '/api/auth/register-phone',
    method: 'POST',
    data: { phone, nickname: name, openid, userType: 'rider' },
    header: { 'content-type': 'application/json' },
    requireWorkbench: false
  });
}

async function riderPhoneLogin(phone, openid) {
  return request({
    url: '/api/auth/phone-login',
    method: 'POST',
    data: { phone, openid, userType: 'rider' },
    header: { 'content-type': 'application/json' },
    requireWorkbench: false
  });
}

async function getRiderProfile(riderName) {
  return request({
    url: `/api/mobile/rider-auth/me?riderName=${encodeURIComponent(riderName)}`,
    requireWorkbench: false
  });
}

module.exports = {
  riderRegister,
  riderPhoneLogin,
  getRiderProfile
};
