import Foundation

/// SDK 配置。曝光阈值等口径参数集中于此，与 Android/Web 端一致。
public struct TrackerConfig {
    public let appId: String
    public let appVersion: String
    public let endpoint: URL
    public var channel: String = ""
    public var debug: Bool = false
    /// prod | test；未设置时 debug=true → test
    public var env: String? = nil

    /// 曝光判定：可视面积比例阈值
    public var exposureRatio: Double = 0.5
    /// 曝光判定：持续时长（秒）
    public var exposureDuration: TimeInterval = 0.5
    /// 同一元素实例单次页面进入内最大曝光次数
    public var exposureMaxPerPage: Int = 3
    /// 攒批条数
    public var batchSize: Int = 20
    /// 攒批最大间隔（秒）
    public var flushInterval: TimeInterval = 15
    /// 前台应用心跳间隔（秒），默认 60
    public var heartbeatInterval: TimeInterval = 60

    public init(appId: String, appVersion: String, endpoint: URL) {
        self.appId = appId
        self.appVersion = appVersion
        self.endpoint = endpoint
    }
}

/// 元素声明：eid 必须在元素池登记，params 自动继承给子视图。
public struct ElementMeta {
    public let eid: String
    public let pos: Int?
    public let params: [String: Any]?
    /// 后台下发的透传参数包（推荐 trace 等），端上不理解内容，原样上报；随 params 同规则继承
    public let pt: [String: Any]?

    public init(eid: String, pos: Int? = nil, params: [String: Any]? = nil, pt: [String: Any]? = nil) {
        self.eid = eid
        self.pos = pos
        self.params = params
        self.pt = pt
    }
}
