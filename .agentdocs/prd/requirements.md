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

### F04: 可选自建实时查询

* **固定边界**: 自建服务只替代实时号码查询，官方离线数据库同步保持固定且始终可用。
* **配置验证**:
    * Base URL 只接受根路径 HTTPS；示例使用 `https://mast.example.com/`。
    * 支持系统信任 TLS，以及面向自签名证书的精确 SPKI SHA-256 Pinning；两种模式都必须校验证书
      有效期和域名/IP SAN。
    * 启用前必须验证服务名称、最低 SemVer、API Version 2、Instance ID、`query_v2` capability 和
      响应身份 Header。
* **Backend 切换**: 配置完整验证并安全提交后才原子发布新的 Backend Snapshot；进行中的请求持 lease
  继续使用旧 Snapshot，普通切换等待最后 lease 后再关闭旧 Client。普通存储失败保留旧 Backend 且不
  宣称候选生效；自建配置损坏或安全校验失败时保持用户选择但立即撤销联网，不静默切回官方。
* **Fail Open**: 自建查询的所有失败都允许来电通过，且不得用官方实时查询做第二次请求。
* **source 隔离**: 官方与每个自建 Instance 按 Backend ID 独立保存 source 顺序、启用状态和可用性。
* **反馈隔离**: 自建结果不保存反馈 Token、不显示反馈入口、不发送反馈请求；历史官方反馈仍只提交到
  官方 Client。

### F05: 自建凭据与隐私

* Token 必须由 Android Keystore AES-GCM 保护，密文文件从 Auto Backup 和设备迁移中排除。
* UI 状态、Room、默认 SharedPreferences、App 导出备份、日志和异常展示不得包含明文 Token。Dialog
  只能将 Token 作为一次性 `CharArray` 直接移交验证入口，并在所有完成路径清零。
* 日志不得输出完整号码、完整自建 URL、Pin、Authorization Header、完整请求/响应或私人联调地址；
  仅允许稳定错误分类、耗时、HTTP 状态和脱敏 ID。
* 配置界面只展示脱敏 Host、服务版本和验证时间，不展示完整 URL、Token、Pin 或 Instance ID。DNS
  每个 label 只保留首字符；IPv4 只保留第一段；IPv6 固定显示 `[IPv6]`，不得回退展示非法原始输入。

## 3. 性能需求

### P01: 延迟约束

* **50ms 目标 (Local)**: 来电时的本地数据库查询 (**Room**) 优先满足 **50ms** 目标；超过 100ms
  必须记录警告，硬性上限为 3 秒。
* **1–10s 规则 (Online)**: 若本地未命中，允许进行在线查询。超时由用户在 1 至 10 秒内配置，默认
  5 秒；超范围旧配置自动修正。超时后强制放行并记录脱敏分类与耗时。
* **网络权限**: APP 必须保留网络请求权限以支持在线查询和数据库更新。

## 4. 技术约束与质量

* **架构**: MVVM (Model-View-ViewModel) 配合 Repository 模式。
* **技术栈**: Kotlin, Jetpack Compose, Room, Retrofit, Koin。
* **服务隔离**: `CallScreeningService` 必须保持极度精简。繁重的任务（如下载）必须剥离至 `WorkManager`
  或前台 Service (Foreground Service)。
