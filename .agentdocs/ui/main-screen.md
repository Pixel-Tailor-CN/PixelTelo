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

## 设计组件

* **TopAppBar**: 简单的 Material3 TopAppBar。
* **Scaffold**: 标准 Scaffold。
* **Cards**: 使用 `OutlinedCard` 或 `ElevatedCard` 进行状态显示。
* **Theme**: 在支持的设备 (Android 12+) 上必须使用 `dynamicLightColorScheme` 和
  `dynamicDarkColorScheme`。
