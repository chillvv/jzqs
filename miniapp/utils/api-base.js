// 开发时可以换成你的本地或测试服务器地址（开发模式下可以用 HTTP/IP）
// 正式上线默认走 HTTPS 正式域名
const DEFAULT_API_BASE_URL = 'https://jzqs.top';

function resolveApiBaseUrl(value) {
  const normalized = String(value || '').trim().replace(/\/+$/, '');
  return normalized || DEFAULT_API_BASE_URL;
}

module.exports = {
  DEFAULT_API_BASE_URL,
  resolveApiBaseUrl
};
