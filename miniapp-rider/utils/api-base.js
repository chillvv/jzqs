// 正式环境默认走 HTTPS 域名，域名解析到当前服务器 IP
const DEFAULT_API_BASE_URL = 'https://jzqs.top';

function resolveApiBaseUrl(value) {
  const normalized = String(value || '').trim().replace(/\/+$/, '');
  return normalized || DEFAULT_API_BASE_URL;
}

module.exports = {
  DEFAULT_API_BASE_URL,
  resolveApiBaseUrl
};
