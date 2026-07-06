package com.giso.gateway.registry;

import com.giso.gateway.GatewayConfig;
import com.giso.gateway.Registry;
import com.giso.gateway.auth.AdminUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryBundleTest {
    private static Registry registry;

    @BeforeAll
    static void setup() throws Exception {
        GatewayConfig config = new GatewayConfig();
        config.schemaDir = "../../schema";
        config.registryBackend = "yaml";
        registry = Registry.create(config);
    }

    @Test
    void exportIncludesOnlyLiveWhenUnreferenced() {
        Map<String, List<Map<String, Object>>> all = new LinkedHashMap<>();
        all.put("params", List.of(
                Map.of("key", "live_p", "type", "string", "status", "live"),
                Map.of("key", "draft_p", "type", "string", "status", "draft")));
        all.put("pages", List.of());
        all.put("elements", List.of());
        all.put("events", List.of());

        Map<String, Object> bundle = RegistryBundle.buildExport("test", all, 42, "op");
        assertEquals("giso-registry-bundle", bundle.get("format"));
        assertEquals(1, bundle.get("total"));
        assertEquals(1, bundle.get("live_total"));
        assertEquals(0, bundle.get("dependency_total"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> params = (List<Map<String, Object>>)
                ((Map<String, Object>) bundle.get("registry")).get("params");
        assertEquals(1, params.size());
        assertEquals("live_p", params.get(0).get("key"));
    }

    @Test
    void exportIncludesDraftDependenciesReferencedByLivePages() {
        Map<String, List<Map<String, Object>>> all = new LinkedHashMap<>();
        all.put("params", List.of(
                Map.of("key", "sport_id", "type", "string", "status", "draft")));
        all.put("elements", List.of(
                Map.of("eid", "banner", "desc", "banner", "status", "draft")));
        all.put("pages", List.of(
                Map.of("pgid", "bet_lobby", "params", List.of("sport_id"), "status", "live"),
                Map.of("pgid", "home", "elements", List.of("banner"), "status", "live")));
        all.put("events", List.of());

        Map<String, Object> bundle = RegistryBundle.buildExport("test", all, 42, "op");
        assertEquals(4, bundle.get("total"));
        assertEquals(2, bundle.get("live_total"));
        assertEquals(2, bundle.get("dependency_total"));
        @SuppressWarnings("unchecked")
        Map<String, Object> reg = (Map<String, Object>) bundle.get("registry");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> params = (List<Map<String, Object>>) reg.get("params");
        assertEquals(1, params.size());
        assertEquals("sport_id", params.get(0).get("key"));
        assertEquals("draft", params.get(0).get("status"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> elements = (List<Map<String, Object>>) reg.get("elements");
        assertEquals(1, elements.size());
        assertEquals("banner", elements.get(0).get("eid"));
    }

    @Test
    void exportImportDryRunRoundTripWithDependencies() throws Exception {
        String paramKey = "exp_rt_p_" + System.nanoTime();
        String pageKey = "exp_rt_pg_" + System.nanoTime();
        String space = "bundle_rt_" + System.nanoTime();
        registry.upsert(space, "params", new LinkedHashMap<>(Map.of(
                "key", paramKey, "type", "string", "desc", "dep", "status", "draft")),
                "tester", AdminUser.ROLE_SPACE_ADMIN);
        registry.upsert(space, "pages", new LinkedHashMap<>(Map.of(
                "pgid", pageKey, "desc", "page", "params", List.of(paramKey), "status", "live")),
                "tester", AdminUser.ROLE_SPACE_ADMIN);

        Map<String, Object> bundle = registry.exportBundle(space, "tester");
        String importSpace = space + "_target";
        Map<String, Object> preview = registry.importBundle(importSpace, bundle, true, "tester");
        assertEquals(0, preview.get("failed"), String.valueOf(preview.get("errors")));
    }

    @Test
    void validateBundleShapeRejectsBadFormat() {
        assertEquals("不支持的 format（期望 giso-registry-bundle）",
                RegistryBundle.validateBundleShape(Map.of("format", "other", "version", 1)));
    }

    @Test
    void importBundleDryRunResolvesRefsWithinBundle() throws Exception {
        String paramKey = "bundle_ref_p_" + System.nanoTime();
        String pageKey = "bundle_ref_pg_" + System.nanoTime();
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("format", RegistryBundle.FORMAT);
        bundle.put("version", RegistryBundle.VERSION);
        bundle.put("space_key", "default");
        bundle.put("registry", Map.of(
                "params", List.of(Map.of("key", paramKey, "type", "string", "desc", "x", "status", "live")),
                "pages", List.of(Map.of(
                        "pgid", pageKey, "desc", "page", "params", List.of(paramKey), "status", "live")),
                "elements", List.of(),
                "events", List.of()));

        Map<String, Object> preview = registry.importBundle("default", bundle, true, "tester");
        assertEquals(0, preview.get("failed"), String.valueOf(preview.get("errors")));
    }

    @Test
    void importBundleDryRunResolvesPageElementRefsWithinBundle() throws Exception {
        String pageKey = "bundle_el_pg_" + System.nanoTime();
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("format", RegistryBundle.FORMAT);
        bundle.put("version", RegistryBundle.VERSION);
        bundle.put("space_key", "default");
        bundle.put("registry", Map.of(
                "params", List.of(),
                "pages", List.of(Map.of(
                        "pgid", pageKey, "desc", "lobby", "elements", List.of("banner"), "status", "live")),
                "elements", List.of(Map.of(
                        "eid", "banner", "desc", "banner", "status", "live")),
                "events", List.of()));

        Map<String, Object> preview = registry.importBundle("default", bundle, true, "tester");
        assertEquals(0, preview.get("failed"), String.valueOf(preview.get("errors")));
    }

    @Test
    void exportImportDryRunRoundTripWithPageElements() throws Exception {
        String pageKey = "exp_el_pg_" + System.nanoTime();
        String space = "bundle_el_rt_" + System.nanoTime();
        registry.upsert(space, "elements", new LinkedHashMap<>(Map.of(
                "eid", "banner", "desc", "banner", "status", "draft")),
                "tester", AdminUser.ROLE_SPACE_ADMIN);
        registry.upsert(space, "pages", new LinkedHashMap<>(Map.of(
                "pgid", pageKey, "desc", "lobby", "elements", List.of("banner"), "status", "live")),
                "tester", AdminUser.ROLE_SPACE_ADMIN);

        Map<String, Object> bundle = registry.exportBundle(space, "tester");
        String importSpace = space + "_target";
        Map<String, Object> preview = registry.importBundle(importSpace, bundle, true, "tester");
        assertEquals(0, preview.get("failed"), String.valueOf(preview.get("errors")));
    }

    @Test
    void importBundleDryRunDoesNotPersist() throws Exception {
        Map<String, Object> exported = registry.exportBundle("default", "tester");
        String key = "bundle_dry_" + System.nanoTime();
        Map<String, Object> param = new LinkedHashMap<>();
        param.put("key", key);
        param.put("type", "string");
        param.put("desc", "bundle test");
        param.put("status", "live");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> params = (List<Map<String, Object>>)
                ((Map<String, Object>) exported.get("registry")).get("params");
        params.add(param);

        Map<String, Object> preview = registry.importBundle("default", exported, true, "tester");
        assertTrue((Boolean) preview.get("dry_run"));
        assertEquals(0, preview.get("failed"));
        assertTrue(registry.all("default").get("params").stream()
                .noneMatch(p -> key.equals(p.get("key"))));
    }

    @Test
    void importBundleForcesLive() throws Exception {
        String key = "bundle_live_" + System.nanoTime();
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("format", RegistryBundle.FORMAT);
        bundle.put("version", RegistryBundle.VERSION);
        bundle.put("space_key", "default");
        bundle.put("registry", Map.of(
                "params", List.of(Map.of("key", key, "type", "string", "desc", "x", "status", "draft")),
                "pages", List.of(),
                "elements", List.of(),
                "events", List.of()));

        Map<String, Object> result = registry.importBundle("default", bundle, false, "tester");
        assertEquals(0, result.get("failed"));
        var row = registry.all("default").get("params").stream()
                .filter(p -> key.equals(p.get("key")))
                .findFirst()
                .orElseThrow();
        assertEquals("live", row.get("status"));
        registry.delete("default", "params", key, "tester", AdminUser.ROLE_SPACE_ADMIN);
    }
}
