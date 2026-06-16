import { ADMIN_AUTH_STORAGE_KEY, parseAdminAuthSession } from "../../modules/auth/adminAuth.helpers";

type RealtimeMessage = {
  type?: string;
  eventType?: string;
  payload?: Record<string, unknown>;
};

type RealtimeListener = (message: RealtimeMessage) => void;

let socket: WebSocket | null = null;
let reconnectTimer: number | null = null;
let manuallyStopped = false;
let suppressSocketNoiseUntil = 0;
const listeners = new Set<RealtimeListener>();

function resolveRealtimeUrl() {
  const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
  return `${protocol}//${window.location.host}/ws/realtime`;
}

function currentToken() {
  const session = parseAdminAuthSession(window.localStorage.getItem(ADMIN_AUTH_STORAGE_KEY));
  return session?.token || "";
}

function clearReconnectTimer() {
  if (reconnectTimer != null) {
    window.clearTimeout(reconnectTimer);
    reconnectTimer = null;
  }
}

function scheduleReconnect() {
  if (manuallyStopped || reconnectTimer != null) {
    return;
  }
  const delay = suppressSocketNoiseUntil > Date.now()
    ? Math.max(3000, suppressSocketNoiseUntil - Date.now())
    : 3000;
  reconnectTimer = window.setTimeout(() => {
    reconnectTimer = null;
    startAdminRealtime();
  }, delay);
}

function notify(message: RealtimeMessage) {
  listeners.forEach((listener) => listener(message));
}

export function startAdminRealtime() {
  if (typeof window === "undefined") {
    return;
  }
  const token = currentToken();
  if (!token) {
    stopAdminRealtime();
    return;
  }
  if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
    return;
  }
  manuallyStopped = false;
  clearReconnectTimer();
  const createdSocket = new WebSocket(resolveRealtimeUrl());
  socket = createdSocket;
  let authenticated = false;
  let everOpened = false;
  createdSocket.addEventListener("open", () => {
    if (createdSocket.readyState !== WebSocket.OPEN) {
      return;
    }
    everOpened = true;
    createdSocket.send(JSON.stringify({ type: "AUTH", token, client: "admin" }));
  });
  createdSocket.addEventListener("message", (event) => {
    try {
      const parsed = JSON.parse(String(event.data || "{}"));
      if (parsed?.type === "AUTH_OK" || parsed?.eventType === "AUTH_OK") {
        authenticated = true;
      }
      notify(parsed);
    } catch {
      notify({ type: "PARSE_ERROR" });
    }
  });
  createdSocket.addEventListener("close", () => {
    if (socket === createdSocket) {
      socket = null;
    }
    scheduleReconnect();
  });
  createdSocket.addEventListener("error", () => {
    if (!everOpened && !authenticated) {
      suppressSocketNoiseUntil = Date.now() + 5000;
      return;
    }
    if (createdSocket.readyState === WebSocket.OPEN || createdSocket.readyState === WebSocket.CONNECTING) {
      createdSocket.close();
    }
  });
}

export function stopAdminRealtime() {
  manuallyStopped = true;
  clearReconnectTimer();
  if (socket) {
    socket.close();
    socket = null;
  }
}

export function useAdminRealtime(listener: RealtimeListener) {
  listeners.add(listener);
  startAdminRealtime();
  return () => {
    listeners.delete(listener);
  };
}
