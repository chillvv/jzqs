const listeners = new Set();

let socketTask = null;
let reconnectTimer = null;
let getToken = null;
let clientLabel = 'customer';
let manuallyStopped = false;

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
  reconnectTimer = setTimeout(() => {
    reconnectTimer = null;
    connect();
  }, 3000);
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
  manuallyStopped = false;
  clearReconnectTimer();
  socketTask = wx.connectSocket({
    url: resolveSocketUrl(app.globalData.apiBaseUrl)
  });
  socketTask.onOpen(() => {
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
  connect();
}

function subscribe(listener) {
  listeners.add(listener);
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
