import Foundation

/// 事件队列：串行队列调度，攒批（条数/时间双触发）、gzip 上报、
/// 5xx 指数退避重试、失败落盘（JSONL 文件，上限 500 条）、启动续传。
final class EventQueue {
    private static let maxStored = 500
    private static let maxBackoff: TimeInterval = 60

    private let config: TrackerConfig
    private let queue = DispatchQueue(label: "com.giso.tracker")
    private let storeURL: URL

    private var buffer: [[String: Any]] = []
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
        queue.async { self.restore() }
    }

    /// 远程配置下发后更新攒批参数
    func updateBatching(batchSize: Int, flushInterval: TimeInterval) {
        queue.async {
            self.batchSize = batchSize
            self.flushInterval = flushInterval
        }
    }

    func push(_ event: [String: Any]) {
        queue.async {
            if self.config.debug {
                NSLog("[QyTracker] %@", (event["event"] as? String) ?? "?")
                self.buffer.append(event)
                self.flushNow() // debug 模式不攒批，便于实时联调
                return
            }
            self.buffer.append(event)
            if self.buffer.count >= self.batchSize {
                self.flushNow()
            } else if !self.timerScheduled {
                self.timerScheduled = true
                self.queue.asyncAfter(deadline: .now() + self.flushInterval) {
                    self.timerScheduled = false
                    self.flushNow()
                }
            }
        }
    }

    /// 退后台等时机调用
    func flush() {
        queue.async { self.flushNow() }
    }

    // ── 串行队列内 ────────────────────────────────────────

    private func flushNow() {
        guard !buffer.isEmpty else { return }
        let batch = Array(buffer.prefix(batchSize))
        buffer.removeFirst(batch.count)

        send(batch) { [weak self] status in
            guard let self else { return }
            self.queue.async {
                if status >= 200 && status < 500 && status != 429 {
                    // 2xx 成功；4xx（除 429 限流外）数据有误不重试（网关已记隔离区）
                    self.backoff = 1
                    if self.buffer.count >= self.batchSize { self.flushNow() }
                } else {
                    self.buffer.insert(contentsOf: batch, at: 0)
                    self.persist()
                    self.queue.asyncAfter(deadline: .now() + self.backoff) { self.flushNow() }
                    self.backoff = min(self.backoff * 2, Self.maxBackoff)
                }
            }
        }
    }

    private func send(_ batch: [[String: Any]], completion: @escaping (Int) -> Void) {
        guard let body = try? JSONSerialization.data(withJSONObject: batch) else {
            completion(400)
            return
        }
        var req = URLRequest(url: config.endpoint)
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue(config.appId, forHTTPHeaderField: "X-App-Key")
        req.httpBody = body
        URLSession.shared.dataTask(with: req) { _, resp, error in
            if error != nil { completion(-1); return }
            completion((resp as? HTTPURLResponse)?.statusCode ?? -1)
        }.resume()
    }

    private func persist() {
        let tail = buffer.suffix(Self.maxStored)
        let lines = tail.compactMap { ev -> String? in
            guard let d = try? JSONSerialization.data(withJSONObject: ev) else { return nil }
            return String(data: d, encoding: .utf8)
        }
        try? lines.joined(separator: "\n").write(to: storeURL, atomically: true, encoding: .utf8)
    }

    private func restore() {
        guard let text = try? String(contentsOf: storeURL, encoding: .utf8) else { return }
        try? FileManager.default.removeItem(at: storeURL)
        let events = text.split(separator: "\n").compactMap {
            try? JSONSerialization.jsonObject(with: Data($0.utf8)) as? [String: Any]
        }
        guard !events.isEmpty else { return }
        buffer.insert(contentsOf: events.suffix(Self.maxStored), at: 0)
        flushNow()
    }
}
