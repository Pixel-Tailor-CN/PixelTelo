# 主页面 UI 规范

## 概览

主页面是 Pixel Telo 的入口点。必须严格遵守 **Material3** 设计规范，并支持 **Monet (动态取色)**。

## 状态

### 1. 数据完整性检查 (初始/空状态)

* **触发条件**: 当 `Repository.recordCount == 0` 时。
* **UI 元素**:
    * **状态指示**: 醒目的警告卡片或插图，指示“无数据/数据库为空”。
    * **操作**: 主按钮“下载数据”（或“初始化”）。
    * **反馈**: 点击操作后显示下载进度指示器 (LinearProgressIndicator)。

### 2. 已填充状态 (正常运行)

* **触发条件**: 当 `Repository.recordCount > 0` 时。
* **UI 元素**:
    * **状态卡片**: 显示“受保护”或“运行中”。
    * **统计**: 显示已拦截通话数量、上次更新时间。
    * **操作**: 设置入口、手动检查更新。

### 3. 数据源下线提示 (WarningCard)

* **触发条件**: `HomeViewModel.sourceState.unavailableEnabledSources` 非空，即用户已启用的联网查询
  source 被当前 Backend 下线。source 清单在应用启动时后台刷新一次，不阻塞首页首帧；刷新失败沿用
  当前 Backend 缓存，不误报下线，也不展示其他 Backend 的 source。
* **UI 元素**: 不可忽略的 WarningCard，列出下线的 source ID，提供“调整数据源”按钮。
* **导航**: 按钮经 `MainActivity` 协调，先打开 `SettingViewModel` 的 source 设置 BottomSheet，
  再切换到设置页。用户修正配置后卡片自动消失。

### 4. 自建服务安全提示 (WarningCard)

* **触发条件**: 用户选择了自建 Backend，但 `QueryBackendProvider` 因配置/凭据不可用、TLS/Pin、
  服务版本、API Version、Instance ID 或身份 Header 问题发布 `Blocked` 状态。
* **非触发条件**: 偶发网络超时、429 和普通 5xx 不形成持续卡片；首页不主动执行健康轮询。
* **UI 元素**: 不可忽略的 WarningCard，只展示本地化安全分类和“调整查询服务”操作，不展示 Token、
  完整 URL、Pin、Instance ID、服务端响应正文或异常堆栈。
* **导航**: 操作跳转到设置页的在线查询区域。用户可重新测试、修改配置或显式切回官方；只有完整重验证
  成功才解除阻止状态。
* **来电行为**: 卡片只反映 Provider 状态；实际来电查询在 Snapshot 不可用时直接 Fail Open，不回退
  官方实时查询。

## 记录详情反馈入口

* 点击拦截记录卡片弹出的详情 BottomSheet 中，若记录带有 `querySource` 则展示命中来源。
* 只有 `queryBackendId == "official"`、存在有效 Token 且 `feedbackStatus == PENDING` 时，才展示
  “结果准确 / 结果不准确”两个操作；提交期间按钮禁用；
  可重试失败展示错误并允许重试（状态保持 `PENDING`）。
* 终态（`POSITIVE`/`NEGATIVE`/`ALREADY_SUBMITTED`/`EXPIRED`/`INVALID`）只展示状态文字，
  不再提供提交按钮；自建记录固定为 `UNAVAILABLE`，不展示反馈区域。

## 拦截记录展示

* 使用 Paging 3 分页加载：每页 30 条，预取 10 条，最多在内存保留约 90 条记录。
* 匹配到联系人时，卡片首行同时显示姓名和号码；未命中、权限缺失或联系人查询失败时只显示号码。
* 归属地独立显示省份和城市；省市相同只显示一次，空值不显示占位，不显示运营商。
* 联系人查询范围限定为当前 Paging 已加载窗口，联系人变化后只刷新当前窗口。
* 详情 BottomSheet 同步展示联系人姓名、完整号码和归属地，所有号码操作仍使用原始号码。
* Paging refresh 失败时保留已经显示的记录，并在列表内提供轻量重试入口。

## 设计组件

* **TopAppBar**: 简单的 Material3 TopAppBar。
* **Scaffold**: 标准 Scaffold。
* **Cards**: 使用 `OutlinedCard` 或 `ElevatedCard` 进行状态显示。
* **Theme**: 在支持的设备 (Android 12+) 上必须使用 `dynamicLightColorScheme` 和
  `dynamicDarkColorScheme`。
