# 隔离区 FAQ

## 什么事件进隔离区？

校验 **error** 级问题：非标准事件、未登记 pgid/eid、页面结构体违规、参数类型错误等。

**missing** 级通常仍进 raw topic，但带 `_quality=missing`。

## 如何查看原因？

管理台 **实时联调** → 展开事件 → 查看 `_issues` 数组（field + msg）。

## 如何修复回放？

1. 修正 registry（管理台或 YAML）
2. 发布为 live
3. 回放隔离区：

```bash
python3 server/ops/replay_quarantine.py replay \
  --gateway http://localhost:8123 \
  --app-key your-key --limit 500
```

支持 Kafka 或 JSONL 输入；`sample` 子命令可导出样本。

## 与 Doris 的关系

隔离 topic `giso_events_quarantine` 可单独落表分析，不参与常规 DWD 指标。
