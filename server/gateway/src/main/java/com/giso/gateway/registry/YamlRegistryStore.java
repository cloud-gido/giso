package com.giso.gateway.registry;

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 本地开发：从 schema/*.yaml 加载，管理台写回磁盘。 */
public final class YamlRegistryStore implements RegistryStore {
    private final Path schemaDir;

    public YamlRegistryStore(Path schemaDir) {
        this.schemaDir = schemaDir;
    }

    @Override
    public RegistrySnapshot load() throws IOException {
        Map<String, Map<String, Map<String, Object>>> tables = RegistryKinds.emptyTables();
        Yaml yaml = new Yaml();
        for (var e : RegistryKinds.FILES.entrySet()) {
            String kind = e.getKey();
            Map<String, Object> doc = yaml.load(Files.readString(schemaDir.resolve(e.getValue()[0])));
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) doc.get(e.getValue()[1]);
            RegistryKinds.putItems(kind, items, tables);
        }
        return new RegistrySnapshot(tables, 0);
    }

    @Override
    public WriteResult upsert(String kind, Map<String, Object> item, String operator) throws IOException {
        RegistrySnapshot snap = load();
        String key = String.valueOf(item.get(RegistryKinds.idField(kind)));
        snap.tables().get(kind).put(key, item);
        persist(kind, snap.tables().get(kind));
        return WriteResult.ok(0);
    }

    @Override
    public WriteResult delete(String kind, String key, String operator) throws IOException {
        RegistrySnapshot snap = load();
        if (snap.tables().get(kind).remove(key) == null) {
            return WriteResult.fail("不存在: " + key);
        }
        persist(kind, snap.tables().get(kind));
        return WriteResult.ok(0);
    }

    @Override
    public String backendName() {
        return "yaml";
    }

    private void persist(String kind, Map<String, Map<String, Object>> table) throws IOException {
        DumperOptions opts = new DumperOptions();
        opts.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        opts.setAllowUnicode(true);
        opts.setIndent(2);
        Yaml yaml = new Yaml(opts);
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("version", "1.0");
        doc.put(RegistryKinds.rootKey(kind), new ArrayList<>(table.values()));
        StringWriter sw = new StringWriter();
        sw.write("# " + RegistryKinds.yamlFile(kind) + " — 由管理页面写回；最终以 Git 提交为审计记录\n");
        yaml.dump(doc, sw);
        Files.writeString(schemaDir.resolve(RegistryKinds.yamlFile(kind)), sw.toString());
    }
}
