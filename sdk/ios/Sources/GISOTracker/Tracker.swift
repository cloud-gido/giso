import Foundation
import UIKit

/// iOS 埋点 SDK 门面（单例）。与 Android/Web 端同协议、同口径。
///
/// 事件收敛：10 个标准事件全部由 SDK 控制触发时机——
/// 生命周期（含前台 app_heartbeat）、页面、元素曝光/点击。
public final class Tracker {
    public static private(set) var shared: Tracker!
    private static let heartbeatMin: TimeInterval = 15
    private static let heartbeatMax: TimeInterval = 300

    private let config: TrackerConfig
    private let common: CommonParams
    private let queue: EventQueue
    private let exposure: ExposureTracker

    /// view → 元素声明；参数继承沿视图树向上查找
    private let metas = NSMapTable<UIView, AnyObject>.weakToStrongObjects()

    private var curPgid = ""
    private var curPgParams: [String: Any]?
    private var curPgPt: [String: Any]?
    private var pageEnterTs = Date()
    private var refPgid = ""
    private var refEid = ""
    private var foregroundTs = Date()
    private var heartbeatInterval: TimeInterval
    private var lastHeartbeatTs = Date()
    private var heartbeatTimer: Timer?

    @discardableResult
    public static func initialize(config: TrackerConfig) -> Tracker {
        if shared == nil { shared = Tracker(config: config) }
        return shared
    }

    private init(config: TrackerConfig) {
        self.config = config
        self.heartbeatInterval = Self.clampHeartbeat(config.heartbeatInterval)
        self.common = CommonParams(config: config)
        self.queue = EventQueue(config: config)

        var exposureCallback: ((UIView, Int, Double) -> Void)!
        self.exposure = ExposureTracker(
            ratio: config.exposureRatio,
            duration: config.exposureDuration,
            maxPerPage: config.exposureMaxPerPage,
            onExposure: { view, dur, ratio in exposureCallback(view, dur, ratio) })
        exposureCallback = { [weak self] view, dur, ratio in
            self?.onExposure(view: view, durMs: dur, maxRatio: ratio)
        }

        registerLifecycle()
        trackInstallAndLaunch()
        fetchRemoteConfig()
    }

    /// 拉取服务端口径配置（/v1/config），失败静默沿用本地默认值
    private func fetchRemoteConfig() {
        let urlStr = config.endpoint.absoluteString
            .replacingOccurrences(of: "/v1/track", with: "/v1/config")
        guard urlStr != config.endpoint.absoluteString, let url = URL(string: urlStr) else { return }
        URLSession.shared.dataTask(with: url) { [weak self] data, resp, _ in
            guard let self,
                  (resp as? HTTPURLResponse)?.statusCode == 200,
                  let data,
                  let c = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return }
            DispatchQueue.main.async {
                // 曝光轮询在主线程 Timer 上跑，更新口径也回主线程，避免数据竞争
                self.exposure.updateThresholds(
                    ratio: c["exposure_ratio"] as? Double ?? self.config.exposureRatio,
                    duration: (c["exposure_duration_ms"] as? Double).map { $0 / 1000 } ?? self.config.exposureDuration,
                    maxPerPage: c["exposure_max_per_page"] as? Int ?? self.config.exposureMaxPerPage)
            }
            self.queue.updateBatching(
                batchSize: c["batch_size"] as? Int ?? self.config.batchSize,
                flushInterval: (c["flush_interval_ms"] as? Double).map { $0 / 1000 } ?? self.config.flushInterval)
            if let hb = c["heartbeat_interval_ms"] as? Double {
                DispatchQueue.main.async {
                    self.heartbeatInterval = Self.clampHeartbeat(hb / 1000)
                }
            }
        }.resume()
    }

    private static func clampHeartbeat(_ sec: TimeInterval) -> TimeInterval {
        min(max(sec, heartbeatMin), heartbeatMax)
    }

    private func startHeartbeat() {
        stopHeartbeat()
        lastHeartbeatTs = Date()
        heartbeatTimer = Timer.scheduledTimer(withTimeInterval: heartbeatInterval, repeats: true) { [weak self] _ in
            self?.onHeartbeatTick()
        }
    }

    private func stopHeartbeat() {
        heartbeatTimer?.invalidate()
        heartbeatTimer = nil
    }

    private func onHeartbeatTick() {
        let now = Date()
        let dur = Int(now.timeIntervalSince(lastHeartbeatTs) * 1000)
        lastHeartbeatTs = now
        var page = pageContext()
        page["fg_dur"] = max(0, dur)
        emit("app_heartbeat", page: page)
    }

    public func setUid(_ uid: String) { common.setUid(uid) }
    public func clearUid() { common.setUid("") }

    /// 业务设备 ID（历史账号体系兼容）；启动后尽早调用
    public func setBizDid(_ bizDid: String) { common.setBizDid(bizDid) }
    public func clearBizDid() { common.setBizDid("") }

    public func flush() { queue.flush() }

    // ── 页面 ──────────────────────────────────────────────

    /// viewDidAppear 调用
    /// - Parameter pt: 后台下发的透传参数包（如推荐 trace），本页所有事件自动携带
    public func enterPage(_ pgid: String, params: [String: Any]? = nil, pt: [String: Any]? = nil) {
        if !curPgid.isEmpty { exitPage() }
        curPgid = pgid
        curPgParams = params
        curPgPt = pt
        pageEnterTs = Date()
        exposure.resetPage()
        emit("page_enter", page: pageContext(), pt: curPgPt)
    }

    /// viewWillDisappear 调用
    public func exitPage() {
        guard !curPgid.isEmpty else { return }
        var page = pageContext()
        page["pg_stay"] = Int(Date().timeIntervalSince(pageEnterTs) * 1000)
        emit("page_exit", page: page, pt: curPgPt)
        refPgid = curPgid
        curPgid = ""
        curPgParams = nil
        curPgPt = nil
    }

    // ── 元素 ──────────────────────────────────────────────

    /// 声明元素：自动监测曝光与点击。容器（如视频卡 cell）bind 一次参数，子视图自动继承。
    public func bind(_ view: UIView, _ meta: ElementMeta) {
        metas.setObject(MetaBox(meta), forKey: view)
        exposure.observe(view)
        hookTap(view)
    }

    public func unbind(_ view: UIView) {
        metas.removeObject(forKey: view)
    }

    // ── 业务事件 ───────────────────────────────────────────

    /// - Parameter pt: 后台下发的透传参数包，与页面级透传包合并后上报（本次调用优先）
    public func bizEvent(_ code: String, params: [String: Any]? = nil, pt: [String: Any]? = nil) {
        var biz: [String: Any] = ["code": code]
        if let params { biz["params"] = params }
        emit("biz_event", page: pageContext(), biz: biz, pt: mergePt(curPgPt, pt))
    }

    // ── 内部：曝光/点击 ────────────────────────────────────

    private final class MetaBox {
        let meta: ElementMeta
        init(_ meta: ElementMeta) { self.meta = meta }
    }

    private final class TapHook: UITapGestureRecognizer, UIGestureRecognizerDelegate {
        var onTap: (() -> Void)?

        init() {
            super.init(target: nil, action: nil)
            addTarget(self, action: #selector(fire))
            cancelsTouchesInView = false
            delegate = self
        }

        @objc private func fire() { onTap?() }

        // 与业务手势/按钮事件共存，不抢占
        func gestureRecognizer(_ g: UIGestureRecognizer,
                               shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool {
            true
        }
    }

    private func hookTap(_ view: UIView) {
        guard !(view.gestureRecognizers?.contains(where: { $0 is TapHook }) ?? false) else { return }
        let tap = TapHook()
        tap.onTap = { [weak self, weak view] in
            guard let self, let view else { return }
            guard let ctx = self.elementContext(view) else { return }
            self.refEid = (self.metas.object(forKey: view) as? MetaBox)?.meta.eid ?? ""
            self.emit("element_click", page: self.pageContext(), element: ctx.el,
                      pt: self.mergePt(self.curPgPt, ctx.pt))
        }
        view.isUserInteractionEnabled = true
        view.addGestureRecognizer(tap)
    }

    private func onExposure(view: UIView, durMs: Int, maxRatio: Double) {
        guard let ctx = elementContext(view) else { return }
        var el = ctx.el
        el["exp_dur"] = durMs
        el["exp_ratio"] = maxRatio
        emit("element_exposure", page: pageContext(), element: el, pt: mergePt(curPgPt, ctx.pt))
    }

    /// 沿视图树向上收集祖先 bind 元素：参数继承（params 与 pt 同规则）+ mod 推导
    private func elementContext(_ view: UIView) -> (el: [String: Any], pt: [String: Any]?)? {
        guard let box = metas.object(forKey: view) as? MetaBox else { return nil }
        let meta = box.meta

        var chain: [ElementMeta] = []
        var mod: String?
        var p = view.superview
        while let v = p {
            if let b = metas.object(forKey: v) as? MetaBox {
                chain.append(b.meta)
                if mod == nil { mod = b.meta.eid }
            }
            p = v.superview
        }
        // 自根向叶合并，叶子（自身）优先级最高
        var merged: [String: Any] = [:]
        var mergedPt: [String: Any] = [:]
        for m in chain.reversed() {
            if let params = m.params { merged.merge(params) { _, new in new } }
            if let pt = m.pt { mergedPt.merge(pt) { _, new in new } }
        }
        if let params = meta.params { merged.merge(params) { _, new in new } }
        if let pt = meta.pt { mergedPt.merge(pt) { _, new in new } }

        var el: [String: Any] = ["eid": meta.eid]
        if let mod { el["mod"] = mod }
        if let pos = meta.pos { el["pos"] = pos }
        if !merged.isEmpty { el["params"] = merged }
        return (el, mergedPt.isEmpty ? nil : mergedPt)
    }

    /// 合并页面级与元素/调用级透传包（后者优先）；皆空返回 nil
    private func mergePt(_ a: [String: Any]?, _ b: [String: Any]?) -> [String: Any]? {
        var merged = a ?? [:]
        if let b { merged.merge(b) { _, new in new } }
        return merged.isEmpty ? nil : merged
    }

    // ── 内部：生命周期 ─────────────────────────────────────

    private func trackInstallAndLaunch() {
        let key = "giso_tracker_activated"
        if !UserDefaults.standard.bool(forKey: key) {
            UserDefaults.standard.set(true, forKey: key)
            emit("app_install")
        }
        emit("app_launch")
    }

    private func registerLifecycle() {
        let nc = NotificationCenter.default
        nc.addObserver(forName: UIApplication.didBecomeActiveNotification, object: nil, queue: .main) { [weak self] _ in
            guard let self else { return }
            self.common.onForeground()
            self.foregroundTs = Date()
            var ev: [String: Any] = [
                "event": "app_foreground",
                "log_id": UUID().uuidString.lowercased(),
                "ctime": Int(Date().timeIntervalSince1970 * 1000),
                "common": self.common.snapshot(),
            ]
            self.queue.onForeground(ev)
            self.startHeartbeat()
        }
        nc.addObserver(forName: UIApplication.didEnterBackgroundNotification, object: nil, queue: .main) { [weak self] _ in
            guard let self else { return }
            self.stopHeartbeat()
            var page = self.pageContext()
            page["fg_dur"] = Int(Date().timeIntervalSince(self.foregroundTs) * 1000)
            var ev: [String: Any] = [
                "event": "app_background",
                "log_id": UUID().uuidString.lowercased(),
                "ctime": Int(Date().timeIntervalSince1970 * 1000),
                "common": self.common.snapshot(),
                "page": page,
            ]
            self.queue.onBackground(ev)
        }
    }

    // ── 内部：发送 ─────────────────────────────────────────

    private func pageContext() -> [String: Any] {
        var o: [String: Any] = ["pgid": curPgid]
        if let p = curPgParams, !p.isEmpty { o["pg_params"] = p }
        if !refPgid.isEmpty { o["ref_pgid"] = refPgid }
        if !refEid.isEmpty { o["ref_eid"] = refEid }
        return o
    }

    private func emit(_ event: String, page: [String: Any]? = nil,
                      element: [String: Any]? = nil, biz: [String: Any]? = nil,
                      pt: [String: Any]? = nil) {
        var ev: [String: Any] = [
            "event": event,
            "log_id": UUID().uuidString.lowercased(),
            "ctime": Int(Date().timeIntervalSince1970 * 1000),
            "common": common.snapshot(),
        ]
        if let page { ev["page"] = page }
        if let element { ev["element"] = element }
        if let biz { ev["biz"] = biz }
        if let pt, !pt.isEmpty { ev["pt"] = pt }
        queue.push(ev)
    }
}
