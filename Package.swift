// swift-tools-version:5.9
// 对外 SwiftPM 入口（monorepo 根目录）。
// 外部团队通过固定 tag 引用，无需访问业务源码；后续可改为 binaryTarget XCFramework。
import PackageDescription

let package = Package(
    name: "GISOTracker",
    platforms: [.iOS(.v13)],
    products: [
        .library(name: "GISOTracker", targets: ["GISOTracker"])
    ],
    targets: [
        .target(name: "GISOTracker", path: "sdk/ios/Sources/GISOTracker")
    ]
)
