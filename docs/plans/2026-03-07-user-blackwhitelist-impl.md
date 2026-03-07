# 用户自定义黑白名单 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 为 PixelTelo 添加用户自定义黑白名单功能，优先级高于骚扰库与联网查询。

**Architecture:** 黑白名单作为新实体存入现有 AppDatabase（版本升至 2），SpamNumberRepository 在原有流程前插入白名单/黑名单检查。新增第三个底部导航页 ListScreen（TabRow 切换黑/白名单），备份/恢复流程增加选择步骤。

**Tech Stack:** Room（+Migration）、Kotlin Coroutines/Flow、Jetpack Compose、Koin、kotlinx.serialization

---

## Task 1: 新实体 + Dao

**Files:**
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/entity/UserListEntry.kt`
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/dao/UserListDao.kt`

**Step 1: 创建 `UserListEntry.kt`**

```kotlin
package vip.mystery0.pixel.telo.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 用户自定义黑白名单条目。
 * phoneNumber 可以是具体号码（精确匹配）或前缀（前缀匹配）。
 */
@Entity(
    tableName = "user_list",
    indices = [Index(value = ["phoneNumber", "listType"], unique = true)]
)
data class UserListEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** 号码或前缀，如 "13800138000" 或 "400" */
    val phoneNumber: String,
    /** true = 前缀匹配，false = 精确匹配 */
    val isPrefix: Boolean,
    /** BLACK 或 WHITE */
    val listType: ListType,
    /** 可选备注 */
    val remark: String?,
    /** 添加时间戳 */
    val addedAt: Long,
)

enum class ListType { BLACK, WHITE }
```

**Step 2: 创建 `UserListDao.kt`**

```kotlin
package vip.mystery0.pixel.telo.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import vip.mystery0.pixel.telo.data.entity.ListType
import vip.mystery0.pixel.telo.data.entity.UserListEntry

@Dao
interface UserListDao {
    /** 实时监听指定类型的名单，供 UI 展示 */
    @Query("SELECT * FROM user_list WHERE listType = :type ORDER BY addedAt DESC")
    fun observeByType(type: ListType): Flow<List<UserListEntry>>

    /** 获取指定类型的全部条目，供备份用 */
    @Query("SELECT * FROM user_list WHERE listType = :type")
    suspend fun getAllByType(type: ListType): List<UserListEntry>

    /** 插入，若 (phoneNumber, listType) 已存在则忽略 */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: UserListEntry): Long

    @Delete
    suspend fun delete(entry: UserListEntry)

    /** 查询是否存在匹配的条目（精确或前缀），用于拦截判断 */
    @Query("""
        SELECT * FROM user_list
        WHERE listType = :type
        AND (
            (isPrefix = 0 AND phoneNumber = :phone) OR
            (isPrefix = 1 AND :phone LIKE phoneNumber || '%')
        )
        LIMIT 1
    """)
    suspend fun findMatch(phone: String, type: ListType): UserListEntry?
}
```

**Step 3: 编译验证**

```bash
./gradlew assembleDebug
```

期望：BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/data/entity/UserListEntry.kt
git add app/src/main/java/vip/mystery0/pixel/telo/data/dao/UserListDao.kt
git commit -m "feat: add UserListEntry entity and UserListDao"
```

---

## Task 2: AppDatabase 升级（v1 → v2）

**Files:**
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/AppDatabase.kt`

**Step 1: 修改 AppDatabase**

将文件内容替换为：

```kotlin
package vip.mystery0.pixel.telo.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import vip.mystery0.pixel.telo.data.dao.BlockedCallDao
import vip.mystery0.pixel.telo.data.dao.UserListDao
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.UserListEntry

@Database(
    entities = [BlockedCall::class, UserListEntry::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun blockedCallDao(): BlockedCallDao
    abstract fun userListDao(): UserListDao
}

/** 从 v1 升级到 v2：新增 user_list 表 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS `user_list` (
                `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `phoneNumber` TEXT NOT NULL,
                `isPrefix` INTEGER NOT NULL,
                `listType` TEXT NOT NULL,
                `remark` TEXT,
                `addedAt` INTEGER NOT NULL,
                UNIQUE(`phoneNumber`, `listType`)
            )
        """.trimIndent())
    }
}
```

**Step 2: 更新 Koin AppModule — 注册 Migration 和 UserListDao**

修改 `app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt`，将 `Room.databaseBuilder` 部分改为：

```kotlin
single {
    Room.databaseBuilder(
        androidContext(),
        AppDatabase::class.java,
        "app-database"
    )
        .addMigrations(MIGRATION_1_2)
        .build()
}

single { get<AppDatabase>().blockedCallDao() }
single { get<AppDatabase>().userListDao() }  // 新增这行
```

**Step 3: 编译验证**

```bash
./gradlew assembleDebug
```

**Step 4: Commit**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/data/AppDatabase.kt
git add app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt
git commit -m "feat: upgrade AppDatabase to v2, add user_list table"
```

---

## Task 3: UserListRepository

**Files:**
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/UserListRepository.kt`

**Step 1: 创建 Repository**

```kotlin
package vip.mystery0.pixel.telo.data.repository

import kotlinx.coroutines.flow.Flow
import vip.mystery0.pixel.telo.data.dao.UserListDao
import vip.mystery0.pixel.telo.data.entity.ListType
import vip.mystery0.pixel.telo.data.entity.UserListEntry

class UserListRepository(private val dao: UserListDao) {

    /** 实时监听黑名单 */
    fun observeBlackList(): Flow<List<UserListEntry>> = dao.observeByType(ListType.BLACK)

    /** 实时监听白名单 */
    fun observeWhiteList(): Flow<List<UserListEntry>> = dao.observeByType(ListType.WHITE)

    /** 查询黑名单是否命中（精确或前缀），命中则返回条目 */
    suspend fun findBlackListMatch(phone: String): UserListEntry? =
        dao.findMatch(phone, ListType.BLACK)

    /** 查询白名单是否命中（精确或前缀），命中则返回条目 */
    suspend fun findWhiteListMatch(phone: String): UserListEntry? =
        dao.findMatch(phone, ListType.WHITE)

    /**
     * 添加条目。若 (phoneNumber, listType) 已存在则忽略并返回 false。
     */
    suspend fun add(
        phoneNumber: String,
        isPrefix: Boolean,
        listType: ListType,
        remark: String?
    ): Boolean {
        val entry = UserListEntry(
            phoneNumber = phoneNumber.trim(),
            isPrefix = isPrefix,
            listType = listType,
            remark = remark?.trim()?.takeIf { it.isNotBlank() },
            addedAt = System.currentTimeMillis()
        )
        return dao.insert(entry) != -1L
    }

    suspend fun delete(entry: UserListEntry) = dao.delete(entry)

    /** 获取指定类型全部条目，供备份用 */
    suspend fun getAllByType(type: ListType): List<UserListEntry> = dao.getAllByType(type)
}
```

**Step 2: 注册到 Koin**

在 `AppModule.kt` 中添加（紧跟 `userListDao()` 那行之后）：

```kotlin
single { UserListRepository(get()) }
```

并在 import 中添加：
```kotlin
import vip.mystery0.pixel.telo.data.repository.UserListRepository
```

**Step 3: 编译验证**

```bash
./gradlew assembleDebug
```

**Step 4: Commit**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/data/repository/UserListRepository.kt
git add app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt
git commit -m "feat: add UserListRepository and register in Koin"
```

---

## Task 4: 拦截逻辑 — 黑白名单优先

**Files:**
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/entity/BlockedCall.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/SpamNumberRepository.kt`

**Step 1: ResultType 新增两个枚举值**

在 `BlockedCall.kt` 中，`ResultType` 枚举末尾添加：

```kotlin
enum class ResultType {
    INTERCEPT,
    PASS_BUT_NOTIFY,
    NETWORK_TIMEOUT,
    PASS,
    BLACK_LIST,   // 用户黑名单拦截（新增）
    WHITE_LIST,   // 用户白名单放行（新增）
}
```

**Step 2: 修改 SpamNumberRepository.kt**

在文件顶部 import 区域新增：
```kotlin
import vip.mystery0.pixel.telo.data.repository.UserListRepository
```

在类中新增注入：
```kotlin
private val userListRepository: UserListRepository by inject()
```

修改 `checkSpam()` 方法，在 "1. Local Lookup" 注释之前插入黑白名单检查：

```kotlin
suspend fun checkSpam(phoneNumber: String, forceNetworkQuery: Boolean = false): CheckResult {
    val start = System.currentTimeMillis()
    val phone = phoneNumber.removePrefix("+86")
    var localCost: Long
    var networkCost: Long

    // 0. 用户白名单检查（最高优先级，直接放行）
    val whiteMatch = userListRepository.findWhiteListMatch(phone)
    if (whiteMatch != null) {
        Log.i(TAG, "White list hit: $phone, entry: ${whiteMatch.phoneNumber}")
        return CheckResult(false, whiteMatch.remark ?: "", ResultType.WHITE_LIST, 0, 0)
    }

    // 0. 用户黑名单检查（最高优先级，直接拦截）
    val blackMatch = userListRepository.findBlackListMatch(phone)
    if (blackMatch != null) {
        Log.i(TAG, "Black list hit: $phone, entry: ${blackMatch.phoneNumber}")
        return CheckResult(true, blackMatch.remark ?: "", ResultType.BLACK_LIST, 0, 0)
    }

    // 1. Local Lookup
    // ... 以下原有代码保持不变 ...
```

**Step 3: 编译验证**

```bash
./gradlew assembleDebug
```

**Step 4: Commit**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/data/entity/BlockedCall.kt
git add app/src/main/java/vip/mystery0/pixel/telo/data/repository/SpamNumberRepository.kt
git commit -m "feat: add BLACK_LIST/WHITE_LIST result types, check user list before spam db"
```

---

## Task 5: ListViewModel

**Files:**
- Create: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/ListViewModel.kt`

**Step 1: 创建 ListViewModel**

```kotlin
package vip.mystery0.pixel.telo.viewmodel

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.ListType
import vip.mystery0.pixel.telo.data.entity.UserListEntry
import vip.mystery0.pixel.telo.data.repository.UserListRepository

class ListViewModel : ViewModel(), KoinComponent {
    companion object {
        private const val TAG = "ListViewModel"
    }

    private val userListRepository: UserListRepository by inject()
    private val context: Context by inject()

    /** 当前选中的 Tab：BLACK 或 WHITE */
    var currentTab by mutableStateOf(ListType.BLACK)
        private set

    /** 是否展示"添加条目" BottomSheet */
    var showAddSheet by mutableStateOf(false)
        private set

    /** 添加表单：号码输入 */
    var inputPhone by mutableStateOf("")

    /** 添加表单：是否前缀匹配 */
    var inputIsPrefix by mutableStateOf(false)

    /** 添加表单：备注 */
    var inputRemark by mutableStateOf("")

    /** 错误提示（为 null 表示无错误） */
    var addErrorMessage by mutableStateOf<String?>(null)
        private set

    /** toast 消息（用完即清） */
    var toastMessage by mutableStateOf<String?>(null)
        private set

    val blackList: StateFlow<List<UserListEntry>> = userListRepository.observeBlackList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val whiteList: StateFlow<List<UserListEntry>> = userListRepository.observeWhiteList()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(type: ListType) {
        currentTab = type
    }

    fun openAddSheet() {
        inputPhone = ""
        inputIsPrefix = false
        inputRemark = ""
        addErrorMessage = null
        showAddSheet = true
    }

    fun closeAddSheet() {
        showAddSheet = false
    }

    fun clearToast() {
        toastMessage = null
    }

    fun confirmAdd() {
        val phone = inputPhone.trim()
        if (phone.isBlank()) {
            addErrorMessage = context.getString(R.string.error_phone_empty)
            return
        }
        viewModelScope.launch {
            val success = userListRepository.add(phone, inputIsPrefix, currentTab, inputRemark)
            if (success) {
                showAddSheet = false
                toastMessage = context.getString(R.string.msg_added_to_list)
            } else {
                addErrorMessage = context.getString(R.string.error_phone_already_exists)
            }
        }
    }

    fun delete(entry: UserListEntry) {
        viewModelScope.launch {
            try {
                userListRepository.delete(entry)
            } catch (e: Exception) {
                Log.e(TAG, "Delete failed", e)
            }
        }
    }

    /**
     * 从首页快捷添加，号码直接写入，不校验重复（忽略冲突）。
     * @return true 表示成功插入，false 表示已存在
     */
    suspend fun quickAdd(phoneNumber: String, listType: ListType): Boolean {
        return userListRepository.add(phoneNumber, false, listType, null)
    }
}
```

**Step 2: 编译验证**

```bash
./gradlew assembleDebug
```

**Step 3: Commit**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/viewmodel/ListViewModel.kt
git commit -m "feat: add ListViewModel for blacklist/whitelist management"
```

---

## Task 6: ListScreen UI

**Files:**
- Create: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/ListScreen.kt`

**Step 1: 创建 ListScreen**

```kotlin
package vip.mystery0.pixel.telo.ui.screen

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import vip.mystery0.pixel.telo.R
import vip.mystery0.pixel.telo.data.entity.ListType
import vip.mystery0.pixel.telo.data.entity.UserListEntry
import vip.mystery0.pixel.telo.ui.components.SwipeToDeleteContainer
import vip.mystery0.pixel.telo.viewmodel.ListViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ListScreen(viewModel: ListViewModel) {
    val context = LocalContext.current
    val blackList by viewModel.blackList.collectAsState()
    val whiteList by viewModel.whiteList.collectAsState()

    // 展示 Toast
    LaunchedEffect(viewModel.toastMessage) {
        viewModel.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearToast()
        }
    }

    // Tab 与 Pager 联动，但禁用 Pager 手势以避免与底部导航左右滑动冲突
    val tabs = listOf(ListType.BLACK, ListType.WHITE)
    val tabLabels = listOf(
        stringResource(R.string.tab_blacklist),
        stringResource(R.string.tab_whitelist)
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val scope = rememberCoroutineScope()

    // 同步 ViewModel tab 状态与 Pager 页面
    LaunchedEffect(pagerState.currentPage) {
        viewModel.selectTab(tabs[pagerState.currentPage])
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { viewModel.openAddSheet() }) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.action_add_entry))
            }
        }
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            TabRow(selectedTabIndex = pagerState.currentPage) {
                tabs.forEachIndexed { index, _ ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = {
                            scope.launch { pagerState.animateScrollToPage(index) }
                        },
                        text = { Text(tabLabels[index]) }
                    )
                }
            }

            // userScrollEnabled = false：禁用左右滑动，防止与底部导航手势冲突
            HorizontalPager(
                state = pagerState,
                userScrollEnabled = false,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val list = if (page == 0) blackList else whiteList
                UserListContent(
                    entries = list,
                    onDelete = { viewModel.delete(it) }
                )
            }
        }
    }

    // 添加条目 BottomSheet
    if (viewModel.showAddSheet) {
        ModalBottomSheet(onDismissRequest = { viewModel.closeAddSheet() }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp)
                    .imePadding(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    stringResource(
                        if (viewModel.currentTab == ListType.BLACK)
                            R.string.title_add_to_blacklist
                        else
                            R.string.title_add_to_whitelist
                    ),
                    style = MaterialTheme.typography.titleLarge
                )

                OutlinedTextField(
                    value = viewModel.inputPhone,
                    onValueChange = {
                        viewModel.inputPhone = it
                        viewModel.addErrorMessage = null
                    },
                    label = { Text(stringResource(R.string.label_phone_number)) },
                    placeholder = {
                        Text(
                            if (viewModel.inputIsPrefix)
                                stringResource(R.string.hint_prefix_example)
                            else
                                stringResource(R.string.hint_exact_example)
                        )
                    },
                    isError = viewModel.addErrorMessage != null,
                    supportingText = viewModel.addErrorMessage?.let { { Text(it) } },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            stringResource(R.string.label_prefix_match),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            stringResource(R.string.summary_prefix_match),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = viewModel.inputIsPrefix,
                        onCheckedChange = { viewModel.inputIsPrefix = it }
                    )
                }

                OutlinedTextField(
                    value = viewModel.inputRemark,
                    onValueChange = { viewModel.inputRemark = it },
                    label = { Text(stringResource(R.string.label_remark_optional)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.closeAddSheet() },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.action_cancel)) }
                    Button(
                        onClick = { viewModel.confirmAdd() },
                        modifier = Modifier.weight(1f)
                    ) { Text(stringResource(R.string.action_confirm)) }
                }
            }
        }
    }
}

@Composable
private fun UserListContent(
    entries: List<UserListEntry>,
    onDelete: (UserListEntry) -> Unit,
) {
    if (entries.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .height(360.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                stringResource(R.string.list_no_entries),
                style = MaterialTheme.typography.bodyLarge
            )
        }
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(entries, key = { it.id }) { entry ->
                SwipeToDeleteContainer(
                    onDelete = { onDelete(entry) },
                    contentVerticalPadding = 4.dp
                ) {
                    UserListEntryItem(entry)
                }
            }
        }
    }
}

@Composable
private fun UserListEntryItem(entry: UserListEntry) {
    androidx.compose.material3.Card(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 号码：前缀匹配显示为 "400*"
            val displayNumber = if (entry.isPrefix) "${entry.phoneNumber}*" else entry.phoneNumber
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(displayNumber, style = MaterialTheme.typography.titleMedium)
                Text(
                    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(entry.addedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // 备注（若有）
            if (!entry.remark.isNullOrBlank()) {
                Text(
                    entry.remark,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            // 前缀匹配标签
            if (entry.isPrefix) {
                Text(
                    stringResource(R.string.label_prefix_match),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
```

**Step 2: 编译验证**

```bash
./gradlew assembleDebug
```

**Step 3: Commit**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/ui/screen/ListScreen.kt
git commit -m "feat: add ListScreen with TabRow blacklist/whitelist management"
```

---

## Task 7: 导航 — 新增 LIST Tab

**Files:**
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/MainActivity.kt`

**Step 1: 修改 MainActivity**

1. 在 import 区域新增：
```kotlin
import androidx.compose.material.icons.filled.Rule
import vip.mystery0.pixel.telo.ui.screen.ListScreen
import vip.mystery0.pixel.telo.viewmodel.ListViewModel
```

2. 在 `MainActivity` 类中新增 ViewModel：
```kotlin
private val listViewModel: ListViewModel by viewModels()
```

3. 修改 `AppDestinations` 枚举，在 HOME 和 SETTINGS 之间插入 LIST：
```kotlin
enum class AppDestinations(val titleResId: Int, val labelResId: Int, val icon: ImageVector) {
    HOME(R.string.app_name, R.string.nav_home, Icons.Default.Home),
    LIST(R.string.nav_list, R.string.nav_list, Icons.Default.Rule),
    SETTINGS(R.string.nav_settings, R.string.nav_settings, Icons.Default.Settings)
}
```

4. 在 `when (currentDestination)` 块中新增 LIST 分支：
```kotlin
AppDestinations.LIST -> {
    ListScreen(listViewModel)
}
```

**Step 2: 编译验证**

```bash
./gradlew assembleDebug
```

**Step 3: Commit**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/MainActivity.kt
git commit -m "feat: add LIST tab to bottom navigation"
```

---

## Task 8: 首页快捷添加 + 新 ResultType 展示

**Files:**
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/HomeViewModel.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/HomeScreen.kt`

**Step 1: HomeViewModel 新增快捷添加方法**

在 `HomeViewModel.kt` 中新增注入和方法：

在类顶部新增 import：
```kotlin
import vip.mystery0.pixel.telo.data.entity.ListType
import vip.mystery0.pixel.telo.data.repository.UserListRepository
```

在类中新增注入：
```kotlin
private val userListRepository: UserListRepository by inject()
```

在类中新增状态和方法：
```kotlin
/** 长按快捷添加的目标号码（非 null 时弹出 BottomSheet） */
var quickAddPhone by mutableStateOf<String?>(null)
    private set

fun openQuickAdd(phoneNumber: String) {
    quickAddPhone = phoneNumber
}

fun closeQuickAdd() {
    quickAddPhone = null
}

/** 快捷加入黑名单，返回是否成功（false 表示已存在） */
suspend fun quickAddToBlackList(phone: String): Boolean =
    userListRepository.add(phone, false, ListType.BLACK, null)

/** 快捷加入白名单，返回是否成功（false 表示已存在） */
suspend fun quickAddToWhiteList(phone: String): Boolean =
    userListRepository.add(phone, false, ListType.WHITE, null)
```

需要在类头部添加 `import androidx.compose.runtime.mutableStateOf` 已存在，补充：
```kotlin
import androidx.compose.runtime.setValue
import vip.mystery0.pixel.telo.data.entity.ListType
import vip.mystery0.pixel.telo.data.repository.UserListRepository
```

**Step 2: HomeScreen 修改**

2a. 在 `HomeScreen` 函数内，新增状态和快捷添加 Sheet：

在 `HomeScreen` 组合函数参数/变量区域，新增：
```kotlin
val quickAddPhone by viewModel.quickAddPhone.collectAsState() // 注意：quickAddPhone 是 mutableStateOf
```

由于 `quickAddPhone` 用的是 `mutableStateOf`，需要改用：
```kotlin
val quickAddPhone = viewModel.quickAddPhone
val coroutineScope = rememberCoroutineScope()
```

在 `HomeScreen` 的 return 之前（最后的 `AlertDialog` 后面），新增快捷添加 BottomSheet：
```kotlin
val phone = quickAddPhone
if (phone != null) {
    ModalBottomSheet(onDismissRequest = { viewModel.closeQuickAdd() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                phone,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = {
                    coroutineScope.launch {
                        val success = viewModel.quickAddToBlackList(phone)
                        Toast.makeText(
                            context,
                            if (success) context.getString(R.string.msg_added_to_blacklist)
                            else context.getString(R.string.msg_already_in_blacklist),
                            Toast.LENGTH_SHORT
                        ).show()
                        viewModel.closeQuickAdd()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.action_add_to_blacklist)) }
            OutlinedButton(
                onClick = {
                    coroutineScope.launch {
                        val success = viewModel.quickAddToWhiteList(phone)
                        Toast.makeText(
                            context,
                            if (success) context.getString(R.string.msg_added_to_whitelist)
                            else context.getString(R.string.msg_already_in_whitelist),
                            Toast.LENGTH_SHORT
                        ).show()
                        viewModel.closeQuickAdd()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.action_add_to_whitelist)) }
            OutlinedButton(
                onClick = { viewModel.closeQuickAdd() },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.action_cancel)) }
        }
    }
}
```

2b. 在 `BlockedCallItem` 中，让卡片支持长按触发快捷添加。

修改 `blockedCallsList` 函数签名，新增 `onLongClick` 参数，并在调用处传入。

修改 `BlockedCallItem` 组合函数签名：
```kotlin
@Composable
fun BlockedCallItem(call: BlockedCall, onRetry: (() -> Unit)? = null, onLongClick: (() -> Unit)? = null)
```

在 Card 上添加长按手势：
```kotlin
import androidx.compose.foundation.combinedClickable

Card(
    modifier = Modifier
        .padding(vertical = 4.dp)
        .fillMaxWidth()
        .combinedClickable(
            onClick = {},
            onLongClick = { onLongClick?.invoke() }
        )
) { ... }
```

`LazyListScope.blockedCallsList` 新增 `onLongClick` 参数并传递：
```kotlin
private fun LazyListScope.blockedCallsList(
    calls: List<BlockedCall>,
    onDelete: (BlockedCall) -> Unit,
    onRetry: (BlockedCall) -> Unit,
    onLongClick: (BlockedCall) -> Unit,
)
```

在 `HomeScreen` 调用处新增：
```kotlin
blockedCallsList(
    calls = blockedCalls,
    onDelete = { ... },
    onRetry = { ... },
    onLongClick = { call -> viewModel.openQuickAdd(call.phoneNumber) },
)
```

2c. 在 `BlockedCallItem` 的 `resultText` when 块新增两个 case：
```kotlin
val resultText = when (call.resultType) {
    ResultType.INTERCEPT -> stringResource(R.string.result_intercept_spam)
    ResultType.PASS_BUT_NOTIFY -> stringResource(R.string.result_pass_but_notify)
    ResultType.NETWORK_TIMEOUT -> stringResource(R.string.result_network_timeout)
    ResultType.PASS -> stringResource(R.string.result_pass_always_record)
    ResultType.BLACK_LIST -> stringResource(R.string.result_black_list)
    ResultType.WHITE_LIST -> stringResource(R.string.result_white_list)
}
val resultColor = when (call.resultType) {
    ResultType.PASS -> MaterialTheme.colorScheme.secondary
    ResultType.NETWORK_TIMEOUT -> MaterialTheme.colorScheme.error
    ResultType.BLACK_LIST -> MaterialTheme.colorScheme.error
    ResultType.WHITE_LIST -> MaterialTheme.colorScheme.tertiary
    else -> MaterialTheme.colorScheme.primary
}
```

**Step 3: 编译验证**

```bash
./gradlew assembleDebug
```

**Step 4: Commit**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/viewmodel/HomeViewModel.kt
git add app/src/main/java/vip/mystery0/pixel/telo/ui/screen/HomeScreen.kt
git commit -m "feat: home screen long-press quick-add to blacklist/whitelist, show new result types"
```

---

## Task 9: 备份/恢复集成

**Files:**
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/dto/BackupData.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/BackupRepository.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/ui/screen/SettingsScreen.kt`

### Step 1: 更新 BackupData.kt

```kotlin
package vip.mystery0.pixel.telo.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 备份文件根结构，序列化为 ZIP 内的 backup.json
 */
@Serializable
data class BackupData(
    val version: Int = 2,
    @SerialName("exported_at") val exportedAt: Long,
    val records: List<BlockedCallDto> = emptyList(),
    @SerialName("black_list") val blackList: List<UserListEntryDto> = emptyList(),
    @SerialName("white_list") val whiteList: List<UserListEntryDto> = emptyList(),
)

@Serializable
data class BlockedCallDto(
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("block_time") val blockTime: Long,
    val remark: String? = null,
    @SerialName("result_type") val resultType: String,
    @SerialName("local_duration") val localDuration: Long = 0,
    @SerialName("network_duration") val networkDuration: Long = 0
)

@Serializable
data class UserListEntryDto(
    @SerialName("phone_number") val phoneNumber: String,
    @SerialName("is_prefix") val isPrefix: Boolean,
    val remark: String? = null,
    @SerialName("added_at") val addedAt: Long,
)
```

### Step 2: 更新 BackupRepository.kt

```kotlin
package vip.mystery0.pixel.telo.data.repository

import kotlinx.serialization.json.Json
import vip.mystery0.pixel.telo.data.dao.BlockedCallDao
import vip.mystery0.pixel.telo.data.dao.UserListDao
import vip.mystery0.pixel.telo.data.dto.BackupData
import vip.mystery0.pixel.telo.data.dto.BlockedCallDto
import vip.mystery0.pixel.telo.data.dto.UserListEntryDto
import vip.mystery0.pixel.telo.data.entity.BlockedCall
import vip.mystery0.pixel.telo.data.entity.ListType
import vip.mystery0.pixel.telo.data.entity.ResultType
import vip.mystery0.pixel.telo.data.entity.UserListEntry
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * 备份选项：控制哪些数据参与备份/恢复
 */
data class BackupOptions(
    val includeBlockedCalls: Boolean = true,
    val includeBlackList: Boolean = true,
    val includeWhiteList: Boolean = true,
)

/**
 * 从备份文件解析出的预览信息，用于在恢复时展示给用户
 */
data class BackupPreview(
    val data: BackupData,
    val blockedCallCount: Int,
    val blackListCount: Int,
    val whiteListCount: Int,
)

/**
 * 负责拦截记录及黑白名单的备份与恢复操作。
 * 备份格式：ZIP 压缩包，内含 backup.json。
 */
class BackupRepository(
    private val blockedCallDao: BlockedCallDao,
    private val userListDao: UserListDao,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * 将选定的数据导出为 ZIP 备份文件，写入给定的输出流。
     */
    suspend fun backup(outputStream: OutputStream, options: BackupOptions = BackupOptions()) {
        val records = if (options.includeBlockedCalls) {
            blockedCallDao.getAllSnapshot().map { it.toDto() }
        } else emptyList()

        val blackList = if (options.includeBlackList) {
            userListDao.getAllByType(ListType.BLACK).map { it.toDto() }
        } else emptyList()

        val whiteList = if (options.includeWhiteList) {
            userListDao.getAllByType(ListType.WHITE).map { it.toDto() }
        } else emptyList()

        val backupData = BackupData(
            exportedAt = System.currentTimeMillis(),
            records = records,
            blackList = blackList,
            whiteList = whiteList,
        )
        val jsonString = json.encodeToString(BackupData.serializer(), backupData)

        ZipOutputStream(outputStream).use { zip ->
            zip.putNextEntry(ZipEntry("backup.json"))
            zip.write(jsonString.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    /**
     * 解析 ZIP 备份文件，返回预览信息（不执行写入）。
     */
    fun parseBackup(inputStream: InputStream): BackupPreview {
        val jsonString = readJsonFromZip(inputStream)
        val data = json.decodeFromString(BackupData.serializer(), jsonString)
        return BackupPreview(
            data = data,
            blockedCallCount = data.records.size,
            blackListCount = data.blackList.size,
            whiteListCount = data.whiteList.size,
        )
    }

    /**
     * 恢复数据，按 options 决定恢复哪些部分。
     * @return 各部分实际插入的数量
     */
    suspend fun restore(preview: BackupPreview, options: BackupOptions): RestoreResult {
        var insertedCalls = 0
        var insertedBlack = 0
        var insertedWhite = 0

        if (options.includeBlockedCalls) {
            for (dto in preview.data.records) {
                val existing = blockedCallDao.findByKey(dto.phoneNumber, dto.blockTime)
                if (existing == null) {
                    blockedCallDao.insert(dto.toEntity())
                    insertedCalls++
                }
            }
        }

        if (options.includeBlackList) {
            for (dto in preview.data.blackList) {
                userListDao.insert(dto.toEntity(ListType.BLACK))
                    .let { if (it != -1L) insertedBlack++ }
            }
        }

        if (options.includeWhiteList) {
            for (dto in preview.data.whiteList) {
                userListDao.insert(dto.toEntity(ListType.WHITE))
                    .let { if (it != -1L) insertedWhite++ }
            }
        }

        return RestoreResult(insertedCalls, insertedBlack, insertedWhite)
    }

    data class RestoreResult(
        val insertedCalls: Int,
        val insertedBlack: Int,
        val insertedWhite: Int,
    )

    private fun readJsonFromZip(inputStream: InputStream): String {
        ZipInputStream(inputStream).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == "backup.json") {
                    return zip.readBytes().toString(Charsets.UTF_8)
                }
                entry = zip.nextEntry
            }
        }
        error("backup.json not found in ZIP archive")
    }

    private fun BlockedCall.toDto() = BlockedCallDto(
        phoneNumber = phoneNumber,
        blockTime = blockTime,
        remark = remark,
        resultType = resultType.name,
        localDuration = localDuration,
        networkDuration = networkDuration
    )

    private fun BlockedCallDto.toEntity() = BlockedCall(
        phoneNumber = phoneNumber,
        blockTime = blockTime,
        remark = remark,
        resultType = runCatching { ResultType.valueOf(resultType) }.getOrDefault(ResultType.INTERCEPT),
        localDuration = localDuration,
        networkDuration = networkDuration
    )

    private fun UserListEntry.toDto() = UserListEntryDto(
        phoneNumber = phoneNumber,
        isPrefix = isPrefix,
        remark = remark,
        addedAt = addedAt,
    )

    private fun UserListEntryDto.toEntity(listType: ListType) = UserListEntry(
        phoneNumber = phoneNumber,
        isPrefix = isPrefix,
        listType = listType,
        remark = remark,
        addedAt = addedAt,
    )
}
```

同时更新 Koin 注册，修改 `AppModule.kt` 中 `BackupRepository` 的注册：
```kotlin
single { BackupRepository(get(), get()) }  // 新增第二个 get() 注入 UserListDao
```

### Step 3: 更新 SettingViewModel

新增以下状态和方法（在现有 `backupRestoreState` 等之后）：

```kotlin
// ---- 备份选项 Sheet ----
/** 是否展示备份选项选择 Sheet */
var showBackupOptionsSheet by mutableStateOf(false)
    private set

val backupOptions = mutableStateOf(BackupOptions())

fun openBackupOptionsSheet() {
    backupOptions.value = BackupOptions()
    showBackupOptionsSheet = true
}

fun closeBackupOptionsSheet() {
    showBackupOptionsSheet = false
}

// ---- 恢复：解析预览 + 恢复选项 Sheet ----
/** 已解析的备份预览，非 null 时展示恢复选项 Sheet */
var backupPreview by mutableStateOf<BackupRepository.BackupPreview?>(null)
    private set

val restoreOptions = mutableStateOf(BackupOptions())

fun parseBackupFile(inputStream: InputStream) {
    viewModelScope.launch {
        backupRestoreState = BackupRestoreState.Processing
        try {
            val preview = withContext(Dispatchers.IO) {
                backupRepository.parseBackup(inputStream)
            }
            backupPreview = preview
            // 根据备份文件内容，默认勾选有数据的部分
            restoreOptions.value = BackupOptions(
                includeBlockedCalls = preview.blockedCallCount > 0,
                includeBlackList = preview.blackListCount > 0,
                includeWhiteList = preview.whiteListCount > 0,
            )
            backupRestoreState = BackupRestoreState.Idle
        } catch (e: Exception) {
            Log.e(TAG, "Parse backup failed", e)
            backupRestoreState = BackupRestoreState.Failure(
                context.getString(R.string.msg_restore_failed, e.message)
            )
        }
    }
}

fun closeRestoreOptionsSheet() {
    backupPreview = null
}

fun performBackupWithOptions(outputStream: OutputStream) {
    val options = backupOptions.value
    showBackupOptionsSheet = false
    viewModelScope.launch {
        backupRestoreState = BackupRestoreState.Processing
        try {
            withContext(Dispatchers.IO) { backupRepository.backup(outputStream, options) }
            backupRestoreState = BackupRestoreState.Success(
                context.getString(R.string.msg_backup_exported)
            )
        } catch (e: Exception) {
            Log.e(TAG, "Backup failed", e)
            backupRestoreState = BackupRestoreState.Failure(
                context.getString(R.string.msg_backup_failed, e.message)
            )
        }
    }
}

fun performRestoreWithOptions() {
    val preview = backupPreview ?: return
    val options = restoreOptions.value
    backupPreview = null
    viewModelScope.launch {
        backupRestoreState = BackupRestoreState.Processing
        try {
            val result = withContext(Dispatchers.IO) {
                backupRepository.restore(preview, options)
            }
            backupRestoreState = BackupRestoreState.Success(
                context.getString(
                    R.string.msg_restored_summary,
                    result.insertedCalls,
                    result.insertedBlack,
                    result.insertedWhite,
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Restore failed", e)
            backupRestoreState = BackupRestoreState.Failure(
                context.getString(R.string.msg_restore_failed, e.message)
            )
        }
    }
}
```

需要在 SettingViewModel 顶部新增 import：
```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import vip.mystery0.pixel.telo.data.repository.BackupOptions
```

### Step 4: 更新 SettingsScreen

**备份流程修改**：

将原来的 `backupLauncher` 回调改为调用 `performBackupWithOptions`：
```kotlin
val backupLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.CreateDocument("application/zip")
) { uri ->
    uri?.let {
        val stream = context.contentResolver.openOutputStream(it)
        if (stream != null) viewModel.performBackupWithOptions(stream)
    }
}
```

将"备份记录" Preference 的 `onClick` 改为：
```kotlin
onClick = { viewModel.openBackupOptionsSheet() }
```

**恢复流程修改**：

将原来的 `restoreLauncher` 回调改为先解析文件：
```kotlin
val restoreLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.OpenDocument()
) { uri ->
    uri?.let {
        val stream = context.contentResolver.openInputStream(it)
        if (stream != null) viewModel.parseBackupFile(stream)
    }
}
```

**新增备份选项 Sheet**（放在现有备份/恢复结果 Sheet 之后）：

```kotlin
// 备份选项选择 Sheet
if (viewModel.showBackupOptionsSheet) {
    var options by viewModel.backupOptions
    ModalBottomSheet(onDismissRequest = { viewModel.closeBackupOptionsSheet() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.title_backup_select), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.msg_backup_select_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            CheckboxRow(
                checked = options.includeBlockedCalls,
                label = stringResource(R.string.label_backup_blocked_calls),
                onCheckedChange = { options = options.copy(includeBlockedCalls = it) }
            )
            CheckboxRow(
                checked = options.includeBlackList,
                label = stringResource(R.string.label_backup_blacklist),
                onCheckedChange = { options = options.copy(includeBlackList = it) }
            )
            CheckboxRow(
                checked = options.includeWhiteList,
                label = stringResource(R.string.label_backup_whitelist),
                onCheckedChange = { options = options.copy(includeWhiteList = it) }
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.closeBackupOptionsSheet() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = {
                        val date = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                        backupLauncher.launch("pixeltelo_backup_$date.zip")
                    },
                    enabled = options.includeBlockedCalls || options.includeBlackList || options.includeWhiteList,
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.action_backup)) }
            }
        }
    }
}

// 恢复选项选择 Sheet
val preview = viewModel.backupPreview
if (preview != null) {
    var options by viewModel.restoreOptions
    ModalBottomSheet(onDismissRequest = { viewModel.closeRestoreOptionsSheet() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(stringResource(R.string.title_restore_select), style = MaterialTheme.typography.titleLarge)
            CheckboxRow(
                checked = options.includeBlockedCalls,
                label = stringResource(R.string.label_restore_blocked_calls, preview.blockedCallCount),
                enabled = preview.blockedCallCount > 0,
                onCheckedChange = { options = options.copy(includeBlockedCalls = it) }
            )
            CheckboxRow(
                checked = options.includeBlackList,
                label = stringResource(R.string.label_restore_blacklist, preview.blackListCount),
                enabled = preview.blackListCount > 0,
                onCheckedChange = { options = options.copy(includeBlackList = it) }
            )
            CheckboxRow(
                checked = options.includeWhiteList,
                label = stringResource(R.string.label_restore_whitelist, preview.whiteListCount),
                enabled = preview.whiteListCount > 0,
                onCheckedChange = { options = options.copy(includeWhiteList = it) }
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.closeRestoreOptionsSheet() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = { viewModel.performRestoreWithOptions() },
                    modifier = Modifier.weight(1f)
                ) { Text(stringResource(R.string.action_restore)) }
            }
        }
    }
}
```

在 SettingsScreen 文件末尾添加辅助 Composable：
```kotlin
@Composable
private fun CheckboxRow(
    checked: Boolean,
    label: String,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
        androidx.compose.material3.Checkbox(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}
```

**Step 5: 编译验证**

```bash
./gradlew assembleDebug
```

**Step 6: Commit**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/data/dto/BackupData.kt
git add app/src/main/java/vip/mystery0/pixel/telo/data/repository/BackupRepository.kt
git add app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt
git add app/src/main/java/vip/mystery0/pixel/telo/ui/screen/SettingsScreen.kt
git add app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt
git commit -m "feat: backup/restore with per-section selection (blocked calls, blacklist, whitelist)"
```

---

## Task 10: 国际化字符串

**Files:**
- Modify: `app/src/main/res/values/strings.xml`（英文）
- Modify: `app/src/main/res/values-zh/strings.xml`（中文）

**Step 1: 在 values/strings.xml 末尾 `</resources>` 之前添加**

```xml
<!-- Navigation - List -->
<string name="nav_list">Lists</string>

<!-- List Screen -->
<string name="tab_blacklist">Blacklist</string>
<string name="tab_whitelist">Whitelist</string>
<string name="list_no_entries">No entries yet</string>
<string name="action_add_entry">Add Entry</string>
<string name="title_add_to_blacklist">Add to Blacklist</string>
<string name="title_add_to_whitelist">Add to Whitelist</string>
<string name="label_prefix_match">Prefix match</string>
<string name="summary_prefix_match">Match all numbers starting with this prefix</string>
<string name="hint_prefix_example">e.g. 400 matches all 400-xxx numbers</string>
<string name="hint_exact_example">e.g. 13800138000</string>
<string name="label_remark_optional">Remark (optional)</string>
<string name="action_confirm">Confirm</string>
<string name="error_phone_empty">Phone number cannot be empty</string>
<string name="error_phone_already_exists">This number already exists in the list</string>
<string name="msg_added_to_list">Entry added</string>

<!-- Home Screen - Quick Add -->
<string name="action_add_to_blacklist">Add to Blacklist</string>
<string name="action_add_to_whitelist">Add to Whitelist</string>
<string name="msg_added_to_blacklist">Added to blacklist</string>
<string name="msg_already_in_blacklist">Already in blacklist</string>
<string name="msg_added_to_whitelist">Added to whitelist</string>
<string name="msg_already_in_whitelist">Already in whitelist</string>

<!-- Home Screen - Result Types -->
<string name="result_black_list">Reason: User Blacklist</string>
<string name="result_white_list">Reason: User Whitelist (Allowed)</string>

<!-- Backup/Restore Selection -->
<string name="title_backup_select">Select Data to Backup</string>
<string name="msg_backup_select_hint">Choose which data to include in this backup</string>
<string name="label_backup_blocked_calls">Intercept Records</string>
<string name="label_backup_blacklist">Blacklist</string>
<string name="label_backup_whitelist">Whitelist</string>
<string name="action_backup">Backup</string>
<string name="title_restore_select">Select Data to Restore</string>
<string name="label_restore_blocked_calls">Intercept Records (%1$d entries)</string>
<string name="label_restore_blacklist">Blacklist (%1$d entries)</string>
<string name="label_restore_whitelist">Whitelist (%1$d entries)</string>
<string name="action_restore">Restore</string>
<string name="msg_restored_summary">Restored: %1$d intercept records, %2$d blacklist, %3$d whitelist entries</string>
```

**Step 2: 在 values-zh/strings.xml 末尾 `</resources>` 之前添加**

```xml
<!-- Navigation - List -->
<string name="nav_list">名单</string>

<!-- List Screen -->
<string name="tab_blacklist">黑名单</string>
<string name="tab_whitelist">白名单</string>
<string name="list_no_entries">暂无条目</string>
<string name="action_add_entry">添加条目</string>
<string name="title_add_to_blacklist">加入黑名单</string>
<string name="title_add_to_whitelist">加入白名单</string>
<string name="label_prefix_match">前缀匹配</string>
<string name="summary_prefix_match">匹配所有以该前缀开头的号码</string>
<string name="hint_prefix_example">如：400 可匹配所有 400 开头的号码</string>
<string name="hint_exact_example">如：13800138000</string>
<string name="label_remark_optional">备注（可选）</string>
<string name="action_confirm">确认</string>
<string name="error_phone_empty">号码不能为空</string>
<string name="error_phone_already_exists">该号码在名单中已存在</string>
<string name="msg_added_to_list">已添加</string>

<!-- Home Screen - Quick Add -->
<string name="action_add_to_blacklist">加入黑名单</string>
<string name="action_add_to_whitelist">加入白名单</string>
<string name="msg_added_to_blacklist">已加入黑名单</string>
<string name="msg_already_in_blacklist">该号码已在黑名单中</string>
<string name="msg_added_to_whitelist">已加入白名单</string>
<string name="msg_already_in_whitelist">该号码已在白名单中</string>

<!-- Home Screen - Result Types -->
<string name="result_black_list">原因：用户黑名单拦截</string>
<string name="result_white_list">原因：用户白名单放行</string>

<!-- Backup/Restore Selection -->
<string name="title_backup_select">选择备份内容</string>
<string name="msg_backup_select_hint">请选择需要包含在本次备份中的数据</string>
<string name="label_backup_blocked_calls">拦截记录</string>
<string name="label_backup_blacklist">黑名单</string>
<string name="label_backup_whitelist">白名单</string>
<string name="action_backup">备份</string>
<string name="title_restore_select">选择恢复内容</string>
<string name="label_restore_blocked_calls">拦截记录（共 %1$d 条）</string>
<string name="label_restore_blacklist">黑名单（共 %1$d 条）</string>
<string name="label_restore_whitelist">白名单（共 %1$d 条）</string>
<string name="action_restore">恢复</string>
<string name="msg_restored_summary">已恢复：%1$d 条拦截记录、%2$d 条黑名单、%3$d 条白名单</string>
```

**Step 3: 最终编译验证**

```bash
./gradlew assembleDebug
```

期望：BUILD SUCCESSFUL，无报错，无缺少字符串警告。

**Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml
git add app/src/main/res/values-zh/strings.xml
git commit -m "feat: add i18n strings for blacklist/whitelist feature"
```

---

## 验证清单

完成所有 Task 后，在真机或模拟器上手动验证：

- [ ] 首次安装（或清除数据）后，数据库正常升级，不崩溃
- [ ] 底部导航出现三个 Tab：拦截记录 / 名单 / 设置
- [ ] 名单页 Tab 切换：点击黑名单/白名单正常切换，左右滑动不触发切换
- [ ] 添加黑名单条目（精确 + 前缀），列表正确展示 `400*`
- [ ] 滑动删除条目后消失
- [ ] 来电模拟：白名单命中 → 直接放行，首页记录显示"用户白名单放行"
- [ ] 来电模拟：黑名单命中 → 直接拦截，首页记录显示"用户黑名单拦截"
- [ ] 首页记录长按 → 弹出快捷添加 Sheet，选择后 Toast 提示
- [ ] 备份：点击"备份"弹出选择 Sheet，只勾选"拦截记录"后备份，ZIP 解压验证 blackList/whiteList 为空数组
- [ ] 恢复：选择旧格式备份（version=1），黑白名单选项灰显
- [ ] 恢复：选择新格式备份，选部分恢复，去重正确
