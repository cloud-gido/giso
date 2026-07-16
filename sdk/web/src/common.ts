import type { CommonParams, TrackerConfig } from './types';

const SDK_VERSION = '1.0.9';
const DID_KEY = '_giso_did';
const SESSION_KEY = '_giso_session';
const SESSION_GAP_MS = 30 * 60 * 1000;

export function uuid(): string {
  if (typeof crypto !== 'undefined' && crypto.randomUUID) return crypto.randomUUID();
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

function getOrCreateDid(): string {
  try {
    let did = localStorage.getItem(DID_KEY);
    if (!did) {
      did = uuid();
      localStorage.setItem(DID_KEY, did);
    }
    return did;
  } catch {
    return uuid(); // 无存储环境降级为会话级 did
  }
}

/** 会话：30 分钟无活动则重开 */
function getSessionId(): string {
  const now = Date.now();
  try {
    const raw = sessionStorage.getItem(SESSION_KEY);
    if (raw) {
      const { id, ts } = JSON.parse(raw);
      if (now - ts < SESSION_GAP_MS) {
        sessionStorage.setItem(SESSION_KEY, JSON.stringify({ id, ts: now }));
        return id;
      }
    }
  } catch { /* fallthrough */ }
  const id = 's-' + uuid();
  try { sessionStorage.setItem(SESSION_KEY, JSON.stringify({ id, ts: now })); } catch { /* noop */ }
  return id;
}

function netType(): string {
  const conn = (navigator as any).connection;
  return conn?.effectiveType ?? 'unknown';
}

function tzOffset(): string {
  const m = -new Date().getTimezoneOffset();
  const sign = m >= 0 ? '+' : '-';
  const h = String(Math.floor(Math.abs(m) / 60)).padStart(2, '0');
  const mm = String(Math.abs(m) % 60).padStart(2, '0');
  return `${sign}${h}:${mm}`;
}

export function collectCommonParams(config: TrackerConfig, uid: string, bizDid = ''): CommonParams {
  const env = config.env ?? (config.debug ? 'test' : 'prod');
  return {
    app_id: config.appId,
    app_pkg: config.appPkg ?? '',
    platform: 'web',
    app_vrsn: config.appVersion,
    sdk_vrsn: SDK_VERSION,
    sdk_runtime: 'web',
    did: getOrCreateDid(),
    uid,
    biz_did: bizDid,
    session_id: getSessionId(),
    channel: config.channel ?? '',
    env,
    os_vrsn: navigator.platform ?? '',
    dev_brand: 'web',
    dev_model: navigator.userAgent.slice(0, 64),
    screen_res: `${screen.width}x${screen.height}`,
    net_type: netType(),
    lang: navigator.language,
    tz: tzOffset(),
  };
}
