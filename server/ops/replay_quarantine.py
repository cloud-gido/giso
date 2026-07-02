#!/usr/bin/env python3
"""
隔离区事件修复回放：消费 quarantine topic 或 JSONL，剥离 _issues 后重新 POST /v1/track。

用法:
  # 从 Kafka 回放（修 registry 后）
  export GISO_KAFKA_BOOTSTRAP=localhost:9092
  python3 server/ops/replay_quarantine.py replay \\
    --gateway http://localhost:8123 --app-key video-android-beta --limit 100

  # 从文件回放
  python3 server/ops/replay_quarantine.py replay --input /tmp/quarantine.jsonl --dry-run

  # 抽样到文件
  python3 server/ops/replay_quarantine.py sample --output /tmp/q.jsonl --limit 50
"""
from __future__ import annotations

import argparse
import json
import os
import sys
import urllib.request
from typing import Any, Iterator


def iter_kafka(topic: str, limit: int) -> Iterator[dict[str, Any]]:
    bootstrap = os.environ.get("GISO_KAFKA_BOOTSTRAP", "localhost:9092")
    from kafka import KafkaConsumer

    consumer = KafkaConsumer(
        topic,
        bootstrap_servers=bootstrap.split(","),
        auto_offset_reset="earliest",
        consumer_timeout_ms=8000,
        value_deserializer=lambda v: json.loads(v.decode("utf-8")),
    )
    n = 0
    for msg in consumer:
        yield msg.value
        n += 1
        if n >= limit:
            break


def iter_file(path: str, limit: int) -> Iterator[dict[str, Any]]:
    n = 0
    with open(path, encoding="utf-8") as f:
        for line in f:
            line = line.strip()
            if not line:
                continue
            yield json.loads(line)
            n += 1
            if n >= limit:
                break


def strip_envelope(raw: dict[str, Any]) -> dict[str, Any]:
    out = dict(raw)
    for k in ("_issues", "_quality", "stime"):
        out.pop(k, None)
    return out


def post_track(gateway: str, app_key: str, events: list[dict[str, Any]]) -> tuple[int, str]:
    url = gateway.rstrip("/") + "/v1/track"
    data = json.dumps(events).encode("utf-8")
    req = urllib.request.Request(
        url,
        data=data,
        headers={"Content-Type": "application/json", "X-App-Key": app_key},
        method="POST",
    )
    with urllib.request.urlopen(req, timeout=30) as resp:
        return resp.status, resp.read().decode("utf-8", errors="replace")


def cmd_sample(args: argparse.Namespace) -> int:
    it = iter_kafka(args.topic, args.limit) if not args.input else iter_file(args.input, args.limit)
    n = 0
    with open(args.output, "w", encoding="utf-8") as out:
        for ev in it:
            out.write(json.dumps(ev, ensure_ascii=False) + "\n")
            n += 1
    print(f"sampled {n} -> {args.output}")
    return 0


def cmd_replay(args: argparse.Namespace) -> int:
    it = iter_kafka(args.topic, args.limit) if not args.input else iter_file(args.input, args.limit)
    ok = fail = 0
    batch: list[dict[str, Any]] = []
    for raw in it:
        batch.append(strip_envelope(raw))
        if len(batch) < args.batch:
            continue
        if args.dry_run:
            ok += len(batch)
        else:
            try:
                code, _ = post_track(args.gateway, args.app_key, batch)
                ok += len(batch) if code == 204 else 0
                fail += 0 if code == 204 else len(batch)
            except Exception as e:
                print(f"batch failed: {e}", file=sys.stderr)
                fail += len(batch)
        batch = []
    if batch:
        if args.dry_run:
            ok += len(batch)
        else:
            try:
                code, _ = post_track(args.gateway, args.app_key, batch)
                ok += len(batch) if code == 204 else 0
                fail += 0 if code == 204 else len(batch)
            except Exception as e:
                print(f"batch failed: {e}", file=sys.stderr)
                fail += len(batch)
    print(f"replay done: ok={ok} fail={fail} dry_run={args.dry_run}")
    return 1 if fail else 0


def main() -> int:
    parser = argparse.ArgumentParser(description="GISO quarantine sample/replay")
    sub = parser.add_subparsers(dest="cmd", required=True)

    p_sample = sub.add_parser("sample", help="export quarantine events to JSONL")
    p_sample.add_argument("--topic", default="giso_events_quarantine")
    p_sample.add_argument("--input", help="read JSONL instead of Kafka")
    p_sample.add_argument("--output", required=True)
    p_sample.add_argument("--limit", type=int, default=100)

    p_replay = sub.add_parser("replay", help="re-validate via gateway /v1/track")
    p_replay.add_argument("--gateway", default=os.environ.get("GISO_GATEWAY", "http://localhost:8123"))
    p_replay.add_argument("--app-key", default=os.environ.get("GISO_APP_KEY", "video-android-beta"))
    p_replay.add_argument("--topic", default="giso_events_quarantine")
    p_replay.add_argument("--input", help="JSONL file instead of Kafka")
    p_replay.add_argument("--limit", type=int, default=500)
    p_replay.add_argument("--batch", type=int, default=20)
    p_replay.add_argument("--dry-run", action="store_true")

    args = parser.parse_args()
    try:
        import kafka  # noqa: F401
    except ImportError:
        if not args.input and getattr(args, "cmd", "") != "":
            print("pip install kafka-python (or pass --input JSONL)", file=sys.stderr)
            return 1

    if args.cmd == "sample":
        return cmd_sample(args)
    if args.cmd == "replay":
        return cmd_replay(args)
    return 1


if __name__ == "__main__":
    raise SystemExit(main())
