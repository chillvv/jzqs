// 正式环境默认走 HTTPS 域名，域名解析到当前服务器 IP
const DEFAULT_API_BASE_URL = 'https://jzqs.top';
const DEFAULT_CLOUD_ENV_ID = 'cloud1-4g88w3e2dedee471';
const DEFAULT_SERVICE_HEADERS = {
  'X-WX-SERVICE': 'tcbanyservice',
  'X-Vm-Service': 'lhins-2f9aerfm'
};

function resolveApiBaseUrl(value) {
  const normalized = String(value || '').trim().replace(/\/+$/, '');
  return normalized || DEFAULT_API_BASE_URL;
}

function resolveCloudEnvId(value) {
  const normalized = String(value || '').trim();
  return normalized || DEFAULT_CLOUD_ENV_ID;
}

function resolveServiceHeaders(value) {
  if (!value || typeof value !== 'object') {
    return { ...DEFAULT_SERVICE_HEADERS };
  }

  return {
    ...DEFAULT_SERVICE_HEADERS,
    ...value
  };
}

module.exports = {
  DEFAULT_API_BASE_URL,
  DEFAULT_CLOUD_ENV_ID,
  DEFAULT_SERVICE_HEADERS,
  resolveApiBaseUrl,
  resolveCloudEnvId,
  resolveServiceHeaders
};
