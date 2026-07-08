package com.giso.gateway.screenshot;

import com.giso.gateway.ScreenshotStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

/** 本地目录存储（开发 / 单副本）。 */
public final class LocalScreenshotBackend implements ScreenshotBackend {
    private static final Set<String> ALLOWED_EXT = Set.of("png", "jpg", "jpeg", "webp", "gif");
    private static final Pattern SAFE_SPACE = Pattern.compile("[^a-zA-Z0-9_-]");
    private static final Map<String, String> MIME = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "webp", "image/webp",
            "gif", "image/gif");

    private final Path root;

    public LocalScreenshotBackend(Path root) throws IOException {
        this.root = root.toAbsolutePath().normalize();
        Files.createDirectories(this.root);
    }

    @Override
    public String save(String spaceKey, String originalName, byte[] data) throws IOException {
        validate(data, originalName);
        String space = sanitizeSpace(spaceKey);
        String ext = extension(originalName);
        String name = UUID.randomUUID() + "." + ext;
        Path dir = root.resolve(space);
        Files.createDirectories(dir);
        Files.write(dir.resolve(name), data);
        return publicPath(space, name);
    }

    @Override
    public ScreenshotStore.Loaded loadRelative(String rel) throws IOException {
        if (rel == null || rel.isBlank()) return null;
        String cleaned = rel.replace('\\', '/');
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        if (cleaned.contains("..")) return null;
        Path file = root.resolve(cleaned).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file)) return null;
        String ext = extension(file.getFileName().toString());
        String mime = MIME.getOrDefault(ext, "application/octet-stream");
        return new ScreenshotStore.Loaded(Files.readAllBytes(file), mime);
    }

    @Override
    public String name() {
        return "local(" + root + ")";
    }

    static void validate(byte[] data, String originalName) {
        if (data == null || data.length == 0) throw new IllegalArgumentException("空文件");
        if (data.length > ScreenshotStore.MAX_BYTES) {
            throw new IllegalArgumentException("图片不能超过 " + (ScreenshotStore.MAX_BYTES / 1024 / 1024) + "MB");
        }
        String ext = extension(originalName);
        if (!ALLOWED_EXT.contains(ext)) {
            throw new IllegalArgumentException("仅支持 png/jpg/webp/gif");
        }
    }

    static String sanitizeSpace(String spaceKey) {
        String s = spaceKey == null || spaceKey.isBlank() ? "default" : spaceKey.trim();
        s = SAFE_SPACE.matcher(s).replaceAll("_");
        return s.isEmpty() ? "default" : s;
    }

    static String publicPath(String space, String name) {
        return "/admin/screenshots/" + space + "/" + name;
    }

    static String extension(String name) {
        if (name == null) return "";
        int i = name.lastIndexOf('.');
        if (i < 0 || i == name.length() - 1) return "";
        return name.substring(i + 1).toLowerCase(Locale.ROOT);
    }

    static String mimeForExt(String ext) {
        return MIME.getOrDefault(ext, "application/octet-stream");
    }
}
