#!/usr/bin/env python3
"""
注册表校验 + 四端常量代码生成。

用法:
    python3 tools/codegen/generate.py            # 校验并生成
    python3 tools/codegen/generate.py --check    # 仅校验（CI 用），生成物有 diff 则退出非 0
"""
import re
import subprocess
import sys
from pathlib import Path

import yaml

ROOT = Path(__file__).resolve().parents[2]
SCHEMA = ROOT / "schema"

SNAKE = re.compile(r"^[a-z][a-z0-9_]{0,31}$")
TYPES = {"string", "int", "float", "bool", "object"}
# 登记条目生命周期（status 可选，缺省视为 live）：
#   draft=登记中  dev=开发中  testing=测试验证中  live=线上  deprecated=已废弃
STATUSES = {"draft", "dev", "testing", "live", "deprecated"}
HEADER = "AUTO-GENERATED from schema/ by tools/codegen/generate.py — DO NOT EDIT."


def load():
    return {
        "params": yaml.safe_load((SCHEMA / "params.yaml").read_text())["params"],
        "pages": yaml.safe_load((SCHEMA / "pages.yaml").read_text())["pages"],
        "elements": yaml.safe_load((SCHEMA / "elements.yaml").read_text())["elements"],
        "events": yaml.safe_load((SCHEMA / "biz_events.yaml").read_text())["events"],
    }


def validate(reg) -> list[str]:
    errs = []
    seen = {}

    def uniq(kind, key):
        if (kind, key) in seen:
            errs.append(f"{kind} 重复定义: {key}")
        seen[(kind, key)] = True

    param_keys = set()
    for p in reg["params"]:
        uniq("param", p["key"])
        param_keys.add(p["key"])
        if not SNAKE.match(p["key"]):
            errs.append(f"参数命名违规(snake_case, ≤32): {p['key']}")
        if p.get("type") not in TYPES:
            errs.append(f"参数 {p['key']} 类型非法: {p.get('type')}")
        for field in ("desc", "owner", "since"):
            if not p.get(field):
                errs.append(f"参数 {p['key']} 缺少 {field}")

    def check_params(kind, key, keys):
        for k in keys:
            if k not in param_keys:
                errs.append(f"{kind} {key} 引用未登记参数: {k}")

    def check_status(kind, key, item):
        s = item.get("status")
        if s is not None and s not in STATUSES:
            errs.append(f"{kind} {key} status 非法: {s}（可选值 {sorted(STATUSES)}）")

    eids = {e["eid"] for e in reg["elements"]}
    for pg in reg["pages"]:
        uniq("page", pg["pgid"])
        if not SNAKE.match(pg["pgid"]):
            errs.append(f"页面命名违规: {pg['pgid']}")
        check_params("页面", pg["pgid"], pg.get("params", []))
        check_status("页面", pg["pgid"], pg)
        # 页面结构体：elements 绑定必须引用已登记元素
        for e in pg.get("elements", []):
            if e not in eids:
                errs.append(f"页面 {pg['pgid']} 的 elements 绑定引用未登记元素: {e}")

    for el in reg["elements"]:
        uniq("element", el["eid"])
        if not SNAKE.match(el["eid"]):
            errs.append(f"元素命名违规: {el['eid']}")
        check_params("元素", el["eid"], el.get("params", []))
        check_status("元素", el["eid"], el)
        for c in el.get("children", []):
            if c not in eids:
                errs.append(f"元素 {el['eid']} 的 children 引用未登记元素: {c}")

    for ev in reg["events"]:
        uniq("event", ev["code"])
        if not SNAKE.match(ev["code"]):
            errs.append(f"事件命名违规: {ev['code']}")
        if ev.get("source") not in ("client", "server"):
            errs.append(f"事件 {ev['code']} source 非法: {ev.get('source')}")
        check_params("事件", ev["code"], ev.get("params", []))
        check_status("事件", ev["code"], ev)
    for p in reg["params"]:
        check_status("参数", p["key"], p)
    return errs


def const_name(s: str) -> str:
    return s.upper()


def dart_const_name(s: str) -> str:
    parts = s.split("_")
    if not parts:
        return s
    return parts[0] + "".join(p[:1].upper() + p[1:] for p in parts[1:] if p)


def gen_ts(reg) -> str:
    def block(name, items, key, desc):
        lines = [f"export const {name} = {{"]
        for it in items:
            lines.append(f"  /** {it[desc]} */")
            lines.append(f"  {const_name(it[key])}: '{it[key]}',")
        lines.append("} as const;\n")
        return "\n".join(lines)

    return (
        f"// {HEADER}\n\n"
        + block("Pages", reg["pages"], "pgid", "desc")
        + "\n" + block("Elements", reg["elements"], "eid", "desc")
        + "\n" + block("Params", reg["params"], "key", "desc")
        + "\n" + block("BizEvents", reg["events"], "code", "desc")
    )


def gen_java(reg) -> dict[str, str]:
    def cls(name, items, key, desc):
        lines = [
            f"// {HEADER}",
            "package com.giso.tracker;",
            "",
            f"public final class {name} {{",
            f"    private {name}() {{ }}",
            "",
        ]
        for it in items:
            lines.append(f"    /** {it[desc]} */")
            lines.append(f"    public static final String {const_name(it[key])} = \"{it[key]}\";")
        lines.append("}")
        return "\n".join(lines) + "\n"

    return {
        "Pages.java": cls("Pages", reg["pages"], "pgid", "desc"),
        "Elements.java": cls("Elements", reg["elements"], "eid", "desc"),
        "Params.java": cls("Params", reg["params"], "key", "desc"),
        "BizEvents.java": cls("BizEvents", reg["events"], "code", "desc"),
    }


def gen_dart(reg) -> str:
    def block(class_name, items, key, desc):
        lines = [
            "",
            f"/// Registry constants for `{class_name}`.",
            f"class {class_name} {{",
            f"  {class_name}._();",
            "",
        ]
        for it in items:
            lines.append(f"  /// {it[desc]}")
            lines.append(f"  static const String {dart_const_name(it[key])} = '{it[key]}';")
        lines.append("}")
        return "\n".join(lines)

    return (
        f"// {HEADER}\n"
        + block("Pages", reg["pages"], "pgid", "desc")
        + block("Elements", reg["elements"], "eid", "desc")
        + block("Params", reg["params"], "key", "desc")
        + block("BizEvents", reg["events"], "code", "desc")
        + "\n"
    )


def gen_swift(reg) -> str:
    def block(name, items, key, desc):
        lines = [f"public enum {name} {{"]
        for it in items:
            camel = re.sub(r"_(\w)", lambda m: m.group(1).upper(), it[key])
            lines.append(f"    /// {it[desc]}")
            lines.append(f"    public static let {camel} = \"{it[key]}\"")
        lines.append("}\n")
        return "\n".join(lines)

    return (
        f"// {HEADER}\n\n"
        + block("Pages", reg["pages"], "pgid", "desc")
        + "\n" + block("Elements", reg["elements"], "eid", "desc")
        + "\n" + block("Params", reg["params"], "key", "desc")
        + "\n" + block("BizEvents", reg["events"], "code", "desc")
    )


OUTPUTS = {
    "sdk/web/src/generated.ts": lambda reg: gen_ts(reg),
    "sdk/ios/Sources/GISOTracker/Generated.swift": lambda reg: gen_swift(reg),
    "sdk/flutter/giso_tracker/lib/src/generated.dart": lambda reg: gen_dart(reg),
}


def main():
    check_only = "--check" in sys.argv
    reg = load()
    errs = validate(reg)
    if errs:
        print("注册表校验失败:")
        for e in errs:
            print("  -", e)
        sys.exit(1)
    print(f"注册表校验通过: {len(reg['params'])} 参数, {len(reg['pages'])} 页面, "
          f"{len(reg['elements'])} 元素, {len(reg['events'])} 业务事件")

    files: dict[Path, str] = {}
    for rel, fn in OUTPUTS.items():
        files[ROOT / rel] = fn(reg)
    for name, content in gen_java(reg).items():
        files[ROOT / "sdk/android/src/main/java/com/giso/tracker" / name] = content

    dirty = []
    for path, content in files.items():
        if not path.exists() or path.read_text() != content:
            dirty.append(path)
        if not check_only:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)

    if check_only and dirty:
        print("以下生成物与注册表不同步，请运行 python3 tools/codegen/generate.py 并提交:")
        for p in dirty:
            print("  -", p.relative_to(ROOT))
        sys.exit(1)
    print(("已更新" if not check_only else "生成物同步") + f": {len(files)} 个文件")


if __name__ == "__main__":
    main()
