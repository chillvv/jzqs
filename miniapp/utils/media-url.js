function isAbsoluteUrl(value) {
  return /^https?:\/\//i.test(value);
}

// 微信开发者工具本地临时文件服务（127.0.0.1:<port>/__tmp__/...）、
// 真机临时文件协议（wxfile://tmp_...）等只存在于本机，真机/其他设备永远访问不到，
// 直接降级为不可显示，避免渲染层一直报 500。
function isLocalTempFile(value) {
  if (/^wxfile:\/\/tmp_/i.test(value)) {
    return true;
  }
  // 127.0.0.1 / localhost / [::1] 下的 __tmp__ 临时目录
  if (/(^|\/\/)(127\.0\.0\.1|localhost|\[::1\])(:\d+)?\/__tmp__\//i.test(value)) {
    return true;
  }
  return false;
}

// 本地回环地址但指向可还原的服务器资源（/uploads/...）：
// 把 "http://127.0.0.1:8081/uploads/xxx.jpg" 还原成 "/uploads/xxx.jpg" 相对路径，
// 后续交由相对路径逻辑重新拼上真机可访问的 apiBaseUrl，从而自动修复历史脏数据。
function stripLocalhostOrigin(value) {
  const match = /^https?:\/\/(localhost|127\.0\.0\.1|\[::1\])(:\d+)?(\/(uploads|api)\/.+)$/i.exec(value);
  if (match) {
    return match[3];
  }
  return null;
}

function resolveMediaUrl(value, baseUrl) {
  const normalized = String(value || '').trim();
  if (!normalized) {
    return '';
  }
  if (
    normalized.startsWith('cloud://') ||
    normalized.startsWith('wxfile://') ||
    normalized.startsWith('data:') ||
    normalized.startsWith('.')
  ) {
    return normalized;
  }
  // 本地临时文件：真机无法访问，降级为不显示，消除 500。
  if (isLocalTempFile(normalized)) {
    return '';
  }
  const root = String(baseUrl || '').trim().replace(/\/+$/, '');
  // 绝对 URL：先尝试把本地回环地址还原成相对路径，再走相对路径拼接逻辑。
  if (isAbsoluteUrl(normalized)) {
    const restored = stripLocalhostOrigin(normalized);
    if (restored) {
      return root ? `${root}${restored}` : restored;
    }
    return normalized;
  }
  const path = normalized.startsWith('/') ? normalized : `/${normalized}`;
  return root ? `${root}${path}` : normalized;
}

module.exports = {
  resolveMediaUrl,
  isLocalTempFile,
  stripLocalhostOrigin
};
