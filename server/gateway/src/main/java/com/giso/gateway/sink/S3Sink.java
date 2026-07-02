package com.giso.gateway.sink;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.giso.gateway.GatewayConfig;
import com.giso.gateway.Metrics;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * S3 湖仓 Bronze 层出口：本地缓冲 JSONL，按天滚动后上传。
 * 与 GIDO deployment 仓 S3 warehouse 对齐，供 Flink/Paimon 离线摄入。
 *
 * 对象路径：{prefix}{raw|quarantine}/dt=YYYY-MM-DD/{filename}.jsonl
 */
public final class S3Sink implements EventSink {
    private static final DateTimeFormatter DT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final S3Client s3;
    private final String bucket;
    private final String prefix;
    private final Path bufferDir;
    private final long flushBytes;
    private LocalDate curDate;
    private BufferedWriter raw;
    private BufferedWriter quarantine;
    private Path rawPath;
    private Path quarantinePath;
    private long rawBytes;
    private long quarantineBytes;

    public S3Sink(GatewayConfig config) throws IOException {
        if (config.s3Bucket == null || config.s3Bucket.isBlank()) {
            throw new IllegalArgumentException("s3 sink 需要配置 s3.bucket 或 GISO_S3_BUCKET");
        }
        this.bucket = config.s3Bucket;
        this.prefix = normalizePrefix(config.s3Prefix);
        this.bufferDir = Path.of(config.s3BufferDir);
        this.flushBytes = config.s3FlushBytes;
        Files.createDirectories(bufferDir);

        var builder = S3Client.builder().region(Region.of(config.s3Region));
        if (config.s3Endpoint != null && !config.s3Endpoint.isBlank()) {
            builder = builder.endpointOverride(URI.create(config.s3Endpoint.trim()));
            builder = builder.forcePathStyle(true);
        }
        if (config.s3AccessKey != null && !config.s3AccessKey.isBlank()
                && config.s3SecretKey != null && !config.s3SecretKey.isBlank()) {
            builder = builder.credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(config.s3AccessKey, config.s3SecretKey)));
        } else {
            builder = builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        this.s3 = builder.build();
        roll();
    }

    @Override
    public synchronized void accept(ObjectNode event, boolean q) {
        try {
            if (!LocalDate.now().equals(curDate)) {
                flushAndUpload(true);
                flushAndUpload(false);
                roll();
            }
            String line = event.toString();
            if (q) {
                quarantine.write(line);
                quarantine.newLine();
                quarantine.flush();
                quarantineBytes += line.length() + 1;
                if (quarantineBytes >= flushBytes) flushAndUpload(true);
            } else {
                raw.write(line);
                raw.newLine();
                raw.flush();
                rawBytes += line.length() + 1;
                if (rawBytes >= flushBytes) flushAndUpload(false);
            }
            Metrics.inc(q ? "giso_s3_events_quarantine_total" : "giso_s3_events_raw_total");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void flushAndUpload(boolean q) throws IOException {
        Path path = q ? quarantinePath : rawPath;
        BufferedWriter w = q ? quarantine : raw;
        if (w != null) {
            w.flush();
            w.close();
            if (q) quarantine = null;
            else raw = null;
        }
        if (path == null || !Files.exists(path) || Files.size(path) == 0) {
            if (q) quarantineBytes = 0;
            else rawBytes = 0;
            return;
        }
        String stream = q ? "quarantine" : "raw";
        String key = prefix + stream + "/dt=" + curDate.format(DT) + "/"
                + path.getFileName();
        try {
            s3.putObject(PutObjectRequest.builder()
                            .bucket(bucket)
                            .key(key)
                            .contentType("application/x-ndjson")
                            .build(),
                    RequestBody.fromFile(path));
            Files.deleteIfExists(path);
            Metrics.inc("giso_s3_uploads_" + stream + "_total");
        } catch (Exception e) {
            System.err.println("[s3-sink] upload failed key=" + key + ": " + e.getMessage());
            Metrics.inc("giso_s3_upload_errors_" + stream + "_total");
        }
        if (q) quarantineBytes = 0;
        else rawBytes = 0;
    }

    private void roll() throws IOException {
        curDate = LocalDate.now();
        String stamp = curDate.format(DT) + "-" + UUID.randomUUID().toString().substring(0, 8);
        rawPath = bufferDir.resolve("giso_events_raw-" + stamp + ".jsonl");
        quarantinePath = bufferDir.resolve("giso_events_quarantine-" + stamp + ".jsonl");
        raw = Files.newBufferedWriter(rawPath, StandardCharsets.UTF_8);
        quarantine = Files.newBufferedWriter(quarantinePath, StandardCharsets.UTF_8);
        rawBytes = 0;
        quarantineBytes = 0;
    }

    private static String normalizePrefix(String p) {
        if (p == null || p.isBlank()) return "giso/";
        return p.endsWith("/") ? p : p + "/";
    }

    @Override
    public String name() {
        return "s3(" + bucket + "/" + prefix + ")";
    }

    @Override
    public synchronized void close() {
        try {
            flushAndUpload(true);
            flushAndUpload(false);
        } catch (IOException e) {
            System.err.println("[s3-sink] close flush failed: " + e.getMessage());
        }
        s3.close();
    }
}
