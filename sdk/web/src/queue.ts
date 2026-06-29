import type { TrackEvent } from './types';

const STORE_KEY = '_giso_evq';
const MAX_STORED = 500;
const MAX_RETRY_BACKOFF = 60_000;

/** 事件队列：攒批、落盘、失败指数退避重试、页面卸载兜底 */
export class EventQueue {
  private buffer: TrackEvent[] = [];
  private timer: number | null = null;
  private sending = false;
  private backoff = 1000;

  /** 远程配置下发后更新攒批参数 */
  updateBatching(batchSize: number, flushInterval: number): void {
    this.batchSize = batchSize;
    this.flushInterval = flushInterval;
  }

  constructor(
    private endpoint: string,
    private appKey: string,
    private batchSize: number,
    private flushInterval: number,
    private debug: boolean,
  ) {
    this.buffer = this.restore();
    window.addEventListener('pagehide', () => this.flush(true));
    document.addEventListener('visibilitychange', () => {
      if (document.visibilityState === 'hidden') this.flush(true);
    });
  }

  push(ev: TrackEvent): void {
    if (this.debug) {
      // debug 模式不攒批，便于实时联调
      console.debug('[qy-tracker]', ev.event, ev);
      this.buffer.push(ev);
      this.flush();
      return;
    }
    this.buffer.push(ev);
    if (this.buffer.length >= this.batchSize) {
      this.flush();
    } else if (this.timer === null) {
      this.timer = window.setTimeout(() => this.flush(), this.flushInterval);
    }
  }

  flush(useBeacon = false): void {
    if (this.timer !== null) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    if (this.buffer.length === 0 || (this.sending && !useBeacon)) return;

    const batch = this.buffer.splice(0, this.batchSize);
    const body = JSON.stringify(batch);

    if (useBeacon && navigator.sendBeacon) {
      // 卸载场景：sendBeacon 不能带自定义 header，appKey 走 query
      const ok = navigator.sendBeacon(`${this.endpoint}?k=${this.appKey}`, body);
      if (!ok) this.persist(batch);
      return;
    }

    this.sending = true;
    fetch(this.endpoint, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', 'X-App-Key': this.appKey },
      body,
      keepalive: true,
    })
      .then((res) => {
        this.sending = false;
        if (res.status >= 500 || res.status === 429) throw new Error(`server ${res.status}`);
        // 2xx 成功；4xx（除 429 限流外）数据有误不重试（网关已记隔离区）
        this.backoff = 1000;
        if (this.buffer.length >= this.batchSize) this.flush();
      })
      .catch(() => {
        this.sending = false;
        this.buffer.unshift(...batch);
        this.persist(this.buffer);
        window.setTimeout(() => this.flush(), this.backoff);
        this.backoff = Math.min(this.backoff * 2, MAX_RETRY_BACKOFF);
      });
  }

  private persist(events: TrackEvent[]): void {
    try {
      localStorage.setItem(STORE_KEY, JSON.stringify(events.slice(-MAX_STORED)));
    } catch { /* 存储满则放弃落盘 */ }
  }

  private restore(): TrackEvent[] {
    try {
      const raw = localStorage.getItem(STORE_KEY);
      if (raw) {
        localStorage.removeItem(STORE_KEY);
        return JSON.parse(raw);
      }
    } catch { /* noop */ }
    return [];
  }
}
