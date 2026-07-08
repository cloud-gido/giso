package com.giso.gateway.screenshot;

import com.giso.gateway.GatewayConfig;
import com.giso.gateway.ScreenshotStore;
import com.giso.gateway.aws.S3Clients;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.core.sync.RequestBody;

import java.io.IOException;
import java.nio.file.Path;
import java.util.UUID;

/** S3 对象存储 + Gateway 内存/磁盘缓存（多副本共享）。 */
public final class S3ScreenshotBackend implements ScreenshotBackend {
    private final S3Client s3;
    private final String bucket;
    private final String keyPrefix;
    private final ScreenshotCache cache;

    public S3ScreenshotBackend(GatewayConfig config) throws IOException {
        if (config.s3Bucket == null || config.s3Bucket.isBlank()) {
            throw new IllegalArgumentException("S3 预览图需要 s3.bucket 或 GISO_S3_BUCKET");
        }
        this.bucket = config.s3Bucket;
        this.keyPrefix = resolveKeyPrefix(config);
        this.s3 = S3Clients.create(config);
        this.cache = new ScreenshotCache(Path.of(config.screenshotsCacheDir), config.screenshotsCacheMaxMb);
    }

    @Override
    public String save(String spaceKey, String originalName, byte[] data) throws IOException {
        LocalScreenshotBackend.validate(data, originalName);
        String space = LocalScreenshotBackend.sanitizeSpace(spaceKey);
        String ext = LocalScreenshotBackend.extension(originalName);
        String name = UUID.randomUUID() + "." + ext;
        String rel = space + "/" + name;
        String key = keyPrefix + rel;
        String mime = LocalScreenshotBackend.mimeForExt(ext);
        s3.putObject(PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(mime)
                        .cacheControl("private, max-age=86400")
                        .build(),
                RequestBody.fromBytes(data));
        cache.put(rel, data, mime);
        return LocalScreenshotBackend.publicPath(space, name);
    }

    @Override
    public ScreenshotStore.Loaded loadRelative(String rel) throws IOException {
        if (rel == null || rel.isBlank()) return null;
        String cleaned = rel.replace('\\', '/');
        while (cleaned.startsWith("/")) cleaned = cleaned.substring(1);
        if (cleaned.contains("..")) return null;

        ScreenshotStore.Loaded cached = cache.get(cleaned);
        if (cached != null) return cached;

        String key = keyPrefix + cleaned;
        try {
            byte[] bytes = s3.getObject(
                    GetObjectRequest.builder().bucket(bucket).key(key).build(),
                    ResponseTransformer.toBytes()).asByteArray();
            String ext = LocalScreenshotBackend.extension(cleaned);
            String mime = LocalScreenshotBackend.mimeForExt(ext);
            cache.put(cleaned, bytes, mime);
            return new ScreenshotStore.Loaded(bytes, mime);
        } catch (NoSuchKeyException e) {
            return null;
        }
    }

    @Override
    public String name() {
        return "s3(" + bucket + "/" + keyPrefix + ")";
    }

    public static String resolveKeyPrefix(GatewayConfig config) {
        String p = config.screenshotsS3Prefix;
        if (p == null || p.isBlank()) {
            p = S3Clients.normalizePrefix(config.s3Prefix) + "screenshots/";
        }
        return S3Clients.normalizePrefix(p);
    }

    /** 供测试断言 key 拼装。 */
    public static String objectKey(GatewayConfig config, String rel) {
        return resolveKeyPrefix(config) + rel;
    }
}
