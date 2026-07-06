package com.giso.gateway.registry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryRefsTest {

    @Test
    void normalizeDedupesLists() {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("params", new ArrayList<>(List.of("a", "b", "a")));
        RegistryRefs.normalizeLists(item);
        @SuppressWarnings("unchecked")
        List<String> ps = (List<String>) item.get("params");
        assertEquals(List.of("a", "b"), ps);
    }

    @Test
    void validateRejectsUnknownParam() {
        var tables = RegistryKinds.emptyTables();
        tables.get("pages").put("home", Map.of("pgid", "home", "params", List.of("missing_key")));
        String err = RegistryRefs.validate(tables, "pages", Map.of(
                "pgid", "home2", "params", List.of("vid", "ghost_param")));
        assertNotNull(err);
        assertTrue(err.contains("未登记参数"));
    }

    @Test
    void validateRejectsUnknownChildElement() {
        var tables = RegistryKinds.emptyTables();
        tables.get("elements").put("btn", Map.of("eid", "btn"));
        String err = RegistryRefs.validate(tables, "elements", Map.of(
                "eid", "card", "children", List.of("ghost_el")));
        assertNotNull(err);
        assertTrue(err.contains("children"));
    }

    @Test
    void hintsOrphanParamAndReferences() {
        var tables = RegistryKinds.emptyTables();
        tables.get("params").put("vid", Map.of("key", "vid", "type", "string", "status", "live"));
        tables.get("params").put("unused", Map.of("key", "unused", "type", "string", "status", "live"));
        tables.get("pages").put("home", Map.of(
                "pgid", "home", "params", List.of("vid"), "elements", List.of()));
        Map<String, Object> hints = RegistryRefs.hints(tables, "params", Map.of(
                "key", "unused", "type", "string", "status", "live"));
        assertEquals(1, hints.get("orphan_params_count"));
        assertTrue(hints.get("warn").toString().contains("未被"));
    }

    @Test
    void hintsElementParentPages() {
        var tables = RegistryKinds.emptyTables();
        tables.get("elements").put("btn", Map.of("eid", "btn", "domain", "bet"));
        tables.get("pages").put("lobby", Map.of(
                "pgid", "lobby", "domain", "bet", "elements", List.of("btn")));
        Map<String, Object> hints = RegistryRefs.hints(tables, "elements", Map.of("eid", "btn", "domain", "bet"));
        @SuppressWarnings("unchecked")
        List<String> parents = (List<String>) hints.get("parent_pages");
        assertEquals(List.of("lobby"), parents);
    }

    @Test
    void validParamRefPasses() {
        var tables = RegistryKinds.emptyTables();
        tables.get("params").put("vid", Map.of("key", "vid", "type", "string"));
        assertNull(RegistryRefs.validate(tables, "pages", Map.of(
                "pgid", "feed", "params", List.of("vid"))));
    }
}
