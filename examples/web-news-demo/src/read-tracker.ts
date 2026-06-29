import { BizEvents, Params, Tracker } from '@giso/tracker-web';

/** 文章阅读结算：离开详情页前上报 news_read */
export class ReadTracker {
  private startMs = Date.now();
  private activeMs = 0;
  private lastActiveMark = Date.now();
  private maxPct = 0;
  private destroyed = false;

  constructor(
    private readonly aid: string,
    private readonly newsCat: string,
  ) {
    window.addEventListener('scroll', this.onScroll, { passive: true });
    document.addEventListener('visibilitychange', this.onVisibility);
    this.onScroll();
  }

  private onScroll = (): void => {
    const height = document.documentElement.scrollHeight - window.innerHeight;
    const pct = height <= 0 ? 1 : window.scrollY / height;
    this.maxPct = Math.max(this.maxPct, Math.min(1, pct));
  };

  private onVisibility = (): void => {
    if (document.hidden) {
      this.pause();
    } else {
      this.resume();
    }
  };

  private pause(): void {
    this.activeMs += Date.now() - this.lastActiveMark;
  }

  private resume(): void {
    this.lastActiveMark = Date.now();
  }

  /** 离开文章页前调用（在 exitPage 之前） */
  finish(): void {
    if (this.destroyed) return;
    this.destroyed = true;
    if (!document.hidden) this.pause();
    const readDur = Math.max(0, Math.round(this.activeMs));
    const readPct = Math.round(this.maxPct * 100) / 100;
    Tracker.get().bizEvent(BizEvents.NEWS_READ, {
      [Params.AID]: this.aid,
      [Params.NEWS_CAT]: this.newsCat,
      [Params.READ_DUR]: readDur,
      [Params.READ_PCT]: readPct,
    });
    this.destroy();
  }

  destroy(): void {
    window.removeEventListener('scroll', this.onScroll);
    document.removeEventListener('visibilitychange', this.onVisibility);
  }
}
