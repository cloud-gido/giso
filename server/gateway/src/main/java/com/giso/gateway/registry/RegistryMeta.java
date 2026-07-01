package com.giso.gateway.registry;

import java.util.List;
import java.util.Map;

/** 注册表元信息与审计查询（PostgreSQL 生产环境）。 */
public interface RegistryMeta {
    boolean ping() throws Exception;

    Map<String, Object> meta() throws Exception;

    List<Map<String, Object>> audit(String kind, String key, int limit) throws Exception;

    WriteResult publish(String kind, String key, String operator) throws Exception;

    WriteResult deprecate(String kind, String key, String operator) throws Exception;

    WriteResult approve(String kind, String key, String operator) throws Exception;

    WriteResult reject(String kind, String key, String operator) throws Exception;
}
