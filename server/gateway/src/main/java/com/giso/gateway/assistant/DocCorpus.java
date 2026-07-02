package com.giso.gateway.assistant;

import com.giso.gateway.GatewayConfig;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** 从 docs/ 或 classpath 加载 Markdown 语料并分块检索。 */
final class DocCorpus {
    record Chunk(String source, String heading, String body) { }

    private final List<Chunk> chunks = new ArrayList<>();

    DocCorpus(GatewayConfig config) throws IOException {
        for (String dir : config.assistantDocsDirs) {
            Path p = Path.of(dir);
            if (!Files.isDirectory(p)) continue;
            loadDir(p, p);
        }
        if (chunks.isEmpty() && config.assistantCorpusClasspath != null) {
            loadClasspath(config.assistantCorpusClasspath);
        }
    }

    List<Chunk> search(String query, int limit) {
        if (query == null || query.isBlank()) return List.of();
        String[] terms = tokenize(query);
        if (terms.length == 0) return List.of();
        return chunks.stream()
                .map(c -> new Scored(c, score(c, terms)))
                .filter(s -> s.score > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed())
                .limit(limit)
                .map(s -> s.chunk)
                .toList();
    }

    int size() { return chunks.size(); }

    private record Scored(Chunk chunk, int score) { }

    private static int score(Chunk c, String[] terms) {
        String hay = (c.source + " " + c.heading + " " + c.body).toLowerCase(Locale.ROOT);
        int s = 0;
        for (String t : terms) {
            if (hay.contains(t)) s += t.length() >= 4 ? 3 : 1;
        }
        if (c.heading.toLowerCase(Locale.ROOT).contains(terms[0])) s += 5;
        return s;
    }

    private static String[] tokenize(String q) {
        return Pattern.compile("[\\p{L}\\p{N}_]+")
                .matcher(q.toLowerCase(Locale.ROOT))
                .results()
                .map(m -> m.group())
                .filter(t -> t.length() > 1)
                .distinct()
                .toArray(String[]::new);
    }

    private void loadDir(Path root, Path dir) throws IOException {
        try (var stream = Files.walk(dir)) {
            stream.filter(p -> p.toString().endsWith(".md"))
                    .forEach(p -> {
                        try {
                            ingest(root.relativize(p).toString().replace('\\', '/'),
                                    Files.readString(p, StandardCharsets.UTF_8));
                        } catch (IOException ignored) { }
                    });
        }
    }

    private void loadClasspath(String base) throws IOException {
        String prefix = base.startsWith("/") ? base.substring(1) : base;
        List<String> names = List.of(
                "tracking-flow.md", "faq-architecture.md", "faq-app-key.md",
                "faq-quarantine.md", "integration-steps.md", "product-overview.md");
        ClassLoader cl = DocCorpus.class.getClassLoader();
        for (String name : names) {
            String path = prefix + "/" + name;
            try (InputStream in = cl.getResourceAsStream(path)) {
                if (in == null) continue;
                ingest("copilot/" + name, new String(in.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
    }

    private void ingest(String source, String markdown) {
        String[] parts = markdown.split("(?m)^## ");
        if (parts.length <= 1) {
            chunks.add(new Chunk(source, "(overview)", parts[0].trim()));
            return;
        }
        for (int i = 1; i < parts.length; i++) {
            String block = parts[i].trim();
            int nl = block.indexOf('\n');
            String heading = nl > 0 ? block.substring(0, nl).trim() : block;
            String body = nl > 0 ? block.substring(nl + 1).trim() : "";
            if (!body.isEmpty()) chunks.add(new Chunk(source, heading, body));
        }
    }
}
