# 用户自定义黑白名单功能设计文档

**日期**: 2026-03-07
**状态**: 已批准，待实现

---

## 功能概述

为 PixelTelo 添加用户自定义黑白名单功能，允许用户手动指定号码或号码前缀的放行/拦截规则，优先级高于骚扰库与联网查询。

---

## 一、数据层

### 1.1 新实体：`UserListEntry`

存入现有 `AppDatabase`，数据库版本从 **1 升至 2**，需编写 Room Migration。

```kotlin
@Entity(
    tableName = "user_list",
    indices = [Index(value = ["phoneNumber", "listType"], unique = true)]
)
data class UserListEntry(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNumber: String,   // 具体号码 或 前缀（如 "400"）
    val isPrefix: Boolean,     // true = 前缀匹配；false = 精确匹配
    val listType: ListType,    // BLACK 或 WHITE
    val remark: String?,       // 可选备注
    val addedAt: Long,         // 添加时间戳
)

enum class ListType { BLACK, WHITE }
```

### 1.2 新 Dao：`UserListDao`

```kotlin
@Dao
interface UserListDao {
    @Query("SELECT * FROM user_list WHERE listType = :type ORDER BY addedAt DESC")
    fun observeByType(type: ListType): Flow<List<UserListEntry>>

    @Query("SELECT * FROM user_list WHERE listType = :type")
    suspend fun getAllByType(type: ListType): List<UserListEntry>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entry: UserListEntry): Long

    @Delete
    suspend fun delete(entry: UserListEntry)
}
```

### 1.3 AppDatabase 升级

- 版本：1 → 2
- 新增 `user_list` 表
- 编写 `MIGRATION_1_2`

### 1.4 新 Repository：`UserListRepository`

提供：
- `observeBlackList()` / `observeWhiteList()`：Flow，供 UI 展示
- `addEntry(phoneNumber, isPrefix, listType, remark)`：添加条目
- `deleteEntry(entry)`：删除条目
- `checkBlackList(phone)` / `checkWhiteList(phone)`：用于拦截时查询，支持精确匹配和前缀匹配
- `getAllByType(type)`：备份用

---

## 二、拦截逻辑修改

### 2.1 ResultType 新增枚举值

```kotlin
enum class ResultType {
    INTERCEPT,        // 骚扰库/联网判定拦截
    PASS_BUT_NOTIFY,  // 提示但不拦截
    NETWORK_TIMEOUT,  // 联网查询超时
    PASS,             // 正常放行
    BLACK_LIST,       // 用户黑名单拦截（新增）
    WHITE_LIST,       // 用户白名单放行（新增）
}
```

### 2.2 SpamNumberRepository.checkSpam() 新执行顺序

```
1. 查用户白名单（精确 + 前缀）
   → 命中 → 返回 CheckResult(shouldBlock=false, resultType=WHITE_LIST)

2. 查用户黑名单（精确 + 前缀）
   → 命中 → 返回 CheckResult(shouldBlock=true, resultType=BLACK_LIST)

3. 查本地骚扰库（原有逻辑）

4. 联网查询（原有逻辑）
```

白名单查询必须在黑名单之前，以防同一号码同时出现时以白名单为准（或在添加时做互斥校验）。

### 2.3 号码匹配规则

```kotlin
// 精确匹配
entry.phoneNumber == phone && !entry.isPrefix

// 前缀匹配
phone.startsWith(entry.phoneNumber) && entry.isPrefix
```

---

## 三、导航结构

底部导航从 2 个 Tab 扩展为 **3 个**：

| 位置 | Tab | 图标 | 页面 |
|------|-----|------|------|
| 左   | 首页 | Home | HomeScreen |
| 中   | 名单 | Rule | ListScreen（新增） |
| 右   | 设置 | Settings | SettingsScreen |

`AppDestinations` 枚举新增 `LIST` 项。

---

## 四、名单页面（ListScreen）

### 4.1 整体结构

```
┌─────────────────────────────┐
│  [  黑名单  ] [  白名单  ]   │  ← TabRow（点击切换，无左右滑动）
├─────────────────────────────┤
│  13800138000  骚扰电话  黑   │
│  400*         营销前缀  黑   │
│  ...                        │
│                             │
│         暂无条目             │  ← 空态提示
│                             │
│                        [+]  │  ← FAB（右下角）
└─────────────────────────────┘
```

### 4.2 Tab 切换

- 使用 `TabRow` + `HorizontalPager`，但设置 `userScrollEnabled = false`，禁用左右滑动翻页，防止与底部导航的手势冲突。
- 用户仅通过点击 Tab 切换。

### 4.3 列表条目

每条 `UserListEntry` 显示：
- 号码（若 `isPrefix=true` 显示为 `400*`）
- 备注（若有）
- 添加时间

支持 `SwipeToDeleteContainer`（复用现有组件）滑动删除。

### 4.4 添加条目（FAB → BottomSheet）

点击 FAB 弹出 `ModalBottomSheet`，包含：
- 号码输入框（`OutlinedTextField`）
- "前缀匹配" `Switch`（开启后输入框 hint 变为"如：400 匹配所有400开头"）
- 备注输入框（可选）
- 取消 / 确认按钮
- `imePadding` 处理键盘弹出

确认时校验：号码不能为空；若与同名单中已有条目重复，提示"已存在"；若与对侧名单中存在相同号码，提示"该号码已在[黑/白]名单中"。

### 4.5 ViewModel：`ListViewModel`

状态：
```kotlin
val blackList: StateFlow<List<UserListEntry>>
val whiteList: StateFlow<List<UserListEntry>>
var showAddSheet: Boolean
var currentTab: ListType   // BLACK or WHITE
```

---

## 五、首页快捷添加

在 `BlockedCallItem` 上，通过长按弹出 `ModalBottomSheet`，提供：
- "加入黑名单"
- "加入白名单"

选择后，若号码已在名单中则 Toast 提示"已在[黑/白]名单中"；否则直接写入并 Toast 提示"已添加"，无需二次确认。

---

## 六、备份/恢复集成

### 6.1 数据结构更新

```kotlin
@Serializable
data class BackupData(
    val version: Int = 2,
    val blockedCalls: List<BackupBlockedCall> = emptyList(),
    val blackList: List<BackupUserListEntry> = emptyList(),   // 新增
    val whiteList: List<BackupUserListEntry> = emptyList(),   // 新增
)

@Serializable
data class BackupUserListEntry(
    val phoneNumber: String,
    val isPrefix: Boolean,
    val remark: String?,
    val addedAt: Long,
)
```

### 6.2 备份流程（新增选择步骤）

1. 用户点击"备份"
2. 弹出 `ModalBottomSheet`，显示三个复选框（默认全选）：
   - ☑ 拦截记录
   - ☑ 黑名单
   - ☑ 白名单
3. 用户确认后，打开系统文件保存对话框（`CreateDocument`）
4. 仅将选中部分写入 ZIP

### 6.3 恢复流程（新增选择步骤）

1. 用户点击"恢复"
2. 打开系统文件选择器（`OpenDocument`），选择备份 ZIP
3. 解析文件后，弹出 `ModalBottomSheet` 显示文件信息及可选部分：
   - ☑ 拦截记录（共 N 条）
   - ☑ 黑名单（共 N 条）— 若备份文件无此数据则灰显
   - ☑ 白名单（共 N 条）— 若备份文件无此数据则灰显
4. 用户确认后执行导入

### 6.4 去重策略

- `BlockedCall`：按 `(phoneNumber, blockTime)` 去重（沿用现有）
- `UserListEntry`：按 `(phoneNumber, listType)` 去重，跳过已存在的条目

---

## 七、首页拦截记录展示

`ResultType.BLACK_LIST` 和 `WHITE_LIST` 需要在 `BlockedCallItem` 中新增对应的文案和颜色：

| ResultType | 文案 | 颜色 |
|-----------|------|------|
| `BLACK_LIST` | 黑名单拦截 | `colorScheme.error` |
| `WHITE_LIST` | 白名单放行 | `colorScheme.tertiary` |

---

## 八、国际化

所有新增字符串需同步添加到 `values/strings.xml`（英文）和 `values-zh-rCN/strings.xml`（中文）。

---

## 验证清单

- [ ] 白名单号码来电：直接放行，跳过骚扰库和联网
- [ ] 黑名单号码来电：直接拦截，跳过骚扰库和联网
- [ ] 前缀匹配：400 开头的号码被前缀规则正确命中
- [ ] 同号码在黑白名单都存在时：白名单优先（或添加时互斥校验）
- [ ] 名单页 Tab 切换：点击切换正常，左右滑动不触发页面切换
- [ ] 滑动删除：条目删除后下次来电不再命中
- [ ] 快捷添加：从首页拦截记录一键加入名单
- [ ] 备份：选择部分备份后，ZIP 内容正确
- [ ] 恢复：旧版 backup（无黑白名单）：黑白名单选项灰显
- [ ] 恢复：选择部分恢复，去重逻辑正确
