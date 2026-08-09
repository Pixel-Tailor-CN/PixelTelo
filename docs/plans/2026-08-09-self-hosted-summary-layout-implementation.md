# 自建服务摘要分行布局实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将设置页与管理对话框共用的自建服务摘要改为完整地址、版本和验证时间三行展示。

**Architecture:** 继续复用 `SelfHostedBackendSummary`，不新增 UI 状态或业务逻辑。组件直接读取已验证配置的规范化 `baseUrl`，通过三个独立字符串资源构建带小间距的纵向布局。

**Tech Stack:** Kotlin、Jetpack Compose、Material3、Android String Resources

## Global Constraints

- 与用户沟通、代码注释和项目文档使用中文，日志使用英文。
- 不展示 Token、Authorization Header 或其他凭据。
- 不新增或运行单元测试；使用编译、Debug 构建与 Lint 验证。
- 保留工作区中与本任务无关的已有修改。

---

### Task 1: 调整共享摘要组件与文案

**Files:**
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/settings/SelfHostedQueryDialog.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

**Interfaces:**
- Consumes: `VerifiedSelfHostedConfig.baseUrl`、`version`、`verifiedAtEpochMillis`
- Produces: `SelfHostedBackendSummary(config: VerifiedSelfHostedConfig)` 的三行摘要 UI

- [x] **Step 1: 拆分中英文字符串资源**

将原有组合字符串替换为地址、版本和验证时间三个带标签的格式化字符串。中文标签为“服务地址”“服务版本”“验证时间”，英文标签为“Server address”“Server version”“Verified at”。

- [x] **Step 2: 修改 Compose 布局**

移除 `maskSelfHostedHost` 调用与 import，直接使用 `config.baseUrl`。在 `SelfHostedBackendSummary` 内使用 `Column(verticalArrangement = Arrangement.spacedBy(4.dp))` 放置三个 `Text`，沿用 `bodyMedium` 和 `onSurfaceVariant`，允许文本自然换行。

- [x] **Step 3: 检查调用方一致性**

确认 `OnlineQueryPreferences` 与自建服务管理对话框仍调用同一个 `SelfHostedBackendSummary`，不复制展示逻辑。

- [x] **Step 4: 执行静态检查**

运行 `rg` 确认 UI 不再调用 `maskSelfHostedHost`，并运行 `git diff --check`。

### Task 2: 构建验证

**Files:**
- Verify: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/settings/SelfHostedQueryDialog.kt`
- Verify: `app/src/main/res/values/strings.xml`
- Verify: `app/src/main/res/values-zh/strings.xml`

**Interfaces:**
- Consumes: Task 1 的三行摘要实现
- Produces: 可安装的 Debug APK 与通过的 Lint 报告

- [x] **Step 1: 编译 Kotlin**

运行 `./gradlew :app:compileDebugKotlin --rerun-tasks --console=plain`，预期 `BUILD SUCCESSFUL`。

- [x] **Step 2: 构建 Debug APK**

运行 `./gradlew :app:assembleDebug --console=plain`，预期 `BUILD SUCCESSFUL`。

- [x] **Step 3: 运行 Lint**

运行 `./gradlew lint --console=plain`，预期 `BUILD SUCCESSFUL`。

- [x] **Step 4: 安装并检查工作区**

若模拟器已连接，运行 `./gradlew :app:installDebug --console=plain`。最后运行 `git diff --check` 和 `git status --short`，确认用户已有的 `gradle/libs.versions.toml` 修改未被覆盖。
