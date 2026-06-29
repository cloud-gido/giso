/* 管理 API 封装 */
export const api = (p, opt) => fetch('/admin/api' + p, opt).then((r) => r.json());

export function connectSSE(onEvent, onState) {
  const es = new EventSource('/admin/api/stream');
  es.onopen = () => onState(true);
  es.onerror = () => onState(false);
  es.onmessage = (e) => onEvent(JSON.parse(e.data));
}
