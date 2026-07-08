package com.giso.gateway;

import com.giso.gateway.screenshot.LocalScreenshotBackend;
import com.giso.gateway.screenshot.S3ScreenshotBackend;
import com.giso.gateway.screenshot.ScreenshotBackend;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 注册表预览图：本地目录或 S3（多副本），通过 /admin/screenshots/... 由 Gateway 回源。
 */
public final class ScreenshotStore {
    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private final ScreenshotBackend backend;

    private ScreenshotStore(ScreenshotBackend backend) {
        this.backend = backend;
    }

    public static ScreenshotStore create(GatewayConfig config) throws IOException {
        return new ScreenshotStore(resolveBackend(config));
    }

    static ScreenshotBackend resolveBackend(GatewayConfig config) throws IOException {
        String mode = config.screenshotsBackend == null ? "auto" : config.screenshotsBackend.trim().toLowerCase();
        boolean useS3 = switch (mode) {
            case "s3" -> true;
            case "local" -> false;
            default -> config.s3Bucket != null && !config.s3Bucket.isBlank();
        };
        if (useS3) {
            return new S3ScreenshotBackend(config);
        }
        String dir = config.screenshotsDir;
        if (dir == null || dir.isBlank()) {
            dir = Path.of(config.fileDir, "screenshots").toString();
        }
        Files.createDirectories(Path.of(dir));
        return new LocalScreenshotBackend(Path.of(dir));
    }

    /** 保存上传文件，返回管理台可访问路径（如 /admin/screenshots/default/uuid.png）。 */
    public String save(String spaceKey, String originalName, byte[] data) throws IOException {
        return backend.save(spaceKey, originalName, data);
    }

    public Loaded loadRelative(String rel) throws IOException {
        return backend.loadRelative(rel);
    }

    public String backendName() {
        return backend.name();
    }

    public record Loaded(byte[] bytes, String contentType) { }
}
