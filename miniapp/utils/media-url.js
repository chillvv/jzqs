function isAbsoluteUrl(value) {
  return /^https?:\/\//i.test(value);
}

function resolveMediaUrl(value, baseUrl) {
  const normalized = String(value || '').trim();
  if (!normalized) {
    return '';
  }
  if (
    isAbsoluteUrl(normalized) ||
    normalized.startsWith('cloud://') ||
    normalized.startsWith('wxfile://') ||
    normalized.startsWith('data:') ||
    normalized.startsWith('.')
  ) {
    return normalized;
  }
  const root = String(baseUrl || '').trim().replace(/\/+$/, '');
  if (!root) {
    return normalized;
  }
  const path = normalized.startsWith('/') ? normalized : `/${normalized}`;
  return `${root}${path}`;
}

module.exports = {
  resolveMediaUrl
};
