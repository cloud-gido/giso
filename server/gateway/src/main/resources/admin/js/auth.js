/**
 * 管理台认证模块（Cookie 会话，全站唯一状态源）。
 *
 * 状态机：
 *   login.html  → redirectIfAuthenticated()  已登录则进控制台
 *   index.html  → requireUser()            未登录则进 login
 *   api 401     → requireLoginRedirect()   清缓存并跳转
 */
const LOGIN = '/admin/login.html';
const HOME = '/admin/';

/** 内存缓存：同页内避免重复 /me；硬刷新后由 /me 重建。 */
let cachedUser = null;

export function getUser() {
  return cachedUser;
}

export function setUser(user) {
  cachedUser = user;
}

export function clearUser() {
  cachedUser = null;
}

export function loginUrl(next) {
  if (!next || next === LOGIN || next.startsWith(LOGIN + '?')) return LOGIN;
  return `${LOGIN}?next=${encodeURIComponent(next)}`;
}

function isValidProfile(data) {
  return data
    && !data.error
    && data.auth_enabled !== false
    && (data.ok === true || !!data.username);
}

/** 拉取 /me 并更新缓存；401 视为未登录，403 保留会话（避免无空间权限时误跳登录页）。 */
export async function refreshUser() {
  const r = await fetch('/admin/api/me', { credentials: 'same-origin' });
  if (r.status === 401) {
    clearUser();
    return null;
  }
  const data = await r.json().catch(() => null);
  if (r.status === 403) {
    const err = new Error(data?.error || '无权访问');
    err.code = 'forbidden';
    throw err;
  }
  if (!r.ok) return null;
  if (!isValidProfile(data)) {
    clearUser();
    return null;
  }
  cachedUser = data;
  return data;
}

/** 控制台启动：必须有有效会话，否则跳转登录。 */
export async function requireUser() {
  if (cachedUser) return cachedUser;
  try {
    const user = await refreshUser();
    if (!user) {
      requireLoginRedirect();
      throw new Error('unauthorized');
    }
    return user;
  } catch (e) {
    if (e?.code === 'forbidden') throw e;
    throw e;
  }
}

/** 登录页：已有会话则直接进控制台。 */
export async function redirectIfAuthenticated() {
  try {
    const user = await refreshUser();
    if (!user) return false;
    const next = new URLSearchParams(location.search).get('next') || HOME;
    location.replace(next);
    return true;
  } catch (e) {
    if (e?.code === 'forbidden') return false;
    throw e;
  }
}

/** 登录：服务端校验 + 签发 Cookie，响应含完整 user profile。 */
export async function signIn(username, password) {
  const r = await fetch('/admin/api/login', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username, password }),
  });
  const data = await r.json().catch(() => ({}));
  if (!r.ok) {
    const err = new Error(data.error || '登录失败');
    err.code = data.code;
    err.retryAfterSec = data.retry_after_sec;
    err.attemptsRemaining = data.attempts_remaining;
    throw err;
  }
  if (!isValidProfile(data)) throw new Error(data.error || '登录失败');
  cachedUser = data;
  return data;
}

/** 退出：服务端销毁会话 + 清 Cookie + 清前端缓存。 */
export async function signOut() {
  clearUser();
  try {
    await fetch('/admin/api/logout', { method: 'POST', credentials: 'same-origin' });
  } catch {
    /* 网络失败仍跳转；下次 /me 会 401 */
  }
  location.replace(LOGIN);
}

export function requireLoginRedirect() {
  clearUser();
  location.replace(loginUrl(location.pathname + location.search));
}
