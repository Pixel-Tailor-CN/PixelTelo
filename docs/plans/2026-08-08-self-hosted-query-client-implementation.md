# 自建实时查询服务 App 接入实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 为 Pixel Telo 完整实现官方/自建实时查询 Backend 切换、安全凭据、系统 TLS 与 SPKI Pinning、运行期版本身份校验、source 隔离、反馈隔离和安全告警。

**架构：** 保留固定官方离线同步链路，将官方查询与自建查询拆成独立 OkHttp/Retrofit Client；`QueryBackendProvider` 以不可变 `QueryBackendSnapshot` 为一次请求提供稳定 Backend。自建配置经临时 Client 完整验证后原子启用，Token 由 Android Keystore 保护，运行期安全错误阻止继续访问自建服务但绝不回退官方实时查询。

**技术栈：** Kotlin 2.4、Android MinSDK 29、Jetpack Compose Material3、OkHttp 5、Retrofit 3、Kotlinx Serialization、Room 2.8、Koin 4.2、Android Keystore、Coroutines/Flow。

## 全局约束

- 最低自建服务端版本首版固定为 `0.1.2`，只在 `gradle/libs.versions.toml` 维护，经 `BuildConfig.MIN_SELFHOST_SERVER_VERSION` 使用。
- 私人联调实例的域名、IP、Token、证书和 Pin 不得写入代码、文档、资源、日志、测试夹具或提交信息。
- 官方离线同步 Base URL、证书策略和下载地址不可配置。
- 自建实时查询失败必须 Fail Open，且不得自动回退或再次请求官方实时查询。
- 网络查询超时继续由用户配置并夹紧在 1 至 10 秒，默认 5 秒。
- 只允许根路径 HTTPS；禁止 HTTP、userinfo、query、fragment、业务子路径和自动 Redirect。
- 自建业务请求必须使用 Bearer Token；401 不重试，不使用 OkHttp Authenticator。
- 自建服务只支持 API Version 2，最低 capability 为 `query_v2`。
- Release 拒绝预发布服务端；Debug 仅通过默认关闭的调试开关允许预发布版本。
- 自建查询不保存反馈 Token、不显示反馈入口、不调用反馈 API。
- 不新增权限，不新增相机扫码，不引入大型 SemVer 或安全依赖。
- 代码注释、KDoc 和项目文档使用中文；日志文本使用英文且不得包含完整号码或凭据。
- 遵循项目策略：不创建 `src/test` 单元测试，不运行 `test`、`check` 或其他单元测试命令。
- 每个任务实施前检查并保留用户现有未提交改动，尤其不得覆盖 `gradle/libs.versions.toml` 的既有修改。

## 文件结构与职责

### 新增文件

- `app/src/main/java/vip/mystery0/pixel/telo/data/query/SemanticVersion.kt`：严格 SemVer 解析和比较。
- `app/src/main/java/vip/mystery0/pixel/telo/data/query/QueryBackend.kt`：Backend 类型、身份、Snapshot 和查询结果封装。
- `app/src/main/java/vip/mystery0/pixel/telo/data/query/SelfHostedConfig.kt`：自建草稿、已验证配置、TLS 模式、状态和安全错误模型。
- `app/src/main/java/vip/mystery0/pixel/telo/data/query/SelfHostedCredentialStore.kt`：Keystore AES-GCM 加解密与独立密文存储。
- `app/src/main/java/vip/mystery0/pixel/telo/data/query/SelfHostedTls.kt`：自签名证书有效期、Hostname/SAN 和精确 SPKI Pin 校验。
- `app/src/main/java/vip/mystery0/pixel/telo/data/query/SelfHostedCompatibilityInterceptor.kt`：运行期版本 Header 与 Instance ID 校验。
- `app/src/main/java/vip/mystery0/pixel/telo/data/query/SelfHostedQueryClientFactory.kt`：临时和已验证自建 Client 构建。
- `app/src/main/java/vip/mystery0/pixel/telo/data/query/QueryBackendProvider.kt`：当前 Backend Snapshot、配置验证、原子切换和阻止状态。
- `app/src/main/java/vip/mystery0/pixel/telo/data/remote/SelfHostedApi.kt`：info API 与 DTO。
- `app/src/main/java/vip/mystery0/pixel/telo/data/repository/SelfHostedConfigRepository.kt`：非敏感配置和按 Backend 的 source 配置存储。
- `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/settings/SelfHostedQueryDialog.kt`：自建配置草稿 UI。

### 修改文件

- `gradle/libs.versions.toml`、`app/build.gradle.kts`：最低版本单点配置。
- `app/src/main/java/vip/mystery0/pixel/telo/data/remote/QueryApi.kt`：拆分反馈接口、可空反馈 Token、扩展错误 DTO。
- `app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt`：三 Client 和新组件注入。
- `app/src/main/java/vip/mystery0/pixel/telo/data/repository/QueryRepository.kt`：按 Snapshot 查询和按 Backend 管理 source。
- `app/src/main/java/vip/mystery0/pixel/telo/data/repository/SpamNumberRepository.kt`：携带 Backend 身份并保持 Fail Open。
- `app/src/main/java/vip/mystery0/pixel/telo/data/AppDatabase.kt`：Room 版本 8 升 9。
- `app/src/main/java/vip/mystery0/pixel/telo/data/entity/BlockedCall.kt`：持久化 `queryBackendId`。
- `app/src/main/java/vip/mystery0/pixel/telo/data/repository/BlockedCallRepository.kt`：只为官方结果保存反馈凭据。
- `app/src/main/java/vip/mystery0/pixel/telo/service/TeloCallScreeningService.kt`：写入 Backend 身份。
- `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/HomeViewModel.kt`：安全告警和官方反馈门禁。
- `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt`：Backend 配置草稿、验证、切换和当前 source 状态。
- `app/src/main/java/vip/mystery0/pixel/telo/receiver/FeedbackActionReceiver.kt`：历史记录的官方反馈门禁。
- `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/HomeScreen.kt`：自建安全 WarningCard。
- `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/SettingsScreen.kt`：自建配置 Dialog 挂载。
- `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/settings/OnlineQueryPreferences.kt`：Backend 设置入口和摘要。
- `app/src/main/res/values/strings.xml`、`app/src/main/res/values-zh/strings.xml`：中英文 UI 文案。
- `app/src/main/res/xml/backup_rules.xml`、`app/src/main/res/xml/data_extraction_rules.xml`：排除凭据文件。
- `.agentdocs/architecture/mvvm-structure.md`、`.agentdocs/architecture/sync-strategy.md`、`.agentdocs/prd/requirements.md`、`.agentdocs/ui/main-screen.md`、`.agentdocs/index.md`：架构和产品文档。

---

### Task 1：最低版本配置与严格 SemVer

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/query/SemanticVersion.kt`

**Interfaces:**
- Produces: `BuildConfig.MIN_SELFHOST_SERVER_VERSION: String`
- Produces: `SemanticVersion.parse(value: String, allowPreRelease: Boolean): SemanticVersion?`
- Produces: `SemanticVersion.compareTo(other: SemanticVersion): Int`

- [x] **Step 1：添加最低服务端版本的单点配置**

在 `[versions]` 中增加 `selfhost-min-server = "0.1.2"`，保留文件中的用户既有版本修改。在 `defaultConfig` 中增加：

```kotlin
buildConfigField(
    "String",
    "MIN_SELFHOST_SERVER_VERSION",
    "\"${libs.versions.selfhost.min.server.get()}\"",
)
```

- [x] **Step 2：实现严格 SemVer 值对象**

实现不可变 `SemanticVersion`，解析规则覆盖 `MAJOR.MINOR.PATCH`、可选 prerelease 和 build metadata；拒绝负数、缺段、前导零、空 prerelease 标识和非法字符。比较时依次比较主版本、次版本、补丁和 SemVer prerelease 优先级，build metadata 不参与比较。

- [x] **Step 3：增加构建期防错**

在 Gradle 配置阶段用严格稳定版本正则校验 Version Catalog 中的最低版本，非法时抛出 `GradleException("Invalid self-host minimum server version")`，避免错误配置进入 APK。

- [x] **Step 4：执行阶段验证**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`，生成的 Debug `BuildConfig` 包含值 `0.1.2`。

- [x] **Step 5：提交本任务**

仅暂存本任务文件，确认未带入原有无关修改后提交，建议提交信息：`feat: 添加自建服务最低版本配置`。

### Task 2：Backend、配置与远程契约模型

**Files:**
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/query/QueryBackend.kt`
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/query/SelfHostedConfig.kt`
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/remote/SelfHostedApi.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/remote/QueryApi.kt`

**Interfaces:**
- Produces: `enum class QueryBackendType { OFFICIAL, SELF_HOSTED }`
- Produces: `data class QueryBackendSnapshot(val backendId: String, val type: QueryBackendType, val queryApi: QueryApi, val feedbackSupported: Boolean, val selfHostedIdentity: SelfHostedIdentity? = null)`
- Produces: `data class BackendQueryResponse(val backendId: String, val response: QueryResponse, val feedbackSupported: Boolean)`
- Produces: `enum class SelfHostedTlsMode { SYSTEM, SPKI_PIN }`
- Produces: `data class SelfHostedDraft(val baseUrl: String, val token: String, val tlsMode: SelfHostedTlsMode, val spkiPin: String = "", val allowPreRelease: Boolean = false)`
- Produces: `data class VerifiedSelfHostedConfig(...)`，包含规范化 URL、TLS 模式、Pin、Instance ID、Version、API Version、capabilities 和验证时间，不包含明文 Token
- Produces: `sealed interface SelfHostedBlockReason`
- Produces: `SelfHostedApi.getInfo(): Response<SelfHostedInfoResponse>`
- Produces: `OfficialFeedbackApi.submitFeedback(request: FeedbackRequest): FeedbackResponse`

- [x] **Step 1：定义 Backend 和查询结果模型**

官方常量使用 `OFFICIAL_BACKEND_ID = "official"`，自建 ID 由 `selfHostedBackendId(instanceId)` 返回 `selfhost:<lowercase UUID>`。`QueryBackendSnapshot` 必须完全不可变，禁止持有可变草稿。

- [x] **Step 2：定义自建配置状态模型**

增加 `SelfHostedConnectionState`，至少包含 `NotConfigured`、`Ready(config)`、`Blocked(config, reason)`；`SelfHostedBlockReason` 区分凭据、TLS、Pin、低版本、API 不兼容、Instance 改变和 Header 错误，提供安全的 UI 分类而非原始异常正文。

- [x] **Step 3：拆分查询和反馈 API**

`QueryApi` 只保留 `getSources()` 与 `queryNumber()`；把反馈方法移动到 `OfficialFeedbackApi`。将 `QueryResponse.feedbackToken` 改为 `String? = null`，`QueryErrorResponse` 增加 `code` 与 `requestId` 可空字段。

- [x] **Step 4：定义 info API**

`SelfHostedApi` 使用 `@GET("api/selfhost/v1/info")`，返回 `Response<SelfHostedInfoResponse>` 以便验证正文和 Header。所有 DTO 使用 `@SerialName` 匹配 snake_case，并继续启用未知字段忽略。

- [x] **Step 5：编译并提交**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`。如调用方因反馈 API 拆分暂时无法编译，则同一步先把 Koin 中官方反馈 API 注入和 `QueryRepository` 构造参数调整到可编译，但不改变运行行为。

建议提交信息：`refactor: 定义实时查询 Backend 契约`。

### Task 3：Keystore 凭据与非敏感配置持久化

**Files:**
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/query/SelfHostedCredentialStore.kt`
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/SelfHostedConfigRepository.kt`
- Modify: `app/src/main/res/xml/backup_rules.xml`
- Modify: `app/src/main/res/xml/data_extraction_rules.xml`

**Interfaces:**
- Produces: `SelfHostedCredentialStore.save(token: CharArray): Result<Unit>`
- Produces: `SelfHostedCredentialStore.load(): Result<CharArray>`
- Produces: `SelfHostedCredentialStore.clear()`
- Produces: `SelfHostedConfigRepository.connectionState: StateFlow<SelfHostedConnectionState>`
- Produces: `SelfHostedConfigRepository.loadVerifiedConfig(): VerifiedSelfHostedConfig?`
- Produces: `SelfHostedConfigRepository.commitVerified(config: VerifiedSelfHostedConfig, token: CharArray): Result<Unit>`
- Produces: `SelfHostedConfigRepository.markBlocked(reason: SelfHostedBlockReason)`
- Produces: `SelfHostedConfigRepository.clearBlockedState()`

- [x] **Step 1：实现 Keystore AES-GCM 存储**

使用 `AndroidKeyStore`、AES/GCM/NoPadding、256-bit Key 和每次随机 12-byte IV。密文保存为版本化结构，例如 `version|base64(iv)|base64(ciphertext)`；凭据文件名固定为 `self_hosted_credentials`，Key Alias 固定为应用私有值。方法内部在 `finally` 中清零传入和临时 `CharArray`/`ByteArray`。

- [x] **Step 2：实现非敏感配置 Repository**

使用独立 `self_hosted_config` SharedPreferences。序列化已验证配置、当前 Backend 类型、阻止原因和验证时间；不得序列化 Token。提交新配置时先保存新密文，再保存完整配置，最后更新启用指针；失败时保持旧配置可用并清理未引用的新密文。

- [x] **Step 3：排除凭据备份**

在 API 30 及以下规则中增加：

```xml
<exclude domain="sharedpref" path="self_hosted_credentials.xml" />
```

在 API 31+ 的 `cloud-backup` 和 `device-transfer` 中都排除同一路径。非敏感配置可参与备份，但恢复后因 Keystore 凭据不存在必须进入重新验证状态。

- [x] **Step 4：静态安全检查**

Run: `rg -n "self_hosted_credentials|Authorization|Bearer" app/src/main`

Expected: 凭据文件只出现在 Credential Store 与两份排除规则中；没有日志打印 Token 的代码。

- [x] **Step 5：编译并提交**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`。

建议提交信息：`feat: 使用 Keystore 保护自建服务凭据`。

### Task 4：URL、系统 TLS 与自签名 SPKI Pinning Client

**Files:**
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/query/SelfHostedTls.kt`
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/query/SelfHostedQueryClientFactory.kt`

**Interfaces:**
- Produces: `normalizeSelfHostedBaseUrl(raw: String): Result<HttpUrl>`
- Produces: `normalizeSpkiPin(raw: String): Result<String>`
- Produces: `SelfHostedQueryClientFactory.createDraftClient(draft: SelfHostedDraft): Result<SelfHostedClientBundle>`
- Produces: `SelfHostedQueryClientFactory.createVerifiedClient(config: VerifiedSelfHostedConfig, token: CharArray, onBlocked: (SelfHostedBlockReason) -> Unit): Result<SelfHostedClientBundle>`
- Produces: `data class SelfHostedClientBundle(val queryApi: QueryApi, val selfHostedApi: SelfHostedApi, val close: () -> Unit)`

- [x] **Step 1：实现 URL 与 Pin 规范化**

使用 OkHttp `HttpUrl` 解析，只接受 `https`、根路径、无 userinfo/query/fragment、合法 host/port；关闭相对业务路径输入。Pin 只接受解码后正好 32 bytes 的 `sha256/<Base64>`，保存时输出规范 Base64。

- [x] **Step 2：实现系统信任模式**

使用全新 `OkHttpClient.Builder` 的平台默认 TrustManager 与 Hostname Verifier，设置 `followRedirects(false)`、`followSslRedirects(false)`，不共享官方 Client Dispatcher、ConnectionPool、CookieJar 或 Authenticator。

- [x] **Step 3：实现 Pinning TrustManager**

专用 `X509ExtendedTrustManager` 在服务端校验中执行：叶子证书有效期检查、目标 host 的标准 OkHttp Hostname/SAN 校验、叶子公钥 SPKI SHA-256 计算和 `MessageDigest.isEqual` 常量时间比较。仅接受当前配置的精确 Pin；不得接受任意自签名证书，也不得修改系统 Trust Store。

- [x] **Step 4：限制 Bearer Token 发送范围**

专用 Interceptor 在每个请求前比较 scheme、host 和有效端口，仅完全匹配草稿/已验证 URL 时添加 `Authorization: Bearer <token>`。不使用 Authenticator，401 不重试；禁止自动 Redirect，避免跨 Host 泄露。

- [x] **Step 5：编译和本地无凭据检查**

Run: `./gradlew :app:assembleDebug`

Run: `rg -n -S "http://" app docs/plans/2026-08-08-self-hosted-query-client-implementation.md`

Expected: 构建成功；不存在新增明文 HTTP 地址。私人实例地址使用本机保存、未纳入版本控制的 denylist 另行扫描。

- [x] **Step 6：提交本任务**

建议提交信息：`feat: 支持自建服务 SPKI Pinning`。

### Task 5：配置验证与运行期版本身份拦截

**Files:**
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/query/SelfHostedCompatibilityInterceptor.kt`
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/query/QueryBackendProvider.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/query/SelfHostedQueryClientFactory.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/SelfHostedConfigRepository.kt`

**Interfaces:**
- Produces: `QueryBackendProvider.snapshot(): QueryBackendSnapshot?`
- Produces: `QueryBackendProvider.state: StateFlow<QueryBackendState>`
- Produces: `QueryBackendProvider.validateAndEnable(draft: SelfHostedDraft): SelfHostedValidationResult`
- Produces: `QueryBackendProvider.revalidate(): SelfHostedValidationResult`
- Produces: `QueryBackendProvider.useOfficial()`
- Produces: `sealed interface SelfHostedValidationResult { data class Success(...); data class Failure(val category: SelfHostedErrorCategory, val safeMessage: String?) }`

- [x] **Step 1：实现草稿验证流水线**

按 URL/Pin 校验、临时 Client、info、info Header、sources 的顺序执行。验证 `service`、稳定/Debug SemVer、最低版本、API Version 2、UUID Instance ID、`query_v2` capability；info 正文身份必须与三个 Header 一致。

- [x] **Step 2：实现运行期 Interceptor**

对每个鉴权响应检查三个 Header：`X-Pixel-Telo-Server-Version`、`X-Pixel-Telo-API-Version`、`X-Pixel-Telo-Instance-ID`。缺失、重复冲突、非法、低版本、API 非 2 或 Instance ID 变化时关闭响应、触发 `markBlocked` 并抛出稳定安全异常。

- [x] **Step 3：实现不可变 Snapshot 和原子切换**

官方 Snapshot 固定使用官方 Query API、`backendId="official"`、`feedbackSupported=true`。自建验证完全成功后提交配置和凭据，再一次性发布自建 Snapshot。`snapshot()` 在安全阻止状态返回 `null`，让调用方直接 Fail Open；绝不返回官方 Snapshot 作为降级。

- [x] **Step 4：处理启动恢复**

App 启动读取当前 Backend。官方直接可用；自建配置完整且凭据可解密时构建已验证 Client；凭据缺失、Key 失效或配置损坏时进入 Blocked，不发网络请求且不自动切回官方。

- [x] **Step 5：编译并提交**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`。

建议提交信息：`feat: 强制校验自建服务版本身份`。

### Task 6：拆分三条 Client 并接入 Koin

**Files:**
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/SyncRepository.kt`（仅在构造参数需明确命名时修改）

**Interfaces:**
- Consumes: Task 2–5 的 API、Factory、Repository 和 Provider
- Produces: Koin qualifier `officialSync`、`officialQuery`、`officialFeedback`
- Produces: 单例 `QueryBackendProvider`、`SelfHostedConfigRepository`、`SelfHostedCredentialStore`

- [x] **Step 1：为官方网络对象增加 qualifier**

构建彼此独立的官方 Sync Retrofit 和官方 Query Retrofit，Base URL 都继续使用现有固定官方地址。Sync Retrofit 只创建 `SyncApi`；Query Retrofit 创建 `QueryApi` 和 `OfficialFeedbackApi`。

- [x] **Step 2：注册自建组件**

注入 `Context`、共享安全 `Json`、Credential Store、Config Repository、Client Factory 和 Backend Provider。自建 Client 不注册为固定 Retrofit 单例，由 Factory 根据已验证配置创建。

- [x] **Step 3：检查依赖链隔离**

确认 `SyncRepository` 只能拿到官方 `SyncApi`，且任何自建 URL、Token、Pin 或 Backend 状态都不在其构造参数中。

- [x] **Step 4：编译并提交**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`，Koin 构造无重复或缺失定义。

建议提交信息：`refactor: 隔离官方与自建网络 Client`。

### Task 7：按 Backend 隔离 source 并改造查询链路

**Files:**
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/QueryRepository.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/SpamNumberRepository.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/HomeViewModel.kt`

**Interfaces:**
- Changes: `QueryRepository.queryNumber(phone: String): BackendQueryResponse`
- Changes: `QueryRepository.sourceState: StateFlow<QuerySourceState>` 始终对应当前 Backend
- Changes: `QueryRepository.refreshSources(): Result<Unit>` 使用一次 Snapshot
- Changes: `QueryRepository.submitFeedback(...)` 始终调用官方反馈 API
- Produces: `CheckResult.queryBackendId: String?`

- [x] **Step 1：迁移 source 存储结构**

把 `query_source_config` 迁移为序列化 Map，Key 为 Backend ID。首次读取时若新 Map 不存在，将旧值保存为 `official`，重新读取确认成功后删除旧 Key；失败则保留旧 Key并将官方配置置为未初始化。

- [x] **Step 2：让 source 操作绑定 Snapshot**

刷新、保存、恢复默认值和 invalid source 更新均显式使用操作开始时的 Backend ID。Backend 切换时先发布目标缓存或空加载状态，再异步刷新；来电查询中不得刷新 sources。

- [x] **Step 3：改造实时查询返回值**

`queryNumber()` 一次读取 Snapshot 和该 Backend sources，调用 Snapshot 的 Query API，返回 `BackendQueryResponse`。自建响应强制 `feedbackToken=null`；Snapshot 不可用时抛出稳定的 BackendBlocked 异常供上层 Fail Open。

- [x] **Step 4：保持 SpamNumberRepository 超时与 Fail Open**

`queryNetwork()` 和 `checkSpam()` 继续使用 `networkTimeoutMs()` 与 `withTimeout`。将 Backend ID 写入 `CheckResult`；自建异常不得触发第二次官方查询。安全错误使用英文脱敏日志，只记录分类和耗时。

- [x] **Step 5：更新 ViewModel 的 source 状态消费**

设置页和首页继续消费 `queryRepository.sourceState`，但 Backend 切换时清空编辑草稿并按新 Backend 重载，防止旧 source 短暂显示或保存到新 Backend。

- [x] **Step 6：编译并提交**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`，现有官方查询行为保持可用。

建议提交信息：`feat: 按实时查询 Backend 隔离数据源`。

### Task 8：Room 迁移与反馈严格归属

**Files:**
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/AppDatabase.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/entity/BlockedCall.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/BlockedCallRepository.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/service/TeloCallScreeningService.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/HomeViewModel.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/receiver/FeedbackActionReceiver.kt`

**Interfaces:**
- Produces: `BlockedCall.queryBackendId: String?`
- Produces: `MIGRATION_8_9`
- Changes: `BlockedCallRepository.insert(..., queryBackendId: String? = null, feedbackToken: String? = null)`
- Changes: `BlockedCallRepository.attachQueryResult(call, result: BackendQueryResponse): BlockedCall`

- [x] **Step 1：增加 Backend 归属字段和迁移**

Room 版本升至 9，`MIGRATION_8_9` 执行：

```sql
ALTER TABLE `blocked_calls` ADD COLUMN `queryBackendId` TEXT
```

旧记录中具有非空 `feedbackToken` 的记录更新为 `queryBackendId='official'`，以保留现有反馈能力；其他旧记录保持 null。

- [x] **Step 2：在所有写入路径携带 Backend ID**

更新来电服务、手动测试和重试网络查询写回逻辑。纯本地结果 Backend ID 为 null；官方联网结果为 `official`；自建结果为 `selfhost:<instance_id>`。

- [x] **Step 3：在 Repository 强制反馈门禁**

只有 `queryBackendId == "official"` 且 `feedbackSupported == true` 时保存非空 Token 并设为 `PENDING`。自建结果无条件清除 Token 并使用 `UNAVAILABLE`，不能依赖服务端是否省略字段。

- [x] **Step 4：在 UI 与 Receiver 增加第二道门禁**

Home 反馈组件、`HomeViewModel.submitFeedback()` 和 `FeedbackActionReceiver` 都要求记录 Backend ID 为 `official`。不符合时不调用网络，必要时把异常旧记录更新为 `UNAVAILABLE`。

- [x] **Step 5：编译并提交**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`，Room KSP 校验迁移后的实体结构可编译。

建议提交信息：`feat: 隔离自建查询反馈归属`。

### Task 9：设置页 Backend 配置与完整验证交互

**Files:**
- Create: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/settings/SelfHostedQueryDialog.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/settings/OnlineQueryPreferences.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/SettingsScreen.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

**Interfaces:**
- Produces: `SelfHostedDraftUiState`
- Produces: `SettingViewModel.openSelfHostedConfig()`、`updateSelfHostedDraft(...)`、`validateAndEnableSelfHosted()`、`revalidateSelfHosted()`、`useOfficialBackend()`
- Consumes: `QueryBackendProvider.state` 和验证 API

- [x] **Step 1：增加 Backend 设置入口和摘要**

在 source 设置前增加“实时查询服务器”。摘要展示官方服务，或自建服务的脱敏 Host、最近验证版本和时间；不展示完整 URL、Token、Pin 或 Instance ID。

- [x] **Step 2：实现配置 Dialog**

包含 Base URL、密码样式 Token、TLS 模式、Pinning 模式下的 SPKI Pin、“测试并启用”和取消。编辑使用普通 Compose 内存状态且不使用 `rememberSaveable`；关闭 Dialog 时清空 Token `TextFieldValue` 和草稿引用。

- [x] **Step 3：实现状态与分类错误展示**

验证时禁用重复提交并显示进度。失败只展示 URL、网络、TLS、Pin、Token、版本、协议或身份分类文案；不直接展示底层异常、完整 URL 或响应正文。已启用配置提供“重新测试”“修改配置”“切换回官方服务”。

- [x] **Step 4：增加 Debug 预发布开关**

只在 `BuildConfig.DEBUG` 时显示“允许预发布服务端”，默认关闭且只影响当前草稿/验证，不降低 Release 校验。

- [x] **Step 5：处理反馈通知设置**

自建 Backend 下禁用或隐藏“查询结果反馈通知”，摘要说明自建服务不支持反馈；切回官方时恢复用户原有偏好，不强制覆盖其持久设置。

- [x] **Step 6：编译并提交**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`，中英文资源齐全，无缺失资源引用。

建议提交信息：`feat: 添加自建查询服务器设置`。

### Task 10：首页安全告警与运行状态

**Files:**
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/HomeViewModel.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/HomeScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

**Interfaces:**
- Produces: `HomeViewModel.selfHostedWarning: StateFlow<SelfHostedWarning?>`
- Consumes: `QueryBackendProvider.state`

- [x] **Step 1：映射持续安全错误**

只把凭据失效、TLS/Pin 失败、低版本、API 不兼容、Instance 改变和 Header 错误映射为 Warning。偶发超时、429 和 5xx 不产生持续卡片。

- [x] **Step 2：实现 WarningCard**

在现有权限、数据库和 source 告警区域加入自建安全卡片。标题和正文只展示安全分类，操作跳转到设置页对应在线查询区域；不得展示 Token、Pin、完整 URL、堆栈或服务端错误正文。

- [x] **Step 3：确保安全阻止后的来电行为**

Home 状态只负责展示，不触发后台健康轮询。被阻止后实际查询由 Backend Provider 直接拒绝，`SpamNumberRepository` Fail Open。

- [x] **Step 4：编译并提交**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`。

建议提交信息：`feat: 显示自建服务安全告警`。

### Task 11：架构文档、静态安全审计与最终验证

**Files:**
- Modify: `.agentdocs/architecture/mvvm-structure.md`
- Modify: `.agentdocs/architecture/sync-strategy.md`
- Modify: `.agentdocs/prd/requirements.md`
- Modify: `.agentdocs/ui/main-screen.md`
- Modify: `.agentdocs/index.md`
- Modify: `docs/plans/2026-08-08-self-hosted-query-client-implementation.md`（勾选实际完成项和记录偏差）

**Interfaces:**
- Consumes: 所有前序任务的最终代码结构
- Produces: 可维护的架构、产品、安全和 UI 文档

- [x] **Step 1：更新长期文档**

记录三 Client 隔离、Backend Snapshot 数据流、官方离线同步固定边界、自建 Fail Open 不回退、Keystore 凭据、版本身份阻止状态、source/反馈隔离和首页 WarningCard。文档示例只使用 `https://mast.example.com` 或 IANA 保留地址。

- [ ] **Step 2：执行私人实例与凭据泄漏扫描**

Run:

```powershell
$privatePattern = $env:PIXEL_TELO_PRIVATE_INSTANCE_PATTERN
if ([string]::IsNullOrWhiteSpace($privatePattern)) { throw "Private instance denylist is missing" }
rg -n -S $privatePattern app .agentdocs docs gradle
rg -n -S "Authorization:\s*Bearer\s+[^<]" app .agentdocs docs gradle
```

Expected: 第一个命令无匹配；第二个命令中的 Bearer 只可能出现在使用 `<token>` 占位符的公开协议文档中。环境变量只在本机设置，不写入脚本、Shell 历史或仓库文件。

- [x] **Step 3：执行敏感日志审计**

Run:

```powershell
rg -n "Log\.(v|d|i|w|e).*?(token|Authorization|phoneNumber|response|baseUrl|spkiPin)" app/src/main/java
```

Expected: 人工逐项确认没有打印 Token、完整号码、完整响应、完整自建 URL 或 Pin；保留的安全日志只含分类、耗时和脱敏 ID。

- [x] **Step 4：执行编译和 Lint**

Run: `./gradlew :app:assembleDebug`

Expected: `BUILD SUCCESSFUL`。

Run: `./gradlew lint`

Expected: `BUILD SUCCESSFUL`，没有本功能新增的 Error；不运行单元测试或 `check`。

- [ ] **Step 5：执行手工连接矩阵**

使用仅本地持有的配置分别验证：系统信任域名、系统信任 IP、自签名域名 + 正确 Pin、自签名 IP + 正确 Pin、错误 Pin、过期证书、SAN 不匹配、错误 Token、低版本、Header 缺失、Instance 改变、429/503/504 和网络超时。联调地址与凭据不写入仓库或共享日志。

- [ ] **Step 6：执行 Google Pixel 真机验证**

验证来电 Fail Open、无官方回退、Directory Provider 展示、进程恢复、Backend 切换、source 隔离、首页告警、官方反馈正常和自建反馈禁用。无法在当前环境执行时，在交付说明中逐项列出未验证项和原因。

- [x] **Step 7：检查依赖与工作区**

确认没有为本功能新增不必要依赖，`libs.versions.toml` 中依赖仍使用项目既有稳定策略；用 `git status --short` 和 `git diff --check` 确认未覆盖用户无关改动、无空白错误。

- [x] **Step 8：提交最终文档和收尾修改**

建议提交信息：`docs: 更新自建查询客户端架构文档`。

### 2026-08-09 收尾验证记录

当前代码、长期文档、静态日志审计、依赖检查、`:app:assembleDebug` 与完整 `lint` 已完成；没有运行
`test`、`check` 或其他单元测试命令。以下项目因当前控制器缺少仅本地持有的输入或设备而保持未完成，
不得据此宣告设备级验收完成：

* **私人实例 denylist 扫描**：`PIXEL_TELO_PRIVATE_INSTANCE_PATTERN` 未设置，控制器必须在持有本地
  denylist 的环境补跑 Step 2；本次没有构造、记录或连接任何私人实例。
* **手工连接矩阵**：当前环境没有受控测试实例、证书与凭据，系统信任域名、系统信任 IP、自签名域名 +
  正确 Pin、自签名 IP + 正确 Pin、错误 Pin、过期证书、SAN 不匹配、错误 Token、低版本、Header 缺失、
  Instance 改变、429、503、504 和网络超时均未执行。
* **Google Pixel 真机矩阵**：当前环境未连接 Google Pixel 设备，来电 Fail Open、无官方回退、
  Directory Provider 展示、进程恢复、Backend 切换、source 隔离、首页 WarningCard、官方反馈正常、
  自建反馈禁用均未执行。

实施与原计划的实际差异如下：

* `OfficialFeedbackApi` 复用隔离的官方查询 Retrofit/OkHttp 网络栈；`officialFeedback` qualifier 标识 API
  契约，不额外创建第三个官方连接池。三个隔离网络边界实际为官方离线同步、官方实时查询/反馈和动态
  自建实时查询。
* 服务端配对文本机器格式仍未稳定，因此第一版只提供 URL、Token 与 Pin 分项输入，没有发明整段解析
  协议，也没有新增相机权限。
* Task 3 审查提出的 Backend/TLS enum 磁盘兼容性已在前序修复中改为稳定字符串 codec；Task 9 审查提出
  的未使用 `LocalContext` 已在前序 UI 修复中移除，本次无需再次修改。
* 最终日志扫描发现旧来电和 Directory Provider 日志会输出完整号码/URI，本轮已移除号码、URI 与服务端
  label，仅保留英文分类、状态和耗时。

## 实施完成定义

只有同时满足以下条件才可宣告完成：

1. 官方离线同步和官方实时查询保持可用。
2. 系统信任 TLS 与自签名 SPKI Pinning 均可完成自建配置验证。
3. 配置切换原子化，进行中的请求保持原 Snapshot。
4. 运行期版本、API 和 Instance ID 异常会阻止自建请求并告警。
5. 自建所有失败 Fail Open 且不访问官方实时查询。
6. source 配置按 Backend ID 隔离并完成旧官方配置迁移。
7. 自建结果在存储、UI 和 Receiver 三层都无法触发反馈。
8. Token 由 Keystore 保护并从所有备份路径排除。
9. 私人联调实例和凭据未进入任何受版本控制内容。
10. `:app:assembleDebug` 与 `lint` 成功；未运行项目禁止的单元测试命令。
11. 真机项目已验证，或交付说明明确列出需要用户执行的设备验证。
