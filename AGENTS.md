# AGENTS.md

本文件为 Codex (Codex.ai/code) 在本仓库中工作的统一协作指南。

## 沟通与输出规范

- 与用户的所有回复与沟通使用中文。
- 生成的代码注释、KDoc 与项目文档使用中文。
- 日志打印使用英文。
- 涉及专业术语时保留英文，例如 Directory Provider、Retrofit、Room、MVVM，必要时附带中文解释。

## 项目概览

Pixel Telo 是专为 Google Pixel 设备设计的来电识别与拦截应用，采用原生 Android 风格，强调隐私保护和极速响应。

**项目名称**: Pixel Telo
**核心定位**: 轻量级、隐私优先、零打扰的原生体验
**核心价值**: 原生体验、隐私优先、极致轻量
**目标设备**: Google Pixel 系列及类原生 Android (AOSP) 设备
**兼容性**: MinSDK 29 (Android 10), TargetSDK 35 (Android 15)

## 开发原则

- 在进行任何功能开发时，优先考虑“原生集成”与“性能开销”，避免破坏 Pixel 系统的纯净体验。
- 新增功能必须评估对“原生体验”的影响，避免打扰用户。
- 涉及底层系统行为时，例如不同 ROM 对 Directory Provider 的支持差异，优先进行原型验证。
- 坚持隐私优先和最小权限原则，仅采集与处理核心功能所需的数据。
- 本项目默认不新增、不维护、不运行单元测试。除非用户明确要求，否则功能开发与问题修复不要创建
  `src/test` 测试文件，也不要执行 `./gradlew test`、`./gradlew testDebugUnitTest` 或包含单元测试的
  `./gradlew check`。

## 构建与开发命令

### 基础构建

```bash
# 编译 Debug 版本
./gradlew assembleDebug

# 编译 app Debug 版本，代码变更后优先执行
./gradlew :app:assembleDebug

# 编译 Release 版本
./gradlew assembleRelease

# 清理构建产物
./gradlew clean
```

### 安装与卸载

```bash
# 安装 Debug 版本到设备
./gradlew installDebug

# 卸载所有版本
./gradlew uninstallAll
```

### 代码检查

```bash
# 运行 Lint 检查
./gradlew lint
```

### 设备测试

```bash
# 在连接的设备上运行 instrumentation 测试
./gradlew connectedAndroidTest

# 运行所有设备检查
./gradlew connectedCheck
```

## 核心架构

### 双数据库设计

项目使用两个独立的 Room 数据库：

1. **AppDatabase** (`app-database`): 存储应用运行时数据
   - `BlockedCall`: 拦截记录，包括实际拦截、仅提示、超时等状态
   - 位置: `vip.mystery0.pixel.telo.data.AppDatabase`

2. **MastDatabase** (动态路径): 存储骚扰号码库
   - `SpamNumberEntity`: 骚扰号码数据
   - `MetadataEntity`: 数据库元信息
   - 特点: 从云端下载，可动态更新，初始为空
   - 位置: `vip.mystery0.pixel.telo.data.MastDatabase`

### MVVM 架构

- **Model**: Room 实体 + Repository 层。
- **View**: Jetpack Compose UI。
- **ViewModel**: 业务逻辑处理，使用 Kotlin Flow 管理数据流。
- 使用 `ViewModel` 管理 UI 状态。
- 使用 `StateFlow` 或 `LiveData` 进行数据驱动。
- 使用 `Repository` 模式隔离数据源，包括本地 Room 与网络 Retrofit。
- **依赖注入**: Koin，配置在 `di/AppModule.kt`，包括 Koin-Android 与 Koin-Compose。

### 三大核心组件

1. **TeloCallScreeningService** (`service/TeloCallScreeningService.kt`)
   - 继承 `CallScreeningService`，负责来电拦截。
   - 必须保持极简，重型任务分离至 WorkManager 或前台 Service。
   - 拦截记录写入系统通话记录 (Blocked Calls)。

2. **TeloDirectoryProvider** (`provider/TeloDirectoryProvider.kt`)
   - 继承 `ContentProvider`，实现 Directory Provider。
   - 在系统拨号器中显示来电信息，这是唯一推荐方式。
   - 严禁默认使用悬浮窗 (Overlay)，除非作为特定 ROM 的降级方案。

3. **SpamNumberRepository** (`data/repository/SpamNumberRepository.kt`)
   - 号码查询核心逻辑：先本地查询，再网络查询。
   - 必须实现并遵守性能约束。

## 关键性能约束 (CRITICAL)

### 来电查询规则

来电时的本地数据库查询 (Room) 必须极速完成，严禁阻塞来电界面。

- 目标性能: 本地查询优先满足 **50ms 规则**。
- 硬性上限: 本地查询必须在 **3s 内完成**。
- 实现位置: `SpamNumberRepository.checkSpam()`。
- 本地查询超过 100ms 必须记录警告日志。

### 网络超时限制

实时网络查询必须严格限制超时时间，超时后允许来电通过。

- 目标超时: 实时网络查询优先控制在 **2s** 内。
- 硬性上限: 实时网络查询不得超过 **3s**，含网络延迟。
- 实现: `withTimeout(3000L)` in `SpamNumberRepository.checkSpam()`。
- 超时后允许来电通过，但记录为 `ResultType.NETWORK_TIMEOUT`。

### 数据策略

- App 安装初始状态本地骚扰号码库为**空**。
- 主页必须包含“数据完整性检测”逻辑。
- 若检测到本地库为空，需醒目提示用户从云端下载初始化数据。
- 数据更新通过 Retrofit 下载数据库文件或增量数据，并写入或替换 MastDatabase。

## 代码规范

### 语言与风格

- **语言**: Kotlin，启用 Strict mode，JVM Target 21。
- **语言规范**: 严格遵循 Kotlin 官方编码规范。
- **Android 规范**: 遵循 Modern Android Development (MAD) 指南。
- **UI 框架**: Jetpack Compose + Material3，支持 Pixel 动态取色 (Monet)。
- **注释**: 使用中文编写 KDoc 与行内注释。
- **文件大小**: 单文件原则上不超过 1000 行。

### 架构约束

- 业务逻辑必须位于 `ViewModel` 或 `UseCase`，严禁写入 Activity/UI 层。
- 避免在 Activity 中编写业务逻辑。
- 核心算法必须有详细中文注释，包括号码匹配策略、Provider 查询分发等。
- `CallScreeningService` 必须保持极简，繁重的数据下载与写入任务需剥离至 `WorkManager` 或前台 Service。
- 新增可复用组件后，需要检查并更新对应架构文档。

### 权限控制

坚持最小权限原则，仅申请核心功能必要权限。当前项目允许的核心权限包括：

- `READ_CALL_LOG`: 读取通话记录。
- `READ_PHONE_STATE`: 读取电话状态。
- `READ_CONTACTS`: Directory Provider 必需。
- `MANAGE_OWN_CALLS`: 管理来电。
- `ANSWER_PHONE_CALLS`: 接听或拒接来电相关能力。
- `INTERNET`: 网络查询。

新增权限前必须说明用途，并确认无法通过现有权限或系统 API 实现。

## 技术栈

- **UI**: Jetpack Compose + Material3 + Monet 动态取色。
- **数据库**: Room (SQLite)。
- **网络**: Retrofit + OkHttp + Kotlinx Serialization。
- **异步**: Kotlin Coroutines + Flow。
- **依赖注入**: Koin。
- **构建**: Gradle (Kotlin DSL) + KSP。

## 验证清单

在提交代码前，根据改动范围执行对应验证：

1. **编译检查**: 执行 `./gradlew :app:assembleDebug`，确保编译通过。
2. **Lint 检查**: 执行 `./gradlew lint`，检查潜在代码质量问题。
3. **单元测试策略**: 默认不新增、不运行单元测试；除非用户明确要求，否则跳过 `./gradlew test`、
   `./gradlew testDebugUnitTest` 与包含单元测试的 `./gradlew check`。
4. **全新安装流程**: 主页是否提示“数据缺失”，下载、解压、入库流程是否正常。
5. **拦截测试**: 使用模拟号码，例如 `10086`，测试 `CallScreeningService` 挂断逻辑及通话记录写入。
6. **原生 UI 集成**: 在 Pixel 拨号器验证 Directory Provider 是否成功注入或返回 Label 信息。
7. **真机验证**: 必须在 Google Pixel 真机上验证 Provider 的缓存刷新与显示效果。
8. **性能测试**: 验证本地查询和网络查询是否满足时间约束。
9. **依赖检查**: 确认 `libs.versions.toml` 中无过时或不稳定依赖警告。

## 项目本地校验流程

1. 命令行执行 `./gradlew :app:assembleDebug`。
2. 确认无编译错误。
3. 根据变更范围执行 `./gradlew lint` 或设备测试；默认不执行单元测试命令。
4. 确认 `libs.versions.toml` 中的依赖版本符合项目稳定性要求。

## 版本管理

- **versionCode**: 自动从 git commit 数量生成。
- **versionName**: 从 `libs.versions.toml` 读取，附加 git hash。
- **签名配置**: 在 `signing.gradle` 中配置。

## 文档体系

文档与记忆采用 Markdown 格式，存放于 `.agentdocs/` 及其子目录下。

索引文档: `.agentdocs/index.md`

### 文档分类

- `prd/`: 产品与需求文档。
- `prd/requirements.md`: 核心功能需求 (Call Guardian, Data Sync)。
- `architecture/`: 架构设计与技术细节文档。
- `architecture/mvvm-structure.md`: MVVM 架构分层与数据流向。
- `architecture/native-integration.md`: Directory Provider 与 CallScreeningService 实现细节。
- `architecture/sync-strategy.md`: 数据库初始化与云端更新策略。
- `ui/`: 界面规范文档。
- `ui/main-screen.md`: 主页状态管理 (Empty vs Populated)。
- `workflow/`: 任务流与开发指引。

## 全局重要决策

- **Architecture**: MVVM。
- **Network**: Retrofit。
- **Database**: Room，本地库初始为空，通过云端更新。
- **UI Strategy**: Directory Provider 是核心系统集成方式。
- **Interception Strategy**: CallScreeningService 负责拦截执行，并正确写入 Blocked Calls。

## 任务处理指南

### 分阶段实施建议

1. **基础架构**: Koin、Room、Retrofit 模块搭建。
2. **核心拦截**: `CallScreeningService` 与 `DirectoryProvider` 实现。
3. **UI 与交互**: 实现主页“空数据检测”逻辑与下载更新功能。
4. **UI 完善**: 适配 Material You 动态主题。

### 任务回顾

在任务完成并呈现最终消息前，进行以下检查：

- 检查是否产生新的可复用组件，并更新架构文档。
- 检查 `.agentdocs/` 下的文档是否需要更新。
- 确认 `libs.versions.toml` 中的依赖版本是否符合项目稳定性要求。
- 说明已执行的验证命令；若无法执行真机或设备验证，需要明确说明原因。
