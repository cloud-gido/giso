package com.giso.gateway;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** 注册表预览图：按空间存于本地目录，通过 /admin/screenshots/... 访问。 */
public final class ScreenshotStore {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "jpeg", "webp", "gif");
    private static final Pattern SAFE_SPACE = Pattern.compile("[^a-zA-Z0-9_-]");
    private static final Map<String, String> MIME = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp",
            "gif", "image/gif");

    private final Path root;

    public ScreenshotStore(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    public static ScreenshotStore create(GatewayConfig config) throws IOException {
        String dir = config.screenshotsDir;
        if (dir == null || dir.isBlank()) {
            dir = Path.of(config.fileDir, "screenshots").toString();
        }
        return new ScreenshotStore(Path.of(dir));
    }

    /** 保存上传文件，返回管理台可访问路径（如 /admin/screenshots/default/uuid.png）。 */
    public String save(String spaceKey, String originalName, byte[] data) throws IOException {
        if (data == null || data.length == 0) throw new IllegalArgumentException("空文件");
        if (data.length > MAX_BYTES) {
            throw new IllegalArgumentException("图片不能超过 " + (MAX_BYTES / 1024 / 1024) + "MB");
        }
        String ext = extension(originalName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException("仅支持 png/jpg/webp/gif");
        }
        String space = sanitizeSpace(spaceKey);
        String name = UUID.randomUUID() + "." + ext;
        Path dir = root.resolve(space);
        Files.createDirectories(dir);
        Files.write(dir.resolve(name), data);
        return "/admin/screenshots/" + space + "/" + name;
    }

    public Loaded loadRelative(String rel) throws IOException {
        if (rel == null || rel.isBlank()) return null;
        String cleaned = rel.replace('\\', '/');
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        if (cleaned.contains("..")) return null;
        Path file = root.resolve(cleaned).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) return null;
        String ext = extension(file.getFileName().toString());
        String mime = MIME.getOrDefault(ext, "application/octet-stream");
        return new Loaded(Files.readAllBytes(file), mime);
    }

    static String sanitizeSpace(String spaceKey) {
        String s = spaceKey == null || spaceKey.isBlank() ? "default" : spaceKey.trim();
        s = SAFE_SPACE.matcher(s).replaceAll("_");
        return s.isEmpty() ? "default" : s;
    }

    private static String extension(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) return "";
        return name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    record Loaded(byte[] bytes, String contentType) { }
}
