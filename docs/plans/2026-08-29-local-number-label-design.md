# 持久化本地号码标签设计

## 1. 背景

Issue #9 希望允许用户为具体号码设置持久化本地标签，并在后续来电和历史记录中继续展示。
现有“结果准确/不准确”用于评价联网数据源质量，黑白名单用于决定拦截或放行，二者都不能承担
中性的本地身份备注职责。

当前 `BlockedCall.label` 保存某一次查询得到的数据源标签快照，`UserListEntry.remark` 则依附于
黑白名单行为规则。复用任一字段都会混淆展示数据与拦截决策，因此本功能建立独立的本地标签模型。

## 2. 已确认的产品决策

| 决策项 | 结论 |
| --- | --- |
| 数据模型 | 新增独立 `local_number_labels` Room 表 |
| 标签数量 | 每个归一化号码只能有一个本地标签 |
| 标签长度 | `trim()` 后最多 40 个字符 |
| 号码身份 | 使用 `PhoneNumberNormalizer.normalizeForLookup()` 结果作为唯一键 |
| 号码变体 | 普通号码、`+86` 和已支持的一卡多号形式共享标签 |
| 展示关系 | 本地标签与数据源标签独立，不互相覆盖 |
| Directory Provider | 两者都有时显示 `本地标签 · 数据源标签` |
| 查询行为 | 本地标签不替代、缩短或短路现有离线及联网查询 |
| 拦截行为 | 本地标签不改变拦截、放行、名单、反馈或查询结果语义 |
| 显示开关 | 新增统一开关，默认关闭；关闭只隐藏普通展示 |
| 历史记录 | 动态关联当前标签，不向 `BlockedCall` 复制本地标签 |
| 编辑入口 | 来电记录详情支持设置、修改和删除 |
| 管理入口 | 设置页提供统一查看、搜索、编辑和删除页面 |
| 首次创建 | 只能从已有来电记录创建，不支持任意输入号码新建 |
| 备份恢复 | 本地标签作为独立数据范围供用户选择 |
| 恢复冲突 | 同一归一化号码由备份标签覆盖当前标签 |
| 数据上传 | 本期不实现上传、上传状态、网络 API 或后台任务 |
| 最终验证设备 | Android 模拟器 |

## 3. 目标与非目标

### 3.1 目标

- 为有效号码设置、修改和删除一个持久化本地标签。
- 同一归一化号码的历史记录和未来来电动态展示当前标签。
- 在应用内明确区分联系人名称、本地标签和数据源标签。
- 在 Directory Provider 中组合本地标签和数据源标签，同时保持现有查询流程完整执行。
- 通过统一开关控制本地标签的普通展示，默认关闭。
- 提供统一标签管理、搜索、编辑和删除能力。
- 将本地标签作为独立范围纳入现有备份与恢复流程。
- 保持来电链路 Fail Open，本地标签异常不得影响识别、拦截或放行。

### 3.2 非目标

- 不实现标签上传、社区标记、审核、撤回或离线数据源分发。
- 不支持一个号码多个标签、颜色、分类或层级。
- 不允许在管理页任意输入号码创建首个标签。
- 不把本地标签转换为黑白名单规则。
- 不从数据源标签、联系人名称或名单备注自动生成本地标签。
- 不修改现有查询反馈协议。
- 不新增权限或第三方依赖。
- 不承诺模拟器验证结果覆盖所有 Google Phone 真机版本的缓存和展示差异。

## 4. 整体架构

### 4.1 方案选择

采用独立 `LocalNumberLabel` 表和 Repository。

拒绝以下方案：

- 扩展 `UserListEntry` 增加中性类型：会污染黑白名单行为规则模型，并使匹配、备份和管理逻辑充满特殊分支。
- 写入 `BlockedCall.label`：会覆盖数据源标签快照，修改时需要扫描历史记录，清空记录还会丢失标签。

### 4.2 组件职责

- `LocalNumberLabelDao`：负责 Room 精确查询、窗口批量观察、全量管理观察、Upsert、删除和备份快照。
- `LocalNumberLabelRepository`：作为本地标签规则的单一事实来源，统一处理号码归一化、标签校验、删除语义和恢复冲突。
- `SpamNumberRepository`：保持现有号码识别和拦截决策职责，不读取本地标签，也不修改 `CheckResult.label` 语义。
- `TeloDirectoryProvider`：完整执行现有识别查询，并在最终 Cursor 展示阶段组合本地标签和数据源标签。
- `TeloCallScreeningService`：继续依据 `CheckResult` 决策；仅在 Overlay 需要且显示开关开启时读取本地标签。
- `IncomingCallOverlay`：分别接收并展示本地标签和数据源标签，不提前合并为不可区分的字符串。
- `HomeViewModel`：仅为当前 Paging 已加载窗口观察本地标签映射，并驱动详情状态。
- 标签管理 ViewModel：观察全部标签，负责搜索、编辑、删除和操作反馈。
- `BackupRepository`：按用户选择导出或恢复本地标签数据范围。

### 4.3 数据流

```text
来电号码
  ├─ SpamNumberRepository.checkSpam()
  │    ├─ 黑白名单
  │    ├─ 离线号码库
  │    └─ 必要时完整执行联网查询
  │                ↓
  │       CheckResult / 拦截决策 / 数据源标签
  │
  └─ 显示开关开启且消费端需要时
       LocalNumberLabelRepository → 本地标签
                         ↓
          最终展示层组合，决策层保持隔离
```

本地标签查询可以与 `checkSpam()` 并行，但不得使现有查询提前结束，也不得改变联网超时、
`shouldBlock`、`ResultType`、反馈 Token 或查询耗时统计。

## 5. 数据模型与 Room 迁移

### 5.1 Entity

```kotlin
@Entity(tableName = "local_number_labels")
data class LocalNumberLabel(
    @PrimaryKey
    val normalizedPhoneNumber: String,
    val label: String,
    val createdAt: Long,
    val updatedAt: Long,
)
```

`normalizedPhoneNumber` 主键已提供精确索引，不增加重复号码索引。不保存原始号码；管理页按已确认规则
展示归一化号码，来电记录仍展示 `BlockedCall.phoneNumber` 中的原始号码。

### 5.2 Room 迁移

- `AppDatabase` 从 v9 升级到 v10。
- 新增 `MIGRATION_9_10`，创建 `local_number_labels` 表。
- 在 Koin 的数据库构建配置中注册迁移。
- 不修改旧 `blocked_calls` 和 `user_list` 数据。
- 升级后标签表为空，不执行自动推导或回填。

### 5.3 DAO 接口

DAO 至少提供以下能力：

```kotlin
@Query("SELECT * FROM local_number_labels WHERE normalizedPhoneNumber = :number LIMIT 1")
suspend fun findByNumber(number: String): LocalNumberLabel?

@Query("SELECT * FROM local_number_labels WHERE normalizedPhoneNumber IN (:numbers)")
fun observeByNumbers(numbers: Set<String>): Flow<List<LocalNumberLabel>>

@Query("SELECT * FROM local_number_labels ORDER BY updatedAt DESC")
fun observeAll(): Flow<List<LocalNumberLabel>>

@Query("SELECT * FROM local_number_labels ORDER BY updatedAt DESC")
suspend fun getAllSnapshot(): List<LocalNumberLabel>

@Upsert
suspend fun upsert(entity: LocalNumberLabel)

@Query("DELETE FROM local_number_labels WHERE normalizedPhoneNumber = :number")
suspend fun deleteByNumber(number: String): Int
```

批量恢复使用独立 `@Transaction` DAO 方法或 Repository 调用 `RoomDatabase.withTransaction`，避免标签数据
恢复到一半。

## 6. Repository 契约与校验

### 6.1 写入规则

`LocalNumberLabelRepository` 集中执行以下规则，UI、Provider 和备份恢复不得复制实现：

1. 通过 `PhoneNumberNormalizer.normalizeForLookup()` 生成唯一号码。
2. 归一化结果为空或无效时拒绝写入。
3. 标签执行 `trim()`。
4. 空标签视为删除请求。
5. 标签超过 40 个字符时拒绝，不自动截断。
6. 新建时 `createdAt`、`updatedAt` 均使用当前时间。
7. 修改时保留 `createdAt`，刷新 `updatedAt`。
8. 标签内容未改变时不执行无意义写入。

建议使用明确结果类型，避免 UI 依赖异常正文：

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
```

异常对象不得直接显示给用户，也不得在日志中输出号码或标签正文。

### 6.2 查询接口

```kotlin
suspend fun find(phoneNumber: String): LocalNumberLabel?

fun observeLabels(phoneNumbers: Set<String>): Flow<Map<String, String>>

fun observeAll(): Flow<List<LocalNumberLabel>>
```

`observeLabels()` 接收原始号码集合，Repository 内部完成归一化、去重和数据库查询，最后映射回原始号码。
调用方不应自行实现号码规则。

## 7. 显示开关

在现有 SharedPreferences 增加稳定键，例如：

```kotlin
const val KEY_SHOW_LOCAL_NUMBER_LABELS = "show_local_number_labels"
```

默认值固定为 `false`。

设置页“应用功能”区域增加：

```text
显示本地号码标签                 [开关]
在来电识别和记录中显示你设置的本地标签
```

开关控制：

- Directory Provider 的本地标签查询与展示；
- 来电 Overlay 的本地标签查询与展示；
- 首页记录列表的本地标签普通展示；
- 记录详情的本地标签身份展示。

开关不控制：

- 标签设置、修改和删除入口；
- 标签管理页；
- 标签数据存储；
- 备份与恢复；
- 未来可能设计的上传开关。

开关关闭时，普通展示链路不订阅或查询本地标签，避免无意义数据库开销；编辑区域仍可按单号码读取现有标签。

## 8. Directory Provider 行为

### 8.1 查询语义

本地标签只是展示增强，不替代联网识别。`TeloDirectoryProvider` 必须完整执行现有
`SpamNumberRepository.checkSpam()`，包括必要的联网查询和原有 1 至 10 秒超时。

显示开关开启时，本地标签查询可与现有识别并行执行；最终等待原识别流程完成后再生成 Cursor。
本地标签查询失败按无标签降级，不能取消或覆盖识别结果。

### 8.2 返回条件

```text
现有识别结果应返回 Directory 记录
或
显示开关开启且存在本地标签
    → 返回记录
否则
    → 返回空 Cursor
```

现有 `shouldFilter` 仍负责数据源识别结果是否应形成 Directory 记录，但本地标签允许安全号码获得一个
仅用于身份展示的 Directory 结果。该行为不得回写 `CheckResult.shouldBlock`。

### 8.3 显示格式

| 本地标签 | 数据源标签 | Directory 显示名称 |
| --- | --- | --- |
| 物业 | 快递送餐 | `物业 · 快递送餐` |
| 物业 | 无 | `物业` |
| 无 | 快递送餐 | `快递送餐` |
| 无 | 无 | 不返回记录 |

组合逻辑封装为纯展示模型或格式化函数：

```kotlin
data class NumberLabelPresentation(
    val localLabel: String?,
    val sourceLabel: String?,
)

fun NumberLabelPresentation.directoryDisplayName(): String?
```

系统联系人是否覆盖 Directory Provider 结果由拨号器决定。Pixel Telo 不查询联系人姓名参与组合，
也不尝试覆盖系统联系人。

## 9. CallScreeningService 与 Overlay

- `SpamNumberRepository.checkSpam()` 和 `CheckResult` 保持原有语义。
- `TeloCallScreeningService` 只依据 `CheckResult` 和现有设置决定拦截、静音或放行。
- `BlockedCall.label` 继续保存本次查询的数据源标签快照。
- 本地标签不写入 `BlockedCall`，也不进入重复来电说明、反馈通知或查询耗时字段。
- 仅当显示开关和 Overlay 均开启时，Service 才为 Overlay 查询本地标签。
- 本地标签查询可与现有识别并行，但失败或超时只隐藏本地标签。
- `IncomingCallOverlay` 分别接收 `localLabel`、`sourceLabel`，使用不同文本层级展示。
- Overlay 的数据源标签展示和位置、时长、样式等现有设置保持不变。

## 10. 历史记录动态展示

本地标签不复制到历史记录。首页使用当前 Paging 已加载窗口中的去重号码集合订阅：

```text
Paging 当前窗口号码
  → distinctUntilChanged
  → LocalNumberLabelRepository.observeLabels()
  → Map<原始号码, 本地标签>
  → Compose 渲染
```

当前 Paging 配置最多保留约 90 条记录，因此批量查询规模有明确上限。标签设置、修改、删除或恢复后，
Room Invalidation 自动刷新当前窗口，不扫描和更新整张 `blocked_calls` 表。

展示开关关闭时，不建立该窗口订阅；详情中的管理区域仍按当前号码单独读取标签。

应用内信息层级保持独立：

```text
联系人：张三
本地标签：物业
识别结果：快递送餐
归属地：上海
```

空字段对应行隐藏。联系人名称、本地标签、数据源标签不得互相覆盖。

## 11. 编辑与统一管理 UI

### 11.1 来电记录详情

详情 BottomSheet 增加独立“本地标签”管理区域。

无标签：

```text
本地标签
尚未设置
[设置标签]
```

已有标签：

```text
本地标签
物业
[修改标签] [删除]
```

即使显示开关关闭，该管理区域仍可用。

编辑 Dialog：

- 展示当前记录原始号码；
- 修改时预填现有标签；
- 输入限制和错误提示使用本地化字符串；
- 保存时由 Repository 执行最终校验；
- 校验或写入失败时保持 Dialog 打开；
- 删除使用独立操作并二次确认；
- 成功后关闭 Dialog，详情、历史列表和管理页通过 Room Flow 自动刷新。

### 11.2 设置入口

设置页“应用功能”区域增加：

```text
本地号码标签
管理已保存的号码标签
```

入口始终可用，不受显示开关影响。

### 11.3 标签管理页

新增非底部导航目的地，从设置页进入：

- TopAppBar 标题为“本地号码标签”；
- 搜索同时匹配归一化号码和标签，忽略英文字母大小写；
- 默认按 `updatedAt DESC` 排序；
- 主文本显示标签，次文本显示归一化号码；
- 每项支持编辑和删除；
- 空状态提示用户从来电记录详情设置标签；
- 搜索无结果显示独立空状态；
- 不提供新增按钮、悬浮按钮或号码输入框。

标签数量为空或搜索结果为空时，不隐藏搜索框之外的页面导航结构。

## 12. 备份与恢复

### 12.1 格式升级

`BackupData.version` 从 4 升级到 5，新增：

```kotlin
@SerialName("local_number_labels")
val localNumberLabels: List<LocalNumberLabelDto> = emptyList()
```

DTO：

```kotlin
@Serializable
data class LocalNumberLabelDto(
    @SerialName("phone_number") val phoneNumber: String,
    val label: String,
    @SerialName("created_at") val createdAt: Long,
    @SerialName("updated_at") val updatedAt: Long,
)
```

### 12.2 独立备份范围

`BackupOptions` 增加：

```kotlin
val includeLocalNumberLabels: Boolean = true
```

备份 BottomSheet 增加默认勾选的“本地号码标签”。用户可以：

- 与其他数据一起备份；
- 取消本地标签；
- 只备份本地标签。

至少选择一个数据范围才能执行备份。显示开关不进入备份。

### 12.3 恢复预览与选择

`BackupPreview` 增加标签数量。恢复预览增加：

```text
本地号码标签（共 N 条）
```

- v1 至 v4 缺少该字段，按空列表处理；
- 数量为 0 时该范围默认不选中并禁用；
- 数量大于 0 时默认选中；
- 用户可以只恢复标签或明确排除标签；
- 未选择标签范围时不得读取、校验或修改当前标签数据。

### 12.4 恢复规则

选择恢复标签后，每条数据重新通过 Repository 规则：

1. 重新归一化号码；
2. 无效号码跳过；
3. 标签 `trim()`；
4. 空标签或超过 40 字符时跳过；
5. 备份内同一归一化号码重复时，最后一条有效记录生效；
6. 当前不存在则插入；
7. 当前存在则由备份标签和时间戳覆盖；
8. 备份中未出现的当前标签保持不变；
9. 非正时间戳使用恢复时当前时间修正。

标签批量恢复在 Room Transaction 中完成。拦截记录和黑白名单继续沿用现有恢复逻辑，不扩大为整个 ZIP
跨数据类型原子事务。

恢复结果分别报告新增、覆盖和跳过数量。

## 13. 性能与一致性

- 主键精确查询不增加额外号码索引。
- 显示开关关闭时，普通展示链路不访问本地标签表。
- Directory Provider 单号码查询与原识别并行，不缩短也不额外串行延长现有联网流程。
- Overlay 只在确实需要展示时查询。
- 首页批量查询仅覆盖当前 Paging 窗口，输入先归一化和去重。
- 管理页使用 Room Flow 观察按更新时间排序的全部标签。
- 不为 Directory Provider 建立长期内存缓存，避免标签修改后应用继续返回旧值。
- 本地标签查询超过 100ms 记录英文 warning，日志不得包含号码或标签正文。
- Room Invalidation 是应用进程内标签一致性的来源，不额外维护容易失效的全局标签缓存。
- 系统拨号器自身缓存不受应用完全控制，模拟器验收时验证可观察到的刷新行为并记录限制。

## 14. 异常处理与隐私

- 无效号码：拒绝创建并显示本地化提示。
- 标签过长：输入区域显示错误，不自动截断。
- 标签读取失败：按无本地标签降级，不影响识别、拦截或放行。
- 标签写入或删除失败：保留当前 UI 状态，提示稍后重试。
- 单条备份标签非法：跳过并计入恢复结果。
- ZIP 或 JSON 整体无法解析：沿用现有恢复失败流程，不写入数据。
- 本地标签不得进入查询 API、反馈 API、通知反馈正文或日志。
- 不新增权限。
- 标签仅保存在 `AppDatabase` 和用户主动生成的备份文件中。
- 清空拦截记录不得删除本地标签。
- 清除应用数据或卸载应用会删除标签，符合 Android 应用数据语义。

## 15. 依赖注入与文件边界

预计新增：

- `data/entity/LocalNumberLabel.kt`
- `data/dao/LocalNumberLabelDao.kt`
- `data/repository/LocalNumberLabelRepository.kt`
- 本地标签管理页面及对应 ViewModel 文件
- 可复用的本地标签编辑 Dialog 或小型管理组件

预计修改：

- `data/AppDatabase.kt`
- `di/AppModule.kt`
- `provider/TeloDirectoryProvider.kt`
- `service/TeloCallScreeningService.kt`
- `service/IncomingCallOverlay.kt`
- `viewmodel/HomeViewModel.kt`
- `ui/screen/HomeScreen.kt`
- 设置页导航与应用功能设置组件
- `data/dto/BackupData.kt`
- `data/repository/BackupRepository.kt`
- `viewmodel/SettingViewModel.kt`
- 备份/恢复 BottomSheet 与中英文字符串资源
- `.agentdocs/architecture/mvvm-structure.md`
- `.agentdocs/architecture/native-integration.md`
- `.agentdocs/ui/main-screen.md`
- `.agentdocs/index.md`

实现时应避免继续扩大已经较大的 `HomeScreen.kt` 和 `SettingViewModel.kt`。新增的标签管理页面、编辑 Dialog
和标签业务状态应放入独立文件；现有页面只负责组合和导航。

## 16. 验证方案

项目约定默认不新增、不运行单元测试，本功能遵循该约束。

### 16.1 静态验证

```bash
./gradlew :app:assembleDebug
./gradlew lint
git diff --check
```

不执行 `test`、`testDebugUnitTest`、`check` 或其他单元测试任务。

### 16.2 Android 模拟器验证

1. 保留 v9 数据库安装新版，验证 v9 → v10 迁移，旧记录和黑白名单不丢失。
2. 验证首次安装和升级后的显示开关均默认为关闭。
3. 从来电记录详情设置、修改、删除标签，确认所有同号码历史记录动态刷新。
4. 验证普通号码、`+86` 和已支持的一卡多号形式共享同一个标签。
5. 关闭显示开关后，列表、普通详情展示、Directory Provider 和 Overlay 隐藏本地标签，但编辑入口仍可使用。
6. 开启显示开关后，应用内分别展示联系人名称、本地标签和数据源标签。
7. 验证 Directory Provider 仍完整执行现有识别流程，并覆盖四种标签组合返回条件。
8. 验证仅有本地标签的安全号码可返回 Directory 身份信息，但不会改变拦截结果。
9. 在模拟器联系人中建立同号码联系人，验证拨号器可观察到的联系人优先行为。
10. 验证 Overlay 分层展示本地标签和数据源标签。
11. 验证本地标签不改变黑白名单、重复来电、反馈 Token、ResultType 和查询耗时。
12. 验证标签管理页搜索、编辑、删除、空状态和搜索无结果状态。
13. 清空全部拦截记录，确认本地标签仍存在。
14. 分别创建包含和不包含本地标签的 v5 备份，检查备份预览和选项。
15. 恢复 v1 至 v4 备份，确认不改变已有本地标签。
16. 恢复 v5 备份时单独选择标签，验证新增、覆盖、跳过统计。
17. 验证显示开关不随数据备份迁移，恢复后保留当前设备设置。
18. 修改标签后重新触发模拟器拨号器查询，记录 Directory Provider 缓存刷新表现。

模拟器能够验证 Provider 返回、应用内状态、数据库迁移和可观察到的拨号器行为，但不能代表全部 Google
Phone 真机版本。若模拟器拨号器不支持或缓存 Directory Provider，应记录环境限制，不将其误判为标签
数据层或拦截决策失败。

## 17. 验收标准

- 本地标签由独立 Room 表持久化，号码主键遵循统一归一化规则。
- 每个号码最多一个不超过 40 字符的标签。
- 显示开关默认关闭，关闭只隐藏普通展示，不影响编辑、存储和备份恢复。
- 本地标签不进入或覆盖 `CheckResult.label`、`BlockedCall.label`、名单规则和反馈协议。
- Directory Provider 完整执行原查询流程，并按规则组合本地标签与数据源标签。
- 本地标签异常不会影响来电拦截、放行或 Fail Open 行为。
- 所有历史记录动态展示当前标签，不批量回写 `blocked_calls`。
- 设置页提供统一管理、搜索、编辑和删除，不允许任意号码首次创建。
- 本地标签是可独立选择的备份与恢复范围，冲突时备份覆盖当前同号码标签。
- 旧数据库和旧备份保持兼容。
- 不新增权限、网络 API、后台任务、上传状态或第三方依赖。
- Debug 构建、Lint 和 `git diff --check` 通过。
- 在 Android 模拟器完成约定功能验证并记录 Directory Provider 环境限制。
