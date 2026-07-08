package com.giso.gateway.screenshot;

import com.giso.gateway.ScreenshotStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 预览图两级缓存：内存 LRU + 可选磁盘（Pod 重启后仍可减少 S3 冷读）。
 */
final class ScreenshotCache {
    private final Path diskDir;
    private final long maxBytes;
    private final LinkedHashMap<String, Entry> memory;
    private long memoryBytes;

    ScreenshotCache(Path diskDir, long maxBytesMb) throws IOException {
        this.diskDir = diskDir == null ? null : diskDir.toAbsolutePath().normalize();
        if (this.diskDir != null) Files.createDirectories(this.diskDir);
        this.maxBytes = Math.max(8, maxBytesMb) * 1024L * 1024L;
        this.memory = new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Entry> eldest) {
                if (memoryBytes <= maxBytes) return false;
                Entry e = eldest.getValue();
                memoryBytes -= e.bytes.length;
                return true;
            }
        };
    }

    ScreenshotStore.Loaded get(String rel) throws IOException {
        Entry hit = memory.get(rel);
        if (hit != null) return new ScreenshotStore.Loaded(hit.bytes, hit.contentType);
        if (diskDir == null) return null;
        Path file = diskFile(rel);
        if (!Files.isRegularFile(file)) return null;
        byte[] bytes = Files.readAllBytes(file);
        String ext = LocalScreenshotBackend.extension(file.getFileName().toString());
        String mime = LocalScreenshotBackend.mimeForExt(ext);
        putMemory(rel, bytes, mime);
        return new ScreenshotStore.Loaded(bytes, mime);
    }

    void put(String rel, byte[] bytes, String contentType) throws IOException {
        putMemory(rel, bytes, contentType);
        if (diskDir == null) return;
        Path file = diskFile(rel);
        Files.createDirectories(file.getParent());
        Files.write(file, bytes);
    }

    private void putMemory(String rel, byte[] bytes, String contentType) {
        Entry prev = memory.remove(rel);
        if (prev != null) memoryBytes -= prev.bytes.length;
        memory.put(rel, new Entry(bytes, contentType));
        memoryBytes += bytes.length;
        while (memoryBytes > maxBytes && !memory.isEmpty()) {
            var it = memory.entrySet().iterator();
            if (!it.hasNext()) break;
            Entry e = it.next().getValue();
            memoryBytes -= e.bytes.length;
            it.remove();
        }
    }

    private Path diskFile(String rel) {
        String cleaned = rel.replace('\\', '/');
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        return diskDir.resolve(cleaned).normalize();
    }

    private record Entry(byte[] bytes, String contentType) { }
}
