## 项目概览

* **项目名称**: Pixel Telo
* **核心价值**: 原生体验、隐私优先、极致轻量。
* **设备支持**:
* **核心目标**: Google Pixel 系列 (Android 10+)。
* **兼容目标**: 运行原生/类原生 Android (AOSP) 的设备。

## 代码质量与开发原则

### 质量要求

* **语言规范**: 严格遵循 Kotlin 官方编码规范。
* **Android 规范**:
* 遵循 Modern Android Development (MAD) 指南。
* **UI 框架**: 全面使用 **Jetpack Compose** (+ Material3)，支持 Pixel 动态取色 (Monet)。
* **架构**: **MVVM** (Model-View-ViewModel)。
* 使用 `ViewModel` 管理 UI 状态。
* 使用 `StateFlow` 或 `LiveData` 进行数据驱动。
* 使用 `Repository` 模式隔离数据源（本地 Room 与 网络 Retrofit）。


* **依赖注入**: 使用 **Koin** (Koin-Android, Koin-Compose)。
* **兼容性**: **MinSDK 为 29 (Android 10)**，**TargetSDK 为 35 (Android 15)**。


* **功能特性规范**:
* **原生集成**:
* **UI 呈现**: 唯一使用 **Directory Provider** API 将标记信息注入系统拨号器。**严禁**默认使用悬浮窗 (
  Overlay)，除非作为特定 ROM 的降级方案。
* **拦截执行**: 使用 `CallScreeningService`，拦截操作需正确写入系统通话记录 (Blocked Calls)。


* **性能限制**:
* **50ms 规则**: 来电时的本地数据库查询（Room）必须在 50ms 内完成，避免阻塞来电界面。
* **网络超时**: 实时网络查询超时时间严格限制为 **2s**。


* **数据策略**:
* **初始状态**: App 安装后本地数据库默认为**空**。
* **空数据检测**: App 主页面必须包含“数据完整性检测”逻辑。若检测到本地库为空，需醒目提示用户从云端下载初始化数据。
* **数据同步**: 使用 **Retrofit** 下载数据库文件或增量数据，并写入/替换本地 Room 数据库。


* **权限控制**:
* **最小权限**: 仅申请核心功能必要的 `READ_CONTACTS` (用于 Provider),
  `ANSWER_PHONE_CALLS` 等权限。


* **代码结构**:
* 单个文件原则上不超过 1000 行。
* **Service 分离**: 拦截服务 (`CallScreeningService`) 必须保持极度精简，繁重的数据下载与写入任务需剥离至
  `WorkManager` 或前台 Service。
* 避免在 Activity 中编写业务逻辑。


* **注释**:
* 使用中文编写清晰的 KDoc 与行内注释。
* 核心算法逻辑（如号码匹配策略、Provider 查询分发）必须添加详细注释说明。

### 技术栈详细

* **网络层**: **Retrofit** + **OkHttp**。
* **本地存储**: **Room Database** (SQLite)。
* **异步处理**: **Kotlin Coroutines** + **Flow**。

### 测试与验证

* **编译检查**:
* 变更代码后，必须执行 `./gradlew :app:assembleDebug` 确保编译通过。


* **Lint 检查**:
* 运行 `./gradlew lint` 检查潜在的代码质量问题。


* **功能验证**:
* **流程验证**: 模拟全新安装，验证主页是否正确提示“数据缺失”，并测试点击更新后的下载、解压、入库流程。
* **拦截验证**: 使用模拟号码（如 `10086`）测试 `CallScreeningService` 是否正确触发挂断。
* **原生 UI 验证**: 在 Pixel 拨号器中输入特定号码，验证 `Directory Provider` 是否成功返回 Label。
* **真机测试**: 必须在 Google Pixel 真机上测试，验证 Directory Provider 的缓存刷新机制。

#### 项目本地校验流程

1. 命令行执行 `./gradlew :app:assembleDebug`。
2. 确认无编译错误，且 `libs.versions.toml` 中无过时警告。

## 文档与记忆

文档与记忆采用 Markdown 格式，存放于 `.agentdocs/` 及其子目录下。
索引文档：`.agentdocs/index.md`

### 文档分类

* `prd/` - 产品与需求
* `prd/requirements.md` - 核心功能需求 (Call Guardian, Data Sync)


* `architecture/` - 架构与技术细节
* `architecture/mvvm-structure.md` - MVVM 架构分层与数据流向
* `architecture/native-integration.md` - Directory Provider 与 CallScreeningService 实现细节
* `architecture/sync-strategy.md` - 数据库初始化与云端更新策略


* `ui/` - 界面规范
* `ui/main-screen.md` - 主页状态管理 (Empty vs Populated)


* `workflow/` - 任务流文档

### 全局重要记忆

* **项目名称**: Pixel Telo
* **SDK 版本**:
* **MinSDK**: 29 (Android 10)
* **TargetSDK**: 35 (Android 15)


* **关键技术决策**:
* **Architecture**: MVVM.
* **Network**: Retrofit.
* **Database**: Room (Empty on start, Cloud update).
* **UI Strategy**: Directory Provider 是核心。

## 任务处理指南

* **需求澄清**: 涉及到底层系统行为（如不同 ROM 对 Directory Provider 的支持差异）时，优先进行原型验证。
* **方案分析**: 新增功能必须评估对“原生体验”的影响，避免打扰用户。
* **分阶段实施**:

1. **基础架构**: Koin, Room, Retrofit 模块搭建。
2. **核心拦截**: `CallScreeningService` 与 `DirectoryProvider` 实现。
3. **UI 与 交互**: 实现主页“空数据检测”逻辑与下载更新功能。
4. **UI 完善**: 适配 Material You 动态主题。

### 任务回顾

* 在任务完成呈现最终消息前，必须进行以下任务回顾：
* 检查是否产生新的可复用组件，并更新架构文档。
* 检查 `.agentdocs/` 下的文档是否需要更新。
* 确认 `libs.versions.toml` 中的依赖版本是否为最新稳定版。

## 沟通原则

* 与用户的所有回复与沟通，文档与代码注释均使用中文。
* 涉及专业术语 (如 "Directory Provider", "Retrofit", "Room", "MVVM") 时保留英文，必要时附带中文解释。