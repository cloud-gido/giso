/** 协议类型定义，与 docs/tracking/02-上报协议规范.md 一一对应 */

export type StandardEvent =
  | 'page_enter'
  | 'page_exit'
  | 'element_exposure'
  | 'element_click'
  | 'biz_event';

export type Params = Record<string, string | number | boolean>;

/**
 * 后台参数透传包：服务端接口下发的私有参数（推荐 trace_id、赔率版本、盘口快照等），
 * 端上不理解内容、不参与注册表校验，原样透传到数仓（Doris pt 列）。
 */
export type Passthrough = Record<string, unknown>;

export interface CommonParams {
  app_id: string;
  app_pkg: string;
  platform: 'web';
  app_vrsn: string;
  sdk_vrsn: string;
  /** SDK 实现栈：web / native / flutter（与 platform OS 正交） */
  sdk_runtime: 'web' | 'native' | 'flutter';
  did: string;
  uid: string;
  session_id: string;
  channel: string;
  env: string;
  os_vrsn: string;
  dev_brand: string;
  dev_model: string;
  screen_res: string;
  net_type: string;
  lang: string;
  tz: string;
}

export interface PageContext {
  pgid: string;
  pg_params?: Params;
  ref_pgid?: string;
  ref_eid?: string;
  pg_stay?: number;
}

export interface ElementContext {
  eid: string;
  mod?: string;
  pos?: number;
  params?: Params;
  exp_dur?: number;
  exp_ratio?: number;
}

export interface BizContext {
  code: string;
  params?: Params;
}

export interface TrackEvent {
  event: StandardEvent;
  log_id: string;
  ctime: number;
  common: CommonParams;
  page?: PageContext;
  element?: ElementContext;
  biz?: BizContext;
  /** 后台参数透传包（页面级与元素级合并，元素级优先），网关不校验内容 */
  pt?: Passthrough;
}

export interface TrackerConfig {
  appId: string;
  appVersion: string;
  endpoint: string;
  channel?: string;
  debug?: boolean;
  /** prod | test；默认 debug=true → test，否则 prod */
  env?: string;
  /** 应用包名覆盖（Web 无自动采集，可手动传入） */
  appPkg?: string;
  /** 曝光判定：可视面积比例阈值，默认 0.5 */
  exposureRatio?: number;
  /** 曝光判定：持续时长 ms，默认 500 */
  exposureDuration?: number;
  /** 同一元素实例单次页面进入内最大曝光次数，默认 3 */
  exposureMaxPerPage?: number;
  /** 攒批条数，默认 20 */
  batchSize?: number;
  /** 攒批最大间隔 ms，默认 15000 */
  flushInterval?: number;
}
