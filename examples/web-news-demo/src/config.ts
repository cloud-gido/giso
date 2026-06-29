/** 上报配置：可通过 .env 覆盖；未配置时按当前页面 host 推断网关（方便局域网） */
export const APP_KEY = 'demo-key';
export const APP_VERSION = '1.0.0-news-demo';

function defaultEndpoint(): string {
  const env = import.meta.env.VITE_TRACK_ENDPOINT;
  if (env) return env;
  const { protocol, hostname } = window.location;
  return `${protocol}//${hostname}:8123/v1/track`;
}

export const TRACK_ENDPOINT = defaultEndpoint();
export const TRACK_DEBUG = import.meta.env.VITE_TRACK_DEBUG !== 'false';
export const ADMIN_URL = TRACK_ENDPOINT.replace(/\/v1\/track\/?$/, '/admin/');
