/**
 * 管理台登录态（Cookie 会话，与 api.js 解耦）。
 * 约定：已登录访问 login.html → 进控制台；未登录访问控制台 → 进登录页；退出清服务端会话。
 */
const LOGIN = '/admin/login.html';
const HOME = '/admin/';

export function loginUrl(next) {
  if (!next || next === LOGIN || next.startsWith(LOGIN + '?')) return LOGIN;
  return `${LOGIN}?next=${encodeURIComponent(next)}`;
}

/** 当前是否已登录（401 / 无 username 视为未登录）。 */
export async function fetchMe() {
  const r = await fetch('/admin/api/me', { credentials: 'same-origin' });
  if (r.status === 401) return null;
  if (!r.ok) return null;
  const data = await r.json().catch(() => null);
  if (!data?.username || data.auth_enabled === false || data.error) return null;
  return data;
}

export async function login(username, password) {
  const r = await fetch('/admin/api/login', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const data = await r.json().catch(() => ({}));
  if (!r.ok) throw new Error(data.error || '登录失败');
  return data;
}

export async function logout() {
  try {
    await fetch('/admin/api/logout', { method: 'POST', credentials: 'same-origin' });
  } catch {
    /* 网络失败仍跳转登录页，旧 Cookie 下次 /me 会 401 */
  }
  location.replace(LOGIN);
}

export function goHome() {
  location.replace(HOME);
}

export function requireLoginRedirect() {
  location.replace(loginUrl(location.pathname + location.search));
}
