/* 管理 API 封装（Cookie 会话 + 空间头 X-GISO-Space） */
const SPACE_KEY = 'giso_space';
let currentSpace = localStorage.getItem(SPACE_KEY) || 'default';

export function getSpace() { return currentSpace; }

export function setSpace(spaceKey) {
  currentSpace = spaceKey || 'default';
  localStorage.setItem(SPACE_KEY, currentSpace);
}

export async function api(p, opt = {}) {
  const headers = { 'X-GISO-Space': currentSpace, ...(opt.headers || {}) };
  if (opt.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
  const r = await fetch('/admin/api' + p, { credentials: 'same-origin', ...opt, headers });
  if (r.status === 401) {
    const next = encodeURIComponent(location.pathname + location.search);
    location.href = '/admin/login.html?next=' + next;
    throw new Error('unauthorized');
  }
  const data = await r.json();
  if (r.status === 403 && !opt._spaceRetried
      && typeof data?.error === 'string' && data.error.includes('空间')
      && currentSpace !== 'default') {
    setSpace('default');
    return api(p, { ...opt, _spaceRetried: true });
  }
  return data;
}

let sseConnection = null;

export function disconnectSSE() {
  if (sseConnection) {
    sseConnection.close();
    sseConnection = null;
  }
}

export function connectSSE(onEvent, onState) {
  disconnectSSE();
  const es = new EventSource('/admin/api/stream?space=' + encodeURIComponent(currentSpace));
  sseConnection = es;
  es.onopen = () => onState(true);
  es.onerror = () => onState(false);
  es.onmessage = (e) => onEvent(JSON.parse(e.data));
}

export async function logout() {
  await fetch('/admin/api/logout', { method: 'POST', credentials: 'same-origin' });
  location.href = '/admin/login.html';
}
