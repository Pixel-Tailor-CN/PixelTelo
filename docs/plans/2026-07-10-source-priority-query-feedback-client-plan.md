# 数据源优先级查询与质量反馈客户端实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为 Pixel Telo 接入 mast v2 查询、可配置 source 优先级、source 下线提示和持久化质量反馈。

**Architecture:** 新增独立 `QueryApi` 与单例 `QueryRepository`，以 `SharedPreferences` 保存少量 source 配置，以 `StateFlow` 驱动首页和设置页。`SpamNumberRepository` 保留本地决策职责，Room `BlockedCall` 只为原本会落库的记录保存反馈凭证与状态。

**Tech Stack:** Kotlin 2.4、Jetpack Compose Material3、Retrofit 3、Kotlinx Serialization、Room 2.8、Koin 4.2、Coroutines/Flow。

## Global Constraints

- MinSDK 29，TargetSDK 37，JVM Target 21。
- 代码注释、KDoc 与项目文档使用中文；日志打印使用英文。
- 不新增权限，不新增依赖，不新增后台定时任务。
- 联网号码查询有效超时限定为 1 至 3 秒，默认 3 秒；超时必须允许来电通过。
- 实时来电查询不得临时请求 source 清单。
- 新 source 默认停用，已下线 source 不静默删除用户配置。
- 不改变正常来电记录策略，不为反馈保存额外通话记录。
- feedback token 不得写入日志或备份。
- 按项目策略不新增、不运行单元测试；中间阶段执行编译检查，最终执行 `:app:assembleDebug` 与 `lint`。
- 不修改或提交用户现有的 `gradle/libs.versions.toml` 工作区变更。

---

### Task 1: 建立 v2 远程契约与 source 配置仓库

**Files:**
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/remote/QueryApi.kt`
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/QueryRepository.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/remote/SyncApi.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt`

**Interfaces:**
- Consumes: `SharedPreferences`、Retrofit、服务端 `/api/v2/*` 接口。
- Produces:
  - `QueryApi.getSources()`、`queryNumber()`、`submitFeedback()`。
  - `QueryRepository.sourceState: StateFlow<QuerySourceState>`。
  - `QueryRepository.refreshSources(): Result<Unit>`。
  - `QueryRepository.saveSourceSelection(items: List<QuerySourceItem>): Boolean`。
  - `QueryRepository.queryNumber(phone: String): QueryResponse`。
  - `QueryRepository.submitFeedback(token: String, positive: Boolean): FeedbackSubmitResult`。

- [x] **Step 1: 定义 v2 DTO 与 Retrofit 接口**

在 `QueryApi.kt` 中移动现有 `PhoneLocationInfo`、`QueryResponse`，并补齐：

```kotlin
@Serializable
data class QueryRequest(val number: String, val sources: List<String>)

@Serializable
data class QueryResponse(
    val phone: String,
    @SerialName("is_spam") val isSpam: Boolean,
    val tag: String = "",
    val confidence: Int,
    val source: String,
    val data: PhoneLocationInfo? = null,
    @SerialName("feedback_token") val feedbackToken: String = "",
    @SerialName("query_mode") val queryMode: String = "v1",
    @SerialName("requested_sources") val requestedSources: List<String> = emptyList(),
    @SerialName("effective_sources") val effectiveSources: List<String> = emptyList(),
    val warnings: List<QueryWarning> = emptyList(),
)

@Serializable
data class QueryWarning(
    val code: String,
    val message: String = "",
    @SerialName("invalid_sources") val invalidSources: List<String> = emptyList(),
)

@Serializable
data class QuerySourcesResponse(
    @SerialName("default_sources") val defaultSources: List<String> = emptyList(),
    @SerialName("available_sources") val availableSources: List<QuerySource> = emptyList(),
)

@Serializable
data class FeedbackRequest(val token: String, val positive: Boolean)

@Serializable
data class FeedbackResponse(val status: String)

interface QueryApi {
    @GET("api/v2/sources")
    suspend fun getSources(): QuerySourcesResponse

    @POST("api/v2/query")
    suspend fun queryNumber(@Body request: QueryRequest): QueryResponse

    @POST("api/v2/query/feedback")
    suspend fun submitFeedback(@Body request: FeedbackRequest): FeedbackResponse
}
```

为保证 Task 1 独立可编译，`SyncApi.kt` 暂时保留现有 v1 `queryNumber()` 方法并返回已移动到 `QueryApi.kt` 的 `QueryResponse`；Task 2 完成调用替换后再删除该兼容方法。

- [x] **Step 2: 定义 source 状态和反馈结果**

在 `QueryRepository.kt` 中定义稳定的数据边界：

```kotlin
data class QuerySourceItem(
    val id: String,
    val enabled: Boolean,
    val available: Boolean,
)

data class QuerySourceState(
    val initialized: Boolean = false,
    val items: List<QuerySourceItem> = emptyList(),
    val defaultSources: List<String> = emptyList(),
    val refreshing: Boolean = false,
    val refreshFailed: Boolean = false,
) {
    val unavailableEnabledSources: List<String>
        get() = items.filter { it.enabled && !it.available }.map { it.id }
}

sealed interface FeedbackSubmitResult {
    data object Accepted : FeedbackSubmitResult
    data object AlreadySubmitted : FeedbackSubmitResult
    data object Expired : FeedbackSubmitResult
    data object Invalid : FeedbackSubmitResult
    data class RetryableFailure(val message: String?) : FeedbackSubmitResult
}
```

- [x] **Step 3: 实现 source 首次初始化与增量合并**

实现 `refreshSources()`：先按 priority 对 `available_sources` 排序并去重；首次排序严格使用服务端 `default_sources` 的原始顺序，再追加不在默认列表中的可用 source，且只启用 `default_sources`；后续保留已有顺序和启用状态；新 source 追加且关闭；旧 source 保留但 `available=false`。只有请求成功时写入 `SharedPreferences`。

持久化结构使用 `@Serializable StoredSourceConfig`，至少保存 `initialized`、`orderedIds`、`enabledIds`、`defaultSources`、`availableIds`。所有读写通过一个 `Mutex` 串行化。

- [x] **Step 4: 实现保存配置、v2 查询与反馈映射**

`saveSourceSelection()` 必须验证至少一个 `available && enabled` 项；`queryNumber()` 仅发送本地启用且可用的有序 ID，未初始化或全部失效时发送空列表。收到任意 warning 的 `invalid_sources` 后把对应 source 标记为不可用并持久化。

`submitFeedback()` 捕获 `HttpException` 并映射：`409 -> AlreadySubmitted`、`410 -> Expired`、`400 -> Invalid`，其他 HTTP/IO 错误返回 `RetryableFailure`。

- [x] **Step 5: 调整 Koin 网络对象**

`AppModule.kt` 注册一个共享 `Json`、一个共享 `Retrofit`、独立 `SyncApi`/`QueryApi` 以及 `QueryRepository`：

```kotlin
single { Json { ignoreUnknownKeys = true } }
single {
    Retrofit.Builder()
        .baseUrl("https://pixeltelo.api.mystery0.vip/")
        .client(get())
        .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
        .build()
}
single { get<Retrofit>().create(SyncApi::class.java) }
single { get<Retrofit>().create(QueryApi::class.java) }
single { QueryRepository(get(), get()) }
```

- [x] **Step 6: 执行编译检查并提交**

Run: `./gradlew :app:compileDebugKotlin`  
Expected: `BUILD SUCCESSFUL`

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/data/remote app/src/main/java/vip/mystery0/pixel/telo/data/repository/QueryRepository.kt app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt
git commit -m "feat: add configurable v2 query repository"
```

### Task 2: 将实时查询接入 v2 并落实 3 秒上限

**Files:**
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/SpamNumberRepository.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/remote/SyncApi.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/SettingsScreen.kt`

**Interfaces:**
- Consumes: `QueryRepository.queryNumber(phone)`、`QueryResponse`。
- Produces: 带 `querySource` 与 `feedbackToken` 的 `CheckResult`；1 至 3 秒超时设置。

- [x] **Step 1: 扩展 `CheckResult` 的网络元数据**

```kotlin
data class CheckResult(
    // 保留现有字段
    val querySource: String? = null,
    val feedbackToken: String? = null,
)
```

所有由 v2 网络响应构建的结果，包括位置/标签黑白名单分支，都必须带上 `response.source` 与 `response.feedbackToken`；纯本地结果保持 null。

- [x] **Step 2: 替换 v1 查询调用**

移除 `SyncApi` 注入，改为注入 `QueryRepository`。`queryNetwork()` 和 `checkSpam()` 的联网阶段统一调用 `queryRepository.queryNumber(phone)`，随后从 `SyncApi` 删除临时保留的 v1 `queryNumber()`。日志只记录 source 与耗时，不输出完整响应、token 或完整号码。

- [x] **Step 3: 收紧超时设置**

将 `DEFAULT_NETWORK_TIMEOUT_SECONDS` 改为 3，设置页 Slider 改为 `1f..3f`、`steps = 1`。读取旧偏好时使用 `coerceIn(1, 3)`，更新时同样夹紧，避免旧安装保留 5 至 30 秒配置。

- [x] **Step 4: 编译并提交**

Run: `./gradlew :app:compileDebugKotlin`  
Expected: `BUILD SUCCESSFUL`

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/data/repository/SpamNumberRepository.kt app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt app/src/main/java/vip/mystery0/pixel/telo/ui/screen/SettingsScreen.kt
git commit -m "feat: route online checks through v2 query"
```

### Task 3: 为通话记录增加反馈持久化

**Files:**
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/entity/BlockedCall.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/AppDatabase.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/BlockedCallRepository.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/service/TeloCallScreeningService.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/HomeViewModel.kt`

**Interfaces:**
- Consumes: `CheckResult.querySource`、`CheckResult.feedbackToken`。
- Produces: Room v6、`FeedbackStatus`、可反馈的 `BlockedCall`。

- [x] **Step 1: 扩展实体与 migration**

```kotlin
enum class FeedbackStatus {
    UNAVAILABLE,
    PENDING,
    POSITIVE,
    NEGATIVE,
    ALREADY_SUBMITTED,
    EXPIRED,
    INVALID,
}

data class BlockedCall(
    // 保留现有字段
    val querySource: String? = null,
    val feedbackToken: String? = null,
    val feedbackStatus: FeedbackStatus = FeedbackStatus.UNAVAILABLE,
)

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `blocked_calls` ADD COLUMN `querySource` TEXT")
        db.execSQL("ALTER TABLE `blocked_calls` ADD COLUMN `feedbackToken` TEXT")
        db.execSQL("ALTER TABLE `blocked_calls` ADD COLUMN `feedbackStatus` TEXT NOT NULL DEFAULT 'UNAVAILABLE'")
    }
}
```

数据库版本改为 6，并在 Koin 的 `Room.databaseBuilder` 注册 `MIGRATION_5_6`。

- [x] **Step 2: 扩展记录写入接口**

`BlockedCallRepository.insert()` 增加可空 `querySource`、`feedbackToken`，并根据 token 是否为空自动写入 `PENDING` 或 `UNAVAILABLE`。新增：

```kotlin
suspend fun attachQueryResult(call: BlockedCall, response: QueryResponse): BlockedCall
suspend fun updateFeedbackStatus(call: BlockedCall, status: FeedbackStatus): BlockedCall
```

两个方法都返回更新后的实体，避免 ViewModel 继续持有旧对象并覆盖新字段。

- [x] **Step 3: 在现有落库点传递反馈信息**

`TeloCallScreeningService` 的所有现有 `insert()` 分支传入 `result.querySource` 与 `result.feedbackToken`，但不新增任何落库分支。`SettingViewModel.saveTestResult()` 同样传递这两个字段。

- [x] **Step 4: 手动重查立即附加 token**

`HomeViewModel.retryNetworkQuery()` 查询成功后先调用 `attachQueryResult()`，再把返回的新实体放入 `RetryQueryState.Success`。`writeQueryResultToRemark()` 必须基于该新实体 copy，防止覆盖 token。

- [x] **Step 5: 编译并提交**

Run: `./gradlew :app:compileDebugKotlin`  
Expected: `BUILD SUCCESSFUL`

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/data app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt app/src/main/java/vip/mystery0/pixel/telo/service/TeloCallScreeningService.kt app/src/main/java/vip/mystery0/pixel/telo/viewmodel
git commit -m "feat: persist query feedback metadata"
```

### Task 4: 实现 source 设置与下线 WarningCard

**Files:**
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/HomeViewModel.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/SettingsScreen.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/HomeScreen.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

**Interfaces:**
- Consumes: `QueryRepository.sourceState` 与 source 配置操作。
- Produces: source 设置 BottomSheet、启动刷新、首页下线提示和直达设置动作。

- [x] **Step 1: 在 ViewModel 中建立 source UI 状态**

`HomeViewModel` 暴露 `sourceState` 并在 `init` 后台调用一次 `refreshSources()`。`SettingViewModel` 保存 BottomSheet 草稿，提供：

```kotlin
fun openQuerySourceSettings()
fun closeQuerySourceSettings()
fun toggleQuerySource(id: String, enabled: Boolean)
fun moveQuerySource(id: String, offset: Int)
fun restoreDefaultQuerySources()
fun saveQuerySources(): Boolean
fun retryQuerySourceRefresh()
```

- [x] **Step 2: 实现设置 BottomSheet**

在“拦截行为”分类增加“联网查询数据源” Preference。BottomSheet 按草稿顺序显示 source ID、可用状态、Switch 和上下移动按钮；无缓存且刷新失败时只显示错误与重试；保存按钮仅在至少一个可用 source 启用时可用。

- [x] **Step 3: 实现首页 WarningCard**

当 `sourceState.unavailableEnabledSources` 非空时显示卡片，文案列出 source ID。卡片不提供忽略操作，按钮调用新的 `onNavigateToSourceSettings`。

- [x] **Step 4: 接通跨页导航**

`MainActivity` 收到回调后先执行 `settingViewModel.openQuerySourceSettings()`，再切换至设置页，保证用户直接看到 source 配置。

- [x] **Step 5: 完成中英文资源并提交**

所有新增可见文本写入 `values/strings.xml` 与 `values-zh/strings.xml`，不新增硬编码 UI 文案。

Run: `./gradlew :app:compileDebugKotlin`  
Expected: `BUILD SUCCESSFUL`

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/MainActivity.kt app/src/main/java/vip/mystery0/pixel/telo/ui app/src/main/java/vip/mystery0/pixel/telo/viewmodel app/src/main/res/values app/src/main/res/values-zh
git commit -m "feat: add query source settings and warnings"
```

### Task 5: 实现记录详情反馈交互

**Files:**
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/HomeViewModel.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/HomeScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

**Interfaces:**
- Consumes: `QueryRepository.submitFeedback()`、`BlockedCallRepository.updateFeedbackStatus()`。
- Produces: 一次性反馈操作、持久化终态和可重试失败态。

- [x] **Step 1: 增加反馈提交 UI 状态**

```kotlin
sealed interface FeedbackSubmissionState {
    data object Idle : FeedbackSubmissionState
    data class Submitting(val callId: Long) : FeedbackSubmissionState
    data class Failure(val callId: Long, val message: String) : FeedbackSubmissionState
}
```

`HomeViewModel.submitFeedback(call, positive)` 映射 `FeedbackSubmitResult`，更新 Room 后同步替换 `quickAddCall`，网络/服务端可重试错误只更新 UI 状态，不改变 `PENDING`。

- [x] **Step 2: 在记录详情展示 source 与反馈操作**

有 `querySource` 时显示命中来源。`PENDING` 时显示“结果准确/结果不准确”按钮；提交中禁用；失败时展示错误并允许重试。终态显示对应状态文字，不再展示提交按钮。

- [x] **Step 3: 确认备份不导出新字段**

保持 `BlockedCallDto` 及 `BlockedCall.toDto()` 不包含 `querySource`、`feedbackToken`、`feedbackStatus`；恢复实体使用字段默认值 `UNAVAILABLE`。

- [x] **Step 4: 编译并提交**

Run: `./gradlew :app:compileDebugKotlin`  
Expected: `BUILD SUCCESSFUL`

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/viewmodel/HomeViewModel.kt app/src/main/java/vip/mystery0/pixel/telo/ui/screen/HomeScreen.kt app/src/main/res/values app/src/main/res/values-zh
git commit -m "feat: add persisted query quality feedback"
```

### Task 6: 更新架构文档并完成验证

**Files:**
- Modify: `.agentdocs/architecture/mvvm-structure.md`
- Modify: `.agentdocs/architecture/native-integration.md`
- Modify: `.agentdocs/ui/main-screen.md`
- Modify: `docs/plans/2026-07-10-source-priority-query-feedback-client-plan.md`

**Interfaces:**
- Consumes: 已完成的客户端实现。
- Produces: 与实现一致的架构说明、验证结果和干净的功能 diff。

- [x] **Step 1: 更新架构与 UI 文档**

记录 `QueryApi -> QueryRepository -> SpamNumberRepository/ViewModel` 数据流、实时查询不得刷新 source、3 秒超时、Room 反馈字段、首页 source 下线 WarningCard 和反馈入口。

- [x] **Step 2: 执行格式与 diff 检查**

Run: `git diff --check`  
Expected: 无输出，退出码 0。

- [x] **Step 3: 构建 Debug APK**

Run: `./gradlew :app:assembleDebug`  
Expected: `BUILD SUCCESSFUL`

- [x] **Step 4: 执行 Android Lint**

Run: `./gradlew lint`  
Expected: `BUILD SUCCESSFUL`，无本次变更新增 error。

- [x] **Step 5: 检查依赖与工作区范围**

确认未新增依赖；确认 `gradle/libs.versions.toml` 仍是用户原有未提交修改且没有进入任何本功能提交；确认未创建 `src/test` 文件或运行单元测试命令。

- [x] **Step 6: 提交文档与验证收尾**

```bash
git add .agentdocs docs/plans/2026-07-10-source-priority-query-feedback-client-plan.md
git commit -m "docs: document source query feedback flow"
```

真机验证必须在 Google Pixel 上另行完成：source 启停/排序、部分与全部下线、来电 v2 查询、Room v5→v6 升级、反馈状态码和 Directory Provider/CallScreeningService 行为。
