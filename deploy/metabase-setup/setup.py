#!/usr/bin/env python3
"""Metabase 一次性初始化：管理员账号 + ClickHouse 数据源 + GISO 预置看板。"""
from __future__ import annotations

import json
import os
import socket
import time
import urllib.error
import urllib.parse
import urllib.request

MB = os.environ.get("METABASE_URL", "http://metabase:3000").rstrip("/")
EMAIL = os.environ.get("MB_ADMIN_EMAIL", "admin@giso.local")
PASSWORD = os.environ.get("MB_ADMIN_PASSWORD", "Giso@Metabase2026")
FIRST = os.environ.get("MB_ADMIN_FIRST", "GISO")
LAST = os.environ.get("MB_ADMIN_LAST", "Admin")
CH_HOST = os.environ.get("CH_HOST", "clickhouse")
CH_PORT = int(os.environ.get("CH_PORT", "8123"))
DORIS_HOST = os.environ.get("DORIS_FE_HOST", "doris-fe")
DORIS_PORT = int(os.environ.get("DORIS_FE_PORT", "9030"))


def as_list(resp: dict | list, key: str = "data") -> list:
    if isinstance(resp, list):
        return resp
    if isinstance(resp, dict) and key in resp and isinstance(resp[key], list):
        return resp[key]
    return []


def http(
    path: str,
    method: str = "GET",
    body: dict | None = None,
    session: str | None = None,
) -> dict | list:
    headers = {"Content-Type": "application/json"}
    if session:
        headers["X-Metabase-Session"] = session
    data = None if body is None else json.dumps(body).encode("utf-8")
    req = urllib.request.Request(f"{MB}{path}", data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            raw = resp.read().decode("utf-8")
            return json.loads(raw) if raw else {}
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"{method} {path} -> {e.code}: {detail}") from e


def wait_ready() -> None:
    for i in range(90):
        try:
            http("/api/health")
            return
        except Exception:
            print(f"[metabase-setup] waiting metabase ({i + 1}/90)...", flush=True)
            time.sleep(2)
    raise RuntimeError("metabase not ready")


def session() -> str | None:
    props = http("/api/session/properties")
    if props.get("has-user-setup"):
        try:
            out = http("/api/session", "POST", {"username": EMAIL, "password": PASSWORD})
            return out["id"]
        except RuntimeError as e:
            if "401" in str(e):
                print(
                    "[metabase-setup] skip: Metabase 已有其他管理员账号，"
                    "请手动连 ClickHouse 或删除 volume 后重建",
                    flush=True,
                )
                return None
            raise
    token = props.get("setup-token")
    if not token:
        raise RuntimeError("no setup-token")
    http(
        "/api/setup",
        "POST",
        {
            "token": token,
            "user": {
                "email": EMAIL,
                "password": PASSWORD,
                "first_name": FIRST,
                "last_name": LAST,
            },
            "prefs": {
                "site_name": "GISO 埋点分析",
                "site_locale": "zh",
            },
        },
    )
    out = http("/api/session", "POST", {"username": EMAIL, "password": PASSWORD})
    print("[metabase-setup] admin account created", flush=True)
    return out["id"]


def find_db(sid: str) -> int | None:
    for db in as_list(http("/api/database", session=sid)):
        if db.get("engine") == "clickhouse" and db.get("name") == "GISO ClickHouse":
            return db["id"]
    return None


def ensure_clickhouse(sid: str) -> int:
    existing = find_db(sid)
    if existing:
        return existing
    out = http(
        "/api/database",
        "POST",
        {
            "name": "GISO ClickHouse",
            "engine": "clickhouse",
            "details": {
                "host": CH_HOST,
                "port": CH_PORT,
                "user": "default",
                "password": None,
                "ssl": False,
                "tunnel-enabled": False,
                "advanced-options": False,
            },
            "auto_run_queries": True,
            "is_full_sync": True,
        },
        session=sid,
    )
    print(f"[metabase-setup] clickhouse database id={out['id']}", flush=True)
    return out["id"]


def doris_reachable() -> bool:
    try:
        with socket.create_connection((DORIS_HOST, DORIS_PORT), timeout=3):
            return True
    except OSError:
        return False


def find_doris_db(sid: str) -> int | None:
    for db in as_list(http("/api/database", session=sid)):
        if db.get("engine") == "mysql" and db.get("name") == "GISO Doris":
            return db["id"]
    return None


def ensure_doris(sid: str) -> int:
    existing = find_doris_db(sid)
    if existing:
        return existing
    out = http(
        "/api/database",
        "POST",
        {
            "name": "GISO Doris",
            "engine": "mysql",
            "details": {
                "host": DORIS_HOST,
                "port": DORIS_PORT,
                "dbname": "tracking",
                "user": "root",
                "password": None,
                "ssl": False,
                "tunnel-enabled": False,
                "advanced-options": False,
            },
            "auto_run_queries": True,
            "is_full_sync": True,
        },
        session=sid,
    )
    print(f"[metabase-setup] doris database id={out['id']}", flush=True)
    return out["id"]


def find_collection(sid: str, name: str) -> int | None:
    for c in as_list(http("/api/collection", session=sid)):
        if c.get("name") == name:
            return c["id"]
    return None


def ensure_collection(sid: str, name: str) -> int:
    cid = find_collection(sid, name)
    if cid:
        return cid
    out = http("/api/collection", "POST", {"name": name, "color": "#509EE3"}, session=sid)
    return out["id"]


def find_card(sid: str, name: str) -> int | None:
    try:
        items = as_list(http("/api/card", session=sid))
        for c in items:
            if c.get("name") == name and not c.get("archived"):
                return c["id"]
    except Exception:
        pass
    return None


def ensure_card(sid: str, db_id: int, collection_id: int, name: str, sql: str, display: str) -> int:
    cid = find_card(sid, name)
    if cid:
        return cid
    out = http(
        "/api/card",
        "POST",
        {
            "name": name,
            "collection_id": collection_id,
            "display": display,
            "dataset_query": {
                "type": "native",
                "database": db_id,
                "native": {"query": sql, "template-tags": {}},
            },
            "visualization_settings": {},
        },
        session=sid,
    )
    print(f"[metabase-setup] card {name} id={out['id']}", flush=True)
    return out["id"]


def find_dashboard(sid: str, name: str) -> int | None:
    for d in as_list(http("/api/dashboard", session=sid)):
        if d.get("name") == name:
            return d["id"]
    return None


def mount_dashboard(
    sid: str,
    collection_id: int,
    name: str,
    layouts: list[tuple[int, int, int, int, int]],
    *,
    force: bool = False,
) -> None:
    """layouts: (card_id, row, col, size_x, size_y)"""
    dash_id = find_dashboard(sid, name)
    if dash_id is None:
        dash = http(
            "/api/dashboard",
            "POST",
            {"name": name, "collection_id": collection_id, "parameters": []},
            session=sid,
        )
        dash_id = dash["id"]
    else:
        detail = http(f"/api/dashboard/{dash_id}", session=sid)
        if detail.get("dashcards") and not force:
            print(f"[metabase-setup] dashboard {name} id={dash_id} already has cards", flush=True)
            return

    dashcards = []
    for i, (cid, row, col, sx, sy) in enumerate(layouts):
        dashcards.append(
            {
                "id": -1 - i,
                "card_id": cid,
                "row": row,
                "col": col,
                "size_x": sx,
                "size_y": sy,
                "series": [],
                "parameter_mappings": [],
                "visualization_settings": {},
            }
        )

    payload = {
        "name": name,
        "collection_id": collection_id,
        "parameters": [],
        "dashcards": dashcards,
    }
    try:
        http(f"/api/dashboard/{dash_id}", "PUT", payload, session=sid)
    except RuntimeError:
        payload["cards"] = payload.pop("dashcards")
        http(f"/api/dashboard/{dash_id}", "PUT", payload, session=sid)

    detail = http(f"/api/dashboard/{dash_id}", session=sid)
    if not detail.get("dashcards"):
        for i, (cid, row, col, sx, sy) in enumerate(layouts):
            try:
                http(
                    f"/api/dashboard/{dash_id}/cards",
                    "POST",
                    {"cardId": cid, "row": row, "col": col, "size_x": sx, "size_y": sy},
                    session=sid,
                )
            except RuntimeError as e:
                print(f"[metabase-setup] add card {cid} warn: {e}", flush=True)

    print(f"[metabase-setup] dashboard {name} id={dash_id} with {len(layouts)} cards", flush=True)


def ensure_dashboard(sid: str, collection_id: int, name: str, card_ids: list[int]) -> None:
    layouts = []
    for i, cid in enumerate(card_ids):
        row, col = (i // 2) * 8, (i % 2) * 12
        layouts.append((cid, row, col, 12, 8))
    mount_dashboard(sid, collection_id, name, layouts)


def setup_clickhouse_dashboard(sid: str, coll_id: int, env: str = "prod") -> None:
    db_id = ensure_clickhouse(sid)
    label = env
    cards = [
        (
            f"每日 DAU（{label}）",
            f"SELECT event_date, dau, launch_users FROM tracking.v_daily_active WHERE env = '{env}' ORDER BY event_date",
            "line",
        ),
        (
            f"事件质量（{label}）",
            f"SELECT event_date, event, total, missing, errors FROM tracking.v_daily_quality WHERE env = '{env}' ORDER BY event_date, event",
            "table",
        ),
        (
            f"视频播放（{label}）",
            f"SELECT event_date, vid, starts, ends, round(avg_play_dur, 1) AS avg_play_dur FROM tracking.v_video_play WHERE env = '{env}' ORDER BY event_date DESC, starts DESC LIMIT 50",
            "table",
        ),
        (
            f"元素 CTR（{label}）",
            f"SELECT event_date, pgid, eid, exposures, clicks, round(ctr, 4) AS ctr FROM tracking.v_element_ctr WHERE env = '{env}' ORDER BY event_date DESC, exposures DESC LIMIT 50",
            "table",
        ),
    ]
    card_ids = [ensure_card(sid, db_id, coll_id, n, sql, disp) for n, sql, disp in cards]
    title = "GISO 埋点总览" if env == "prod" else f"GISO 埋点总览（{env}）"
    ensure_dashboard(sid, coll_id, title, card_ids)


def _doris_metric_cards(env: str) -> list[tuple[str, str, str]]:
    env_filter = f"WHERE env = '{env}'"
    return [
        (
            f"Doris · 每日 DAU（{env}）",
            f"""
SELECT event_date,
       COUNT(DISTINCT did) AS dau,
       COUNT(DISTINCT CASE WHEN event = 'app_launch' THEN did END) AS launch_users
FROM tracking.ods_events
{env_filter}
GROUP BY event_date
ORDER BY event_date
""".strip(),
            "line",
        ),
        (
            f"Doris · 事件质量（{env}）",
            f"""
SELECT event_date,
       event,
       COUNT(*) AS total,
       SUM(CASE WHEN quality = 'missing' THEN 1 ELSE 0 END) AS missing
FROM tracking.ods_events
{env_filter}
GROUP BY event_date, event
ORDER BY event_date, event
""".strip(),
            "table",
        ),
        (
            f"Doris · 视频播放（{env}）",
            f"""
SELECT event_date,
       get_json_string(biz_params, '$.vid') AS vid,
       SUM(CASE WHEN event = 'biz_event' AND biz_code = 'video_play_start' THEN 1 ELSE 0 END) AS starts,
       SUM(CASE WHEN event = 'biz_event' AND biz_code = 'video_play_end' THEN 1 ELSE 0 END) AS ends,
       ROUND(AVG(CASE WHEN event = 'biz_event' AND biz_code = 'video_play_end'
           THEN CAST(get_json_string(biz_params, '$.play_dur') AS DOUBLE) END), 1) AS avg_play_dur
FROM tracking.ods_events
{env_filter}
GROUP BY event_date, get_json_string(biz_params, '$.vid')
HAVING starts > 0
ORDER BY event_date DESC, starts DESC
LIMIT 50
""".strip(),
            "table",
        ),
        (
            f"Doris · 元素 CTR（{env}）",
            f"""
SELECT event_date,
       pgid,
       eid,
       SUM(CASE WHEN event = 'element_exposure' THEN 1 ELSE 0 END) AS exposures,
       SUM(CASE WHEN event = 'element_click' THEN 1 ELSE 0 END) AS clicks,
       CASE WHEN SUM(CASE WHEN event = 'element_exposure' THEN 1 ELSE 0 END) = 0 THEN 0
            ELSE SUM(CASE WHEN event = 'element_click' THEN 1 ELSE 0 END)
               / SUM(CASE WHEN event = 'element_exposure' THEN 1 ELSE 0 END)
       END AS ctr
FROM tracking.ods_events
{env_filter} AND eid IS NOT NULL AND eid != ''
GROUP BY event_date, pgid, eid
ORDER BY event_date DESC, exposures DESC
LIMIT 50
""".strip(),
            "table",
        ),
    ]


def setup_doris_dashboard(sid: str, coll_id: int, env: str = "prod") -> None:
    if not doris_reachable():
        if env == "prod":
            print(
                f"[metabase-setup] skip Doris dashboard ({DORIS_HOST}:{DORIS_PORT} unreachable)",
                flush=True,
            )
        return
    db_id = ensure_doris(sid)
    cards = list(_doris_metric_cards(env))
    if env == "prod":
        cards.append((
            "Doris · 隔离区",
            """
SELECT event_date, event, COUNT(*) AS errors
FROM tracking.ods_events_quarantine
GROUP BY event_date, event
ORDER BY event_date DESC, errors DESC
LIMIT 50
""".strip(),
            "table",
        ))
    card_ids = [ensure_card(sid, db_id, coll_id, n, sql, disp) for n, sql, disp in cards]
    title = "GISO 埋点总览（Doris）" if env == "prod" else f"GISO 埋点总览（Doris · {env}）"
    layouts = [(cid, (i // 2) * 8, (i % 2) * 12, 12, 8) for i, cid in enumerate(card_ids)]
    mount_dashboard(sid, coll_id, title, layouts, force=True)


def setup_unified_dashboard(sid: str, coll_id: int, env: str = "prod") -> None:
    """左右对照：左列 ClickHouse，右列 Doris。"""
    pairs = [
        (f"每日 DAU（{env}）", f"Doris · 每日 DAU（{env}）"),
        (f"事件质量（{env}）", f"Doris · 事件质量（{env}）"),
        (f"视频播放（{env}）", f"Doris · 视频播放（{env}）"),
        (f"元素 CTR（{env}）", f"Doris · 元素 CTR（{env}）"),
    ]
    layouts: list[tuple[int, int, int, int, int]] = []
    for row_i, (ch_name, d_name) in enumerate(pairs):
        row = row_i * 8
        ch_id = find_card(sid, ch_name)
        if ch_id:
            layouts.append((ch_id, row, 0, 12, 8))
        if doris_reachable():
            d_id = find_card(sid, d_name)
            if d_id:
                layouts.append((d_id, row, 12, 12, 8))
    if env == "prod":
        q_id = find_card(sid, "Doris · 隔离区")
        if q_id:
            layouts.append((q_id, len(pairs) * 8, 0, 24, 6))
    if not layouts:
        print(f"[metabase-setup] skip unified dashboard env={env} (no cards)", flush=True)
        return
    title = "GISO 数据总览" if env == "prod" else f"GISO 数据总览（{env}）"
    mount_dashboard(sid, coll_id, title, layouts, force=True)


def main() -> None:
    wait_ready()
    sid = session()
    if sid is None:
        return
    coll_id = ensure_collection(sid, "GISO 埋点分析")
    setup_clickhouse_dashboard(sid, coll_id, "prod")
    setup_clickhouse_dashboard(sid, coll_id, "test")
    setup_doris_dashboard(sid, coll_id, "prod")
    setup_doris_dashboard(sid, coll_id, "test")
    setup_unified_dashboard(sid, coll_id, "prod")
    setup_unified_dashboard(sid, coll_id, "test")
    print("[metabase-setup] done — 联调看板: GISO 数据总览（test）", flush=True)


if __name__ == "__main__":
    main()
