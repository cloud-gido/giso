# GISO Copilot（玑源助手）

> 对齐 GIDO 产品族 Copilot 体验：管理台内嵌答疑，**插拔式 provider**，默认无需 LLM Key。

## 能力范围

| 类型 | 示例问题 |
|------|----------|
| 产品功能 | GISO 与 GIDO 关系、Spaces、管理台能力 |
| 埋点流程 | 接入六步、登记 → 常量 → 联调 → 发布 |
| 上报协议 | `/v1/track`、env 分流、隔离区 |
| 架构与性能 | 单体 Gateway、HTTPS 对 App 影响、高流量容量与限流 |
| 运维部署 | App Key、Doppler、Doris 链路、镜像回滚 `0.1` |

回答会附带 **当前空间登记概况**（pages/elements 数量、待审批数）。

## Provider（插拔）

| provider | 说明 | 配置 |
|----------|------|------|
| `doc`（默认） | 检索 `docs/tracking` + 内置 corpus | 零依赖 |
| `openai` | 阿里云 MaaS（OpenAI 兼容）+ 文档 RAG | `GISO_LLM_API_KEY` |

```yaml
assistant:
  enabled: true
  provider: openai
  docs_dirs: [../../docs/tracking, ../../docs/en]
  openai:
    base_url: https://ws-df7ipa997hhtkd8h.cn-beijing.maas.aliyuncs.com/compatible-mode/v1
    model: qwen-plus
  gido_proxy_url: http://gido-copilot:8080/v1/giso/chat
```

环境变量：

| 变量 | 说明 |
|------|------|
| `GISO_ASSISTANT_ENABLED` | `0` / `1` |
| `GISO_ASSISTANT_PROVIDER` | `doc` / `openai` / `gido_proxy` |
| `GISO_LLM_API_KEY` | 阿里云 MaaS API Key |
| `GISO_LLM_BASE_URL` | 覆盖 base_url（默认已指向北京 MaaS 兼容端点） |
| `GISO_LLM_MODEL` | 模型名，如 `qwen-plus` |
| `GISO_GIDO_COPILOT_URL` | GIDO 代理地址 |

## API

- `GET /admin/api/assistant/status`
- `POST /admin/api/assistant/chat` — `{ "message": "...", "history": [] }`

## UI

管理台 → **系统设置**（平台管理员）：配置 Copilot LLM、勾选出口管道，**保存后立即生效**。

管理台 → **接入助手**（顶栏）：对话界面。

## 扩展新 Provider

1. 实现 `AssistantProvider`
2. 在 `AssistantFactory` 注册
3. `gateway.yaml` 增加配置块 + Doppler 键

与 `EventSink` / `SinkFactory` 同一扩展模式。
