package com.giso.gateway.sink;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.Closeable;

/**
 * 事件出口（插拔式）。
 * 实现：FileSink（本地 JSONL）、KafkaSink（生产管道，下游 Doris Routine Load）。
 * 新增出口（如直写 Doris Stream Load、S3）只需实现本接口并在 SinkFactory 注册。
 */
public interface EventSink extends Closeable {

    /**
     * @param event      完整事件信封（已含 stime/_quality 标记）
     * @param quarantine true 表示校验错误事件，应进入隔离流
     */
    void accept(ObjectNode event, boolean quarantine);

    /** 名称，用于日志与健康检查 */
    String name();

    @Override
    default void close() { }
}
