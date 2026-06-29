import Foundation
import UIKit

/// 曝光监测：CADisplayLink 低频轮询可视比例。
/// 口径与 Android/Web 一致：可视面积 ≥ ratio 持续 ≥ duration 记一次；
/// 滚出（可视 < 20%）后可重记；单次页面进入内每实例最多 maxPerPage 次。
final class ExposureTracker {
    private static let exitRatio = 0.2
    private static let pollInterval: TimeInterval = 0.25

    private var ratio: Double
    private var duration: TimeInterval
    private var maxPerPage: Int
    private let onExposure: (UIView, Int, Double) -> Void  // (view, durMs, maxRatio)

    private final class Pending {
        var enterTs = Date()
        var maxRatio = 0.0
    }

    private var observed = NSHashTable<UIView>.weakObjects()
    private var pending = [ObjectIdentifier: Pending]()
    private var counts = [ObjectIdentifier: Int]()
    private var timer: Timer?

    init(ratio: Double, duration: TimeInterval, maxPerPage: Int,
         onExposure: @escaping (UIView, Int, Double) -> Void) {
        self.ratio = ratio
        self.duration = duration
        self.maxPerPage = maxPerPage
        self.onExposure = onExposure
    }

    func observe(_ view: UIView) {
        observed.add(view)
        startTimerIfNeeded()
    }

    /// 远程配置下发后更新口径
    func updateThresholds(ratio: Double, duration: TimeInterval, maxPerPage: Int) {
        self.ratio = ratio
        self.duration = duration
        self.maxPerPage = maxPerPage
    }

    /// 页面切换时调用：重置每页曝光计数
    func resetPage() {
        counts.removeAll()
        pending.removeAll()
    }

    private func startTimerIfNeeded() {
        guard timer == nil else { return }
        timer = Timer.scheduledTimer(withTimeInterval: Self.pollInterval, repeats: true) { [weak self] _ in
            self?.poll()
        }
    }

    private func poll() {
        for view in observed.allObjects {
            let id = ObjectIdentifier(view)
            let r = Self.visibleRatio(view)
            if r >= ratio {
                if let p = pending[id] {
                    p.maxRatio = max(p.maxRatio, r)
                    if Date().timeIntervalSince(p.enterTs) >= duration {
                        pending.removeValue(forKey: id)
                        let count = counts[id] ?? 0
                        if count < maxPerPage {
                            counts[id] = count + 1
                            let durMs = Int(Date().timeIntervalSince(p.enterTs) * 1000)
                            onExposure(view, durMs, (p.maxRatio * 100).rounded() / 100)
                        }
                    }
                } else {
                    let p = Pending()
                    p.maxRatio = r
                    pending[id] = p
                }
            } else if r < Self.exitRatio {
                pending.removeValue(forKey: id)
            }
        }
    }

    private static func visibleRatio(_ view: UIView) -> Double {
        guard let window = view.window, !view.isHidden, view.alpha > 0.01 else { return 0 }
        let frame = view.convert(view.bounds, to: window)
        let visible = frame.intersection(window.bounds)
        guard !visible.isNull, frame.width > 0, frame.height > 0 else { return 0 }
        return Double((visible.width * visible.height) / (frame.width * frame.height))
    }
}
