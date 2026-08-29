# 持久化本地号码标签 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为归一化号码增加独立、可管理、可选择备份的持久化本地标签，并在用户开启显示后接入 Directory Provider、来电 Overlay 和历史记录展示，同时完全保持现有识别与拦截语义。

**Architecture:** 使用 Room v10 的 `local_number_labels` 独立表和 `LocalNumberLabelRepository` 作为单一事实来源；`SpamNumberRepository` 与 `CheckResult` 不感知本地标签，最终消费端分别取得本地标签和数据源标签。SharedPreferences 开关通过进程级 `LocalNumberLabelPreferences` 暴露 `StateFlow`，历史记录只观察 Paging 当前窗口，备份恢复把标签作为第四个独立范围。

**Tech Stack:** Kotlin、Room、Kotlin Coroutines/Flow、Jetpack Compose Material3、Paging 3、Koin、Android Directory Provider、Kotlinx Serialization。

**Spec:** `docs/plans/2026-08-29-local-number-label-design.md`

## Global Constraints

- 与用户的回复、代码注释、KDoc 和项目文档使用中文；日志使用英文。
- MinSDK 29，TargetSDK 35，JVM Target 21。
- 不新增权限、第三方依赖、网络 API、上传状态或 WorkManager。
- 每个归一化号码最多一个标签；标签 `trim()` 后最多 40 个字符，空标签按删除处理。
- 唯一号码必须由 `PhoneNumberNormalizer.normalizeForLookup()` 产生；空结果或不含数字的结果视为无效。
- “显示本地号码标签”默认关闭；关闭只隐藏普通展示，不影响编辑、管理、存储和备份恢复。
- 本地标签不得写入或覆盖 `CheckResult.label`、`BlockedCall.label`、黑白名单、反馈 Token、`ResultType` 或查询耗时。
- Directory Provider 和 CallScreeningService 必须完整执行现有离线/联网查询，本地标签不得短路查询。
- `CallScreeningService` 中本地标签失败必须 Fail Open，只影响展示。
- 本地标签、电话号码不得写入日志。
- 项目默认不新增、不运行单元测试；不得执行 `test`、`testDebugUnitTest`、`check`。
- 每个实现任务至少执行 `./gradlew :app:assembleDebug`；最终执行 `./gradlew lint` 和 Android 模拟器验收。
- 不提交构建产物、模拟器数据或临时备份文件。

## 执行结果

截至 Task 8。实现分支起点 `bc45e1c`，Task 7 文档提交后 HEAD 为 `224101d`。Task 1–7 为静态构建结果；Task 8 为模拟器实测。外部 SDD 工件绝对路径：`D:/StudioProjects/PixelTelo/.superpowers/sdd/2026-08-29-local-number-label-implementation/task-8-report.md`（gitignored，不是仓库相对可访问文件）。耐久摘要已内嵌本节，审查以本节为准。

### Task 1–6 `assembleDebug`

各任务均执行 `./gradlew :app:assembleDebug`，结果均为 `BUILD SUCCESSFUL`：

- Task 1：基线 `BUILD SUCCESSFUL in 2m 30s`；提交前 `BUILD SUCCESSFUL in 13s`。
- Task 2：`BUILD SUCCESSFUL in 13s`。
- Task 3：`BUILD SUCCESSFUL in 8s`；Fix Round 1 后再编 `BUILD SUCCESSFUL in 4s`。
- Task 4：`BUILD SUCCESSFUL in 7s`；三次修复后再编分别为 `5s` / `6s` / `6s`。
- Task 5：结构修正后 `BUILD SUCCESSFUL in 10s`，最终一次 `BUILD SUCCESSFUL in 2s`。
- Task 6：`BUILD SUCCESSFUL in 5s`；Fix Round 1 后再编 `BUILD SUCCESSFUL in 6s`。

未执行 `./gradlew test`、`testDebugUnitTest` 或 `check`。

### Task 7 最终静态验证

- `./gradlew :app:assembleDebug`：`BUILD SUCCESSFUL in 3s`（39 actionable tasks: 13 executed, 26 up-to-date）。
- `./gradlew lint`：`BUILD SUCCESSFUL in 1m 6s`；报告为 `0 errors, 44 warnings`。无本功能引入的 Error。
- `git diff --check`：无输出。
- `git diff --name-only 5f66517..HEAD`：无 `src/test` 新文件。
- `git diff 5f66517..HEAD -- gradle/libs.versions.toml app/build.gradle.kts AndroidManifest.xml`：无变更。

Lint 44 条均为 Warning。与本功能相关的新增/更新文案 `label_restore_local_number_labels`、`msg_restored_summary` 触发 `PluralsCandidate`，与既有备份恢复字符串同一模式，未当作 Error 修改。其余 Warning 属于自建 Backend、历史文案、KTX 与资源收缩等既有问题。

### Task 8 模拟器验收

唯一设备 `emulator-5554`（`sdk_gphone16k_x86_64` / Pixel_10_Pro AVD，API 37，Android 17）。覆盖安装 `1.4.1.d251.224101d9`，未清数据。未改业务源码。

外部长报告绝对路径：`D:/StudioProjects/PixelTelo/.superpowers/sdd/2026-08-29-local-number-label-implementation/task-8-report.md`。该文件属于 gitignored `/.superpowers/` SDD 工件，**不是仓库相对可访问文件**。耐久摘要已内嵌本节；审查以本小节为准。

**已验证**

- v9 → v10：安装前 `user_version=9` 且无 `local_number_labels`；安装后 `user_version=10`，新表 schema 正确，无迁移崩溃。原 `blocked_calls`/`user_list` 均为 0，空表与既有 prefs 保留。
- 显示开关默认关闭；关闭态详情仍可管理本地标签，列表普通身份区不展示。
- 变体关联：`13800138000` / `+8613800138000` / `12583113800138000` 开启显示后均显示同一本地标签。补证（不重启）：详情 UI 将 `OfficeA` 改为 `OfficeB` 并 Save，关闭 BottomSheet 后同一进程 `pid=21319` 下列表三行均为 `Local label: OfficeB`，`OfficeA` 为 0；数据源「广告营销」仍在。删除后本地标签消失、数据源保留（此前中文「物业前台」路径）。
- 管理页：号码搜索、英文忽略大小写、编辑后 `updatedAt DESC` 置顶、删除需确认、无标签/搜索无结果空状态不同、无新增按钮、Back 后停在 Settings Tab。
- Directory 四组合经 Contacts `directory=8` 实测：`物业前台 · 广告营销`、仅本地 `物业`、仅数据源 `广告营销`、都无则空 Cursor。本地标签未短路 `checkSpam`。`gsm call 19900001111` 的 CallScreeningService `shouldBlock=false`。
- Overlay **部分验证**：真实来电只验证了本地标签显隐（开关开：`19900001111` Overlay 显示「物业」+ `Location query timeout`；开关关：同一号码 Overlay 无本地标签）。
- 备份 v5：仅勾选本地标签时导出按钮可用；无标签备份预览数量为 0 且 Restore 不可用；v5 恢复「新增 1 / 覆盖 1 / 跳过 0」，未包含的当前标签保留；v4 旧备份恢复 1 条记录且本地标签 0 新增/覆盖/跳过；显示开关不随备份恢复改变。
- 备份 v1–v3 最小安全 fixture（无真实联系人/号码隐私数据）经恢复 UI：预览均为 `Local Number Labels (0 entries)`；结果均为 `0 local labels added, 0 overwritten, 0 skipped`；恢复后管理页仍为 `OfficeB`/`KeepMe`。v2 标签黑名单 `V2TagBlack` UI 显示 `Force block`，SQLite `forceBlock=1`（缺省字段回填）；v3 `V3TagBlack` 显式 `force_block:false`，UI 无 Force block 芯片，SQLite `forceBlock=0`。
- 清空拦截记录后标签仍在；重新 Record 同号码后本地标签动态出现。

**未验证 / 环境限制**

- 安装前拦截记录和黑白名单为空，无法证明非空行迁移保留。
- 模拟器 LatinIME 不能 adb 输入中文；中文标签首次写入/改写部分走 Room。不重启三行刷新的补证使用英文 `OfficeA`→`OfficeB`。
- 不能直接 query Telo authority（`BIND_DIRECTORY_SEARCH`）；Directory 结论来自 Contacts `directory=8`。
- 该 AVD 来电 heads-up 在 Directory 有结果时显示 Directory 名而不是联系人；开关关闭后 Directory 对纯本地标签返回空，heads-up 才回落到联系人。
- Overlay 真实路径**未完成双标签**，也**未完成「关闭后仅保留数据源」**（超时会剥离数据源；本地库命中骚扰号不打开 Overlay）。设置页预览 Overlay 只证明组件能分层渲染，不得替代真实来电验收。
- 未在系统拨号器搜索框做缓存刷新手工验证。

**规定最终证据（补证结束时真实输出）**

- `adb -s emulator-5554 logcat -d | grep -E "FATAL EXCEPTION|ANR|Local label lookup too slow|Local label lookup failed"`：无匹配。
- `git status --short`（确认无 `.tmp-task8b/` 后实测，原始输出为空）：

```text
$ git status --short

```

- `git diff --check`：无输出。
- `git log --oneline -10` 起点：`727e486 docs: 记录本地号码标签模拟器验收结果` 及其前 9 条 feat/fix/docs。

补证后再次清理：测试标签/拦截记录/v2-v3 名单行删除；Download 下 `v1-min.zip`/`v2-min.zip`/`v3-min.zip` 删除；无 Task8 联系人；`show_local_number_labels=false`。

### 已知限制

- Task 2 deferred 的 `Icons.Default.Label` deprecation 已在最终审查修复中改为 `Icons.AutoMirrored.Filled.Label`。
- Task 4 deferred：`HomeScreen.kt` 现为 1051 行，超过项目“原则上不超过 1000 行”的指南；本轮按审查要求未拆详情 BottomSheet。
- Task 8 Overlay 双标签与「关闭后仅数据源」仍待真机或可出非超时数据源的来电环境；其余环境限制见上一小节。
- 备份失败改为稳定分类日志后，无法从 logcat 看到异常类型或 JSON 摘要，排障需本地复现。
- 本地标签 100ms 超时后会 cancel deferred；若 Room 查询在取消点不可中断，`coroutineScope` 仍可能短暂等待该查询结束，但超时结果已按 null Fail Open，且不会进入外层拦截错误路径。

### 最终审查修复

修复起点 HEAD `3cfba59`。仅处理审查 P1-1、P1-2 与 P2 AutoMirrored 图标；未拆 `HomeScreen`，未处理 `PluralsCandidate`。外部报告：`D:/StudioProjects/PixelTelo/.superpowers/sdd/2026-08-29-local-number-label-implementation/final-fix-report.md`（gitignored SDD 工件）。

代码提交：

- `ef7038c` `fix: 本地标签查询增加有界超时与 Overlay 权限检查`
- `3254e31` `fix: 备份失败不再记录或展示异常正文`
- `4d47d6e` `fix: 本地标签设置图标改用 AutoMirrored`

**代码修复**

- P1-1：`TeloDirectoryProvider` 与 `TeloCallScreeningService` 对本地标签 deferred 使用 `LOCAL_LABEL_LOOKUP_TIMEOUT_MS = 100` 有界 `await`；查询本身也套 `withTimeoutOrNull(100ms)`。超时返回 null 并 cancel 未完成查询，不进入外层拦截错误路径，完整 `checkSpam` 不被本地标签超时打断。Service 启动查询前增加 `Settings.canDrawOverlays(applicationContext)`。`LocalNumberLabelRepository.find()` 已显式 rethrow `CancellationException`；`observeLabels()` 的 `catch` 同样显式 rethrow，普通异常仍只记录稳定英文日志，不含号码或标签。
- P1-2：`SettingViewModel.parseBackupFile` / `performBackupWithOptions` / `performRestoreWithOptions` 的 `Log.e` 不再附 throwable。用户可见失败改用无格式参数的 `msg_backup_failed` / `msg_restore_failed`，中英文资源同步，避免 JSON 摘要泄露号码或标签。
- P2：`AppFeaturesPreferences` 将 `Icons.Default.Label` 改为 `Icons.AutoMirrored.Filled.Label`。

**验证命令（真实结果）**

- `./gradlew :app:assembleDebug`：`BUILD SUCCESSFUL in 8s`（39 actionable tasks: 19 executed, 20 up-to-date）。
- `./gradlew lint`：`BUILD SUCCESSFUL in 39s`；报告标题 `Lint Report: 44 warnings`，0 errors。无本轮引入的 Error。`PluralsCandidate` 仍为既有 Warning，按审查要求未改。
- `git diff --check`：无 whitespace 错误输出（exit 0）。Git 对 `LocalNumberLabelRepository.kt` 提示 LF→CRLF，属于工作副本换行提示，不是 `--check` 失败。
- 未执行 `./gradlew test`、`testDebugUnitTest` 或 `check`。未读取或提交 `local.properties`。

---

## 文件结构与职责

### 新增文件

- `app/src/main/java/vip/mystery0/pixel/telo/data/entity/LocalNumberLabel.kt`：Room 实体。
- `app/src/main/java/vip/mystery0/pixel/telo/data/dao/LocalNumberLabelDao.kt`：单号码、窗口、管理和批量写入查询。
- `app/src/main/java/vip/mystery0/pixel/telo/data/repository/LocalNumberLabelRepository.kt`：号码归一化、标签校验、写入、观察和恢复规则。
- `app/src/main/java/vip/mystery0/pixel/telo/data/preferences/LocalNumberLabelPreferences.kt`：显示开关及进程内 Flow。
- `app/src/main/java/vip/mystery0/pixel/telo/data/model/NumberLabelPresentation.kt`：Directory Provider 组合展示纯模型。
- `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/LocalNumberLabelEditorViewModel.kt`：详情与管理页共用的单号码编辑状态。
- `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/LocalNumberLabelsViewModel.kt`：统一管理页列表和搜索状态。
- `app/src/main/java/vip/mystery0/pixel/telo/ui/components/LocalNumberLabelEditorDialogs.kt`：编辑、删除确认和错误展示。
- `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/LocalNumberLabelsScreen.kt`：统一管理页面。

### 主要修改文件

- `data/AppDatabase.kt`、`di/AppModule.kt`：Room v10、迁移、DAO/Repository/Preferences 注入。
- `provider/TeloDirectoryProvider.kt`：并行读取本地标签并组合 Cursor 名称。
- `service/TeloCallScreeningService.kt`、`service/IncomingCallOverlay.kt`、`service/IncomingCallOverlayFormatter.kt`：Overlay 分层展示。
- `viewmodel/HomeViewModel.kt`、`ui/screen/HomeScreen.kt`：Paging 窗口标签观察和详情管理入口。
- `viewmodel/SettingViewModel.kt`、`ui/screen/settings/AppFeaturesPreferences.kt`：显示开关和管理入口。
- `MainActivity.kt`：二级管理页状态和返回导航。
- `data/dto/BackupData.kt`、`data/repository/BackupRepository.kt`、`ui/screen/SettingsScreen.kt`：备份格式 v5 和第四个选择范围。
- 中英文 `strings.xml` 与 `.agentdocs/` 架构/UI 文档。

---

### Task 1：建立 Room v10 本地标签数据层

**Files:**

- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/entity/LocalNumberLabel.kt`
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/dao/LocalNumberLabelDao.kt`
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/LocalNumberLabelRepository.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/AppDatabase.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt`

**Interfaces:**

- Produces: `LocalNumberLabel(normalizedPhoneNumber, label, createdAt, updatedAt)`
- Produces: `LocalNumberLabelRepository.find(String): LocalNumberLabel?`
- Produces: `LocalNumberLabelRepository.observe(String): Flow<LocalNumberLabel?>`
- Produces: `LocalNumberLabelRepository.observeLabels(Set<String>): Flow<Map<String, String>>`
- Produces: `LocalNumberLabelRepository.observeAll(): Flow<List<LocalNumberLabel>>`
- Produces: `LocalNumberLabelRepository.getAllSnapshot(): List<LocalNumberLabel>`
- Produces: `LocalNumberLabelRepository.set(String, String): LocalLabelWriteResult`
- Produces: `LocalNumberLabelRepository.delete(String): LocalLabelWriteResult`
- Produces: `LocalNumberLabelRepository.restore(List<LocalNumberLabelRestoreEntry>): RestoreLocalLabelsResult`
- Produces: `MIGRATION_9_10`

- [ ] **Step 1：记录基线构建状态**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`。若失败，停止实现并先记录与本功能无关的基线失败。

- [ ] **Step 2：创建 Room 实体**

创建 `LocalNumberLabel.kt`：

```kotlin
package vip.mystery0.pixel.telo.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** 用户为具体归一化号码设置的持久化本地标签。 */
@Entity(tableName = "local_number_labels")
data class LocalNumberLabel(
    @PrimaryKey val normalizedPhoneNumber: String,
    val label: String,
    val createdAt: Long,
    val updatedAt: Long,
)
```

- [ ] **Step 3：创建 DAO**

创建 `LocalNumberLabelDao.kt`，使用 `List<String>` 作为 Room `IN` 参数，避免空 `Set` 的生成差异：

```kotlin
package vip.mystery0.pixel.telo.data.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import vip.mystery0.pixel.telo.data.entity.LocalNumberLabel

@Dao
interface LocalNumberLabelDao {
    @Query("SELECT * FROM local_number_labels WHERE normalizedPhoneNumber = :number LIMIT 1")
    suspend fun findByNumber(number: String): LocalNumberLabel?

    @Query("SELECT * FROM local_number_labels WHERE normalizedPhoneNumber = :number LIMIT 1")
    fun observeByNumber(number: String): Flow<LocalNumberLabel?>

    @Query("SELECT * FROM local_number_labels WHERE normalizedPhoneNumber IN (:numbers)")
    fun observeByNumbers(numbers: List<String>): Flow<List<LocalNumberLabel>>

    @Query("SELECT * FROM local_number_labels ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<LocalNumberLabel>>

    @Query("SELECT * FROM local_number_labels ORDER BY updatedAt DESC")
    suspend fun getAllSnapshot(): List<LocalNumberLabel>

    @Query("SELECT * FROM local_number_labels WHERE normalizedPhoneNumber IN (:numbers)")
    suspend fun findByNumbers(numbers: List<String>): List<LocalNumberLabel>

    @Upsert
    suspend fun upsert(entry: LocalNumberLabel)

    @Upsert
    suspend fun upsertAll(entries: List<LocalNumberLabel>)

    @Query("DELETE FROM local_number_labels WHERE normalizedPhoneNumber = :number")
    suspend fun deleteByNumber(number: String): Int
}
```

- [ ] **Step 4：增加 v9 → v10 migration**

在 `AppDatabase.kt` 新增：

```kotlin
/** 从 v9 升级到 v10：新增独立的持久化本地号码标签表。 */
val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `local_number_labels` (
                `normalizedPhoneNumber` TEXT NOT NULL,
                `label` TEXT NOT NULL,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`normalizedPhoneNumber`)
            )
            """.trimIndent()
        )
    }
}
```

把 `entities` 增加 `LocalNumberLabel::class`，数据库版本改为 10，并增加：

```kotlin
abstract fun localNumberLabelDao(): LocalNumberLabelDao
```

- [ ] **Step 5：实现 Repository 的稳定结果类型和规则**

创建 `LocalNumberLabelRepository.kt`。文件内定义：

```kotlin
sealed interface LocalLabelWriteResult {
    data object Created : LocalLabelWriteResult
    data object Updated : LocalLabelWriteResult
    data object Deleted : LocalLabelWriteResult
    data object Unchanged : LocalLabelWriteResult
    data object InvalidNumber : LocalLabelWriteResult
    data object LabelTooLong : LocalLabelWriteResult
    data class Failure(val cause: Throwable) : LocalLabelWriteResult
}

data class LocalNumberLabelRestoreEntry(
    val phoneNumber: String,
    val label: String,
    val createdAt: Long,
    val updatedAt: Long,
)

data class RestoreLocalLabelsResult(
    val inserted: Int,
    val overwritten: Int,
    val skipped: Int,
)
```

Repository 构造函数固定为：

```kotlin
class LocalNumberLabelRepository(
    private val dao: LocalNumberLabelDao,
    private val database: AppDatabase,
)
```

号码校验集中为：

```kotlin
private fun normalize(phoneNumber: String): String? =
    PhoneNumberNormalizer.normalizeForLookup(phoneNumber)
        .trim()
        .takeIf { normalized -> normalized.isNotEmpty() && normalized.any(Char::isDigit) }
```

`set()` 必须先读现有实体：空标签调用删除；超过 40 返回 `LabelTooLong`；新建使用相同当前时间；修改保留 `createdAt`；内容相同返回 `Unchanged`。`find()` 使用 `SystemClock.elapsedRealtime()` 统计耗时，超过 100ms 只记录：

```kotlin
Log.w(TAG, "Local label lookup too slow: cost=${costMs}ms")
```

日志不得包含号码或标签。读取异常记录 `Local label lookup failed` 并返回 null。

`observe(phoneNumber)` 对无效号码返回 `flowOf(null)`，有效号码转发 `dao.observeByNumber()`；
`observeAll()` 转发 DAO 的更新时间倒序 Flow；`getAllSnapshot()` 转发 DAO 快照并仅供备份导出使用。

`observeLabels()` 实现原始号码到归一化号码的映射；空输入返回 `flowOf(emptyMap())`；查询结果按归一化号码关联后再映射回原始号码。Flow 异常使用：

```kotlin
.catch {
    Log.w(TAG, "Local label observation failed")
    emit(emptyMap())
}
```

`restore()` 先在内存中按归一化号码去重，最后一条有效记录生效；无效号码、空标签、超过 40 字符计入 `skipped`。时间戳小于等于 0 时使用同一个 `restoreTime`。在 `database.withTransaction` 内读取现有号码集合、统计 inserted/overwritten，并 `upsertAll()`。

- [ ] **Step 6：注册数据库与 Koin 依赖**

在 `AppModule.kt`：

```kotlin
import vip.mystery0.pixel.telo.data.MIGRATION_9_10
```

把迁移追加到 `.addMigrations(...)`，并注册：

```kotlin
single { get<AppDatabase>().localNumberLabelDao() }
single { LocalNumberLabelRepository(get(), get()) }
```

- [ ] **Step 7：编译验证 Room schema**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`，KSP 接受 v10 Entity、DAO、`@Upsert` 和 migration 注册。

- [ ] **Step 8：提交数据层**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/data/entity/LocalNumberLabel.kt app/src/main/java/vip/mystery0/pixel/telo/data/dao/LocalNumberLabelDao.kt app/src/main/java/vip/mystery0/pixel/telo/data/repository/LocalNumberLabelRepository.kt app/src/main/java/vip/mystery0/pixel/telo/data/AppDatabase.kt app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt
git commit -m "feat: 增加持久化本地号码标签数据层"
```

### Task 2：实现显示开关与标签展示模型

**Files:**

- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/preferences/LocalNumberLabelPreferences.kt`
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/model/NumberLabelPresentation.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/settings/AppFeaturesPreferences.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

**Interfaces:**

- Produces: `LocalNumberLabelPreferences.enabled: StateFlow<Boolean>`
- Produces: `LocalNumberLabelPreferences.setEnabled(Boolean)`
- Produces: `NumberLabelPresentation.directoryDisplayName(): String?`
- Produces: `SettingViewModel.showLocalNumberLabels`
- Produces: `SettingViewModel.updateShowLocalNumberLabels(Boolean)`

- [ ] **Step 1：创建进程级显示设置**

创建 `LocalNumberLabelPreferences.kt`：

```kotlin
package vip.mystery0.pixel.telo.data.preferences

import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LocalNumberLabelPreferences(private val preferences: SharedPreferences) {
    companion object {
        const val KEY_SHOW_LOCAL_NUMBER_LABELS = "show_local_number_labels"
    }

    private val _enabled = MutableStateFlow(
        preferences.getBoolean(KEY_SHOW_LOCAL_NUMBER_LABELS, false)
    )
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        preferences.edit { putBoolean(KEY_SHOW_LOCAL_NUMBER_LABELS, enabled) }
        _enabled.value = enabled
    }
}
```

不把键重复声明到 `SettingViewModel`。

- [ ] **Step 2：创建纯展示组合模型**

创建 `NumberLabelPresentation.kt`：

```kotlin
package vip.mystery0.pixel.telo.data.model

data class NumberLabelPresentation(
    val localLabel: String?,
    val sourceLabel: String?,
) {
    fun directoryDisplayName(): String? = listOfNotNull(
        localLabel.cleanLabel(),
        sourceLabel.cleanLabel(),
    ).joinToString(" · ").takeIf { it.isNotEmpty() }
}

private fun String?.cleanLabel(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
```

- [ ] **Step 3：注册 Preferences 单例**

在 `AppModule.kt` 的 SharedPreferences 注册之后增加：

```kotlin
single { LocalNumberLabelPreferences(get()) }
```

Koin DSL 的定义顺序不影响解析，但保留在 SharedPreferences 附近便于维护。

- [ ] **Step 4：接入 SettingViewModel**

注入：

```kotlin
private val localNumberLabelPreferences: LocalNumberLabelPreferences by inject()
```

增加状态和更新函数：

```kotlin
var showLocalNumberLabels by mutableStateOf(localNumberLabelPreferences.enabled.value)
    private set

fun updateShowLocalNumberLabels(enabled: Boolean) {
    showLocalNumberLabels = enabled
    localNumberLabelPreferences.setEnabled(enabled)
}
```

- [ ] **Step 5：在应用功能设置增加默认关闭的开关**

在 `AppFeaturesPreferences.kt` 的功能测试入口之前增加 `SwitchPreference`：

```kotlin
SwitchPreference(
    value = viewModel.showLocalNumberLabels,
    onValueChange = viewModel::updateShowLocalNumberLabels,
    title = { Text(stringResource(R.string.setting_show_local_number_labels)) },
    summary = { Text(stringResource(R.string.setting_show_local_number_labels_summary)) },
    icon = { Icon(Icons.Default.Label, contentDescription = null) },
)
```

增加 `Icons.Default.Label` 导入。

- [ ] **Step 6：补充中英文文案**

英文：

```xml
<string name="setting_show_local_number_labels">Show local number labels</string>
<string name="setting_show_local_number_labels_summary">Show your local labels in caller identification and call records</string>
```

中文：

```xml
<string name="setting_show_local_number_labels">显示本地号码标签</string>
<string name="setting_show_local_number_labels_summary">在来电识别和记录中显示你设置的本地标签</string>
```

- [ ] **Step 7：编译验证设置与 DI**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`；新安装没有旧键时 `enabled.value == false`。

- [ ] **Step 8：提交显示设置**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/data/preferences/LocalNumberLabelPreferences.kt app/src/main/java/vip/mystery0/pixel/telo/data/model/NumberLabelPresentation.kt app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt app/src/main/java/vip/mystery0/pixel/telo/ui/screen/settings/AppFeaturesPreferences.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat: 增加本地号码标签显示开关"
```

### Task 3：接入 Directory Provider 与来电 Overlay

**Files:**

- Modify: `app/src/main/java/vip/mystery0/pixel/telo/provider/TeloDirectoryProvider.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/service/TeloCallScreeningService.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/service/IncomingCallOverlayFormatter.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/service/IncomingCallOverlay.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

**Interfaces:**

- Consumes: `LocalNumberLabelRepository.find(String)`
- Consumes: `LocalNumberLabelPreferences.enabled`
- Consumes: `NumberLabelPresentation.directoryDisplayName()`
- Extends: `IncomingCallOverlay.show(phoneNumber, result, localLabel)`
- Produces: `IncomingCallOverlayContent.localLabelText`、`sourceLabelText`

- [ ] **Step 1：让 Directory Provider 并行读取本地标签**

向 `TeloDirectoryProvider` 注入：

```kotlin
private val localNumberLabelRepository: LocalNumberLabelRepository by inject()
private val localNumberLabelPreferences: LocalNumberLabelPreferences by inject()
```

把 `queryPhoneNumber()` 的 `runBlocking` 主体改为 `coroutineScope`：

```kotlin
return runBlocking(Dispatchers.IO) {
    coroutineScope {
        val localLabelDeferred = if (localNumberLabelPreferences.enabled.value) {
            async { localNumberLabelRepository.find(filter)?.label }
        } else {
            null
        }
        val result = spamNumberRepository.checkSpam(filter)
        val localLabel = localLabelDeferred?.await()
        val sourceLabel = result.label.takeIf { result.shouldBlock && it.isNotBlank() }
        val displayName = NumberLabelPresentation(
            localLabel = localLabel,
            sourceLabel = sourceLabel,
        ).directoryDisplayName()

        if (!result.shouldBlock && localLabel == null) {
            Log.i(TAG, "Phone number has no directory label")
            return@coroutineScope emptyCursor
        }
        if (displayName == null) return@coroutineScope emptyCursor

        // 使用 displayName 构造原有 projection-aware Cursor。
    }
}
```

所有 `DISPLAY_NAME` 和自定义 `LABEL` 字段使用同一个 `displayName`。不得把本地标签写回 `CheckResult`。

- [ ] **Step 2：扩展 Overlay 内容模型**

把 `IncomingCallOverlayContent` 改为：

```kotlin
data class IncomingCallOverlayContent(
    val phoneNumber: String,
    val locationText: String,
    val localLabelText: String?,
    val sourceLabelText: String?,
)
```

把 `buildContent()` 改为分别接收 `localLabel` 和 `sourceLabel`，两者各自 `trim()`，不拼接。

- [ ] **Step 3：让 CallScreeningService 只在 Overlay 需要时并行查询标签**

向 `TeloCallScreeningService` 注入 Repository 和 Preferences。进入 `runBlocking(Dispatchers.IO)` 后，用 `coroutineScope` 包裹现有逻辑，并在执行 `checkSpam()` 前计算：

```kotlin
val shouldLoadOverlayLabel =
    localNumberLabelPreferences.enabled.value &&
        prefs.getBoolean(SettingViewModel.KEY_SHOW_LOCATION_OVERLAY, false) &&
        !prefs.getBoolean(SettingViewModel.KEY_NO_NETWORK_QUERY, false)
val localLabelDeferred = if (shouldLoadOverlayLabel) {
    async { localNumberLabelRepository.find(phoneNumber)?.label }
} else {
    null
}
val result = spamNumberRepository.checkSpam(phoneNumber)
```

所有拦截、落库和反馈逻辑保持原样。最后调用：

```kotlin
showLocationOverlayIfNeeded(
    phoneNumber = phoneNumber,
    result = result,
    localLabel = localLabelDeferred?.await(),
    callRejected = callRejected,
)
```

本地标签 deferred 失败由 Repository 降级为 null；不得让异常进入外层 `catch` 改变来电响应。

- [ ] **Step 4：在 Overlay 分层渲染两个标签**

把 `IncomingCallOverlay.show()` 签名改为：

```kotlin
fun show(phoneNumber: String, result: CheckResult, localLabel: String?)
```

`sourceLabel` 仍排除 `NETWORK_TIMEOUT`。卡片样式和简洁文本样式中分别渲染：

- 本地标签使用 `MaterialTheme.colorScheme.primary` 或现有主强调色；
- 数据源标签沿用现有 `labelText` 的样式；
- 两者都有时分两行；
- 单项为空时不保留空白行。

预览使用：

```kotlin
localLabel = appContext.getString(R.string.location_overlay_preview_local_label)
sourceLabel = "快递外卖"
```

英文和中文资源分别提供本地化预览标签，不把中文硬编码新增到业务路径。

- [ ] **Step 5：编译验证来电链路接口**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`；`CheckResult`、`BlockedCall`、`SpamNumberRepository` 无字段变更。

- [ ] **Step 6：检查语义差异**

Run:

```bash
git diff -- app/src/main/java/vip/mystery0/pixel/telo/data/repository/SpamNumberRepository.kt app/src/main/java/vip/mystery0/pixel/telo/data/entity/BlockedCall.kt
```

Expected: 无输出，证明本任务没有污染识别结果或记录快照模型。

- [ ] **Step 7：提交系统集成**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/provider/TeloDirectoryProvider.kt app/src/main/java/vip/mystery0/pixel/telo/service/TeloCallScreeningService.kt app/src/main/java/vip/mystery0/pixel/telo/service/IncomingCallOverlayFormatter.kt app/src/main/java/vip/mystery0/pixel/telo/service/IncomingCallOverlay.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat: 在原生来电展示中接入本地号码标签"
```

### Task 4：实现历史记录动态标签与共用编辑器

**Files:**

- Create: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/LocalNumberLabelEditorViewModel.kt`
- Create: `app/src/main/java/vip/mystery0/pixel/telo/ui/components/LocalNumberLabelEditorDialogs.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/HomeViewModel.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/HomeScreen.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

**Interfaces:**

- Produces: `HomeViewModel.localLabels: StateFlow<Map<String, String>>`
- Produces: `HomeViewModel.showLocalNumberLabels: StateFlow<Boolean>`
- Produces: `LocalNumberLabelEditorViewModel.state: StateFlow<LocalLabelEditorState>`
- Produces: `observe(phoneNumber)`、`openEditor()`、`updateDraft()`、`save()`、`requestDelete()`、`confirmDelete()`、`close()`
- Consumes: `LocalNumberLabelPreferences.enabled`

- [ ] **Step 1：让 HomeViewModel 只观察 Paging 当前窗口**

注入 `LocalNumberLabelRepository` 和 `LocalNumberLabelPreferences`。直接暴露只读开关 Flow，并增加标签映射：

```kotlin
val showLocalNumberLabels: StateFlow<Boolean> = localNumberLabelPreferences.enabled

private val _localLabels = MutableStateFlow<Map<String, String>>(emptyMap())
val localLabels: StateFlow<Map<String, String>> = _localLabels.asStateFlow()
private var localLabelLookupJob: Job? = null
```

把 `updateLoadedPhoneNumbers()` 末尾同时调用 `resolveLoadedLocalLabels()`。在 `init` 中收集显示开关：

```kotlin
viewModelScope.launch {
    localNumberLabelPreferences.enabled.collect {
        resolveLoadedLocalLabels()
    }
}
```

实现：

```kotlin
private fun resolveLoadedLocalLabels() {
    localLabelLookupJob?.cancel()
    if (!localNumberLabelPreferences.enabled.value || loadedPhoneNumbers.isEmpty()) {
        _localLabels.value = emptyMap()
        return
    }
    localLabelLookupJob = viewModelScope.launch {
        localNumberLabelRepository.observeLabels(loadedPhoneNumbers).collect {
            _localLabels.value = it
        }
    }
}
```

`deleteAll()` 清空 Paging 窗口映射，但不得调用标签 Repository 删除数据。

- [ ] **Step 2：创建单号码编辑 ViewModel**

`LocalLabelEditorState` 至少包含：

```kotlin
data class LocalLabelEditorState(
    val phoneNumber: String? = null,
    val currentLabel: String? = null,
    val draft: String = "",
    val observing: Boolean = false,
    val editorVisible: Boolean = false,
    val deleteConfirmationVisible: Boolean = false,
    val saving: Boolean = false,
    val error: LocalLabelEditorError? = null,
)

enum class LocalLabelEditorError {
    INVALID_NUMBER,
    LABEL_TOO_LONG,
    SAVE_FAILED,
}
```

`observe(phoneNumber)` 取消旧 Job，调用 Repository `observe(phoneNumber)`，并实时更新 `currentLabel`。`updateDraft()` 只限制 Compose 输入最大 41 个字符，允许用户看到“超过 40”错误；最终规则仍由 Repository 决定。

`save()` 将 `LocalLabelWriteResult` 映射为稳定 UI 状态：Created/Updated/Deleted/Unchanged 均关闭编辑框；InvalidNumber、LabelTooLong、Failure 分别映射错误。`confirmDelete()` 调用 Repository `delete()`。`clearTarget()` 在详情关闭时取消观察并恢复初始状态。

- [ ] **Step 3：创建共用编辑与删除 Dialog**

`LocalNumberLabelEditorDialogs.kt` 接收 ViewModel state 和事件，不直接访问 Repository。编辑 Dialog：

- 标题按是否存在 `currentLabel` 使用“设置本地标签”或“修改本地标签”；
- 显示原始号码；
- `OutlinedTextField` 单行、字符计数 `draft.length / 40`；
- 超过 40 时显示错误且禁用保存；
- 保存失败保持 Dialog；
- 删除确认 Dialog 单独展示。

- [ ] **Step 4：在 HomeScreen 列表和详情显示独立标签**

收集：

```kotlin
val localLabels by viewModel.localLabels.collectAsState()
val showLocalNumberLabels by viewModel.showLocalNumberLabels.collectAsState()
```

给 `BlockedCallItem()` 增加 `localLabel: String?`，在数据源标签之前增加独立本地标签行，使用本地化前缀“本地标签：”。不要与 `call.label` 拼接。详情身份区域仅在 `showLocalNumberLabels` 为 true 时展示本地标签；编辑管理区域不使用该门禁。

详情 BottomSheet 打开时：

```kotlin
LaunchedEffect(call.phoneNumber) {
    localLabelEditorViewModel.observe(call.phoneNumber)
}
```

关闭详情时调用 `clearTarget()`。身份展示区只有在显示开关开启时显示本地标签；独立管理区域始终根据 Editor state 显示“尚未设置/当前标签”和设置、修改、删除按钮。

- [ ] **Step 5：在 MainActivity 创建并传递 EditorViewModel**

增加：

```kotlin
private val localNumberLabelEditorViewModel: LocalNumberLabelEditorViewModel by viewModels()
```

调用 `HomeScreen` 时显式传入。不要让 Composable 自行创建多个 EditorViewModel 实例。

- [ ] **Step 6：补充编辑相关中英文文案**

至少增加：本地标签字段名、尚未设置、设置、修改、删除确认、40 字符错误、无效号码、保存失败、字符计数可访问性描述。所有 Toast/Dialog 文案使用资源，不新增中文硬编码。

- [ ] **Step 7：编译验证历史记录与编辑接口**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`；关闭显示开关时 `HomeViewModel.localLabels` 为空，但 Editor 仍可观察单号码。

- [ ] **Step 8：提交历史记录与编辑器**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/viewmodel/LocalNumberLabelEditorViewModel.kt app/src/main/java/vip/mystery0/pixel/telo/ui/components/LocalNumberLabelEditorDialogs.kt app/src/main/java/vip/mystery0/pixel/telo/viewmodel/HomeViewModel.kt app/src/main/java/vip/mystery0/pixel/telo/ui/screen/HomeScreen.kt app/src/main/java/vip/mystery0/pixel/telo/MainActivity.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat: 支持在来电记录中管理本地号码标签"
```

### Task 5：实现统一标签管理页与二级导航

**Files:**

- Create: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/LocalNumberLabelsViewModel.kt`
- Create: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/LocalNumberLabelsScreen.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/settings/AppFeaturesPreferences.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/SettingsScreen.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/MainActivity.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

**Interfaces:**

- Produces: `LocalNumberLabelsViewModel.items: StateFlow<List<LocalNumberLabel>>`
- Produces: `LocalNumberLabelsViewModel.query: StateFlow<String>`
- Extends: `SettingsScreen(viewModel, onNavigateToLocalNumberLabels)`
- Extends: `AppFeaturesPreferences(..., onNavigateToLocalNumberLabels)`

- [ ] **Step 1：创建管理页 ViewModel**

注入 Repository。实现：

```kotlin
private val query = MutableStateFlow("")
val searchQuery: StateFlow<String> = query.asStateFlow()

val items: StateFlow<List<LocalNumberLabel>> = combine(
    repository.observeAll(),
    query,
) { labels, text ->
    val normalizedQuery = text.trim()
    if (normalizedQuery.isEmpty()) labels else labels.filter { entry ->
        entry.normalizedPhoneNumber.contains(normalizedQuery, ignoreCase = true) ||
            entry.label.contains(normalizedQuery, ignoreCase = true)
    }
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
```

Repository 已按 `updatedAt DESC` 返回，过滤后保持原排序。ViewModel 不提供新增号码方法。

- [ ] **Step 2：创建 LocalNumberLabelsScreen**

页面参数：

```kotlin
@Composable
fun LocalNumberLabelsScreen(
    viewModel: LocalNumberLabelsViewModel,
    editorViewModel: LocalNumberLabelEditorViewModel,
    modifier: Modifier = Modifier,
)
```

页面包含：

- 顶部搜索框，匹配号码和标签；
- `LazyColumn`，主文本标签、次文本归一化号码；
- 每项编辑和删除 IconButton；
- 无标签时显示“暂无本地号码标签 / 可在来电记录详情中设置”；
- 有搜索词但结果为空时显示“没有匹配的号码标签”；
- 不提供 FAB、新建按钮或号码输入框；
- 复用 `LocalNumberLabelEditorDialogs`。

点击编辑或删除前先调用 `editorViewModel.observe(entry.normalizedPhoneNumber)`，再打开对应操作。

- [ ] **Step 3：在设置页增加管理入口**

给 `AppFeaturesPreferences` 和 `SettingsScreen` 增加 `onNavigateToLocalNumberLabels` 回调。在显示开关后增加：

```kotlin
Preference(
    title = { Text(stringResource(R.string.setting_local_number_labels)) },
    summary = { Text(stringResource(R.string.setting_local_number_labels_summary)) },
    icon = { Icon(Icons.Default.ManageSearch, contentDescription = null) },
    onClick = onNavigateToLocalNumberLabels,
)
```

入口始终 enabled。

- [ ] **Step 4：在 MainActivity 实现非底部二级页面**

增加：

```kotlin
private val localNumberLabelsViewModel: LocalNumberLabelsViewModel by viewModels()
```

Compose 内使用：

```kotlin
var showLocalNumberLabelsScreen by rememberSaveable { mutableStateOf(false) }
```

当二级页面打开时：

- TopAppBar 标题使用“本地号码标签”；
- navigationIcon 显示返回按钮；
- 隐藏 `NavigationBar` 和 `HorizontalPager`；
- 内容显示 `LocalNumberLabelsScreen`；
- 返回只关闭二级页面，Pager 仍停留在 SETTINGS。

为系统返回键增加：

```kotlin
BackHandler(enabled = showLocalNumberLabelsScreen) {
    showLocalNumberLabelsScreen = false
    localNumberLabelEditorViewModel.clearTarget()
}
```

不要引入 Navigation Compose 依赖。

- [ ] **Step 5：补充管理页文案**

中英文增加管理入口、搜索提示、空状态、搜索无结果、编辑/删除 contentDescription 和页面标题。

- [ ] **Step 6：编译验证二级页面**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`；底部三页枚举 `AppDestinations` 不增加第四项。

- [ ] **Step 7：提交管理页**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/viewmodel/LocalNumberLabelsViewModel.kt app/src/main/java/vip/mystery0/pixel/telo/ui/screen/LocalNumberLabelsScreen.kt app/src/main/java/vip/mystery0/pixel/telo/ui/screen/settings/AppFeaturesPreferences.kt app/src/main/java/vip/mystery0/pixel/telo/ui/screen/SettingsScreen.kt app/src/main/java/vip/mystery0/pixel/telo/MainActivity.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat: 增加本地号码标签统一管理页"
```

### Task 6：把本地标签作为第四个备份恢复范围

**Files:**

- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/dto/BackupData.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/BackupRepository.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/SettingsScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

**Interfaces:**

- Extends: `BackupData.version = 5`
- Extends: `BackupOptions.includeLocalNumberLabels: Boolean`
- Extends: `BackupPreview.localNumberLabelCount: Int`
- Extends: `RestoreResult.localLabels: RestoreLocalLabelsResult`

- [ ] **Step 1：升级备份 DTO 到 v5**

在 `BackupData.kt` 增加：

```kotlin
@Serializable
data class LocalNumberLabelDto(
    @SerialName("phone_number") val phoneNumber: String,
    val label: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
)
```

把 `BackupData.version` 改为 5，并增加：

```kotlin
@SerialName("local_number_labels")
val localNumberLabels: List<LocalNumberLabelDto> = emptyList()
```

默认空列表保证 v1–v4 兼容。

- [ ] **Step 2：扩展 BackupRepository 数据结构**

`BackupOptions` 增加默认 true 的 `includeLocalNumberLabels`；`BackupPreview` 增加 `localNumberLabelCount`；`RestoreResult` 增加：

```kotlin
val localLabels: RestoreLocalLabelsResult
```

向 `BackupRepository` 构造函数注入 `LocalNumberLabelRepository`。

- [ ] **Step 3：实现选择性导出**

`backup()` 中：

```kotlin
val localNumberLabels = if (options.includeLocalNumberLabels) {
    localNumberLabelRepository.getAllSnapshot().map { it.toDto() }
} else {
    emptyList()
}
```

把结果写入 `BackupData`。显示开关不读取、不写入 DTO。

- [ ] **Step 4：实现预览和选择性恢复**

`parseBackup()` 把 `data.localNumberLabels.size` 写入 Preview。

`restore()` 中只有 `includeLocalNumberLabels == true` 时执行：

```kotlin
localNumberLabelRepository.restore(
    preview.data.localNumberLabels.map { dto ->
        LocalNumberLabelRestoreEntry(
            phoneNumber = dto.phoneNumber,
            label = dto.label,
            createdAt = dto.createdAt,
            updatedAt = dto.updatedAt,
        )
    }
)
```

未选择时返回 `RestoreLocalLabelsResult(0, 0, 0)`，不得读取或修改标签表。

- [ ] **Step 5：更新 SettingViewModel 默认选项和结果文案**

解析预览后增加：

```kotlin
includeLocalNumberLabels = preview.localNumberLabelCount > 0
```

恢复成功字符串改为接受六个数量：记录、黑名单、白名单、本地标签新增、覆盖、跳过。

- [ ] **Step 6：在备份与恢复 Sheet 增加第四项**

备份 Sheet 增加默认勾选“本地号码标签”。备份按钮 enabled 条件增加 `includeLocalNumberLabels`。

恢复 Sheet 增加：

```kotlin
BackupCheckboxRow(
    checked = viewModel.restoreOptions.includeLocalNumberLabels,
    label = stringResource(
        R.string.label_restore_local_number_labels,
        preview.localNumberLabelCount,
    ),
    enabled = preview.localNumberLabelCount > 0,
    onCheckedChange = {
        viewModel.restoreOptions = viewModel.restoreOptions.copy(
            includeLocalNumberLabels = it,
        )
    },
)
```

恢复按钮至少一个有数据且已选范围时才 enabled，避免空恢复操作。

- [ ] **Step 7：注册 BackupRepository 新依赖并补充文案**

在 `AppModule.kt` 把：

```kotlin
single { BackupRepository(get(), get()) }
```

改为：

```kotlin
single { BackupRepository(get(), get(), get()) }
```

中英文更新备份摘要，使其包含“本地号码标签”；增加备份/恢复标签范围和六参数恢复结果文案。

- [ ] **Step 8：编译验证序列化与 UI 参数**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`；Kotlinx Serialization 接受 v5 默认字段，所有格式化字符串参数数量匹配。

- [ ] **Step 9：提交备份恢复**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/data/dto/BackupData.kt app/src/main/java/vip/mystery0/pixel/telo/data/repository/BackupRepository.kt app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt app/src/main/java/vip/mystery0/pixel/telo/ui/screen/SettingsScreen.kt app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat: 支持选择性备份恢复本地号码标签"
```

### Task 7：更新架构文档并完成静态验证

**Files:**

- Modify: `.agentdocs/architecture/mvvm-structure.md`
- Modify: `.agentdocs/architecture/native-integration.md`
- Modify: `.agentdocs/ui/main-screen.md`
- Modify: `.agentdocs/index.md`
- Modify: `docs/plans/2026-08-29-local-number-label-implementation.md`

**Interfaces:**

- Documents: Room v10、本地标签边界、Directory Provider 组合、Paging 窗口观察、管理 UI、备份 v5。

- [ ] **Step 1：更新 MVVM 文档**

在 `.agentdocs/architecture/mvvm-structure.md` 增加“本地号码标签”小节，明确：

- `LocalNumberLabelRepository` 是单一事实来源；
- `SpamNumberRepository` 不读取标签；
- 首页只观察 Paging 当前窗口；
- 显示开关关闭时不建立普通展示订阅；
- 标签不写入 `BlockedCall`。

- [ ] **Step 2：更新原生集成文档**

在 `.agentdocs/architecture/native-integration.md` 的 Directory Provider 和 Overlay 部分记录：

- 原识别查询完整执行；
- 本地标签只参与最终展示；
- 两者组合格式为 `本地 · 数据源`；
- 仅有本地标签的安全号码可返回 Directory 身份记录；
- 本地标签失败不影响 `respondToCall()`。

- [ ] **Step 3：更新首页和索引文档**

在 `.agentdocs/ui/main-screen.md` 记录本地标签独立行、详情管理入口、显示开关语义和动态历史关联；在 `.agentdocs/index.md` 增加本实施计划链接。

- [ ] **Step 4：执行最终静态验证**

Run:

```bash
./gradlew :app:assembleDebug
./gradlew lint
git diff --check
```

Expected: 两个 Gradle 命令均 `BUILD SUCCESSFUL`，`git diff --check` 无输出。

- [ ] **Step 5：检查范围与依赖稳定性**

Run:

```bash
git diff --name-only 5f66517..HEAD
git diff 5f66517..HEAD -- gradle/libs.versions.toml app/build.gradle.kts AndroidManifest.xml
git status --short
```

Expected:

- 无 `src/test` 新文件；
- 依赖和 Manifest 无变更；
- 工作区仅包含文档执行结果更新，或已经干净。

- [ ] **Step 6：更新计划执行结果并提交文档**

在本文件顶部 Global Constraints 之后增加“执行结果”小节，写入实际构建/Lint结果、尚未执行的模拟器项和已知限制，不填写虚构结果。

```bash
git add .agentdocs/architecture/mvvm-structure.md .agentdocs/architecture/native-integration.md .agentdocs/ui/main-screen.md .agentdocs/index.md docs/plans/2026-08-29-local-number-label-implementation.md
git commit -m "docs: 更新本地号码标签架构与实施记录"
```

### Task 8：在 Android 模拟器完成迁移与用户流程验收

**Files:**

- No source file changes
- Runtime artifacts only: Debug APK、模拟器数据库、临时联系人、临时备份 ZIP

**Interfaces:**

- Consumes: `app/build/outputs/apk/debug/app-debug.apk`
- Verifies: Room v10、设置默认值、编辑管理、号码归一化、Directory Provider、Overlay、备份 v5。

- [ ] **Step 1：确认唯一目标模拟器**

Run:

```bash
adb devices
```

Expected: 至少一个 `emulator-*` 状态为 `device`。存在多个设备时，后续命令统一使用 `adb -s <serial>`，不得对未选设备操作。

- [ ] **Step 2：保留旧数据安装 Debug 包**

Run:

```bash
./gradlew :app:installDebug
adb shell am force-stop vip.mystery0.pixel.telo.debug
adb shell am start -n vip.mystery0.pixel.telo.debug/vip.mystery0.pixel.telo.MainActivity
```

Expected: 应用启动，无 Room migration crash；原拦截记录和黑白名单仍存在。

- [ ] **Step 3：检查迁移和崩溃日志**

Run:

```bash
adb logcat -d | grep -E "Room|FATAL EXCEPTION|AndroidRuntime|LocalNumberLabel"
```

Expected: 无 v9 → v10 schema 错误、崩溃或号码/标签正文日志。

- [ ] **Step 4：验证默认关闭和关闭态编辑**

手动确认“显示本地号码标签”默认关闭。从一条已有来电记录进入详情：

1. 设置标签“物业”；
2. 确认管理区域显示该标签；
3. 确认列表普通身份区域不显示该标签；
4. 进入统一管理页，确认标签存在且可搜索。

- [ ] **Step 5：验证号码归一化和动态历史关联**

使用现有 Debug 数据或备份恢复准备以下同一号码变体记录：

```text
13800138000
+8613800138000
12583113800138000
```

开启显示开关后，三条记录都显示“物业”。修改为“物业前台”后全部立即刷新；删除后全部消失，数据源标签保持不变。

- [ ] **Step 6：验证统一管理页**

确认：

- 搜索号码和标签均可命中；
- 英文字母搜索忽略大小写；
- 编辑后按 `updatedAt DESC` 移到顶部；
- 删除需要确认；
- 无标签和搜索无结果使用不同空状态；
- 页面没有新增号码按钮；
- 返回后仍停留在设置 Tab。

- [ ] **Step 7：验证 Directory Provider 四种组合**

在模拟器拨号器或通过 `adb shell content query` 触发 Provider 查询，分别验证：

1. 本地标签 + 数据源标签 → `物业 · 快递送餐`；
2. 仅本地标签的安全号码 → `物业`，且 CallScreeningService 仍放行；
3. 仅数据源标签 → 保持原标签；
4. 两者都无 → 空 Cursor。

同时观察联网请求仍按原设置执行，本地标签不得让查询提前返回。

- [ ] **Step 8：验证联系人优先和 Overlay**

在模拟器联系人中保存同一号码，触发拨号器查询并记录联系人优先的可观察结果。启用 Overlay、联网查询和本地标签显示，验证本地标签与数据源标签分层显示；关闭显示开关后 Overlay 只保留数据源标签。

若模拟器拨号器不查询 Directory Provider 或保留系统缓存，记录环境限制和 Provider 直接查询结果，不修改产品逻辑规避模拟器差异。

- [ ] **Step 9：验证备份 v5 选择范围**

执行以下矩阵：

1. 仅勾选本地标签导出，确认按钮可用；
2. 不勾选本地标签导出，恢复预览标签数量为 0；
3. v5 标签备份恢复到存在同号码标签的数据库，确认备份覆盖；
4. 备份中没有的当前标签保持不变；
5. v1–v4 旧备份恢复后现有标签不变；
6. 恢复结果正确显示新增、覆盖、跳过；
7. 显示开关不随备份恢复改变。

- [ ] **Step 10：验证清空记录不删除标签**

执行“全部清空拦截记录”，进入标签管理页确认标签仍存在；恢复一条同号码记录后能够重新动态显示标签。

- [ ] **Step 11：最终日志与工作区检查**

Run:

```bash
adb logcat -d | grep -E "FATAL EXCEPTION|ANR|Local label lookup too slow|Local label lookup failed"
git status --short
git log --oneline -10
```

Expected: 无崩溃或 ANR；若存在慢查询 warning，记录耗时和触发场景；工作区干净。

- [ ] **Step 12：把模拟器结果写回实施计划**

更新本文件“执行结果”，记录：

- 模拟器型号/API；
- v9 → v10 迁移结果；
- Directory Provider 的实际触发方式和缓存限制；
- 号码变体、编辑、管理、Overlay、备份矩阵结果；
- 未解决风险。

提交仅包含实施计划结果更新：

```bash
git add docs/plans/2026-08-29-local-number-label-implementation.md
git commit -m "docs: 记录本地号码标签模拟器验收结果"
```
