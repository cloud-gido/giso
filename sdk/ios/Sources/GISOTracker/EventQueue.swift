import Foundation

/// 事件队列：与 Android 对齐的生命周期约定——
/// 冷启动 spill 延后到首次 foreground 之后；退后台排空；再入前台先清残留再发 foreground。
final class EventQueue {
    private static let maxStored = 500
    private static let maxBackoff: TimeInterval = 60

    private let config: TrackerConfig
    private let queue = DispatchQueue(label: "com.giso.tracker")
    private let storeURL: URL

    private var buffer: [[String: Any]] = []
    private var spill: [[String: Any]] = []
    private var spillReleased = false
    private var timerScheduled = false
    private var backoff: TimeInterval = 1
    private var batchSize: Int
    private var flushInterval: TimeInterval

    init(config: TrackerConfig) {
        self.config = config
        self.batchSize = config.batchSize
        self.flushInterval = config.flushInterval
        let dir = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        storeURL = dir.appendingPathComponent("giso_tracker_queue.jsonl")
        queue.async { self.loadSpillFromDisk() }
    }

    func updateBatching(batchSize: Int, flushInterval: TimeInterval) {
        queue.async {
            self.batchSize = batchSize
            self.flushInterval = flushInterval
        }
    }

    func push(_ event: [String: Any]) {
        queue.async {
            self.buffer.append(event)
            self.maybeFlush(urgent: false)
        }
    }

    /// 进入前台：排空残留 → foreground → 释放冷启动 spill
    func onForeground(_ event: [String: Any], timeout: TimeInterval = 2.5) {
        runBlocking(timeout: timeout) {
            self.flushAll(urgent: false)
            self.buffer.append(event)
            self.flushAll(urgent: false)
            self.releaseSpill()
        }
    }

    /// 退后台：先写不足一个周期的心跳，再写 background，落盘并排空
    func onBackground(_ event: [String: Any], preceding: [[String: Any]] = [],
                      timeout: TimeInterval = 2.5) {
        runBlocking(timeout: timeout) {
            self.cancelTimer()
            self.buffer.append(contentsOf: preceding)
            self.buffer.append(event)
            self.persistAll()
            self.flushAll(urgent: true)
        }
    }

    func flush() {
        queue.async { self.flushAll(urgent: false) }
    }

    // ── 串行队列内 ────────────────────────────────────────

    private func loadSpillFromDisk() {
        guard let text = try? String(contentsOf: storeURL, encoding: .utf8) else { return }
        try? FileManager.default.removeItem(at: storeURL)
        let events = text.split(separator: "\n").compactMap {
            try? JSONSerialization.jsonObject(with: Data($0.utf8)) as? [String: Any]
        }
        spill = Array(events.suffix(Self.maxStored))
        if !spill.isEmpty { persistAll() }
    }

    private func releaseSpill() {
        guard !spillReleased else { return }
        spillReleased = true
        while !spill.isEmpty {
            let n = min(batchSize, spill.count)
            let batch = Array(spill.prefix(n))
            spill.removeFirst(n)
            let status = sendSync(batch)
            if isAccept(status) {
                backoff = 1
            } else {
                spill.insert(contentsOf: batch, at: 0)
                persistAll()
                scheduleRetry()
                return
            }
        }
        persistAll()
    }

    private func maybeFlush(urgent: Bool) {
        if urgent || config.debug {
            flushAll(urgent: urgent)
            return
        }
        if buffer.count >= batchSize {
            flushAll(urgent: false)
        } else if !timerScheduled {
            timerScheduled = true
            queue.asyncAfter(deadline: .now() + flushInterval) { [weak self] in
                guard let self else { return }
                self.timerScheduled = false
                self.flushAll(urgent: false)
            }
        }
    }

    private func flushAll(urgent: Bool) {
        cancelTimer()
        while !buffer.isEmpty {
            let n = min(batchSize, buffer.count)
            let batch = Array(buffer.prefix(n))
            buffer.removeFirst(n)
            let status = sendSync(batch)
            if isAccept(status) {
                backoff = 1
            } else {
                buffer.insert(contentsOf: batch, at: 0)
                persistAll()
                if !urgent { scheduleRetry() }
                return
            }
        }
        if spill.isEmpty {
            try? FileManager.default.removeItem(at: storeURL)
        } else {
            persistAll()
        }
    }

    private func scheduleRetry() {
        let delay = backoff
        backoff = min(backoff * 2, Self.maxBackoff)
        queue.asyncAfter(deadline: .now() + delay) { [weak self] in
            guard let self else { return }
            self.flushAll(urgent: false)
            if self.spillReleased && !self.spill.isEmpty { self.releaseSpill() }
        }
    }

    private func cancelTimer() {
        // 简化：用 timerScheduled 门闩；已调度的 block 会自检 buffer
        timerScheduled = false
    }

    private func isAccept(_ status: Int) -> Bool {
        status >= 200 && status < 500 && status != 429
    }

    private func sendSync(_ batch: [[String: Any]]) -> Int {
        guard let body = try? JSONSerialization.data(withJSONObject: batch) else { return 400 }
        var req = URLRequest(url: config.endpoint)
        req.httpMethod = "POST"
        req.timeoutInterval = 8
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue(config.appId, forHTTPHeaderField: "X-App-Key")
        req.httpBody = body

        let sem = DispatchSemaphore(value: 0)
        var status = -1
        URLSession.shared.dataTask(with: req) { _, resp, error in
            if error == nil {
                status = (resp as? HTTPURLResponse)?.statusCode ?? -1
            }
            sem.signal()
        }.resume()
        _ = sem.wait(timeout: .now() + 8)
        return status
    }

    private func persistAll() {
        if spill.isEmpty && buffer.isEmpty {
            try? FileManager.default.removeItem(at: storeURL)
            return
        }
        var lines: [String] = []
        for ev in spill {
            if lines.count >= Self.maxStored { break }
            if let d = try? JSONSerialization.data(withJSONObject: ev),
               let s = String(data: d, encoding: .utf8) {
                lines.append(s)
            }
        }
        for ev in buffer {
            if lines.count >= Self.maxStored { break }
            if let d = try? JSONSerialization.data(withJSONObject: ev),
               let s = String(data: d, encoding: .utf8) {
                lines.append(s)
            }
        }
        try? lines.joined(separator: "\n").write(to: storeURL, atomically: true, encoding: .utf8)
    }

    private func runBlocking(timeout: TimeInterval, _ work: @escaping () -> Void) {
        let sem = DispatchSemaphore(value: 0)
        queue.async {
            work()
            sem.signal()
        }
        _ = sem.wait(timeout: .now() + max(0.5, timeout))
    }
}
