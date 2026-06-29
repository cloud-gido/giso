// swift-tools-version:5.9
import PackageDescription

let package = Package(
    name: "GISOTracker",
    platforms: [.iOS(.v13)],
    products: [
        .library(name: "GISOTracker", targets: ["GISOTracker"])
    ],
    targets: [
        .target(name: "GISOTracker", path: "Sources/GISOTracker")
    ]
)
