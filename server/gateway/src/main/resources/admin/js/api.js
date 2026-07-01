/* 管理 API 封装（Cookie 会话，登录页 POST /admin/api/login） */
export async function api(p, opt = {}) {
  const headers = { ...(opt.headers || {}) };
  if (opt.body && !headers['Content-Type']) headers['Content-Type'] = 'application/json';
  const r = await fetch('/admin/api' + p, { credentials: 'same-origin', ...opt, headers });
  if (r.status === 401) {
    const next = encodeURIComponent(location.pathname + location.search);
    location.href = '/admin/login.html?next=' + next;
    throw new Error('unauthorized');
  }
  return r.json();
}

export function connectSSE(onEvent, onState) {
  const es = new EventSource('/admin/api/stream');
  es.onopen = () => onState(true);
  es.onerror = () => onState(false);
  es.onmessage = (e) => onEvent(JSON.parse(e.data));
}

export async function logout() {
  await fetch('/admin/api/logout', { method: 'POST', credentials: 'same-origin' });
  location.href = '/admin/login.html';
}
