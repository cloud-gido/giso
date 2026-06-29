# Contributing to GISO · 玑源

感谢参与 **GISO（玑源 · GIDO 的数据源头）** 的建设。本项目采用 **Schema 驱动 + 小 PR** 原则。

## 开发流程

1. Fork 仓库，从 `main` 拉 feature 分支
2. 改 `schema/*.yaml` 时**必须**跑：
   ```bash
   pip install pyyaml
   python3 tools/codegen/generate.py --check
   ```
3. 改网关时跑单测：
   ```bash
   cd server/gateway && mvn test
   ```
4. PR 描述里写清：需求来源、统计口径、看数方式（登记类改动）

## 登记类 PR 规范

- 命名：`snake_case`，≤32 字符
- 参数先在 `params.yaml` 登记，再被 pages/elements/events 引用
- 资金类事件必须 `source: server`
- 页面新条目建议填 `screenshot` + `desc`

## 代码风格

- Java：与 `server/gateway` 现有风格一致，不引入额外框架
- 管理台 JS：ES Module，零构建依赖
- 文档：中文，与 `docs/tracking/` 现有章节结构对齐

## 问题反馈

Bug / 功能建议请开 Issue，附上：复现步骤、期望行为、`schema` 相关条目（如有）。
