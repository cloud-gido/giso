package com.giso.gateway.registry;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegistryImportTemplatesTest {

    @Test
    void parseParamsCsv() {
        String csv = """
                key,type,desc,rule,owner,since,issue_link,status
                foo_key,string,示例,,data-team,1.0,,draft
                # comment
                """;
        List<Map<String, Object>> rows = RegistryImportTemplates.parseCsv("params", csv);
        assertEquals(1, rows.size());
        assertEquals("foo_key", rows.get(0).get("key"));
        assertEquals("string", rows.get(0).get("type"));
        assertEquals("draft", rows.get(0).get("status"));
    }

    @Test
    void parseListFields() {
        String csv = """
                pgid,screenshot,desc,domain,params,elements,owner,since,issue_link,status
                home,,首页,video,"vid,pos",play_btn,pm,1.0,,draft
                """;
        List<Map<String, Object>> rows = RegistryImportTemplates.parseCsv("pages", csv);
        assertEquals(1, rows.size());
        @SuppressWarnings("unchecked")
        List<String> params = (List<String>) rows.get(0).get("params");
        assertTrue(params.contains("vid"));
        assertTrue(params.contains("pos"));
    }

    @Test
    void templateHasHeader() {
        assertTrue(RegistryImportTemplates.csvTemplate("params").startsWith("key,type,desc"));
    }
}
