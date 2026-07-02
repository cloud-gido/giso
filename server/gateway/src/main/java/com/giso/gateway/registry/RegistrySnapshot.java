package com.giso.gateway.registry;

import com.giso.gateway.space.SpaceService;

import java.util.LinkedHashMap;
import java.util.Map;

/** 注册表内存快照（按空间隔离）。 */
public record RegistrySnapshot(
        Map<String, Map<String, Map<String, Map<String, Object>>>> bySpace,
        long globalRevision) {

    public Map<String, Map<String, Map<String, Object>>> tablesFor(String spaceKey) {
        return bySpace.getOrDefault(spaceKey, RegistryKinds.emptyTables());
    }

    public static RegistrySnapshot singleSpace(
            Map<String, Map<String, Map<String, Object>>> tables, long revision) {
        Map<String, Map<String, Map<String, Map<String, Object>>>> m = new LinkedHashMap<>();
        m.put(SpaceService.DEFAULT_SPACE, tables);
        return new RegistrySnapshot(m, revision);
    }
}
