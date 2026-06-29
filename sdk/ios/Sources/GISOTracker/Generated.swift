// AUTO-GENERATED from schema/ by tools/codegen/generate.py — DO NOT EDIT.

public enum Pages {
    /// 首页（四业务线聚合入口）
    public static let home = "home"
    /// 搜索页（含搜索结果）
    public static let search = "search"
    /// 我的/个人中心
    public static let mine = "mine"
    /// 登录/注册页
    public static let login = "login"
    /// 钱包页（充值提现入口）
    public static let wallet = "wallet"
    /// 充值页
    public static let deposit = "deposit"
    /// 提现页
    public static let withdraw = "withdraw"
    /// 视频推荐流页
    public static let videoFeed = "video_feed"
    /// 视频详情/播放页
    public static let videoDetail = "video_detail"
    /// 剧集合集页
    public static let videoSeries = "video_series"
    /// 博彩大厅（赛事列表）
    public static let betLobby = "bet_lobby"
    /// 赛事详情页（盘口列表）
    public static let betMatchDetail = "bet_match_detail"
    /// 注单确认页/弹层
    public static let betSlip = "bet_slip"
    /// 我的注单页
    public static let betHistory = "bet_history"
    /// 预测市场大厅
    public static let pmLobby = "pm_lobby"
    /// 市场详情页（K线/订单簿/持仓）
    public static let pmMarketDetail = "pm_market_detail"
    /// 我的持仓页
    public static let pmPortfolio = "pm_portfolio"
    /// 资讯流页
    public static let newsFeed = "news_feed"
    /// 资讯文章详情页
    public static let newsArticle = "news_article"
}

public enum Elements {
    /// 运营banner位
    public static let banner = "banner"
    /// 搜索框
    public static let searchBox = "search_box"
    /// 底部导航页签
    public static let bottomTab = "bottom_tab"
    /// 登录按钮
    public static let loginBtn = "login_btn"
    /// 充值按钮
    public static let depositBtn = "deposit_btn"
    /// 分享按钮（继承所在模块参数）
    public static let shareBtn = "share_btn"
    /// 视频卡片（封面+标题+CP信息）
    public static let videoCard = "video_card"
    /// 播放按钮
    public static let playBtn = "play_btn"
    /// 点赞按钮
    public static let likeBtn = "like_btn"
    /// CP头像
    public static let cpAvatar = "cp_avatar"
    /// 选集项
    public static let episodeItem = "episode_item"
    /// 全屏按钮
    public static let fullscreenBtn = "fullscreen_btn"
    /// 赛事卡片（队伍+比分+主要盘口）
    public static let matchCard = "match_card"
    /// 赔率按钮（点击即加入注单）
    public static let oddsBtn = "odds_btn"
    /// 确认投注按钮
    public static let betConfirmBtn = "bet_confirm_btn"
    /// 运动项目页签
    public static let sportTab = "sport_tab"
    /// 预测市场卡片（题目+当前概率）
    public static let marketCard = "market_card"
    /// 结果选项按钮（YES/NO/多结果）
    public static let outcomeBtn = "outcome_btn"
    /// 下单确认按钮
    public static let orderConfirmBtn = "order_confirm_btn"
    /// 资讯卡片
    public static let articleCard = "article_card"
    /// 相关推荐文章项
    public static let relatedArticle = "related_article"
}

public enum Params {
    /// 元素在所在模块中的位置
    public static let pos = "pos"
    /// 搜索词
    public static let kw = "kw"
    /// 页签名
    public static let tabName = "tab_name"
    /// 上报环境
    public static let env = "env"
    /// 推荐请求追踪ID
    public static let recTraceId = "rec_trace_id"
    /// 货币
    public static let currency = "currency"
    /// 获客渠道
    public static let channel = "channel"
    /// 视频ID
    public static let vid = "vid"
    /// 创作者/内容提供方ID
    public static let cpId = "cp_id"
    /// 剧集ID（多集内容的合集）
    public static let seriesId = "series_id"
    /// 集数
    public static let epNum = "ep_num"
    /// 播放时长
    public static let playDur = "play_dur"
    /// 视频总时长（毫秒）
    public static let videoDur = "video_dur"
    /// 当前播放进度（毫秒）
    public static let playPos = "play_pos"
    /// 是否自动播放
    public static let isAuto = "is_auto"
    /// 播放倍速
    public static let speed = "speed"
    /// 清晰度
    public static let definition = "definition"
    /// 运动项目ID
    public static let sportId = "sport_id"
    /// 赛事ID
    public static let matchId = "match_id"
    /// 联赛ID
    public static let leagueId = "league_id"
    /// 盘口类型
    public static let marketType = "market_type"
    /// 投注选项ID
    public static let selectionId = "selection_id"
    /// 赔率
    public static let odds = "odds"
    /// 投注金额
    public static let stakeAmt = "stake_amt"
    /// 注单ID
    public static let betId = "bet_id"
    /// 注单类型
    public static let betType = "bet_type"
    /// 是否滚球（赛中投注）
    public static let isLive = "is_live"
    /// 派彩金额（cent）
    public static let payoutAmt = "payout_amt"
    /// 预测市场ID
    public static let pmMarketId = "pm_market_id"
    /// 市场类目
    public static let pmCategory = "pm_category"
    /// 结果选项ID（YES/NO 或多结果）
    public static let outcomeId = "outcome_id"
    /// 订单ID
    public static let orderId = "order_id"
    /// 买卖方向
    public static let orderSide = "order_side"
    /// 价格
    public static let price = "price"
    /// 份额数量
    public static let shares = "shares"
    /// 资讯文章ID
    public static let aid = "aid"
    /// 资讯分类
    public static let newsCat = "news_cat"
    /// 阅读时长（毫秒）
    public static let readDur = "read_dur"
    /// 阅读完成度
    public static let readPct = "read_pct"
    /// 充值金额（cent）
    public static let depositAmt = "deposit_amt"
    /// 提现金额（cent）
    public static let withdrawAmt = "withdraw_amt"
    /// 支付方式
    public static let payMethod = "pay_method"
    /// 失败原因码
    public static let failReason = "fail_reason"
}

public enum BizEvents {
    /// 开始播放（首帧渲染成功）
    public static let videoPlayStart = "video_play_start"
    /// 播放心跳（每30s + 暂停/退出时补报）
    public static let videoPlayHeartbeat = "video_play_heartbeat"
    /// 播放结束（退出/切集/播完）
    public static let videoPlayEnd = "video_play_end"
    /// 播放失败
    public static let videoPlayError = "video_play_error"
    /// 加入注单（点击赔率后成功加入）
    public static let betSlipAdd = "bet_slip_add"
    /// 提交投注（客户端意图，点击确认后发起请求）
    public static let betSubmit = "bet_submit"
    /// 投注成功（服务端事实，事务提交后发出）
    public static let betPlaced = "bet_placed"
    /// 投注被拒（风控/赔率变化/余额不足）
    public static let betRejected = "bet_rejected"
    /// 注单结算（服务端事实）
    public static let betSettled = "bet_settled"
    /// 提交订单（客户端意图）
    public static let pmOrderSubmit = "pm_order_submit"
    /// 订单成交（服务端事实）
    public static let pmOrderFilled = "pm_order_filled"
    /// 订单撤销
    public static let pmOrderCancelled = "pm_order_cancelled"
    /// 市场结算（出结果）
    public static let pmMarketResolved = "pm_market_resolved"
    /// 文章阅读结算（离开文章页时一次性上报）
    public static let newsRead = "news_read"
    /// 注册成功
    public static let signupCompleted = "signup_completed"
    /// 充值到账（服务端事实）
    public static let depositCompleted = "deposit_completed"
    /// 充值失败
    public static let depositFailed = "deposit_failed"
    /// 提现完成（服务端事实）
    public static let withdrawCompleted = "withdraw_completed"
}
