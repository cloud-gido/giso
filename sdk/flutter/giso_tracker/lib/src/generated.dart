// AUTO-GENERATED from schema/ by tools/codegen/generate.py — DO NOT EDIT.

/// Registry constants for `Pages`.
class Pages {
  Pages._();

  /// 首页（四业务线聚合入口）
  static const String home = 'home';
  /// 搜索页（含搜索结果）
  static const String search = 'search';
  /// 我的/个人中心
  static const String mine = 'mine';
  /// 登录/注册页
  static const String login = 'login';
  /// 钱包页（充值提现入口）
  static const String wallet = 'wallet';
  /// 充值页
  static const String deposit = 'deposit';
  /// 提现页
  static const String withdraw = 'withdraw';
  /// 视频推荐流页
  static const String videoFeed = 'video_feed';
  /// 视频详情/播放页
  static const String videoDetail = 'video_detail';
  /// 剧集合集页
  static const String videoSeries = 'video_series';
  /// 博彩大厅（赛事列表）
  static const String betLobby = 'bet_lobby';
  /// 赛事详情页（盘口列表）
  static const String betMatchDetail = 'bet_match_detail';
  /// 注单确认页/弹层
  static const String betSlip = 'bet_slip';
  /// 我的注单页
  static const String betHistory = 'bet_history';
  /// 预测市场大厅
  static const String pmLobby = 'pm_lobby';
  /// 市场详情页（K线/订单簿/持仓）
  static const String pmMarketDetail = 'pm_market_detail';
  /// 我的持仓页
  static const String pmPortfolio = 'pm_portfolio';
  /// 资讯流页
  static const String newsFeed = 'news_feed';
  /// 资讯文章详情页
  static const String newsArticle = 'news_article';
}
/// Registry constants for `Elements`.
class Elements {
  Elements._();

  /// 运营banner位
  static const String banner = 'banner';
  /// 搜索框
  static const String searchBox = 'search_box';
  /// 底部导航页签
  static const String bottomTab = 'bottom_tab';
  /// 登录按钮
  static const String loginBtn = 'login_btn';
  /// 充值按钮
  static const String depositBtn = 'deposit_btn';
  /// 分享按钮（继承所在模块参数）
  static const String shareBtn = 'share_btn';
  /// 视频卡片（封面+标题+CP信息）
  static const String videoCard = 'video_card';
  /// 播放按钮
  static const String playBtn = 'play_btn';
  /// 点赞按钮
  static const String likeBtn = 'like_btn';
  /// CP头像
  static const String cpAvatar = 'cp_avatar';
  /// 选集项
  static const String episodeItem = 'episode_item';
  /// 全屏按钮
  static const String fullscreenBtn = 'fullscreen_btn';
  /// 赛事卡片（队伍+比分+主要盘口）
  static const String matchCard = 'match_card';
  /// 赔率按钮（点击即加入注单）
  static const String oddsBtn = 'odds_btn';
  /// 确认投注按钮
  static const String betConfirmBtn = 'bet_confirm_btn';
  /// 运动项目页签
  static const String sportTab = 'sport_tab';
  /// 预测市场卡片（题目+当前概率）
  static const String marketCard = 'market_card';
  /// 结果选项按钮（YES/NO/多结果）
  static const String outcomeBtn = 'outcome_btn';
  /// 下单确认按钮
  static const String orderConfirmBtn = 'order_confirm_btn';
  /// 资讯卡片
  static const String articleCard = 'article_card';
  /// 相关推荐文章项
  static const String relatedArticle = 'related_article';
}
/// Registry constants for `Params`.
class Params {
  Params._();

  /// 元素在所在模块中的位置
  static const String pos = 'pos';
  /// 搜索词
  static const String kw = 'kw';
  /// 页签名
  static const String tabName = 'tab_name';
  /// 上报环境
  static const String env = 'env';
  /// 推荐请求追踪ID
  static const String recTraceId = 'rec_trace_id';
  /// 货币
  static const String currency = 'currency';
  /// 获客渠道
  static const String channel = 'channel';
  /// 视频ID
  static const String vid = 'vid';
  /// 创作者/内容提供方ID
  static const String cpId = 'cp_id';
  /// 剧集ID（多集内容的合集）
  static const String seriesId = 'series_id';
  /// 集数
  static const String epNum = 'ep_num';
  /// 播放时长
  static const String playDur = 'play_dur';
  /// 视频总时长（毫秒）
  static const String videoDur = 'video_dur';
  /// 当前播放进度（毫秒）
  static const String playPos = 'play_pos';
  /// 是否自动播放
  static const String isAuto = 'is_auto';
  /// 播放倍速
  static const String speed = 'speed';
  /// 清晰度
  static const String definition = 'definition';
  /// 运动项目ID
  static const String sportId = 'sport_id';
  /// 赛事ID
  static const String matchId = 'match_id';
  /// 联赛ID
  static const String leagueId = 'league_id';
  /// 盘口类型
  static const String marketType = 'market_type';
  /// 投注选项ID
  static const String selectionId = 'selection_id';
  /// 赔率
  static const String odds = 'odds';
  /// 投注金额
  static const String stakeAmt = 'stake_amt';
  /// 注单ID
  static const String betId = 'bet_id';
  /// 注单类型
  static const String betType = 'bet_type';
  /// 是否滚球（赛中投注）
  static const String isLive = 'is_live';
  /// 派彩金额（cent）
  static const String payoutAmt = 'payout_amt';
  /// 预测市场ID
  static const String pmMarketId = 'pm_market_id';
  /// 市场类目
  static const String pmCategory = 'pm_category';
  /// 结果选项ID（YES/NO 或多结果）
  static const String outcomeId = 'outcome_id';
  /// 订单ID
  static const String orderId = 'order_id';
  /// 买卖方向
  static const String orderSide = 'order_side';
  /// 价格
  static const String price = 'price';
  /// 份额数量
  static const String shares = 'shares';
  /// 资讯文章ID
  static const String aid = 'aid';
  /// 资讯分类
  static const String newsCat = 'news_cat';
  /// 阅读时长（毫秒）
  static const String readDur = 'read_dur';
  /// 阅读完成度
  static const String readPct = 'read_pct';
  /// 充值金额（cent）
  static const String depositAmt = 'deposit_amt';
  /// 提现金额（cent）
  static const String withdrawAmt = 'withdraw_amt';
  /// 支付方式
  static const String payMethod = 'pay_method';
  /// 失败原因码
  static const String failReason = 'fail_reason';
}
/// Registry constants for `BizEvents`.
class BizEvents {
  BizEvents._();

  /// 开始播放（首帧渲染成功）
  static const String videoPlayStart = 'video_play_start';
  /// 播放心跳（每30s + 暂停/退出时补报）
  static const String videoPlayHeartbeat = 'video_play_heartbeat';
  /// 播放结束（退出/切集/播完）
  static const String videoPlayEnd = 'video_play_end';
  /// 播放失败
  static const String videoPlayError = 'video_play_error';
  /// 加入注单（点击赔率后成功加入）
  static const String betSlipAdd = 'bet_slip_add';
  /// 提交投注（客户端意图，点击确认后发起请求）
  static const String betSubmit = 'bet_submit';
  /// 投注成功（服务端事实，事务提交后发出）
  static const String betPlaced = 'bet_placed';
  /// 投注被拒（风控/赔率变化/余额不足）
  static const String betRejected = 'bet_rejected';
  /// 注单结算（服务端事实）
  static const String betSettled = 'bet_settled';
  /// 提交订单（客户端意图）
  static const String pmOrderSubmit = 'pm_order_submit';
  /// 订单成交（服务端事实）
  static const String pmOrderFilled = 'pm_order_filled';
  /// 订单撤销
  static const String pmOrderCancelled = 'pm_order_cancelled';
  /// 市场结算（出结果）
  static const String pmMarketResolved = 'pm_market_resolved';
  /// 文章阅读结算（离开文章页时一次性上报）
  static const String newsRead = 'news_read';
  /// 注册成功
  static const String signupCompleted = 'signup_completed';
  /// 充值到账（服务端事实）
  static const String depositCompleted = 'deposit_completed';
  /// 充值失败
  static const String depositFailed = 'deposit_failed';
  /// 提现完成（服务端事实）
  static const String withdrawCompleted = 'withdraw_completed';
}
