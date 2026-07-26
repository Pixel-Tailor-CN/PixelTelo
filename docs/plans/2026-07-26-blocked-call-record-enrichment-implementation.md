# 拦截记录信息增强 Implementation Plan

> **执行要求：** REQUIRED SUB-SKILL: 使用 `superpowers:subagent-driven-development`
>（推荐）或 `superpowers:executing-plans` 按任务逐项实施。所有步骤使用 checkbox 跟踪。

**目标：** 为拦截记录增加归属地持久化、联系人动态识别和 Paging 3 分页展示，并在
Android 模拟器完成性能与显示验收。

**架构：** Room 保存可空的省份和城市，并以 `PagingSource` 提供按时间倒序的记录。
`ContactRepository` 只解析当前 Paging 已加载窗口中的去重号码，`HomeViewModel` 组合分页记录、
黑白名单状态和联系人姓名映射，Compose 负责渲染布局 C。

**技术栈：** Kotlin、Room 2.8.4、Paging 3.5.0、Paging Compose、Jetpack Compose、
Kotlin Coroutines、Koin、ContactsContract。

## 执行结果

- 已完成 Room v7→v8 迁移、归属地持久化、备份格式 v4、联系人动态解析和 Paging 3 列表迁移。
- `./gradlew :app:assembleDebug`、`./gradlew lint`、`./gradlew :app:installDebug` 均通过。
- Android 17 Pixel 模拟器保留原数据库升级成功，旧记录未丢失，空归属地不显示占位。
- 使用 504 条临时记录验证分页滚动；常速连续滚动 440 帧无错误日志，
  P50/P90/P95/P99 为 18/27/34/48ms。
- 普通号码、`+86` 号码和移动一卡多号前缀号码均匹配到同一联系人；
  撤销联系人权限后降级为只显示号码，重新授权后恢复姓名。
- 首次无权限启动后，在同一进程授权可重新建立 ContentObserver；前台改名和删除联系人
  均能自动刷新，旧查询不会回灌已失效缓存。
- Paging refresh 加载或失败时保留已有列表内容，错误态在列表内提供重试入口。
- 列表与 BottomSheet 均正确展示联系人和归属地；省市相同时只展示一次，未展示运营商。
- 验证完成后已恢复原数据库并删除本次创建的临时联系人。

## Global Constraints

- 代码注释、KDoc 与项目文档使用中文；日志使用英文。
- 不新增权限，继续使用现有 `READ_CONTACTS`。
- 不把联系人查询放入 `CallScreeningService` 的实时来电响应链路。
- 只保存和展示省份、城市，不保存或展示运营商、`cardType`。
- 旧拦截记录不回填归属地。
- Paging 参数固定为：`pageSize = 30`、`initialLoadSize = 30`、
  `prefetchDistance = 10`、`maxSize = 90`、`enablePlaceholders = false`。
- 项目默认不新增、不运行单元测试；每个任务使用编译检查，最终执行 Lint 和模拟器验证。
- 最终验证设备为用户已启动的 Android 模拟器。

---

### Task 1：接入 Paging 3 并升级拦截记录数据库

**Files:**

- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/entity/BlockedCall.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/AppDatabase.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/dao/BlockedCallDao.kt`

**Interfaces:**

- Produces: `BlockedCall.province: String?`
- Produces: `BlockedCall.city: String?`
- Produces: `BlockedCallDao.getPagingSource(): PagingSource<Int, BlockedCall>`
- Produces: `MIGRATION_7_8`

- [ ] **Step 1：添加 Paging 依赖**

在 `gradle/libs.versions.toml` 增加：

```toml
[versions]
paging = "3.5.0"

[libraries]
androidx-paging-runtime = { group = "androidx.paging", name = "paging-runtime", version.ref = "paging" }
androidx-paging-compose = { group = "androidx.paging", name = "paging-compose", version.ref = "paging" }
androidx-room-paging = { group = "androidx.room", name = "room-paging", version.ref = "room" }
```

在 `app/build.gradle.kts` 的 Room 依赖后增加：

```kotlin
implementation(libs.androidx.room.paging)
implementation(libs.androidx.paging.runtime)
implementation(libs.androidx.paging.compose)
```

- [ ] **Step 2：扩展 `BlockedCall`**

在 `feedbackStatus` 之前增加：

```kotlin
/** 联网查询返回的省份；旧记录或无数据时为 null */
val province: String? = null,
/** 联网查询返回的城市；旧记录或无数据时为 null */
val city: String? = null,
```

- [ ] **Step 3：增加 v7 → v8 migration**

在 `AppDatabase.kt` 增加：

```kotlin
/** 从 v7 升级到 v8：拦截记录新增省份和城市字段 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `blocked_calls` ADD COLUMN `province` TEXT")
        db.execSQL("ALTER TABLE `blocked_calls` ADD COLUMN `city` TEXT")
    }
}
```

同时把 `@Database(version = 7)` 改为 `version = 8`，并在 `AppModule.kt` 导入和注册
`MIGRATION_7_8`。

- [ ] **Step 4：让 DAO 提供 PagingSource**

保留现有 `getAll()` 和 `getAllSnapshot()`，先新增：

```kotlin
@Query("SELECT * FROM blocked_calls ORDER BY blockTime DESC")
fun getPagingSource(): PagingSource<Int, BlockedCall>
```

并导入：

```kotlin
import androidx.paging.PagingSource
```

- [ ] **Step 5：执行编译检查**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`，Room 生成代码接受 v8 schema 和 `PagingSource`。

- [ ] **Step 6：提交**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts app/src/main/java/vip/mystery0/pixel/telo/data/entity/BlockedCall.kt app/src/main/java/vip/mystery0/pixel/telo/data/AppDatabase.kt app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt app/src/main/java/vip/mystery0/pixel/telo/data/dao/BlockedCallDao.kt
git commit -m "feat: 为拦截记录增加归属地字段和分页数据源"
```

### Task 2：持久化归属地并升级备份格式

**Files:**

- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/BlockedCallRepository.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/service/TeloCallScreeningService.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/dto/BackupData.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/BackupRepository.kt`

**Interfaces:**

- Produces: `BlockedCallRepository.blockedCallsPager: Flow<PagingData<BlockedCall>>`
- Extends: `BlockedCallRepository.insert(..., province: String?, city: String?)`
- Extends: `BlockedCallRepository.attachQueryResult()` 写回 `QueryResponse.data`
- Produces: backup format version 4

- [ ] **Step 1：在 Repository 创建 Pager**

在 `BlockedCallRepository` 中增加：

```kotlin
val blockedCallsPager: Flow<PagingData<BlockedCall>> = Pager(
    config = PagingConfig(
        pageSize = 30,
        initialLoadSize = 30,
        prefetchDistance = 10,
        maxSize = 90,
        enablePlaceholders = false,
    ),
    pagingSourceFactory = blockedCallDao::getPagingSource,
).flow
```

并导入 `Pager`、`PagingConfig` 和 `PagingData`。此时保留 `allBlockedCalls`，
直到 Task 4 的 UI 消费端完成迁移，保证每个任务结束时都能独立编译。

- [ ] **Step 2：扩展写入参数并清理空白归属地**

给 `insert()` 增加：

```kotlin
province: String? = null,
city: String? = null,
```

创建实体时写入：

```kotlin
province = province.cleanLocationPart(),
city = city.cleanLocationPart(),
```

在文件底部增加：

```kotlin
private fun String?.cleanLocationPart(): String? = this?.trim()?.takeIf { it.isNotEmpty() }
```

- [ ] **Step 3：手动重查写回归属地但不清除旧值**

扩展 `attachQueryResult()` 中的 `copy()`：

```kotlin
province = response.data?.province.cleanLocationPart() ?: call.province,
city = response.data?.city.cleanLocationPart() ?: call.city,
```

- [ ] **Step 4：所有来电记录写入点传递归属地**

在 `TeloCallScreeningService` 中所有持有 `CheckResult` 的 `insert()` 调用增加：

```kotlin
province = result.locationInfo?.province,
city = result.locationInfo?.city,
```

`NETWORK_TIMEOUT` 没有归属地，保持默认空值即可。

- [ ] **Step 5：升级备份 DTO**

把 `BackupData.version` 改为 4，更新 KDoc，并在 `BlockedCallDto` 增加：

```kotlin
val province: String? = null,
val city: String? = null,
```

在 `BackupRepository` 的双向转换中增加：

```kotlin
province = province,
city = city,
```

旧备份缺少字段时由 Kotlinx Serialization 默认值 `null` 兼容。

- [ ] **Step 6：执行编译检查**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`，所有 `insert()` 参数与 DTO 转换一致。

- [ ] **Step 7：提交**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/data/repository/BlockedCallRepository.kt app/src/main/java/vip/mystery0/pixel/telo/service/TeloCallScreeningService.kt app/src/main/java/vip/mystery0/pixel/telo/data/dto/BackupData.kt app/src/main/java/vip/mystery0/pixel/telo/data/repository/BackupRepository.kt
git commit -m "feat: 持久化拦截记录归属地并升级备份"
```

### Task 3：实现联系人动态解析与缓存

**Files:**

- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/ContactRepository.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt`

**Interfaces:**

- Produces: `ContactRepository.changes: Flow<Unit>`
- Produces: `ContactRepository.resolveNames(phoneNumbers: Set<String>): Map<String, String>`
- Produces: `ContactRepository.invalidateCache()`

- [ ] **Step 1：创建联系人 Repository**

实现以下类；号码和姓名不得进入日志：

```kotlin
package vip.mystery0.pixel.telo.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.util.Log
import android.util.LruCache
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import vip.mystery0.pixel.telo.data.PhoneNumberNormalizer

private data class ContactLookupResult(val name: String?)

class ContactRepository(private val context: Context) {
    companion object {
        private const val TAG = "ContactRepository"
        private const val CACHE_SIZE = 256
    }

    private val resolver = context.contentResolver
    private val cache = LruCache<String, ContactLookupResult>(CACHE_SIZE)
    private val lookupDispatcher = Dispatchers.IO.limitedParallelism(4)

    val changes: Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                invalidateCache()
                trySend(Unit)
            }
        }
        var registered = false
        try {
            resolver.registerContentObserver(
                ContactsContract.Contacts.CONTENT_URI,
                true,
                observer,
            )
            registered = true
        } catch (_: SecurityException) {
            Log.w(TAG, "Unable to observe contacts")
        }
        awaitClose {
            if (registered) resolver.unregisterContentObserver(observer)
        }
    }.conflate()

    suspend fun resolveNames(phoneNumbers: Set<String>): Map<String, String> {
        if (ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.READ_CONTACTS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return emptyMap()
        }
        return coroutineScope {
            phoneNumbers
                .filter { it.isNotBlank() }
                .map { phone ->
                    async(lookupDispatcher) { phone to resolveName(phone) }
                }
                .awaitAll()
                .mapNotNull { (phone, name) -> name?.let { phone to it } }
                .toMap()
        }
    }

    fun invalidateCache() {
        synchronized(cache) { cache.evictAll() }
    }

    private fun resolveName(phone: String): String? {
        synchronized(cache) { cache.get(phone) }?.let { return it.name }
        val name = phoneCandidates(phone).firstNotNullOfOrNull(::queryName)
        synchronized(cache) { cache.put(phone, ContactLookupResult(name)) }
        return name
    }

    private fun phoneCandidates(phone: String): List<String> = listOf(
        phone.trim(),
        PhoneNumberNormalizer.normalizeCountryCode(phone),
        PhoneNumberNormalizer.normalizeForLookup(phone),
    ).filter { it.isNotBlank() }.distinct()

    private fun queryName(phone: String): String? {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phone),
        )
        return try {
            resolver.query(
                uri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0)?.trim()?.takeIf { it.isNotEmpty() }
                else null
            }
        } catch (_: Exception) {
            Log.w(TAG, "Contact lookup failed")
            null
        }
    }
}
```

- [ ] **Step 2：注册 Koin 依赖**

在 `AppModule.kt` 增加导入并注册：

```kotlin
single { ContactRepository(androidContext()) }
```

- [ ] **Step 3：执行编译检查**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`，且编译器未报告联系人 Provider 或协程 API 错误。

- [ ] **Step 4：提交**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/data/repository/ContactRepository.kt app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt
git commit -m "feat: 增加联系人动态解析与有界缓存"
```

### Task 4：将 HomeViewModel 改为分页展示状态

> **原子迁移约束：** Task 4 会改变 `HomeViewModel.blockedCallItems` 的公开类型，
> 必须与 Task 5 的 Compose 消费端迁移连续完成；两项之间不执行编译或提交。

**Files:**

- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/HomeViewModel.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/BlockedCallRepository.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/dao/BlockedCallDao.kt`

**Interfaces:**

- Produces: `HomeViewModel.blockedCallItems: Flow<PagingData<BlockedCallListItem>>`
- Produces: `HomeViewModel.contactNames: StateFlow<Map<String, String>>`
- Produces: `HomeViewModel.updateLoadedPhoneNumbers(Set<String>)`
- Produces: `HomeViewModel.refreshContactNames()`

- [ ] **Step 1：把全量列表转换为 PagingData**

注入 `ContactRepository`，删除 `blockedCalls: StateFlow<List<BlockedCall>>`。把
`blockedCallItems` 改为：

```kotlin
private data class UserLists(
    val black: List<UserListEntry>,
    val white: List<UserListEntry>,
)

private val userLists = combine(
    userListRepository.observeBlackList(),
    userListRepository.observeWhiteList(),
) { black, white -> UserLists(black, white) }

val blockedCallItems: Flow<PagingData<BlockedCallListItem>> =
    repository.blockedCallsPager
        .combine(userLists) { pagingData, lists ->
            pagingData.map { call ->
                buildBlockedCallListItem(call, lists.black, lists.white)
            }
        }
        .cachedIn(viewModelScope)
```

对应导入 `PagingData`、`cachedIn`、`map` 和 Flow 的 `combine`。

- [ ] **Step 2：把列表映射函数改为单条映射**

替换 `buildBlockedCallListItems()`：

```kotlin
fun buildBlockedCallListItem(
    call: BlockedCall,
    blackList: List<UserListEntry>,
    whiteList: List<UserListEntry>,
): BlockedCallListItem {
    val inBlackList = blackList.any { it.matchesPhone(call.phoneNumber) }
    val inWhiteList = whiteList.any { it.matchesPhone(call.phoneNumber) }
    val currentListState = when {
        inBlackList && inWhiteList -> CurrentListState.BOTH
        inBlackList -> CurrentListState.BLACK
        inWhiteList -> CurrentListState.WHITE
        else -> CurrentListState.NONE
    }
    return BlockedCallListItem(call, currentListState)
}
```

- [ ] **Step 3：增加当前加载窗口的联系人状态**

在 ViewModel 中增加：

```kotlin
private val contactRepository: ContactRepository by inject()
private val _contactNames = MutableStateFlow<Map<String, String>>(emptyMap())
val contactNames: StateFlow<Map<String, String>> = _contactNames.asStateFlow()

private var loadedPhoneNumbers: Set<String> = emptySet()
private var contactLookupJob: Job? = null

fun updateLoadedPhoneNumbers(phoneNumbers: Set<String>) {
    val sanitized = phoneNumbers.filter { it.isNotBlank() }.toSet()
    if (sanitized == loadedPhoneNumbers) return
    loadedPhoneNumbers = sanitized
    resolveLoadedContacts()
}

fun refreshContactNames() {
    contactRepository.invalidateCache()
    resolveLoadedContacts()
}

private fun resolveLoadedContacts() {
    contactLookupJob?.cancel()
    if (loadedPhoneNumbers.isEmpty()) {
        _contactNames.value = emptyMap()
        return
    }
    contactLookupJob = viewModelScope.launch {
        delay(100)
        _contactNames.value = contactRepository.resolveNames(loadedPhoneNumbers)
    }
}
```

在 `init` 中增加联系人变化监听：

```kotlin
viewModelScope.launch {
    contactRepository.changes.collect {
        resolveLoadedContacts()
    }
}
```

- [ ] **Step 4：移除旧的全量 UI 数据流**

确认 `HomeViewModel` 已不再引用 `repository.allBlockedCalls` 后，删除
`BlockedCallRepository.allBlockedCalls` 和 `BlockedCallDao.getAll()`；保留
`getAllSnapshot()` 供备份导出使用。

- [ ] **Step 5：继续执行 Compose 消费端迁移**

不要在接口生产端和消费端类型不一致的中间状态执行编译或提交，直接继续 Task 5。

### Task 5：实现 Paging Compose 与布局 C

**Files:**

- Modify: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/HomeScreen.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

**Interfaces:**

- Consumes: `Flow<PagingData<BlockedCallListItem>>`
- Consumes: `StateFlow<Map<String, String>>`
- Produces: 分页加载、联系人姓名与归属地 UI

- [ ] **Step 1：收集 PagingData 并上报已加载号码**

在 `HomeScreen` 中替换列表收集：

```kotlin
val blockedCallItems = viewModel.blockedCallItems.collectAsLazyPagingItems()
val contactNames by viewModel.contactNames.collectAsState()

LaunchedEffect(blockedCallItems) {
    snapshotFlow {
        blockedCallItems.itemSnapshotList.items
            .map { it.call.phoneNumber }
            .toSet()
    }
        .distinctUntilChanged()
        .collect(viewModel::updateLoadedPhoneNumbers)
}
```

在现有 `LifecycleEventEffect(ON_RESUME)` 末尾调用：

```kotlin
viewModel.refreshContactNames()
```

- [ ] **Step 2：把 LazyListScope 改为 Paging 列表**

`blockedCallsList()` 接收 `LazyPagingItems<BlockedCallListItem>` 和
`Map<String, String>`，核心签名与实现为：

```kotlin
private fun LazyListScope.blockedCallsList(
    callItems: LazyPagingItems<BlockedCallListItem>,
    contactNames: Map<String, String>,
    onRetry: (BlockedCall) -> Unit,
    onClick: (BlockedCall) -> Unit,
) {
when {
    callItems.loadState.refresh is LoadState.Loading -> item {
        Box(Modifier.fillMaxWidth().height(360.dp)) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }

    callItems.loadState.refresh is LoadState.Error -> item {
        Column(
            modifier = Modifier.fillMaxWidth().height(360.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.home_records_load_failed))
            Button(onClick = callItems::retry) {
                Text(stringResource(R.string.action_retry))
            }
        }
    }

    callItems.itemCount == 0 -> item {
        Box(Modifier.fillMaxWidth().height(360.dp)) {
            Text(
                stringResource(R.string.home_no_records),
                modifier = Modifier.align(Alignment.Center),
            )
        }
    }

    else -> items(
        count = callItems.itemCount,
        key = callItems.itemKey { it.call.id },
    ) { index ->
        callItems[index]?.let { item ->
            BlockedCallItem(
                call = item.call,
                contactName = contactNames[item.call.phoneNumber],
                currentListState = item.currentListState,
                onRetry = if (item.call.resultType == ResultType.NETWORK_TIMEOUT) {
                    { onRetry(item.call) }
                } else null,
                onClick = { onClick(item.call) },
            )
        }
    }
}

if (callItems.loadState.append is LoadState.Loading) {
    item {
        Box(Modifier.fillMaxWidth().padding(16.dp)) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp).align(Alignment.Center),
            )
        }
    }
}

if (callItems.loadState.append is LoadState.Error) {
    item {
        TextButton(
            onClick = callItems::retry,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_retry))
        }
    }
}
}
```

- [ ] **Step 3：增加归属地格式化**

在 `HomeScreen.kt` 增加：

```kotlin
private fun BlockedCall.locationText(): String? = listOfNotNull(
    province?.trim()?.takeIf { it.isNotEmpty() },
    city?.trim()?.takeIf { it.isNotEmpty() },
).distinct().joinToString(" ").takeIf { it.isNotEmpty() }
```

- [ ] **Step 4：实现布局 C**

给 `BlockedCallItem()` 增加 `contactName: String? = null`。首行左侧使用同一 `Row`
显示姓名和号码，右侧保留重查按钮与时间；姓名设置 `maxLines = 1` 和
`TextOverflow.Ellipsis`。号码始终显示，未命中联系人时不渲染姓名组件：

```kotlin
Row(
    modifier = Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
) {
    Row(
        modifier = Modifier.weight(1f),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (!contactName.isNullOrBlank()) {
            Text(
                text = contactName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = call.phoneNumber,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
            )
        } else {
            Text(
                text = call.phoneNumber,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (onRetry != null) {
            IconButton(onClick = onRetry) {
                Icon(
                    imageVector = Icons.Default.Refresh,
                    contentDescription = stringResource(R.string.action_retry_query),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = formatMills(call.blockTime),
            style = MaterialTheme.typography.bodySmall,
        )
    }
}
```

首行后增加：

```kotlin
call.locationText()?.let { location ->
    Text(
        text = location,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
    )
}
```

- [ ] **Step 5：增强详情 BottomSheet**

在 `quickAddCall` 区域读取：

```kotlin
val contactName = contactNames[phone]
val location = call.locationText()
```

先显示非空联系人姓名，再显示完整号码；归属地存在时增加：

```kotlin
Text(
    text = stringResource(R.string.label_phone_location, location),
    style = MaterialTheme.typography.bodyMedium,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

其余拨号、复制、黑白名单、反馈和重查操作继续使用原始号码。

- [ ] **Step 6：补充中英文字符串**

英文：

```xml
<string name="home_records_load_failed">Failed to load intercept records</string>
<string name="label_phone_location">Location: %1$s</string>
```

中文：

```xml
<string name="home_records_load_failed">拦截记录加载失败</string>
<string name="label_phone_location">归属地：%1$s</string>
```

- [ ] **Step 7：执行编译检查**

Run:

```bash
./gradlew :app:assembleDebug
```

Expected: `BUILD SUCCESSFUL`，Paging Compose、LoadState 和布局参数全部通过编译。

- [ ] **Step 8：提交**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/viewmodel/HomeViewModel.kt app/src/main/java/vip/mystery0/pixel/telo/data/repository/BlockedCallRepository.kt app/src/main/java/vip/mystery0/pixel/telo/data/dao/BlockedCallDao.kt app/src/main/java/vip/mystery0/pixel/telo/ui/screen/HomeScreen.kt app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat: 分页展示拦截记录联系人和归属地"
```

### Task 6：更新架构文档并完成静态验证

**Files:**

- Modify: `.agentdocs/architecture/mvvm-structure.md`
- Modify: `.agentdocs/ui/main-screen.md`
- Modify: `docs/plans/2026-07-26-blocked-call-record-enrichment-implementation.md`

**Interfaces:**

- Documents: Paging 数据流、联系人 Repository、归属地字段和页面展示规则

- [ ] **Step 1：更新架构文档**

在 `.agentdocs/architecture/mvvm-structure.md` 增加：

```markdown
### 拦截记录分页与联系人解析

拦截记录由 Room `PagingSource` 分页加载，`HomeViewModel` 组合黑白名单状态。
`ContactRepository` 只解析 Paging 当前已加载窗口中的去重号码，联系人姓名不持久化，
也不进入 `CallScreeningService`。
```

- [ ] **Step 2：更新主页文档**

在 `.agentdocs/ui/main-screen.md` 增加：

```markdown
### 拦截记录展示

- 每页加载 30 条记录，最多在内存保留约 90 条。
- 匹配到联系人时，同一行显示姓名和号码。
- 归属地独立显示省份和城市，不显示运营商。
- 联系人权限缺失或查询失败时回退到号码。
```

- [ ] **Step 3：执行最终构建和 Lint**

Run:

```bash
./gradlew :app:assembleDebug
./gradlew lint
```

Expected: 两条命令均输出 `BUILD SUCCESSFUL`。不执行 `test`、`check` 或任何单元测试任务。

- [ ] **Step 4：检查依赖稳定性和工作区**

Run:

```bash
git diff --check
git status --short
```

Expected: 无 whitespace error；状态只包含本任务预期文件。

- [ ] **Step 5：提交**

```bash
git add .agentdocs/architecture/mvvm-structure.md .agentdocs/ui/main-screen.md docs/plans/2026-07-26-blocked-call-record-enrichment-implementation.md
git commit -m "docs: 更新拦截记录分页与联系人解析文档"
```

### Task 7：在模拟器完成安装与验收

**Files:**

- No repository file changes

**Interfaces:**

- Consumes: `app/build/outputs/apk/debug/app-debug.apk`
- Verifies: Room migration、Paging、联系人、归属地和布局 C

- [ ] **Step 1：确认目标模拟器**

Run:

```bash
adb devices
```

Expected: 至少一个状态为 `device` 的 emulator。若存在多台设备，先运行以下 PowerShell
命令选取第一个 emulator，并在本节后续命令的 `adb` 后添加 `-s $emulatorSerial`：

```powershell
$emulatorSerial = ((adb devices | Select-String '^emulator-\d+\s+device$' | Select-Object -First 1).Line -split '\s+')[0]
if (-not $emulatorSerial) { throw "No running emulator found" }
```

- [ ] **Step 2：安装并启动 Debug 包**

Run:

```bash
./gradlew :app:installDebug
adb shell am force-stop vip.mystery0.pixel.telo.debug
adb shell am start -n vip.mystery0.pixel.telo.debug/vip.mystery0.pixel.telo.MainActivity
```

Expected: 安装成功并打开 Pixel Telo 首页。

- [ ] **Step 3：验证数据库升级和基础展示**

保留模拟器已有 Debug 数据安装新版，确认应用无 Room schema 崩溃；检查旧记录仍存在且旧记录
不显示空归属地占位。通过 `adb logcat` 确认没有 migration 或 Paging exception：

```bash
adb logcat -d | Select-String -Pattern "Room|Paging|FATAL EXCEPTION|PixelTelo"
```

Expected: 无本次功能相关崩溃。

- [ ] **Step 4：验证联系人号码变体**

在模拟器联系人应用创建一个测试联系人，并使用同一真实号码对应的以下记录验证姓名：

```text
13800138000
+8613800138000
12583113800138000
```

Expected: 三种记录都显示同一个联系人姓名；撤销 `READ_CONTACTS` 后只显示号码，重新授权并
返回首页后恢复姓名。

- [ ] **Step 5：验证分页和大量记录性能**

通过 Debug 备份恢复流程导入至少 500 条临时拦截记录。连续执行滚动：

```bash
adb shell dumpsys gfxinfo vip.mystery0.pixel.telo.debug reset
adb shell input swipe 540 1800 540 350 250
adb shell input swipe 540 1800 540 350 250
adb shell input swipe 540 1800 540 350 250
adb shell dumpsys gfxinfo vip.mystery0.pixel.telo.debug
```

Expected: 页面持续追加记录，无 ANR、崩溃、整表联系人扫描或明显长时间冻结；返回已浏览位置时
联系人缓存能够复用。保存模拟器截图用于人工检查布局 C。

- [ ] **Step 6：验证归属地和手动重查**

使用一条带省份、城市的 Debug 备份记录确认列表和 BottomSheet 展示归属地；对超时记录执行
手动联网重查，确认成功结果写回归属地。省市相同只显示一次，空值不显示，页面不出现运营商。

- [ ] **Step 7：清理临时模拟器数据并检查日志**

只删除本次创建的测试联系人和临时 Debug 记录，不清除其他模拟器数据。检查：

```bash
adb logcat -d | Select-String -Pattern "ContactRepository|FATAL EXCEPTION|ANR"
```

Expected: 日志不包含联系人姓名，无崩溃或 ANR。

- [ ] **Step 8：最终工作区检查**

Run:

```bash
git status --short
git log --oneline -8
```

Expected: 工作区干净，最近提交均属于本需求。
