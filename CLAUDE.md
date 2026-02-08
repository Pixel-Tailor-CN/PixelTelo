# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

## 项目概览

Pixel Telo 是专为 Google Pixel 设备设计的来电识别与拦截应用，采用原生 Android 风格，强调隐私保护和极速响应。

**核心定位**: 轻量级、隐私优先、零打扰的原生体验
**目标设备**: Google Pixel 系列及类原生 Android (AOSP) 设备
**兼容性**: MinSDK 29 (Android 10), TargetSDK 35 (Android 15)

## 构建与开发命令

### 基础构建

```bash
# 编译 Debug 版本
./gradlew assembleDebug

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

### 代码检查与测试

```bash
# 运行 Lint 检查
./gradlew lint

# 运行所有单元测试
./gradlew test

# 运行 Debug 单元测试
./gradlew testDebugUnitTest

# 运行所有检查（包括 lint 和 test）
./gradlew check
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
   - `BlockedCall`: 拦截记录（包括实际拦截、仅提示、超时等状态）
   - 位置: `vip.mystery0.pixel.telo.data.AppDatabase`

2. **MastDatabase** (动态路径): 存储骚扰号码库
   - `SpamNumberEntity`: 骚扰号码数据
   - `MetadataEntity`: 数据库元信息
   - 特点: 从云端下载，可动态更新，初始为空
   - 位置: `vip.mystery0.pixel.telo.data.MastDatabase`

### MVVM 架构

- **Model**: Room 实体 + Repository 层
- **View**: Jetpack Compose UI
- **ViewModel**: 业务逻辑处理，使用 Kotlin Flow
- **依赖注入**: Koin (配置在 `di/AppModule.kt`)

### 三大核心组件

1. **TeloCallScreeningService** (`service/TeloCallScreeningService.kt`)
   - 继承 `CallScreeningService`，负责来电拦截
   - 必须保持极简，重型任务分离至 WorkManager
   - 拦截记录写入系统通话记录 (Blocked Calls)

2. **TeloDirectoryProvider** (`provider/TeloDirectoryProvider.kt`)
   - 继承 `ContentProvider`，实现 Directory Provider
   - 在系统拨号器中显示来电信息（唯一推荐方式）
   - 严禁默认使用悬浮窗 (Overlay)

3. **SpamNumberRepository** (`data/repository/SpamNumberRepository.kt`)
   - 号码查询核心逻辑：先本地查询，再网络查询
   - 实现性能约束（见下文）

## 关键性能约束 (CRITICAL)

### 3s 规则

来电时的本地数据库查询 (Room) **必须在 3s 内完成**，严禁阻塞来电界面。

- 实现位置: `SpamNumberRepository.checkSpam()`
- 本地查询超过 100ms 会记录警告日志

### 网络超时限制

实时网络查询超时时间严格限制为 **3s** (含网络延迟)。

- 实现: `withTimeout(3000L)` in `SpamNumberRepository.checkSpam()`
- 超时后允许来电通过，但记录为 `ResultType.NETWORK_TIMEOUT`

### 数据策略

- App 安装初始状态本地骚扰号码库为**空**
- 主页必须包含"数据完整性检测"，提示用户下载
- 数据更新通过 Retrofit 下载并写入 MastDatabase

## 代码规范

### 语言与风格

- **语言**: Kotlin (Strict mode, JVM Target 21)
- **注释**: 使用**中文**编写 KDoc 与行内注释
- **文件大小**: 单文件原则上不超过 1000 行

### 架构约束

- 业务逻辑必须位于 `ViewModel` 或 `UseCase`，严禁写入 Activity/UI 层
- 核心算法（号码匹配、Provider 查询分发）必须有详细注释
- CallScreeningService 必须保持极简

### 权限控制

坚持最小权限原则，仅申请必要权限：

- `READ_CALL_LOG`: 读取通话记录
- `READ_PHONE_STATE`: 读取电话状态
- `READ_CONTACTS`: Directory Provider 必需
- `MANAGE_OWN_CALLS`: 管理来电
- `INTERNET`: 网络查询

## 验证清单

在提交代码前，必须验证：

1. **全新安装流程**: 主页是否提示"数据缺失"，下载更新流程是否正常
2. **拦截测试**: 使用模拟号码测试 CallScreeningService 挂断逻辑及通话记录写入
3. **原生 UI 集成**: 在 Pixel 拨号器验证 Directory Provider 是否成功注入 Label 信息
4. **真机验证**: 必须在 Google Pixel 真机上验证 Provider 的缓存刷新与显示效果
5. **性能测试**: 验证本地查询和网络查询是否满足时间约束

## 技术栈

- **UI**: Jetpack Compose + Material3 + Monet 动态取色
- **数据库**: Room (SQLite)
- **网络**: Retrofit + OkHttp + Kotlinx Serialization
- **异步**: Kotlin Coroutines + Flow
- **依赖注入**: Koin
- **构建**: Gradle (Kotlin DSL) + KSP

## 版本管理

- **versionCode**: 自动从 git commit 数量生成
- **versionName**: 从 `libs.versions.toml` 读取，附加 git hash
- **签名配置**: 在 `signing.gradle` 中配置

## 文档体系

项目包含详细的 `.agentdocs/` 文档：

- `prd/`: 产品需求文档
- `architecture/`: 架构设计文档
- `ui/`: 界面规范文档
- `workflow/`: 任务流与开发指引

---

**开发原则**: 在进行任何功能开发时，优先考虑"原生集成"与"性能开销"，避免破坏 Pixel 系统的纯净体验。
