const listeners = new Set();

let socketTask = null;
let reconnectTimer = null;
let getToken = null;
let clientLabel = 'rider';
let manuallyStopped = false;

// 重连退避策略：指数退避，封顶 MAX_RECONNECT_DELAY，达到上限后停止自动重连，避免连不上时无限刷屏
let reconnectAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 10;
const BASE_RECONNECT_DELAY = 3000;
const MAX_RECONNECT_DELAY = 30000;

function resetReconnectAttempts() {
  reconnectAttempts = 0;
}

function resolveSocketUrl(apiBaseUrl) {
  return String(apiBaseUrl || '')
    .replace(/^http:/i, 'ws:')
    .replace(/^https:/i, 'wss:')
    .replace(/\/+$/, '') + '/ws/realtime';
}

function clearReconnectTimer() {
  if (reconnectTimer) {
    clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
}

function notify(message) {
  listeners.forEach((listener) => {
    try {
      listener(message || {});
    } catch (error) {
      console.error('[实时连接] 监听器执行失败', error);
    }
  });
}

function scheduleReconnect() {
  if (manuallyStopped || reconnectTimer) {
    return;
  }
  if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
    console.warn('[实时连接] 已达到最大重连次数，停止自动重连，请检查网络或后端 /ws/realtime 是否可用');
    return;
  }
  const delay = Math.min(BASE_RECONNECT_DELAY * Math.pow(1.6, reconnectAttempts), MAX_RECONNECT_DELAY);
  reconnectAttempts += 1;
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connect();
  }, delay);
}

function connect() {
  const app = getApp();
  const token = typeof getToken === 'function' ? getToken() : '';
  if (!token || !app || !app.globalData || !app.globalData.apiBaseUrl) {
    return;
  }
  if (socketTask) {
    return;
  }
  if (reconnectAttempts >= MAX_RECONNECT_ATTEMPTS) {
    console.warn('[实时连接] 已达到最大重连次数，停止自动重连');
    return;
  }
  manuallyStopped = false;
  clearReconnectTimer();
  socketTask = wx.connectSocket({
    url: resolveSocketUrl(app.globalData.apiBaseUrl),
    fail: (err) => {
      // connectSocket 在连接阶段（DNS / TLS / 握手）失败时会走这里。
      // 必须接住，否则会作为未捕获的 Error: timeout 冒泡到 WAService 控制台。
      console.warn('[实时连接] 建立失败，将自动重试', err);
      socketTask = null;
      scheduleReconnect();
    }
  });
  socketTask.onOpen(() => {
    resetReconnectAttempts();
    socketTask.send({
      data: JSON.stringify({ type: 'AUTH', token, client: clientLabel })
    });
  });
  socketTask.onMessage((event) => {
    try {
      notify(JSON.parse(event.data || '{}'));
    } catch (error) {
      notify({ type: 'PARSE_ERROR' });
    }
  });
  socketTask.onClose(() => {
    socketTask = null;
    scheduleReconnect();
  });
  socketTask.onError(() => {
    if (socketTask) {
      socketTask.close();
    }
  });
}

function init(options = {}) {
  getToken = options.getToken;
  clientLabel = options.clientLabel || clientLabel;
  resetReconnectAttempts();
  connect();
}

function subscribe(listener) {
  listeners.add(listener);
  resetReconnectAttempts();
  connect();
  return () => {
    listeners.delete(listener);
  };
}

function stop() {
  manuallyStopped = true;
  clearReconnectTimer();
  if (socketTask) {
    socketTask.close();
    socketTask = null;
  }
}

module.exports = {
  init,
  subscribe,
  stop
};
