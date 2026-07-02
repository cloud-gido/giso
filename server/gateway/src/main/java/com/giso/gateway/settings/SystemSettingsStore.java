package com.giso.gateway.settings;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.Map;
import java.util.Optional;

public interface SystemSettingsStore {
    Optional<JsonNode> get(String key) throws Exception;

    Map<String, JsonNode> getAll() throws Exception;

    void put(String key, JsonNode value, String operator) throws Exception;
}
