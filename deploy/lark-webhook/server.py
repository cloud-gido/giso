#!/usr/bin/env python3
"""Alertmanager webhook → 飞书/Lark 机器人。"""
from __future__ import annotations

import json
import os
import urllib.error
import urllib.request
from http.server import BaseHTTPRequestHandler, HTTPServer

LARK_URL = os.environ["LARK_WEBHOOK_URL"]
PORT = int(os.environ.get("PORT", "8080"))


def to_lark(payload: dict) -> dict:
    status = payload.get("status", "firing")
    alerts = payload.get("alerts") or []
    lines = [f"GISO 埋点告警 [{status.upper()}]"]
    for a in alerts:
        labels = a.get("labels") or {}
        ann = a.get("annotations") or {}
        sev = labels.get("severity", "?")
        name = labels.get("alertname", "?")
        lines.append(f"\n[{sev}] {name}")
        if ann.get("summary"):
            lines.append(str(ann["summary"]))
        if ann.get("description"):
            lines.append(str(ann["description"]))
    text = "\n".join(lines)[:4000]
    return {"msg_type": "text", "content": {"text": text}}


def post_lark(body: dict) -> None:
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        LARK_URL,
        data=data,
        method="POST",
        headers={"Content-Type": "application/json"},
    )
    with urllib.request.urlopen(req, timeout=10) as resp:
        resp.read()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, fmt, *args):  # noqa: ANN001
        print(f"[lark-webhook] {args[0]}", flush=True)

    def do_GET(self) -> None:  # noqa: N802
        if self.path in ("/", "/health"):
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"ok")
            return
        self.send_response(404)
        self.end_headers()

    def do_POST(self) -> None:  # noqa: N802
        if self.path not in ("/", "/webhook"):
            self.send_response(404)
            self.end_headers()
            return
        length = int(self.headers.get("Content-Length", 0))
        raw = self.rfile.read(length)
        try:
            payload = json.loads(raw.decode("utf-8"))
            post_lark(to_lark(payload))
            self.send_response(200)
            self.end_headers()
            self.wfile.write(b"ok")
        except Exception as e:  # noqa: BLE001
            print(f"[lark-webhook] error: {e}", flush=True)
            self.send_response(500)
            self.end_headers()
            self.wfile.write(str(e).encode("utf-8"))


def main() -> None:
    print(f"[lark-webhook] listening :{PORT} → Lark", flush=True)
    HTTPServer(("0.0.0.0", PORT), Handler).serve_forever()


if __name__ == "__main__":
    main()
