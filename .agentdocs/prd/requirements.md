# 产品需求文档 (PRD)

## 1. 核心产品概览

* **项目名称**: Pixel Telo
* **核心价值**: 原生体验、隐私优先、极致轻量。
* **目标设备**: Google Pixel 系列 (Android 10+)。
* **兼容目标**: 运行原生/类原生 Android (AOSP) 的设备。

## 2. 功能需求

### F01: 原生集成 (Call Guardian)

* **Directory Provider**:
    * 必须使用 `Directory Provider` API 将来电显示标签直接注入系统拨号器。
    * **约束**: 严禁将悬浮窗 (Overlay) 作为默认显示方式。Overlay 仅允许作为特定 ROM 的降级方案。
* **Call Screening**:
    * 实现 `CallScreeningService` 以拦截来电。
    * 被拦截的通话必须正确写入系统通话记录，标记为 "Blocked Calls" (拦截通话)。

### F02: 数据管理策略

* **初始状态**: App 安装后，本地数据库默认为**空**。
* **数据完整性检测**:
    * App 主页面必须包含“数据完整性检测”逻辑。
    * 若检测到本地库为空，需醒目提示用户从云端下载初始化数据。
* **数据同步**:
    * 支持通过 **Retrofit** 下载数据库文件或增量更新。
    * 高效地将数据写入/替换到本地 **Room** 数据库。

### F03: 权限管理

* **原则**: 最小权限。
* **核心权限**:
    * `READ_CALL_LOG`
    * `READ_CONTACTS` (Directory Provider 必需)
    * `ANSWER_PHONE_CALLS`

## 3. 性能需求

### P01: 延迟约束

* **100ms 规则 (Local)**: 来电时的本地数据库查询 (**Room**) 必须在 **100ms** 内完成。
* **3s 规则 (Online)**: 若本地未命中，允许进行在线查询，但必须在 **3s** 内超时。超时后强制放行并记录日志。
* **网络权限**: APP 必须保留网络请求权限以支持在线查询和数据库更新。

## 4. 技术约束与质量

* **架构**: MVVM (Model-View-ViewModel) 配合 Repository 模式。
* **技术栈**: Kotlin, Jetpack Compose, Room, Retrofit, Koin。
* **服务隔离**: `CallScreeningService` 必须保持极度精简。繁重的任务（如下载）必须剥离至 `WorkManager`
  或前台 Service (Foreground Service)。
