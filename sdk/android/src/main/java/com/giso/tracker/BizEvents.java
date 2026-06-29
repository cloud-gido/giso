// AUTO-GENERATED from schema/ by tools/codegen/generate.py — DO NOT EDIT.
package com.giso.tracker;

public final class BizEvents {
    private BizEvents() { }

    /** 开始播放（首帧渲染成功） */
    public static final String VIDEO_PLAY_START = "video_play_start";
    /** 播放心跳（每30s + 暂停/退出时补报） */
    public static final String VIDEO_PLAY_HEARTBEAT = "video_play_heartbeat";
    /** 播放结束（退出/切集/播完） */
    public static final String VIDEO_PLAY_END = "video_play_end";
    /** 播放失败 */
    public static final String VIDEO_PLAY_ERROR = "video_play_error";
    /** 加入注单（点击赔率后成功加入） */
    public static final String BET_SLIP_ADD = "bet_slip_add";
    /** 提交投注（客户端意图，点击确认后发起请求） */
    public static final String BET_SUBMIT = "bet_submit";
    /** 投注成功（服务端事实，事务提交后发出） */
    public static final String BET_PLACED = "bet_placed";
    /** 投注被拒（风控/赔率变化/余额不足） */
    public static final String BET_REJECTED = "bet_rejected";
    /** 注单结算（服务端事实） */
    public static final String BET_SETTLED = "bet_settled";
    /** 提交订单（客户端意图） */
    public static final String PM_ORDER_SUBMIT = "pm_order_submit";
    /** 订单成交（服务端事实） */
    public static final String PM_ORDER_FILLED = "pm_order_filled";
    /** 订单撤销 */
    public static final String PM_ORDER_CANCELLED = "pm_order_cancelled";
    /** 市场结算（出结果） */
    public static final String PM_MARKET_RESOLVED = "pm_market_resolved";
    /** 文章阅读结算（离开文章页时一次性上报） */
    public static final String NEWS_READ = "news_read";
    /** 注册成功 */
    public static final String SIGNUP_COMPLETED = "signup_completed";
    /** 充值到账（服务端事实） */
    public static final String DEPOSIT_COMPLETED = "deposit_completed";
    /** 充值失败 */
    public static final String DEPOSIT_FAILED = "deposit_failed";
    /** 提现完成（服务端事实） */
    public static final String WITHDRAW_COMPLETED = "withdraw_completed";
}
