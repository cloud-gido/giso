#!/usr/bin/env python3
"""Kafka → ClickHouse 落地桥（幂等 + Prometheus metrics）。"""
from __future__ import annotations

import json
import os
import threading
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import OrderedDict
from http.server import BaseHTTPRequestHandler, HTTPServer
from typing import Any

from kafka import KafkaConsumer

KAFKA = os.environ.get("KAFKA_BOOTSTRAP", "kafka:9092")
CH_URL = os.environ.get("CLICKHOUSE_URL", "http://clickhouse:8123")
TOPICS = os.environ.get("KAFKA_TOPICS", "giso_events_raw,giso_events_raw_test,giso_events_quarantine").split(",")
BATCH_SIZE = int(os.environ.get("BATCH_SIZE", "50"))
FLUSH_SEC = float(os.environ.get("FLUSH_SEC", "2"))
METRICS_PORT = int(os.environ.get("METRICS_PORT", "9100"))
DEDUP_CACHE = int(os.environ.get("DEDUP_CACHE", "100000"))

_METRICS = {
    "inserted_total": 0,
    "insert_failed_total": 0,
    "dedup_skipped_total": 0,
    "parse_errors_total": 0,
}
_SEEN: OrderedDict[str, bool] = OrderedDict()


def jdump(obj: Any) -> str:
    if obj is None:
        return ""
    return json.dumps(obj, ensure_ascii=False, separators=(",", ":"))


def ms_to_dt(ms: int) -> str:
    sec = ms / 1000.0
    return time.strftime("%Y-%m-%d %H:%M:%S", time.gmtime(sec)) + f".{ms % 1000:03d}"


def esc_sql(s: str) -> str:
    return s.replace("\\", "\\\\").replace("'", "\\'")


def flatten(raw: dict, topic: str) -> tuple[str, dict]:
    common = raw.get("common") or {}
    page = raw.get("page") or {}
    element = raw.get("element") or {}
    biz = raw.get("biz") or {}
    stime_ms = int(raw.get("stime") or int(time.time() * 1000))
    env = common.get("env") or ("test" if topic.endswith("_test") else "prod")
    quarantine = topic == "giso_events_quarantine" or raw.get("_issues") is not None

    if topic == "giso_events_quarantine":
        row = {
            "event_date": time.strftime("%Y-%m-%d", time.gmtime(stime_ms / 1000)),
            "stime": ms_to_dt(stime_ms),
            "event": raw.get("event", ""),
            "env": env,
            "app_id": common.get("app_id", ""),
            "platform": common.get("platform", ""),
            "did": common.get("did", ""),
            "issues": jdump(raw.get("_issues")),
            "raw": jdump(raw),
        }
        return "tracking.ods_events_quarantine", row

    pg_params = page.get("pg_params")
    el_params = element.get("params")
    known = {
        "app_id", "platform", "app_vrsn", "did", "uid", "session_id", "channel", "env"
    }
    common_ext = {k: v for k, v in common.items() if k not in known}

    row = {
        "event_date": time.strftime("%Y-%m-%d", time.gmtime(stime_ms / 1000)),
        "event": raw.get("event", ""),
        "stime": ms_to_dt(stime_ms),
        "ctime": int(raw.get("ctime") or 0),
        "log_id": raw.get("log_id", ""),
        "env": env,
        "app_id": common.get("app_id", ""),
        "platform": common.get("platform", ""),
        "app_vrsn": common.get("app_vrsn", ""),
        "did": common.get("did", ""),
        "uid": common.get("uid", ""),
        "session_id": common.get("session_id", ""),
        "channel": common.get("channel", ""),
        "pgid": page.get("pgid", ""),
        "ref_pgid": page.get("ref_pgid", ""),
        "ref_eid": page.get("ref_eid", ""),
        "pg_stay": int(page.get("pg_stay") or 0),
        "pg_params": jdump(pg_params) if pg_params else "",
        "eid": element.get("eid", ""),
        "mod": element.get("mod", ""),
        "pos": int(element.get("pos") or 0),
        "exp_dur": int(element.get("exp_dur") or 0),
        "exp_ratio": float(element.get("exp_ratio") or 0),
        "el_params": jdump(el_params) if el_params else "",
        "biz_code": biz.get("code", ""),
        "biz_params": jdump(biz.get("params")) if biz.get("params") else "",
        "quality": raw.get("_quality", "ok"),
        "issues": jdump(raw.get("_issues")) if raw.get("_issues") else "",
        "common_ext": jdump(common_ext) if common_ext else "",
        "pt": jdump(raw.get("pt")) if raw.get("pt") else "",
        "is_quarantine": 1 if quarantine else 0,
    }
    return "tracking.ods_events", row


def insert(table: str, rows: list[dict]) -> None:
    if not rows:
        return
    body = "\n".join(json.dumps(r, ensure_ascii=False) for r in rows).encode("utf-8")
    query = f"INSERT INTO {table} FORMAT JSONEachRow"
    req = urllib.request.Request(
        f"{CH_URL}/?query={urllib.parse.quote(query)}",
        data=body,
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        resp.read()


def ch_existing_log_ids(ids: list[str]) -> set[str]:
    if not ids:
        return set()
    in_list = ",".join(f"'{esc_sql(i)}'" for i in ids)
    sql = f"SELECT log_id FROM tracking.ods_events WHERE log_id IN ({in_list}) FORMAT TabSeparated"
    req = urllib.request.Request(
        f"{CH_URL}/?query={urllib.parse.quote(sql)}",
        method="GET",
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        body = resp.read().decode("utf-8")
    return {line.strip() for line in body.splitlines() if line.strip()}


def mark_seen(log_ids: list[str]) -> None:
    for lid in log_ids:
        if not lid:
            continue
        _SEEN[lid] = True
        _SEEN.move_to_end(lid)
    while len(_SEEN) > DEDUP_CACHE:
        _SEEN.popitem(last=False)


def dedup_rows(rows: list[dict]) -> list[dict]:
    if not rows:
        return rows
    candidates = [r for r in rows if r.get("log_id")]
    if not candidates:
        return rows
    ids = [r["log_id"] for r in candidates]
    known = {i for i in ids if i in _SEEN}
    try:
        known |= ch_existing_log_ids([i for i in ids if i not in known])
    except Exception as e:  # noqa: BLE001
        print(f"[event-bridge] dedup query failed: {e}", flush=True)
    out = []
    skipped = 0
    for r in rows:
        lid = r.get("log_id", "")
        if lid and lid in known:
            skipped += 1
            continue
        out.append(r)
    if skipped:
        _METRICS["dedup_skipped_total"] += skipped
    return out


def wait_clickhouse() -> None:
    for _ in range(60):
        try:
            with urllib.request.urlopen(f"{CH_URL}/ping", timeout=3) as r:
                if r.read().strip().startswith(b"Ok"):
                    return
        except (urllib.error.URLError, TimeoutError):
            pass
        time.sleep(2)
    raise RuntimeError("clickhouse not ready")


def render_metrics() -> str:
    lines = [
        "# HELP giso_bridge_inserted_total Rows inserted into ClickHouse",
        "# TYPE giso_bridge_inserted_total counter",
        f"giso_bridge_inserted_total {_METRICS['inserted_total']}",
        "# HELP giso_bridge_insert_failed_total Failed insert batches",
        "# TYPE giso_bridge_insert_failed_total counter",
        f"giso_bridge_insert_failed_total {_METRICS['insert_failed_total']}",
        "# HELP giso_bridge_dedup_skipped_total Duplicate log_id rows skipped",
        "# TYPE giso_bridge_dedup_skipped_total counter",
        f"giso_bridge_dedup_skipped_total {_METRICS['dedup_skipped_total']}",
        "# HELP giso_bridge_parse_errors_total Kafka message parse errors",
        "# TYPE giso_bridge_parse_errors_total counter",
        f"giso_bridge_parse_errors_total {_METRICS['parse_errors_total']}",
    ]
    return "\n".join(lines) + "\n"


class MetricsHandler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):  # noqa: ANN001
        return

    def do_GET(self) -> None:  # noqa: N802
        if self.path not in ("/metrics", "/"):
            self.send_response(404)
            self.end_headers()
            return
        body = render_metrics().encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "text/plain; version=0.0.4; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)


def start_metrics() -> None:
    HTTPServer(("0.0.0.0", METRICS_PORT), MetricsHandler).serve_forever()


def main() -> None:
    threading.Thread(target=start_metrics, daemon=True).start()
    wait_clickhouse()
    print(f"[event-bridge] consuming {TOPICS} → {CH_URL} metrics :{METRICS_PORT}", flush=True)

    consumer = KafkaConsumer(
        *TOPICS,
        bootstrap_servers=KAFKA,
        auto_offset_reset="earliest",
        enable_auto_commit=True,
        group_id="giso-event-bridge",
        value_deserializer=lambda m: json.loads(m.decode("utf-8")),
        consumer_timeout_ms=1000,
    )

    pending: dict[str, list[dict]] = {}
    last_flush = time.time()

    while True:
        polled = False
        for msg in consumer:
            polled = True
            try:
                table, row = flatten(msg.value, msg.topic)
                pending.setdefault(table, []).append(row)
            except Exception as e:  # noqa: BLE001
                _METRICS["parse_errors_total"] += 1
                print(f"[event-bridge] parse error: {e}", flush=True)
            if sum(len(v) for v in pending.values()) >= BATCH_SIZE:
                break

        now = time.time()
        if pending and (polled or now - last_flush >= FLUSH_SEC):
            for table, rows in list(pending.items()):
                try:
                    if table == "tracking.ods_events":
                        rows = dedup_rows(rows)
                    if not rows:
                        continue
                    insert(table, rows)
                    if table == "tracking.ods_events":
                        mark_seen([r.get("log_id", "") for r in rows])
                    _METRICS["inserted_total"] += len(rows)
                    print(f"[event-bridge] inserted {len(rows)} → {table}", flush=True)
                except Exception as e:  # noqa: BLE001
                    _METRICS["insert_failed_total"] += 1
                    print(f"[event-bridge] insert failed: {e}", flush=True)
            pending.clear()
            last_flush = now


if __name__ == "__main__":
    main()
