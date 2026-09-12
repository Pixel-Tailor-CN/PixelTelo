# AGENTS.md

Pixel Telo 是面向 Google Pixel / AOSP 的来电识别与拦截应用，优先原生体验、隐私和来电响应速度。

## 协作方式

- 回复、代码注释、KDoc 与项目文档使用中文，日志使用英文，专业术语保留英文。
- 对已明确或上下文已授权的任务，直接完成实现、相关验证与必要修复。常规可逆选择自行判断；只有影响产品结果、无法推断的关键需求或未获授权的不可逆操作才需询问，先完成不依赖答案的工作。
- 当前用户要求优先于技能中的流程建议；项目约束优先于通用模板。技能不自动产生额外审批、TDD、提交或发布要求。若确有指令冲突导致停顿，指出具体文件和条款。
- 按任务需要读取代码和文档。小改动直接处理；跨模块或有关键取舍的任务先给简短计划，需长期跟踪时才写入 `docs/plans/`。
- 技能仅在用户点名或任务确实符合其用途时使用；不因关键词重合加载整套流程。工具与子代理能力以当前会话为准，并行工作遵守当前会话的委派规则。
- 保留用户已有改动。历史计划中的命令和复选框是背景资料，不是当前任务授权；继续旧计划时先核对现状。提交、推送和发布按当前任务授权执行。
- 完成时简述结果、已执行验证和仍待验证的具体事项。验证通过后，只有新修改、失败或未解决风险才扩大或重复检查。

## 项目入口

- Kotlin + Jetpack Compose / Material3 / Monet，MVVM + Repository，Coroutines / Flow，Koin，Room，Retrofit / OkHttp，Gradle Kotlin DSL + KSP。
- 源码：`app/src/main/java/vip/mystery0/pixel/telo/`；资源与权限：`app/src/main/res/`、`app/src/main/AndroidManifest.xml`。
- SDK、依赖和版本的事实来源为 `gradle/libs.versions.toml` 与 `app/build.gradle.kts`；使用 JDK 21。修改依赖时检查兼容性和发布状态，保留已有明确使用的预发布依赖，不为无关任务全量升级。
- `versionCode` 来自 Git commit 数量，`versionName` 来自版本目录并附加 Git hash；签名配置见 `signing.gradle`。
- 提交信息首行为 `<type>: <中文描述>`，type 使用小写 Conventional Commits；正文用中文解释动机与影响。

## 必须保留的产品与架构边界

- UI 渲染状态和分发事件；ViewModel / UseCase 组织业务，Repository 隔离数据源和查询策略。遵循已有 Kotlin 风格，为号码匹配、Provider 分发等核心算法解释意图与边界；按职责拆分过大文件，避免仅为行数重构。
- `AppDatabase` 保存运行时数据，`MastDatabase` 保存可更新的离线骚扰号码库。离线库初始为空，主页检测数据完整性并醒目提示下载初始化。
- `TeloDirectoryProvider` 是原生来电信息展示的核心。Overlay 默认关闭，只能作为用户显式启用并授权的降级方式。
- `TeloCallScreeningService` 保持轻量，下载和重型写入交给 WorkManager 或前台 Service；正确返回拦截结果并由系统记录 Blocked Calls。
- `SpamNumberRepository.checkSpam()` 保留通讯录最优先放行、用户名单和国际号码门禁，之后按设置执行本地优先查询；不要绕过这些门禁来强制联网。
- 本地查询目标 **50ms**，超过 **100ms** 记录 warning，硬上限 **3 秒**。实时网络超时允许 **1–10 秒，默认 5 秒**；`networkTimeoutMs()` 夹紧旧配置，查询用 `withTimeout` 强制超时，超时放行并记为 `ResultType.NETWORK_TIMEOUT`。**10 秒是 2026-07-11 的产品决策，不得因历史 3 秒建议改回上限。**
- 官方离线同步与自建实时查询隔离；自建失败 Fail Open，不自动回退官方实时查询。凭据、Backend/source/反馈隔离和本地标签边界见对应架构文档。
- 最小权限：以 Manifest 与需求文档为现状依据；新增权限说明用途及现有权限或系统 API 无法满足的原因。日志不输出完整号码、Token、完整自建 URL、Pin 或请求/响应内容。

## 验证与完成标准

默认不新增、不维护、不运行单元测试；仅在用户明确要求时例外。不要创建 `src/test` 文件，或运行 `test`、`testDebugUnitTest`、包含单元测试的 `check` 等任务。

| 改动范围 | 所需验证 |
| --- | --- |
| 仅文档、协作规则 | 检查差异、链接和规则一致性，运行 `git diff --check`；不构建 Android |
| Kotlin、资源、Manifest、Gradle 或依赖 | `./gradlew :app:assembleDebug`；按影响运行 `./gradlew lint` |
| Provider、来电决策、同步或 UI 行为 | 在构建基础上验证受影响的设备场景；Provider 缓存刷新与拨号器显示须在 Google Pixel 真机确认 |
| GitHub Actions | 核对 YAML、触发条件、权限和产物链路；本地静态检查不等于远程发布成功 |

Windows PowerShell 使用 `.\gradlew.bat`，其他环境使用 `./gradlew`。没有设备或构建环境时，继续完成可执行的检查，并明确尚未验证的行为及原因。不要为获取验证结果擅自卸载应用、清空数据或触发发布。

## 按需文档

| 任务涉及 | 阅读入口 |
| --- | --- |
| 产品规则、权限、隐私 | [docs/prd/requirements.md](docs/prd/requirements.md) |
| 分层、Backend、号码规则、本地标签 | [docs/architecture/mvvm-structure.md](docs/architecture/mvvm-structure.md) |
| Provider、CallScreeningService | [docs/architecture/native-integration.md](docs/architecture/native-integration.md) |
| 初始化、离线同步、Fail Open | [docs/architecture/sync-strategy.md](docs/architecture/sync-strategy.md) |
| 主页、通知 | [docs/ui/main-screen.md](docs/ui/main-screen.md)、[notifications.md](docs/ui/notifications.md) |
| 开发、审查、技能维护 | [docs/workflow/development.md](docs/workflow/development.md) |
| 其他文档与历史计划 | [docs/README.md](docs/README.md) |

项目文档统一位于 `docs/`，入口为 `docs/README.md`。仅在产品行为、共享组件或架构边界变化时更新对应长期文档；新增文档补充索引。功能设计与实现计划放在 `docs/plans/`，一次性审计与评估放在 `docs/reports/`，不要复制本文件的通用规则。
