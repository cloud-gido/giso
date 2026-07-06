package com.giso.gateway.debug;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/** 管理台联调近期事件缓冲（memory 单副本 | redis 多副本共享）。 */
public interface DebugBuffer extends AutoCloseable {

    void append(ObjectNode wrapped, String spaceKey);

    List<ObjectNode> recent(int limit, String spaceKey, String did, String event, String status);

    List<ObjectNode> recentByDid(String did);

    void clearRecent(String spaceKey);

    /** 实现名：memory | redis */
    String backendName();

    @Override
    default void close() { }
}
