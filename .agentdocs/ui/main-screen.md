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
  source 被服务端下线。source 清单在应用启动时后台刷新一次，不阻塞首页首帧；刷新失败沿用缓存，
  不误报下线。
* **UI 元素**: 不可忽略的 WarningCard，列出下线的 source ID，提供“调整数据源”按钮。
* **导航**: 按钮经 `MainActivity` 协调，先打开 `SettingViewModel` 的 source 设置 BottomSheet，
  再切换到设置页。用户修正配置后卡片自动消失。

## 记录详情反馈入口

* 点击拦截记录卡片弹出的详情 BottomSheet 中，若记录带有 `querySource` 则展示命中来源。
* `feedbackStatus == PENDING` 时展示“结果准确 / 结果不准确”两个操作；提交期间按钮禁用；
  可重试失败展示错误并允许重试（状态保持 `PENDING`）。
* 终态（`POSITIVE`/`NEGATIVE`/`ALREADY_SUBMITTED`/`EXPIRED`/`INVALID`）只展示状态文字，
  不再提供提交按钮；`UNAVAILABLE` 不展示反馈区域。

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
