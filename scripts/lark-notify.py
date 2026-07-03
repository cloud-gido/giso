#!/usr/bin/env python3
"""GISO → Lark 交互卡片（链接默认 GitLab 主仓）。

模式:
  push     每次推 main 后通知（默认，测试 webhook）
  release  版本发布通知

Webhook（勿提交仓库）:
  LARK_WEBHOOK_URL_TEST   测试群 — push 默认 / CI 自动
  LARK_WEBHOOK_URL_PROD   正式群 — 仅 --channel prod --approve-prod 时发送

用法:
  python3 scripts/lark-notify.py --mode push --commit $(git rev-parse HEAD)
  python3 scripts/lark-notify.py --mode release --version 1.0.1
  python3 scripts/lark-notify.py --mode push --channel prod --approve-prod  # 需显式确认
"""
from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import urllib.error
import urllib.request
from datetime import date

DEFAULT_GITLAB = "https://gitlab.com/gamelinelab/data/giso"
DEFAULT_ADMIN = "https://gamelinelab-giso.envir.dev/admin/"


def gitlab_blob(base: str, path: str, ref: str = "main") -> str:
    return f"{base.rstrip('/')}/-/blob/{ref}/{path}"


def gitlab_commit(base: str, sha: str) -> str:
    return f"{base.rstrip('/')}/-/commit/{sha}"


def git_show_stat(sha: str) -> str:
    try:
        out = subprocess.check_output(
            ["git", "show", "--stat", "--format=", sha],
            stderr=subprocess.DEVNULL,
            text=True,
        )
        lines = [ln.rstrip() for ln in out.strip().splitlines() if ln.strip()]
        if len(lines) > 12:
            lines = lines[:12] + [f"… 共 {len(lines)} 个文件变更"]
        return "\n".join(f"`{ln}`" if " | " in ln else ln for ln in lines) or "—"
    except (subprocess.CalledProcessError, FileNotFoundError):
        return "—"


def resolve_webhook(channel: str, approve_prod: bool, explicit: str | None) -> str | None:
    if explicit:
        return explicit
    if channel == "prod":
        if not approve_prod:
            print("error: 正式 Lark 须加 --approve-prod", file=sys.stderr)
            return None
        return os.environ.get("LARK_WEBHOOK_URL_PROD")
    return os.environ.get("LARK_WEBHOOK_URL_TEST") or os.environ.get("LARK_WEBHOOK_URL")


def build_push_card(
    *,
    gitlab_base: str,
    commit: str,
    message: str,
    channel: str,
    stat: str,
) -> dict:
    sha = commit[:7]
    link = gitlab_commit(gitlab_base, commit)
    admin_doc = gitlab_blob(gitlab_base, "docs/tracking/15-%E7%AE%A1%E7%90%86%E6%8E%A7%E5%88%B6%E5%8F%B0%E4%BA%A7%E5%93%81%E4%BB%8B%E7%BB%8D.md")
    env_label = "正式" if channel == "prod" else "测试"
    template = "green" if channel == "prod" else "wathet"

    return {
        "msg_type": "interactive",
        "card": {
            "config": {"wide_screen_mode": True},
            "header": {
                "title": {"tag": "plain_text", "content": f"📦 GISO main 已推送 · [{env_label}]"},
                "template": template,
            },
            "elements": [
                {
                    "tag": "div",
                    "text": {
                        "tag": "lark_md",
                        "content": (
                            f"**[{sha}]({link})** {message}\n"
                            f"📅 {date.today().isoformat()} · 链接指向 GitLab 主仓"
                        ),
                    },
                },
                {"tag": "hr"},
                {
                    "tag": "div",
                    "text": {"tag": "lark_md", "content": f"**变更摘要**\n{stat}"},
                },
                {"tag": "hr"},
                {
                    "tag": "div",
                    "text": {
                        "tag": "lark_md",
                        "content": (
                            "**🔗 入口**\n"
                            f"• [GitLab 仓库]({gitlab_base.rstrip('/')})\n"
                            f"• [本次 commit]({link})\n"
                            f"• [管理台产品介绍]({admin_doc})\n"
                            f"• 测试管理台：{DEFAULT_ADMIN}"
                        ),
                    },
                },
            ],
        },
    }


def build_release_card(
    version: str,
    *,
    gitlab_base: str,
    admin_url: str,
    commit: str | None,
    date_range: str | None,
    channel: str,
) -> dict:
    changelog = gitlab_blob(gitlab_base, "CHANGELOG.md")
    flutter_doc = gitlab_blob(gitlab_base, "docs/tracking/14-Flutter%E6%8E%A5%E5%85%A5%E6%8C%87%E5%8D%97.md")
    sdk_doc = gitlab_blob(gitlab_base, "docs/tracking/13-SDK%E5%88%86%E5%8F%91%E4%B8%8E%E7%89%88%E6%9C%AC.md")
    admin_doc = gitlab_blob(gitlab_base, "docs/tracking/15-%E7%AE%A1%E7%90%86%E6%8E%A7%E5%88%B6%E5%8F%B0%E4%BA%A7%E5%93%81%E4%BB%8B%E7%BB%8D.md")
    repo = gitlab_base.rstrip("/")
    env_label = "正式" if channel == "prod" else "测试"

    meta = date_range or date.today().isoformat()
    if commit:
        meta += f"  ·  [{commit[:7]}]({gitlab_commit(gitlab_base, commit)})"

    return {
        "msg_type": "interactive",
        "card": {
            "config": {"wide_screen_mode": True},
            "header": {
                "title": {"tag": "plain_text", "content": f"🚀 GISO · 玑源  v{version} 发布 · [{env_label}]"},
                "template": "blue",
            },
            "elements": [
                {
                    "tag": "div",
                    "text": {
                        "tag": "lark_md",
                        "content": (
                            "**GIDO 的数据源头** · Schema 驱动行为分析平台\n"
                            "五端 SDK + 管理台 v2 · PostgreSQL 注册表 · 多空间\n"
                            f"📅 {meta}"
                        ),
                    },
                },
                {"tag": "hr"},
                {
                    "tag": "div",
                    "fields": [
                        {
                            "is_short": True,
                            "text": {
                                "tag": "lark_md",
                                "content": f"**Flutter SDK**\n`giso_tracker`\n[接入指南]({flutter_doc})",
                            },
                        },
                        {
                            "is_short": True,
                            "text": {
                                "tag": "lark_md",
                                "content": f"**SDK 分发**\nMaven / npm / SPM\n[13-分发]({sdk_doc})",
                            },
                        },
                        {
                            "is_short": True,
                            "text": {
                                "tag": "lark_md",
                                "content": f"**管理台 v2**\n[截图介绍]({admin_doc})\n联调 · 审批 · CSV",
                            },
                        },
                        {
                            "is_short": True,
                            "text": {
                                "tag": "lark_md",
                                "content": "**平台化**\n多空间 · Session\n接入助手 · i18n",
                            },
                        },
                    ],
                },
                {"tag": "hr"},
                {
                    "tag": "div",
                    "text": {
                        "tag": "lark_md",
                        "content": (
                            f"**🔗 GitLab**\n"
                            f"• [gamelinelab/data/giso]({repo})\n"
                            f"• [CHANGELOG v{version}]({changelog})\n"
                            f"• 测试管理台：{admin_url}"
                        ),
                    },
                },
            ],
        },
    }


def post_lark(webhook: str, body: dict) -> dict:
    data = json.dumps(body, ensure_ascii=False).encode("utf-8")
    req = urllib.request.Request(
        webhook,
        data=data,
        method="POST",
        headers={"Content-Type": "application/json; charset=utf-8"},
    )
    with urllib.request.urlopen(req, timeout=15) as resp:
        return json.loads(resp.read().decode())


def main() -> int:
    p = argparse.ArgumentParser(description="GISO Lark notify (GitLab links)")
    p.add_argument("--mode", choices=("push", "release"), default="push")
    p.add_argument("--channel", choices=("test", "prod"), default="test")
    p.add_argument("--approve-prod", action="store_true", help="正式群必显式确认")
    p.add_argument("--version", default=os.environ.get("GISO_VERSION", "1.0.1"))
    p.add_argument("--commit", default=os.environ.get("GISO_COMMIT") or os.environ.get("GITHUB_SHA"))
    p.add_argument("--message", default=os.environ.get("GISO_COMMIT_MESSAGE"))
    p.add_argument("--date-range", default=os.environ.get("GISO_DATE_RANGE"))
    p.add_argument("--gitlab-base", default=os.environ.get("GITLAB_REPO_URL", DEFAULT_GITLAB))
    p.add_argument("--admin-url", default=os.environ.get("GISO_ADMIN_URL", DEFAULT_ADMIN))
    p.add_argument("--webhook", default=None)
    p.add_argument("--dry-run", action="store_true")
    args = p.parse_args()

    webhook = resolve_webhook(args.channel, args.approve_prod, args.webhook)

    if args.mode == "push":
        commit = args.commit
        if not commit:
            print("error: --commit required for push mode", file=sys.stderr)
            return 1
        message = args.message
        if not message:
            try:
                message = subprocess.check_output(
                    ["git", "log", "-1", "--format=%s", commit],
                    text=True,
                ).strip()
            except subprocess.CalledProcessError:
                message = "(no message)"
        card = build_push_card(
            gitlab_base=args.gitlab_base,
            commit=commit,
            message=message,
            channel=args.channel,
            stat=git_show_stat(commit),
        )
    else:
        card = build_release_card(
            args.version,
            gitlab_base=args.gitlab_base,
            admin_url=args.admin_url,
            commit=args.commit,
            date_range=args.date_range,
            channel=args.channel,
        )

    if args.dry_run:
        print(json.dumps(card, ensure_ascii=False, indent=2))
        return 0

    if not webhook:
        print("error: webhook not configured (LARK_WEBHOOK_URL_TEST / _PROD)", file=sys.stderr)
        return 1

    try:
        result = post_lark(webhook, card)
    except urllib.error.HTTPError as e:
        print(f"HTTP {e.code}: {e.read().decode()}", file=sys.stderr)
        return 1

    print(json.dumps(result, ensure_ascii=False))
    return 0 if result.get("code") == 0 else 1


if __name__ == "__main__":
    raise SystemExit(main())
