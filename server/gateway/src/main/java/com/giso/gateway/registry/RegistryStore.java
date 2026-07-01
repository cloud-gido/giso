package com.giso.gateway.registry;

import java.util.List;
import java.util.Map;

/** 注册表持久化：YAML（开发）或 PostgreSQL（生产）。 */
public interface RegistryStore extends RegistryMeta {
    RegistrySnapshot load() throws Exception;

    WriteResult upsert(String kind, Map<String, Object> item, String operator) throws Exception;

    WriteResult delete(String kind, String key, String operator) throws Exception;

    String backendName();

    /** 仅 postgres：当前全局 revision，yaml 固定 0。 */
    default long fetchGlobalRevision() throws Exception {
        return load().globalRevision();
    }

    @Override
    default boolean ping() {
        return true;
    }

    @Override
    default Map<String, Object> meta() throws Exception {
        return Map.of("backend", backendName(), "revision", fetchGlobalRevision());
    }

    @Override
    default List<Map<String, Object>> audit(String kind, String key, int limit) throws Exception {
        return List.of();
    }

    @Override
    default WriteResult publish(String kind, String key, String operator) throws Exception {
        return WriteResult.fail("当前后端不支持 publish（仅 postgres）");
    }

    @Override
    default WriteResult deprecate(String kind, String key, String operator) throws Exception {
        return WriteResult.fail("当前后端不支持 deprecate（仅 postgres）");
    }
}
