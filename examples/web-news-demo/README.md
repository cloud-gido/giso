# GISO 资讯 Web 演示

面向 **资讯业务线** 的 Web 端埋点演示，功能对齐 Android 长视频 Demo 的联调体验：底部浮层展示 `did` / `pgid` / 网关状态，事件实时上报管理台。

## 演示能力

| 场景 | pgid | 埋点 |
|---|---|---|
| 资讯流（分类 Tab） | `news_feed` | `page_enter/exit`、`article_card` 曝光/点击、`share_btn` |
| 文章详情 | `news_article` | 阅读结算 `news_read`、`related_article`、`share_btn` |

## 快速开始

### 1. 启动 GISO 网关

```bash
cd deploy && docker compose up -d
# 管理台 http://localhost:8123/admin/  (admin / admin123)
```

### 2. 启动资讯 Demo

**方式 A（推荐，无需安装 Node）：**

```bash
cd examples/web-news-demo
chmod +x run.sh
./run.sh
```

**方式 B（本机已装 Node.js）：**

```bash
cd examples/web-news-demo
npm install
npm run dev
```

**方式 C（仅 Docker）：**

```bash
cd deploy
docker compose --profile demo up -d news-web-demo --build
```

浏览器打开 http://localhost:5180/

> 未安装 npm 时执行 `brew install node` 亦可本地开发。

### 3. 管理台联调

1. 打开 http://localhost:8123/admin/ → **实时联调**
2. 点击 Demo 底部 **复制 did**，粘贴到过滤框
3. 操作：切换分类 Tab → 点击资讯卡片 → 滚动阅读 → 点相关推荐 → 返回

预期事件示例：

```
page_enter(news_feed) → element_exposure(article_card)
→ element_click(article_card) → page_enter(news_article)
→ biz_event(news_read) → page_exit(news_article)
```

### 4. 断言 API（可选）

```bash
curl -s -X POST http://localhost:8123/admin/api/assert \
  -u admin:admin123 \
  -H 'Content-Type: application/json' \
  -d '{
    "did": "你的did",
    "expect": [
      {"event": "page_enter", "pgid": "news_feed"},
      {"event": "element_click", "eid": "article_card"},
      {"event": "page_enter", "pgid": "news_article"},
      {"event": "biz_event", "code": "news_read"}
    ]
  }'
```

## 局域网 / 真机联调

创建 `.env.local`：

```bash
VITE_TRACK_ENDPOINT=http://192.168.x.x:8123/v1/track
VITE_TRACK_DEBUG=true
```

`npm run dev` 已开启 `host: true`，同事可通过 `http://你的IP:5180/` 访问页面。

## 配置

| 变量 | 默认 | 说明 |
|---|---|---|
| `VITE_TRACK_ENDPOINT` | `http://localhost:8123/v1/track` | 网关上报地址 |
| `VITE_TRACK_DEBUG` | `true` | `false` 关闭 debug 实时上报 |

## 工程结构

```
examples/web-news-demo/
  src/
    pages/feed.ts       资讯流 news_feed
    pages/article.ts    详情 news_article + news_read
    read-tracker.ts     阅读时长/滚动深度结算
    debug-panel.ts      联调浮层（对齐 Android TrackerHelper）
    catalog.ts          演示文章数据
```

## 埋点要点

- 全部使用 `@giso/tracker-web` 生成常量（`Pages` / `Elements` / `BizEvents` / `Params`）
- `debug: true` → `env=test`，事件进 `events_raw_test`，不污染生产分析
- `news_read` 在离开详情页前上报（`read_dur` 前台累计毫秒，`read_pct` 最大滚动深度 0~1）
