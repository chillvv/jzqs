// 骑手端实时连接（WebSocket）
// 设计要点：
// 1. 连接成功后定时发 PING 心跳，避免被代理/服务端按空闲超时断开。
// 2. 连接建立后仅发送一次 AUTH，收到 AUTH_OK 才视为认证成功并重置重连退避。
// 3. 收到 ERROR（如 token 解析失败）表示认证已不可能成功，立即停止重连并提示，避免无限刷屏。
// 4. 重连采用指数退避；subscribe 不再清零退避计数，防止反复 onShow 把间隔压到最短。

const HEARTBEAT_INTERVAL = 25000; // 25s 发一次 PING
const CONNECT_TIMEOUT = 8000;
const BASE_RECONNECT_DELAY = 3000;
const MAX_RECONNECT_ATTEMPTS = 10;

let socketTask = null;
let listeners = new Set();
let reconnectAttempts = 0;
let reconnectTimer = null;
let connectTimer = null;
let heartbeatTimer = null;
let settled = false;
let authFailed = false;
let getToken = () => '';
let clientLabel = 'rider';

// 将 REST 基址转换为 WebSocket 地址：
// https://  -> wss:// ，http:// -> ws:// （wx.connectSocket 只接受 ws/wss 协议）
function resolveSocketUrl(apiBaseUrl) {
  return String(apiBaseUrl || '')
    .replace(/^http:/i, 'ws:')
    .replace(/^https:/i, 'wss:')
    .replace(/\/+$/, '') + '/ws/realtime';
}

function resetReconnectAttempts() {
  reconnectAttempts = 0;
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
}

function startHeartbeat() {
  stopHeartbeat();
  heartbeatTimer = setInterval(() => {
    if (socketTask) {
      try {
        socketTask.send({ data: JSON.stringify({ type: 'PING' }) });
      } catch (e) {
        // 发送失败会在 onClose/onError 中处理
      }
    }
  }, HEARTBEAT_INTERVAL);
}

function stopHeartbeat() {
  if (heartbeatTimer) {
    clearInterval(heartbeatTimer);
    heartbeatTimer = null;
  }
}

function scheduleReconnect() {
  if (authFailed) return;
  if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
    console.warn('[实时连接] 已达到最大重连次数，停止重连');
    wx.showToast({ title: '实时连接断开，请稍后刷新', icon: 'none' });
    return;
  }
  const delay = Math.min(BASE_RECONNECT_DELAY * Math.pow(1.6, reconnectAttempts), 30000);
  reconnectAttempts += 1;
  console.log(`[实时连接] ${delay}ms 后重连（第 ${reconnectAttempts} 次）`);
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connect();
  }, delay);
}

function connect() {
  const app = getApp();
  const token = typeof getToken === 'function' ? getToken() : '';

  // 认证已判定失败：不再尝试重连，等待用户重新登录
  if (authFailed) {
    return;
  }
  if (!token) {
    console.warn('[实时连接] 缺少 token，暂不连接');
    return;
  }
  if (!app || !app.globalData || !app.globalData.apiBaseUrl) {
    console.warn('[实时连接] apiBaseUrl 未就绪，暂不连接');
    return;
  }
  if (socketTask) {
    return;
  }

  const url = resolveSocketUrl(app.globalData.apiBaseUrl);
  console.log('[实时连接] 正在连接', url);

  settled = false;
  if (connectTimer) {
    clearTimeout(connectTimer);
  }

  let task;
  try {
    task = wx.connectSocket({ url, fail: (err) => {
      console.error('[实时连接] 建立失败', err);
      socketTask = null;
      scheduleReconnect();
    } });
  } catch (e) {
    console.error('[实时连接] 建立异常', e);
    socketTask = null;
    scheduleReconnect();
    return;
  }
  // 部分基础库版本在 url 非法或不支持时同步返回 undefined，
  // 此时不能对 undefined 调 onOpen，否则会连锁崩溃并无限重连。
  if (!task) {
    console.error('[实时连接] connectSocket 未返回任务对象，跳过本次连接');
    socketTask = null;
    scheduleReconnect();
    return;
  }
  socketTask = task;

  connectTimer = setTimeout(() => {
    if (!settled && socketTask) {
      console.warn('[实时连接] 连接超时');
      try { socketTask.close(); } catch (e) { /* ignore */ }
      socketTask = null;
      scheduleReconnect();
    }
  }, CONNECT_TIMEOUT);

  socketTask.onOpen(() => {
    settled = true;
    clearTimeout(connectTimer);
    console.log('[实时连接] 已建立，发送认证');
    startHeartbeat();
    try {
      socketTask.send({ data: JSON.stringify({ type: 'AUTH', token, client: clientLabel }) });
    } catch (e) {
      console.error('[实时连接] 发送认证失败', e);
    }
  });

  socketTask.onMessage((res) => {
    let msg;
    try {
      msg = JSON.parse(res.data);
    } catch (e) {
      return;
    }
    if (msg && msg.type === 'AUTH_OK') {
      authFailed = false;
      resetReconnectAttempts();
      return;
    }
    if (msg && msg.type === 'PONG') {
      return;
    }
    if (msg && msg.type === 'ERROR') {
      // 认证已失败（如 token 无效/无权限），停止重连，避免无限刷屏
      authFailed = true;
      stopHeartbeat();
      const tip = msg.message || '实时连接认证失败';
      console.warn('[实时连接] 服务端拒绝连接：', tip);
      wx.showToast({ title: tip, icon: 'none' });
      if (socketTask) {
        try { socketTask.close(); } catch (e) { /* ignore */ }
      }
      socketTask = null;
      return;
    }
    listeners.forEach((fn) => {
      try {
        fn(msg);
      } catch (e) {
        console.error('[实时连接] 监听器处理出错', e);
      }
    });
  });

  socketTask.onError(() => {
    if (settled) {
      // 已建立过的连接出错，交由 onClose 处理重连
      if (socketTask) {
        try { socketTask.close(); } catch (e) { /* ignore */ }
      }
      return;
    }
    settled = true;
    clearTimeout(connectTimer);
    console.error('[实时连接] 连接出错');
    stopHeartbeat();
    socketTask = null;
    scheduleReconnect();
  });

  socketTask.onClose(() => {
    clearTimeout(connectTimer);
    stopHeartbeat();
    socketTask = null;
    if (authFailed) {
      return;
    }
    scheduleReconnect();
  });
}

function init(options = {}) {
  if (typeof options.getToken === 'function') {
    getToken = options.getToken;
  }
  if (options.clientLabel) {
    clientLabel = options.clientLabel;
  }
  authFailed = false;
  resetReconnectAttempts();
  connect();
}

function subscribe(listener) {
  listeners.add(listener);
  if (authFailed) {
    // 认证已判定失败：不重连，提示重新登录，避免"看似已连接但收不到任何消息"
    console.warn('[实时连接] 认证已失败，请重新登录');
    return () => {
      listeners.delete(listener);
    };
  }
  connect();
  return () => {
    listeners.delete(listener);
  };
}

function stop() {
  resetReconnectAttempts();
  stopHeartbeat();
  authFailed = false;
  if (socketTask) {
    try { socketTask.close(); } catch (e) { /* ignore */ }
  }
  socketTask = null;
  listeners.clear();
}

module.exports = {
  init,
  subscribe,
  stop,
};
