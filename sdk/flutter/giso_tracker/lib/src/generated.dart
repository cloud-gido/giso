// AUTO-GENERATED from schema/ by tools/codegen/generate.py — DO NOT EDIT.

// AUTO-GENERATED from schema/ by tools/codegen/generate.py — DO NOT EDIT.

/// Registry constants for `Pages`.
class Pages {
  Pages._();

  /// 首页（四业务线聚合入口）
  static const String HOME = 'home';
  /// 搜索页（含搜索结果）
  static const String SEARCH = 'search';
  /// 我的/个人中心
  static const String MINE = 'mine';
  /// 登录/注册页
  static const String LOGIN = 'login';
  /// 钱包页（充值提现入口）
  static const String WALLET = 'wallet';
  /// 充值页
  static const String DEPOSIT = 'deposit';
  /// 提现页
  static const String WITHDRAW = 'withdraw';
  /// 视频推荐流页
  static const String VIDEO_FEED = 'video_feed';
  /// 视频详情/播放页
  static const String VIDEO_DETAIL = 'video_detail';
  /// 剧集合集页
  static const String VIDEO_SERIES = 'video_series';
  /// 博彩大厅（赛事列表）
  static const String BET_LOBBY = 'bet_lobby';
  /// 赛事详情页（盘口列表）
  static const String BET_MATCH_DETAIL = 'bet_match_detail';
  /// 注单确认页/弹层
  static const String BET_SLIP = 'bet_slip';
  /// 我的注单页
  static const String BET_HISTORY = 'bet_history';
  /// 预测市场大厅
  static const String PM_LOBBY = 'pm_lobby';
  /// 市场详情页（K线/订单簿/持仓）
  static const String PM_MARKET_DETAIL = 'pm_market_detail';
  /// 我的持仓页
  static const String PM_PORTFOLIO = 'pm_portfolio';
  /// 资讯流页
  static const String NEWS_FEED = 'news_feed';
  /// 资讯文章详情页
  static const String NEWS_ARTICLE = 'news_article';
}

// AUTO-GENERATED from schema/ by tools/codegen/generate.py — DO NOT EDIT.

/// Registry constants for `Elements`.
class Elements {
  Elements._();

  /// 运营banner位
  static const String BANNER = 'banner';
  /// 搜索框
  static const String SEARCH_BOX = 'search_box';
  /// 底部导航页签
  static const String BOTTOM_TAB = 'bottom_tab';
  /// 登录按钮
  static const String LOGIN_BTN = 'login_btn';
  /// 充值按钮
  static const String DEPOSIT_BTN = 'deposit_btn';
  /// 分享按钮（继承所在模块参数）
  static const String SHARE_BTN = 'share_btn';
  /// 视频卡片（封面+标题+CP信息）
  static const String VIDEO_CARD = 'video_card';
  /// 播放按钮
  static const String PLAY_BTN = 'play_btn';
  /// 点赞按钮
  static const String LIKE_BTN = 'like_btn';
  /// CP头像
  static const String CP_AVATAR = 'cp_avatar';
  /// 选集项
  static const String EPISODE_ITEM = 'episode_item';
  /// 全屏按钮
  static const String FULLSCREEN_BTN = 'fullscreen_btn';
  /// 赛事卡片（队伍+比分+主要盘口）
  static const String MATCH_CARD = 'match_card';
  /// 赔率按钮（点击即加入注单）
  static const String ODDS_BTN = 'odds_btn';
  /// 确认投注按钮
  static const String BET_CONFIRM_BTN = 'bet_confirm_btn';
  /// 运动项目页签
  static const String SPORT_TAB = 'sport_tab';
  /// 预测市场卡片（题目+当前概率）
  static const String MARKET_CARD = 'market_card';
  /// 结果选项按钮（YES/NO/多结果）
  static const String OUTCOME_BTN = 'outcome_btn';
  /// 下单确认按钮
  static const String ORDER_CONFIRM_BTN = 'order_confirm_btn';
  /// 资讯卡片
  static const String ARTICLE_CARD = 'article_card';
  /// 相关推荐文章项
  static const String RELATED_ARTICLE = 'related_article';
}

// AUTO-GENERATED from schema/ by tools/codegen/generate.py — DO NOT EDIT.

/// Registry constants for `Params`.
class Params {
  Params._();

  /// 元素在所在模块中的位置
  static const String POS = 'pos';
  /// 搜索词
  static const String KW = 'kw';
  /// 页签名
  static const String TAB_NAME = 'tab_name';
  /// 上报环境
  static const String ENV = 'env';
  /// 推荐请求追踪ID
  static const String REC_TRACE_ID = 'rec_trace_id';
  /// 货币
  static const String CURRENCY = 'currency';
  /// 获客渠道
  static const String CHANNEL = 'channel';
  /// 视频ID
  static const String VID = 'vid';
  /// 创作者/内容提供方ID
  static const String CP_ID = 'cp_id';
  /// 剧集ID（多集内容的合集）
  static const String SERIES_ID = 'series_id';
  /// 集数
  static const String EP_NUM = 'ep_num';
  /// 播放时长
  static const String PLAY_DUR = 'play_dur';
  /// 视频总时长（毫秒）
  static const String VIDEO_DUR = 'video_dur';
  /// 当前播放进度（毫秒）
  static const String PLAY_POS = 'play_pos';
  /// 是否自动播放
  static const String IS_AUTO = 'is_auto';
  /// 播放倍速
  static const String SPEED = 'speed';
  /// 清晰度
  static const String DEFINITION = 'definition';
  /// 运动项目ID
  static const String SPORT_ID = 'sport_id';
  /// 赛事ID
  static const String MATCH_ID = 'match_id';
  /// 联赛ID
  static const String LEAGUE_ID = 'league_id';
  /// 盘口类型
  static const String MARKET_TYPE = 'market_type';
  /// 投注选项ID
  static const String SELECTION_ID = 'selection_id';
  /// 赔率
  static const String ODDS = 'odds';
  /// 投注金额
  static const String STAKE_AMT = 'stake_amt';
  /// 注单ID
  static const String BET_ID = 'bet_id';
  /// 注单类型
  static const String BET_TYPE = 'bet_type';
  /// 是否滚球（赛中投注）
  static const String IS_LIVE = 'is_live';
  /// 派彩金额（cent）
  static const String PAYOUT_AMT = 'payout_amt';
  /// 预测市场ID
  static const String PM_MARKET_ID = 'pm_market_id';
  /// 市场类目
  static const String PM_CATEGORY = 'pm_category';
  /// 结果选项ID（YES/NO 或多结果）
  static const String OUTCOME_ID = 'outcome_id';
  /// 订单ID
  static const String ORDER_ID = 'order_id';
  /// 买卖方向
  static const String ORDER_SIDE = 'order_side';
  /// 价格
  static const String PRICE = 'price';
  /// 份额数量
  static const String SHARES = 'shares';
  /// 资讯文章ID
  static const String AID = 'aid';
  /// 资讯分类
  static const String NEWS_CAT = 'news_cat';
  /// 阅读时长（毫秒）
  static const String READ_DUR = 'read_dur';
  /// 阅读完成度
  static const String READ_PCT = 'read_pct';
  /// 充值金额（cent）
  static const String DEPOSIT_AMT = 'deposit_amt';
  /// 提现金额（cent）
  static const String WITHDRAW_AMT = 'withdraw_amt';
  /// 支付方式
  static const String PAY_METHOD = 'pay_method';
  /// 失败原因码
  static const String FAIL_REASON = 'fail_reason';
}

// AUTO-GENERATED from schema/ by tools/codegen/generate.py — DO NOT EDIT.

/// Registry constants for `BizEvents`.
class BizEvents {
  BizEvents._();

  /// 开始播放（首帧渲染成功）
  static const String VIDEO_PLAY_START = 'video_play_start';
  /// 播放心跳（每30s + 暂停/退出时补报）
  static const String VIDEO_PLAY_HEARTBEAT = 'video_play_heartbeat';
  /// 播放结束（退出/切集/播完）
  static const String VIDEO_PLAY_END = 'video_play_end';
  /// 播放失败
  static const String VIDEO_PLAY_ERROR = 'video_play_error';
  /// 加入注单（点击赔率后成功加入）
  static const String BET_SLIP_ADD = 'bet_slip_add';
  /// 提交投注（客户端意图，点击确认后发起请求）
  static const String BET_SUBMIT = 'bet_submit';
  /// 投注成功（服务端事实，事务提交后发出）
  static const String BET_PLACED = 'bet_placed';
  /// 投注被拒（风控/赔率变化/余额不足）
  static const String BET_REJECTED = 'bet_rejected';
  /// 注单结算（服务端事实）
  static const String BET_SETTLED = 'bet_settled';
  /// 提交订单（客户端意图）
  static const String PM_ORDER_SUBMIT = 'pm_order_submit';
  /// 订单成交（服务端事实）
  static const String PM_ORDER_FILLED = 'pm_order_filled';
  /// 订单撤销
  static const String PM_ORDER_CANCELLED = 'pm_order_cancelled';
  /// 市场结算（出结果）
  static const String PM_MARKET_RESOLVED = 'pm_market_resolved';
  /// 文章阅读结算（离开文章页时一次性上报）
  static const String NEWS_READ = 'news_read';
  /// 注册成功
  static const String SIGNUP_COMPLETED = 'signup_completed';
  /// 充值到账（服务端事实）
  static const String DEPOSIT_COMPLETED = 'deposit_completed';
  /// 充值失败
  static const String DEPOSIT_FAILED = 'deposit_failed';
  /// 提现完成（服务端事实）
  static const String WITHDRAW_COMPLETED = 'withdraw_completed';
}
