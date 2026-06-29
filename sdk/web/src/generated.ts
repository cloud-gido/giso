// AUTO-GENERATED from schema/ by tools/codegen/generate.py — DO NOT EDIT.

export const Pages = {
  /** 首页（四业务线聚合入口） */
  HOME: 'home',
  /** 搜索页（含搜索结果） */
  SEARCH: 'search',
  /** 我的/个人中心 */
  MINE: 'mine',
  /** 登录/注册页 */
  LOGIN: 'login',
  /** 钱包页（充值提现入口） */
  WALLET: 'wallet',
  /** 充值页 */
  DEPOSIT: 'deposit',
  /** 提现页 */
  WITHDRAW: 'withdraw',
  /** 视频推荐流页 */
  VIDEO_FEED: 'video_feed',
  /** 视频详情/播放页 */
  VIDEO_DETAIL: 'video_detail',
  /** 剧集合集页 */
  VIDEO_SERIES: 'video_series',
  /** 博彩大厅（赛事列表） */
  BET_LOBBY: 'bet_lobby',
  /** 赛事详情页（盘口列表） */
  BET_MATCH_DETAIL: 'bet_match_detail',
  /** 注单确认页/弹层 */
  BET_SLIP: 'bet_slip',
  /** 我的注单页 */
  BET_HISTORY: 'bet_history',
  /** 预测市场大厅 */
  PM_LOBBY: 'pm_lobby',
  /** 市场详情页（K线/订单簿/持仓） */
  PM_MARKET_DETAIL: 'pm_market_detail',
  /** 我的持仓页 */
  PM_PORTFOLIO: 'pm_portfolio',
  /** 资讯流页 */
  NEWS_FEED: 'news_feed',
  /** 资讯文章详情页 */
  NEWS_ARTICLE: 'news_article',
} as const;

export const Elements = {
  /** 运营banner位 */
  BANNER: 'banner',
  /** 搜索框 */
  SEARCH_BOX: 'search_box',
  /** 底部导航页签 */
  BOTTOM_TAB: 'bottom_tab',
  /** 登录按钮 */
  LOGIN_BTN: 'login_btn',
  /** 充值按钮 */
  DEPOSIT_BTN: 'deposit_btn',
  /** 分享按钮（继承所在模块参数） */
  SHARE_BTN: 'share_btn',
  /** 视频卡片（封面+标题+CP信息） */
  VIDEO_CARD: 'video_card',
  /** 播放按钮 */
  PLAY_BTN: 'play_btn',
  /** 点赞按钮 */
  LIKE_BTN: 'like_btn',
  /** CP头像 */
  CP_AVATAR: 'cp_avatar',
  /** 选集项 */
  EPISODE_ITEM: 'episode_item',
  /** 全屏按钮 */
  FULLSCREEN_BTN: 'fullscreen_btn',
  /** 赛事卡片（队伍+比分+主要盘口） */
  MATCH_CARD: 'match_card',
  /** 赔率按钮（点击即加入注单） */
  ODDS_BTN: 'odds_btn',
  /** 确认投注按钮 */
  BET_CONFIRM_BTN: 'bet_confirm_btn',
  /** 运动项目页签 */
  SPORT_TAB: 'sport_tab',
  /** 预测市场卡片（题目+当前概率） */
  MARKET_CARD: 'market_card',
  /** 结果选项按钮（YES/NO/多结果） */
  OUTCOME_BTN: 'outcome_btn',
  /** 下单确认按钮 */
  ORDER_CONFIRM_BTN: 'order_confirm_btn',
  /** 资讯卡片 */
  ARTICLE_CARD: 'article_card',
  /** 相关推荐文章项 */
  RELATED_ARTICLE: 'related_article',
} as const;

export const Params = {
  /** 元素在所在模块中的位置 */
  POS: 'pos',
  /** 搜索词 */
  KW: 'kw',
  /** 页签名 */
  TAB_NAME: 'tab_name',
  /** 推荐请求追踪ID */
  REC_TRACE_ID: 'rec_trace_id',
  /** 货币 */
  CURRENCY: 'currency',
  /** 获客渠道 */
  CHANNEL: 'channel',
  /** 视频ID */
  VID: 'vid',
  /** 创作者/内容提供方ID */
  CP_ID: 'cp_id',
  /** 剧集ID（多集内容的合集） */
  SERIES_ID: 'series_id',
  /** 集数 */
  EP_NUM: 'ep_num',
  /** 播放时长 */
  PLAY_DUR: 'play_dur',
  /** 视频总时长（毫秒） */
  VIDEO_DUR: 'video_dur',
  /** 当前播放进度（毫秒） */
  PLAY_POS: 'play_pos',
  /** 是否自动播放 */
  IS_AUTO: 'is_auto',
  /** 播放倍速 */
  SPEED: 'speed',
  /** 清晰度 */
  DEFINITION: 'definition',
  /** 运动项目ID */
  SPORT_ID: 'sport_id',
  /** 赛事ID */
  MATCH_ID: 'match_id',
  /** 联赛ID */
  LEAGUE_ID: 'league_id',
  /** 盘口类型 */
  MARKET_TYPE: 'market_type',
  /** 投注选项ID */
  SELECTION_ID: 'selection_id',
  /** 赔率 */
  ODDS: 'odds',
  /** 投注金额 */
  STAKE_AMT: 'stake_amt',
  /** 注单ID */
  BET_ID: 'bet_id',
  /** 注单类型 */
  BET_TYPE: 'bet_type',
  /** 是否滚球（赛中投注） */
  IS_LIVE: 'is_live',
  /** 派彩金额（cent） */
  PAYOUT_AMT: 'payout_amt',
  /** 预测市场ID */
  PM_MARKET_ID: 'pm_market_id',
  /** 市场类目 */
  PM_CATEGORY: 'pm_category',
  /** 结果选项ID（YES/NO 或多结果） */
  OUTCOME_ID: 'outcome_id',
  /** 订单ID */
  ORDER_ID: 'order_id',
  /** 买卖方向 */
  ORDER_SIDE: 'order_side',
  /** 价格 */
  PRICE: 'price',
  /** 份额数量 */
  SHARES: 'shares',
  /** 资讯文章ID */
  AID: 'aid',
  /** 资讯分类 */
  NEWS_CAT: 'news_cat',
  /** 阅读时长（毫秒） */
  READ_DUR: 'read_dur',
  /** 阅读完成度 */
  READ_PCT: 'read_pct',
  /** 充值金额（cent） */
  DEPOSIT_AMT: 'deposit_amt',
  /** 提现金额（cent） */
  WITHDRAW_AMT: 'withdraw_amt',
  /** 支付方式 */
  PAY_METHOD: 'pay_method',
  /** 失败原因码 */
  FAIL_REASON: 'fail_reason',
} as const;

export const BizEvents = {
  /** 开始播放（首帧渲染成功） */
  VIDEO_PLAY_START: 'video_play_start',
  /** 播放心跳（每30s + 暂停/退出时补报） */
  VIDEO_PLAY_HEARTBEAT: 'video_play_heartbeat',
  /** 播放结束（退出/切集/播完） */
  VIDEO_PLAY_END: 'video_play_end',
  /** 播放失败 */
  VIDEO_PLAY_ERROR: 'video_play_error',
  /** 加入注单（点击赔率后成功加入） */
  BET_SLIP_ADD: 'bet_slip_add',
  /** 提交投注（客户端意图，点击确认后发起请求） */
  BET_SUBMIT: 'bet_submit',
  /** 投注成功（服务端事实，事务提交后发出） */
  BET_PLACED: 'bet_placed',
  /** 投注被拒（风控/赔率变化/余额不足） */
  BET_REJECTED: 'bet_rejected',
  /** 注单结算（服务端事实） */
  BET_SETTLED: 'bet_settled',
  /** 提交订单（客户端意图） */
  PM_ORDER_SUBMIT: 'pm_order_submit',
  /** 订单成交（服务端事实） */
  PM_ORDER_FILLED: 'pm_order_filled',
  /** 订单撤销 */
  PM_ORDER_CANCELLED: 'pm_order_cancelled',
  /** 市场结算（出结果） */
  PM_MARKET_RESOLVED: 'pm_market_resolved',
  /** 文章阅读结算（离开文章页时一次性上报） */
  NEWS_READ: 'news_read',
  /** 注册成功 */
  SIGNUP_COMPLETED: 'signup_completed',
  /** 充值到账（服务端事实） */
  DEPOSIT_COMPLETED: 'deposit_completed',
  /** 充值失败 */
  DEPOSIT_FAILED: 'deposit_failed',
  /** 提现完成（服务端事实） */
  WITHDRAW_COMPLETED: 'withdraw_completed',
} as const;
