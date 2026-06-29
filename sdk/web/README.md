# @giso/tracker-web

GISO 玑源 Web SDK — 9 事件收敛、参数继承、注册表驱动。

## 接入

```ts
import { Tracker } from '@giso/tracker-web';

const tracker = Tracker.init({
  appId: 'giso',
  appVersion: APP_VERSION,
  endpoint: 'https://track.example.com/v1/track',
  debug: process.env.NODE_ENV !== 'production',
});
```

## 能力

- 曝光观察（IntersectionObserver，远程配置口径）
- 参数继承（页面 → 模块 → 元素）
- 攒批 + 指数退避 + localStorage 持久化
- 远程配置（`GET /v1/config`）

常量从 `schema/` 生成，见 `src/generated.ts`。
