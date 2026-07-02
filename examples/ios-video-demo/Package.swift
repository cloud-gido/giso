// swift-tools-version: 5.9
import PackageDescription

let package = Package(
    name: "GisoVideoDemo",
    platforms: [.iOS(.v15)],
    dependencies: [
        .package(path: "../../sdk/ios")
    ],
    targets: [
        .executableTarget(
            name: "GisoVideoDemo",
            dependencies: [
                .product(name: "GISOTracker", package: "GISOTracker")
            ],
            path: "Sources"
        )
    ]
)
