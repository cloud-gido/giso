package com.giso.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 基于真实 schema/ 注册表的校验测试（surefire 工作目录 = 模块根，../../schema 即仓库注册表）。 */
class RegistryTest {
    private static final ObjectMapper M = new ObjectMapper();
    private static Registry registry;

    @BeforeAll
    static void setup() throws Exception {
        GatewayConfig config = new GatewayConfig();
        config.schemaDir = "../../schema";
        config.registryBackend = "yaml";
        registry = Registry.create(config);
    }

    private static JsonNode ev(String json) throws IOException {
        return M.readTree(json);
    }

    @Test
    void validLaunchEventIsOk() throws IOException {
        var r = registry.validate(ev("""
            {"event":"app_launch","log_id":"x-1",
             "common":{"app_id":"web","platform":"web","did":"d-1"}}"""));
        assertEquals("ok", r.status(), () -> r.issues().toString());
    }

    @Test
    void nonStandardEventIsError() throws IOException {
        var r = registry.validate(ev("{\"event\":\"my_custom_click\"}"));
        assertEquals("error", r.status());
        assertTrue(r.issues().stream().anyMatch(i -> i.field().equals("event")));
    }

    @Test
    void missingCommonParamsIsMissing() throws IOException {
        var r = registry.validate(ev("""
            {"event":"app_launch","log_id":"x-2",
             "common":{"app_id":"web","platform":"","did":""}}"""));
        assertEquals("missing", r.status());
    }

    @Test
    void unregisteredPageIsError() throws IOException {
        var r = registry.validate(ev("""
            {"event":"page_enter","log_id":"x-3",
             "common":{"app_id":"web","platform":"web","did":"d-1"},
             "page":{"pgid":"no_such_page_xyz"}}"""));
        assertEquals("error", r.status());
    }

    @Test
    void registeredPageIsOk() throws IOException {
        var r = registry.validate(ev("""
            {"event":"page_enter","log_id":"x-4",
             "common":{"app_id":"web","platform":"web","did":"d-1"},
             "page":{"pgid":"home"}}"""));
        assertEquals("ok", r.status(), () -> r.issues().toString());
    }

    @Test
    void passthroughPtIsNotValidated() throws IOException {
        // pt 是后台透传包，内容不登记、不校验，任意结构都不影响校验结果
        var r = registry.validate(ev("""
            {"event":"page_enter","log_id":"x-pt",
             "common":{"app_id":"web","platform":"web","did":"d-1"},
             "page":{"pgid":"home"},
             "pt":{"rec_trace_id":"tr-1","nested":{"a":[1,2]},"任意字段":true}}"""));
        assertEquals("ok", r.status(), () -> r.issues().toString());
    }

    @Test
    void unregisteredBizCodeIsError() throws IOException {
        var r = registry.validate(ev("""
            {"event":"biz_event","log_id":"x-5",
             "common":{"app_id":"web","platform":"web","did":"d-1"},
             "biz":{"code":"no_such_event_xyz"}}"""));
        assertEquals("error", r.status());
    }

    @Test
    void elementBoundToPageIsOk() throws IOException {
        // video_card 已绑定到 video_feed（pages.yaml elements）
        var r = registry.validate(ev("""
            {"event":"element_click","log_id":"x-6",
             "common":{"app_id":"web","platform":"web","did":"d-1"},
             "page":{"pgid":"video_feed","pg_params":{"tab_name":"rec"}},
             "element":{"eid":"video_card","params":{"vid":"v1","pos":1,"rec_trace_id":"t1"}}}"""));
        assertEquals("ok", r.status(), () -> r.issues().toString());
    }

    @Test
    void elementNotBoundToPageIsError() throws IOException {
        // odds_btn（博彩）没有绑定到 news_article（资讯）——页面结构体强校验
        var r = registry.validate(ev("""
            {"event":"element_click","log_id":"x-7",
             "common":{"app_id":"web","platform":"web","did":"d-1"},
             "page":{"pgid":"news_article","pg_params":{"aid":"a1","news_cat":"sports"}},
             "element":{"eid":"odds_btn","params":{"market_type":"1x2","selection_id":"s1","odds":1.9}}}"""));
        assertEquals("error", r.status());
        assertTrue(r.issues().stream().anyMatch(i -> i.msg().contains("未绑定")));
    }

    @Test
    void serverFactEventFromClientIsError() throws IOException {
        // bet_placed 登记为 source: server，端上冒充上报应被拦截
        var r = registry.validate(ev("""
            {"event":"biz_event","log_id":"x-8",
             "common":{"app_id":"web","platform":"web","did":"d-1"},
             "biz":{"code":"bet_placed","params":{"bet_id":"b1","match_id":"m1",
               "stake_amt":100,"currency":"USD","odds":1.95,"bet_type":"single"}}}"""));
        assertEquals("error", r.status());
        assertTrue(r.issues().stream().anyMatch(i -> i.msg().contains("服务端事实")));
    }

    @Test
    void serverFactEventFromServerIsOk() throws IOException {
        var r = registry.validate(ev("""
            {"event":"biz_event","log_id":"x-9",
             "common":{"app_id":"giso","platform":"server","uid":"u1"},
             "biz":{"code":"bet_placed","params":{"bet_id":"b1","match_id":"m1",
               "stake_amt":100,"currency":"USD","odds":1.95,"bet_type":"single"}}}"""));
        // 服务端无 did，common 校验按 missing 处理与否取决于通参规则，这里只断言没有"服务端事实"拦截
        assertTrue(r.issues().stream().noneMatch(i -> i.msg().contains("服务端事实")),
                () -> r.issues().toString());
    }
}
