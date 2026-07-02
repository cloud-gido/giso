package com.giso.gateway.assistant;

import com.giso.gateway.GatewayConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 默认 Copilot：基于内置 FAQ / 接入文档检索，无需 LLM Key。
 * 适合功能产品答疑与埋点上报流程答疑。
 */
public final class DocAssistantProvider implements AssistantProvider {
    private final DocCorpus corpus;
    private final int maxChunks;

    public DocAssistantProvider(GatewayConfig config) throws Exception {
        this.corpus = new DocCorpus(config);
        this.maxChunks = config.assistantMaxChunks;
    }

    @Override
    public String name() { return "doc"; }

    @Override
    public boolean ready() { return corpus.size() > 0; }

    @Override
    public ChatResponse chat(ChatRequest req) {
        String q = req.message().trim();
        var hits = corpus.search(q, maxChunks);
        List<String> sources = new ArrayList<>();
        StringBuilder body = new StringBuilder();

        if (hits.isEmpty()) {
            body.append("未在文档库中找到直接匹配。你可以尝试：\n\n")
                    .append("- 换关键词，如「App Key」「隔离区」「上报流程」「空间」\n")
                    .append("- 查看 [接入指南](../../docs/tracking/06-接入指南.md) 与 [FAQ](../../docs/tracking/08-接入常见问题FAQ.md)\n");
            if (req.registryContext() != null && !req.registryContext().isBlank()) {
                body.append("\n**当前空间登记概况**\n\n").append(req.registryContext());
            }
            return new ChatResponse(body.toString(), name(), sources, defaultFollowups(q));
        }

        body.append("根据 GISO 文档与 FAQ，整理如下：\n\n");
        for (DocCorpus.Chunk c : hits) {
            sources.add(c.source() + " · " + c.heading());
            body.append("### ").append(c.heading()).append("\n\n")
                    .append(trim(c.body(), 900)).append("\n\n")
                    .append("*来源: `").append(c.source()).append("`*\n\n");
        }
        if (req.registryContext() != null && !req.registryContext().isBlank()) {
            body.append("---\n\n**当前空间登记概况**\n\n").append(req.registryContext());
        }
        return new ChatResponse(body.toString(), name(), sources, defaultFollowups(q));
    }

    private static String trim(String s, int max) {
        if (s.length() <= max) return s;
        return s.substring(0, max) + "…";
    }

    private static List<String> defaultFollowups(String q) {
        String l = q.toLowerCase(Locale.ROOT);
        if (l.contains("app") && l.contains("key")) {
            return List.of("test 和 prod 环境如何分流？", "video-android-beta 对应哪个空间？");
        }
        if (l.contains("隔离") || l.contains("quarantine")) {
            return List.of("隔离区如何回放？", "如何查看校验错误明细？");
        }
        return List.of("埋点接入六步是什么？", "注册表存在哪里？", "SSE 联调怎么用？");
    }
}
