import Foundation
import UIKit

/// 公共参数采集：did 自生成持久化；session 前后台间隔 30 分钟重开。
final class CommonParams {
    private static let sdkVersion = "1.0.0"
    private static let didKey = "giso_tracker_did"
    private static let sessionGap: TimeInterval = 30 * 60

    private let config: TrackerConfig
    private let did: String

    private var uid = ""
    private var sessionId = ""
    private var lastActiveTs = Date.distantPast

    init(config: TrackerConfig) {
        self.config = config
        if let saved = UserDefaults.standard.string(forKey: Self.didKey) {
            did = saved
        } else {
            did = UUID().uuidString.lowercased()
            UserDefaults.standard.set(did, forKey: Self.didKey)
        }
        renewSession()
    }

    func setUid(_ uid: String) { self.uid = uid }

    /// 进前台时调用：超过会话间隔则重开 session
    func onForeground() {
        if Date().timeIntervalSince(lastActiveTs) > Self.sessionGap { renewSession() }
        lastActiveTs = Date()
    }

    func touch() { lastActiveTs = Date() }

    private func renewSession() {
        sessionId = "s-" + UUID().uuidString.lowercased()
        lastActiveTs = Date()
    }

    func snapshot() -> [String: Any] {
        let screen = UIScreen.main.nativeBounds
        let tzSec = TimeZone.current.secondsFromGMT()
        let sign = tzSec >= 0 ? "+" : "-"
        let tz = String(format: "%@%02d:%02d", sign, abs(tzSec) / 3600, abs(tzSec) % 3600 / 60)
        return [
            "app_id": config.appId,
            "platform": "ios",
            "app_vrsn": config.appVersion,
            "sdk_vrsn": Self.sdkVersion,
            "did": did,
            "uid": uid,
            "session_id": sessionId,
            "channel": config.channel,
            "env": config.env ?? (config.debug ? "test" : "prod"),
            "os_vrsn": UIDevice.current.systemVersion,
            "dev_brand": "Apple",
            "dev_model": deviceModel(),
            "screen_res": "\(Int(screen.width))x\(Int(screen.height))",
            "net_type": "unknown",
            "lang": Locale.preferredLanguages.first ?? "",
            "tz": tz,
        ]
    }

    private func deviceModel() -> String {
        var info = utsname()
        uname(&info)
        return withUnsafeBytes(of: &info.machine) { ptr in
            String(cString: ptr.baseAddress!.assumingMemoryBound(to: CChar.self))
        }
    }
}
