import type {
  BizContext, ElementContext, PageContext, Params, Passthrough, StandardEvent, TrackerConfig, TrackEvent,
} from './types';
import { collectCommonParams, uuid } from './common';
import { EventQueue } from './queue';
import { ExposureObserver, ExposureRecord } from './exposure';

interface ElementMeta {
  eid: string;
  pos?: number;
  params?: Params;
  /** 后台下发的透传参数包，随该元素的曝光/点击原样上报 */
  pt?: Passthrough;
}

/**
 * Web 埋点 SDK 门面。
 * 事件收敛：只暴露 enterPage / bind / bizEvent 三类 API，
 * 曝光与点击的触发时机由 SDK 控制，业务方不可自定义。
 */
export class Tracker {
  private static instance: Tracker | null = null;

  private config: Required<Pick<TrackerConfig,
    'exposureRatio' | 'exposureDuration' | 'exposureMaxPerPage' | 'batchSize' | 'flushInterval'>> & TrackerConfig;
  private queue: EventQueue;
  private exposure: ExposureObserver;
  private metas = new WeakMap<Element, ElementMeta>();
  private uid = '';

  private curPage: { pgid: string; params?: Params; pt?: Passthrough; enterTs: number } | null = null;
  private refPgid = '';
  private refEid = '';

  static init(config: TrackerConfig): Tracker {
    if (!Tracker.instance) Tracker.instance = new Tracker(config);
    return Tracker.instance;
  }

  static get(): Tracker {
    if (!Tracker.instance) throw new Error('Tracker.init() first');
    return Tracker.instance;
  }

  private constructor(config: TrackerConfig) {
    this.config = {
      exposureRatio: 0.5,
      exposureDuration: 500,
      exposureMaxPerPage: 3,
      batchSize: 20,
      flushInterval: 15_000,
      ...config,
    };
    this.queue = new EventQueue(
      config.endpoint, config.appId,
      this.config.batchSize, this.config.flushInterval, !!config.debug,
    );
    this.exposure = new ExposureObserver(
      this.config.exposureRatio, this.config.exposureDuration, this.config.exposureMaxPerPage,
      (r) => this.onExposure(r),
    );
    document.addEventListener('click', (e) => this.onClick(e), { capture: true });
    window.addEventListener('pagehide', () => this.exitPage());
    this.fetchRemoteConfig();
  }

  /** 拉取服务端口径配置（/v1/config），失败静默沿用本地默认值 */
  private fetchRemoteConfig(): void {
    const url = this.config.endpoint.replace(/\/v1\/track\/?$/, '/v1/config');
    if (url === this.config.endpoint) return;
    fetch(url)
      .then((r) => (r.ok ? r.json() : null))
      .then((c) => {
        if (!c) return;
        this.exposure.updateThresholds(
          c.exposure_ratio ?? this.config.exposureRatio,
          c.exposure_duration_ms ?? this.config.exposureDuration,
          c.exposure_max_per_page ?? this.config.exposureMaxPerPage,
        );
        this.queue.updateBatching(
          c.batch_size ?? this.config.batchSize,
          c.flush_interval_ms ?? this.config.flushInterval,
        );
      })
      .catch(() => { /* 静默降级 */ });
  }

  setUid(uid: string): void { this.uid = uid; }
  clearUid(): void { this.uid = ''; }

  // ── 页面 ──────────────────────────────────────────────

  /**
   * 路由挂载完成后调用。SPA 路由切换会自动补报上一页的 page_exit。
   * @param pt 后台下发的透传参数包（如推荐 trace），本页所有事件自动携带
   */
  enterPage(pgid: string, pgParams?: Params, pt?: Passthrough): void {
    if (this.curPage) this.exitPage();
    this.curPage = { pgid, params: pgParams, pt, enterTs: Date.now() };
    this.exposure.resetPage();
    this.emit('page_enter', { page: this.pageContext() }, this.curPage.pt);
  }

  exitPage(): void {
    if (!this.curPage) return;
    const stay = Date.now() - this.curPage.enterTs;
    this.emit('page_exit', { page: { ...this.pageContext(), pg_stay: stay } }, this.curPage.pt);
    this.refPgid = this.curPage.pgid;
    this.curPage = null;
  }

  // ── 元素 ──────────────────────────────────────────────

  /**
   * 声明元素：登记后由 SDK 自动监测曝光与点击。
   * 参数继承：上报时自动合并「页面参数 → 祖先 bind 元素参数 → 自身参数」，子级覆盖父级。
   */
  bind(el: Element, meta: ElementMeta): void {
    this.metas.set(el, meta);
    this.exposure.observe(el);
  }

  unbind(el: Element): void {
    this.metas.delete(el);
    this.exposure.unobserve(el);
  }

  // ── 业务事件 ───────────────────────────────────────────

  bizEvent(code: string, params?: Params, pt?: Passthrough): void {
    const biz: BizContext = { code, params };
    this.emit('biz_event', { page: this.pageContext(), biz }, this.mergePt(this.curPage?.pt, pt));
  }

  flush(): void { this.queue.flush(); }

  // ── 内部 ──────────────────────────────────────────────

  private onExposure(r: ExposureRecord): void {
    const ctx = this.elementContext(r.el);
    if (!ctx) return;
    ctx.context.exp_dur = r.duration;
    ctx.context.exp_ratio = r.maxRatio;
    this.emit('element_exposure', { page: this.pageContext(), element: ctx.context },
      this.mergePt(this.curPage?.pt, ctx.pt));
  }

  private onClick(e: Event): void {
    const target = e.target as Element | null;
    if (!target) return;
    let el: Element | null = target;
    while (el && !this.metas.has(el)) el = el.parentElement;
    if (!el) return;
    const ctx = this.elementContext(el);
    if (!ctx) return;
    this.refEid = ctx.context.eid; // 归因链：下一个 page_enter 的 ref_eid
    this.emit('element_click', { page: this.pageContext(), element: ctx.context },
      this.mergePt(this.curPage?.pt, ctx.pt));
  }

  /** 沿 DOM 向上收集祖先 bind 元素，实现参数继承（params 与 pt 同规则）与 mod 推导 */
  private elementContext(el: Element): { context: ElementContext; pt?: Passthrough } | null {
    const meta = this.metas.get(el);
    if (!meta) return null;

    const inherited: Params = {};
    const inheritedPt: Passthrough = {};
    let mod: string | undefined;
    const chain: ElementMeta[] = [];
    let p = el.parentElement;
    while (p) {
      const m = this.metas.get(p);
      if (m) {
        chain.push(m);
        if (!mod) mod = m.eid;
      }
      p = p.parentElement;
    }
    // 自根向叶合并，叶子（自身）优先级最高
    for (let i = chain.length - 1; i >= 0; i--) {
      Object.assign(inherited, chain[i].params);
      Object.assign(inheritedPt, chain[i].pt);
    }
    const pt = this.mergePt(inheritedPt, meta.pt);

    return {
      context: {
        eid: meta.eid,
        mod,
        pos: meta.pos,
        params: { ...inherited, ...meta.params },
      },
      pt,
    };
  }

  /** 合并透传包（后者优先）；两者皆空返回 undefined，避免上报空对象 */
  private mergePt(a?: Passthrough, b?: Passthrough): Passthrough | undefined {
    if (!a && !b) return undefined;
    const merged = { ...a, ...b };
    return Object.keys(merged).length > 0 ? merged : undefined;
  }

  private pageContext(): PageContext {
    return {
      pgid: this.curPage?.pgid ?? '',
      pg_params: this.curPage?.params,
      ref_pgid: this.refPgid || undefined,
      ref_eid: this.refEid || undefined,
    };
  }

  private emit(event: StandardEvent, parts: Partial<TrackEvent>, pt?: Passthrough): void {
    const ev: TrackEvent = {
      event,
      log_id: uuid(),
      ctime: Date.now(),
      common: collectCommonParams(this.config, this.uid),
      ...parts,
    };
    if (pt) ev.pt = pt;
    this.queue.push(ev);
  }
}
