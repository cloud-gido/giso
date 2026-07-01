package com.giso.gateway.registry;

import com.giso.gateway.GatewayConfig;

/** 按配置创建 RegistryStore。 */
public final class RegistryStores {
    private RegistryStores() { }

    public static RegistryStore create(GatewayConfig config) throws Exception {
        if ("postgres".equalsIgnoreCase(config.registryBackend)) {
            return PostgresRegistryStore.create(config);
        }
        return new YamlRegistryStore(java.nio.file.Path.of(config.schemaDir));
    }
}
