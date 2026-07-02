package com.giso.gateway.sink;

import com.giso.gateway.GatewayConfig;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** 按配置组装出口管道。`sinks: [file, kafka]` 可双写（迁移期常用）。 */
public final class SinkFactory {
    private SinkFactory() { }

    public static List<EventSink> create(GatewayConfig config) throws IOException {
        List<EventSink> sinks = new ArrayList<>();
        for (String type : config.sinks) {
            switch (type) {
                case "file" -> sinks.add(new FileSink(Path.of(config.fileDir)));
                case "kafka" -> sinks.add(new KafkaSink(config));
                case "s3" -> sinks.add(new S3Sink(config));
                default -> throw new IllegalArgumentException("未知 sink 类型: " + type
                        + "（支持 file / kafka / s3；新出口实现 EventSink 后在此注册）");
            }
        }
        if (sinks.isEmpty()) throw new IllegalArgumentException("至少配置一个 sink");
        return sinks;
    }
}
