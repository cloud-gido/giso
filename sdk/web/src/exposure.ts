/**
 * 曝光监测：IntersectionObserver 实现 SDK 统一的曝光口径
 * 口径：可视面积 ≥ ratio(默认50%) 且持续 ≥ duration(默认500ms) 记一次；
 *      滚出（可视 < 20%）后再次满足可重记；单次页面进入内每实例最多 maxPerPage 次。
 */

export interface ExposureRecord {
  el: Element;
  duration: number;
  maxRatio: number;
}

export class ExposureObserver {
  private io: IntersectionObserver;
  private pending = new Map<Element, { enterTs: number; maxRatio: number; timer: number }>();
  private counts = new WeakMap<Element, number>();

  constructor(
    private ratio: number,
    private duration: number,
    private maxPerPage: number,
    private onExposure: (record: ExposureRecord) => void,
  ) {
    this.io = new IntersectionObserver((entries) => this.handle(entries), {
      threshold: [0.2, this.ratio],
    });
  }

  /** 远程配置下发后更新口径（duration/maxPerPage 即时生效；ratio 对新 observe 的元素生效） */
  updateThresholds(ratio: number, duration: number, maxPerPage: number): void {
    this.ratio = ratio;
    this.duration = duration;
    this.maxPerPage = maxPerPage;
  }

  observe(el: Element): void {
    this.io.observe(el);
  }

  unobserve(el: Element): void {
    this.io.unobserve(el);
    this.clearPending(el);
  }

  /** 页面切换时调用：重置计数 */
  resetPage(): void {
    this.counts = new WeakMap();
    this.pending.forEach((_, el) => this.clearPending(el));
  }

  private handle(entries: IntersectionObserverEntry[]): void {
    for (const entry of entries) {
      const el = entry.target;
      if (entry.intersectionRatio >= this.ratio) {
        if (!this.pending.has(el)) {
          const enterTs = performance.now();
          const timer = window.setTimeout(() => this.fire(el), this.duration);
          this.pending.set(el, { enterTs, maxRatio: entry.intersectionRatio, timer });
        } else {
          const p = this.pending.get(el)!;
          p.maxRatio = Math.max(p.maxRatio, entry.intersectionRatio);
        }
      } else if (entry.intersectionRatio < 0.2) {
        // 滚出 80% 以上：取消未达时长的计时，允许下次重新曝光
        this.clearPending(el);
      }
    }
  }

  private fire(el: Element): void {
    const p = this.pending.get(el);
    if (!p) return;
    this.pending.delete(el);
    const count = this.counts.get(el) ?? 0;
    if (count >= this.maxPerPage) return;
    this.counts.set(el, count + 1);
    this.onExposure({
      el,
      duration: Math.round(performance.now() - p.enterTs),
      maxRatio: Math.round(p.maxRatio * 100) / 100,
    });
  }

  private clearPending(el: Element): void {
    const p = this.pending.get(el);
    if (p) {
      clearTimeout(p.timer);
      this.pending.delete(el);
    }
  }
}
