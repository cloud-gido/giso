# 接入步骤摘要

## 第 1 步：登记

管理台 → 注册表配置 → 新增 draft → 联调后 publish 为 live。

四池：params / pages / elements / events。页面可绑定 `elements` 结构体。

## 第 2 步：常量

禁止裸字符串。使用 CI 生成的 `Pages.VIDEO_FEED` 等。

## 第 3 步：埋点代码

- 页面：`page_enter` / `page_exit`
- 元素：曝光/点击 + `ElementMeta`
- 业务：`biz_event` + 登记 code

## 第 4 步：联调

SDK `debug: true` → 管理台 SSE 过滤 did。

## 第 5 步：断言回归

管理台 **用例断言**：声明期望事件序列。

## 第 6 步：覆盖率

GET `/admin/api/coverage` 查看登记未上报条目。

## 可视化登记

**Visual Picker**：截图圈选或 ViewTree JSON → `POST /admin/api/registry/visual-draft`。
