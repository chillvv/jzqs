const DEFAULT_API_BASE_URL = 'https://api.yourdomain.com'; // 请替换为你的腾讯云域名或公网IP (必须是HTTPS)

function resolveApiBaseUrl(value) {
  const normalized = String(value || '').trim().replace(/\/+$/, '');
  return normalized || DEFAULT_API_BASE_URL;
}

module.exports = {
  DEFAULT_API_BASE_URL,
  resolveApiBaseUrl
};
