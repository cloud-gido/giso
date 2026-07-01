package com.giso.gateway.registry;

import java.util.Map;

/** 注册表内存快照（加载自 YAML 或 PostgreSQL）。 */
public record RegistrySnapshot(
        Map<String, Map<String, Map<String, Object>>> tables,
        long globalRevision) {
}
