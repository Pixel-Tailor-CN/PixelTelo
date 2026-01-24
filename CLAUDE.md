# Pixel Telo 项目开发指南

这份文档为 Pixel Telo 项目的开发、架构设计和代码规范提供核心指导。

## 1. 项目概览

* **项目名称**: Pixel Telo
* **核心定位**: 专为 Google Pixel 设备 (Android 10+) 设计的轻量级、隐私优先的原生风格来电识别与拦截应用。
* **核心价值**: 极致原生体验、零打扰、极速响应。
* **目标设备**: Google Pixel 系列及运行类原生 Android (AOSP) 的设备。
* **兼容性**:
  * MinSDK: 29 (Android 10)
  * TargetSDK: 35 (Android 15)

## 2. 核心架构与技术栈

### 架构模式

* **MVVM (Model-View-ViewModel)**: 严格遵循 Modern Android Development (MAD) 指南。
* **依赖注入**: 使用 **Koin** (Koin-Android, Koin-Compose)。
* **UI 框架**: **Jetpack Compose** + Material3 + Monet (动态取色)。
* **数据层**:
  * **本地**: **Room Database** (SQLite)。
  * **远程**: **Retrofit** + **OkHttp**。
* **异步处理**: **Kotlin Coroutines** + **Flow**。

### 关键组件

1. **Directory Provider (核心 UI)**:
  * 唯一用于在系统拨号器中显示来电信息的机制。
  * **严禁**默认使用悬浮窗 (Overlay)，除非作为特定 ROM 的降级方案。
2. **CallScreeningService (核心拦截)**:
  * 负责拦截骚扰电话。
  * 拦截记录必须正确写入系统通话记录 (Blocked Calls)。
3. **WorkManager**:
  * 处理繁重的数据库下载、解压与同步任务。

## 3. 关键性能与业务约束 (CRITICAL)

* **50ms 规则**: 来电时的本地数据库查询 (Room) **必须在 50ms 内完成**，严禁阻塞来电界面。
* **网络超时限制**: 实时网络查询超时时间严格限制为 **2s**。
* **数据策略**:
  * App 安装初始状态本地数据库为 **空**。
  * 主页必须包含“数据完整性检测”：若库为空，醒目提示用户从云端下载。
  * 数据更新通过 Retrofit 下载并写入 Room。
* **权限控制**:
  * 坚持最小权限原则。
  * 仅申请 `READ_CALL_LOG`, `READ_CONTACTS` (Provider 必要), `ANSWER_PHONE_CALLS` 等核心权限。

## 4. 代码规范与工程标准

* **语言**: Kotlin (Strict mode)。
* **文件结构**:
  * 单文件原则上不超过 1000 行。
  * 业务逻辑必须位于 `ViewModel` 或 `UseCase`，严禁写入 Activity/UI 层。
* **注释规范**:
  * 使用 **中文** 编写 KDoc 与行内注释。
  * 核心算法（如号码匹配、Provider 查询分发）必须有详细注释。
* **服务分离**: `CallScreeningService` 必须保持极简，重型任务分离至 `WorkManager`。

## 5. 构建与验证流程

在提交代码前，必须执行以下检查：

* **编译构建**: `./gradlew :app:assembleDebug`
* **代码检查**: `./gradlew lint`
* **单元测试**: `./gradlew test`

### 验证清单

1. **全新安装流程**: 验证主页是否提示“数据缺失”，以及下载更新流程是否正常。
2. **拦截测试**: 使用模拟号码测试 `CallScreeningService` 挂断逻辑及通话记录写入。
3. **原生 UI 集成**: 在 Pixel 拨号器验证 `Directory Provider` 是否成功注入 Label 信息。
4. **真机验证**: 必须在 Google Pixel 真机上验证 Provider 的缓存刷新与显示效果。

## 6. 文档体系 (.agentdocs/)

* `prd/`: 产品需求 (Call Guardian, Data Sync)
* `architecture/`: 架构设计 (MVVM, Native Integration)
* `ui/`: 界面规范 (Main Screen States)
* `workflow/`: 任务流与开发指引

---
**注意**: 在进行任何功能开发时，请优先考虑“原生集成”与“性能开销”，避免破坏 Pixel 系统的纯净体验。
