// 当前默认指向服务器 IP，便于 Docker 服务器直接部署联调
// 小程序正式上线前仍建议切回 HTTPS 域名
const DEFAULT_API_BASE_URL = 'http://150.158.81.55';

function resolveApiBaseUrl(value) {
  const normalized = String(value || '').trim().replace(/\/+$/, '');
  return normalized || DEFAULT_API_BASE_URL;
}

module.exports = {
  DEFAULT_API_BASE_URL,
  resolveApiBaseUrl
};
