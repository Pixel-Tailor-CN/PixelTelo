# 社区骚扰上报与本机上报管理：App 实施计划

> **For agentic workers:** 实施时按当前会话的执行授权选择 `executing-plans`；仅在用户明确选择且工具可用时使用协作执行器。按复选框逐任务推进。本文不是自动执行、提交或发布授权。

**Goal:** 实现 CR-1 官方骚扰上报、本安装历史管理及独立社区提示／可选拦截，不改变原有查询与名单边界。

**Architecture:** 沿用 Compose → ViewModel → Repository。官方管理 API 与活动查询 Backend 分离；设备 ID 存放于不可备份目录，Room 仅保存来电快照及 Token 用途状态，云端列表是管理事实来源。社区查询信息在 Backend 可信边界解析并隔离，经既有查询链路传递，不伪装为 Provider 标签。

**Tech Stack:** Kotlin、Jetpack Compose / Material3、Coroutines / StateFlow、Koin、Room、Retrofit / OkHttp、kotlinx.serialization；JDK 21，沿用当前依赖，不新增 SDK 或权限。

**Spec:** [App 设计方案](2026-10-15-community-reports-app-design.md)。

状态：用户已授权跳过计划审阅直接实施。任务 1–7 的客户端代码已完成，Debug 构建与 Lint 通过；任务 8 的设备／服务端联调尚未完成。已勾选代码项表示实现及静态核对，不代表对应设备场景已验证；混合条目保持未勾选时，其构建部分已通过，剩余工作见末尾验收记录。

## 实施基线与本次对齐

- App 代码读取基线为 `57707c5`；当前 AppDatabase v11、手动备份 v6。设计文档与索引已有未提交变更，必须保留。
- 已只读核对服务端最新设计及实施计划，CR-1 路由、字段、错误码不变。本次读取尚未发现对应产品实现；服务端独立推进，不修改其仓库，不把计划当作已部署证据。
- 号码按 Token 绑定的原字符串处理；正常来电查询仍使用原有 `PhoneNumberNormalizer`，上报阶段不重新写号、不发起补查，列表不按本地号码归一化合并。
- 限流及能力禁用按具体操作处理，不因上报失败禁用管理页。不增加申诉／管理入口或对人工处置能力作承诺。
- 用户新增确认：系统备份／设备迁移排除运行时数据库；社区历史展示快照加入手动备份。计划使用 Room v12、备份 v7，实施前若版本已占用，应整体顺延并同步所有引用。
- 只安排构建、Lint、静态核对和受控设备／协议联调；遵守项目默认不新增、不维护、不运行单元测试的要求。

## Global Constraints

- 提交需要有效官方 `feedback_token` 与当前安装 `device_id`；查询／撤回／删除只使用设备 ID，无独立设备凭证或请求签名。
- 设备 ID 为安全随机 32 字节、无 padding base64url（43 字符），不使用硬件 ID；不可备份、迁移、导出或恢复。
- 同安装同号码最多贡献一票；不在客户端计票或硬编码公共生效阈值。
- 只有已有来电记录可发起上报，无任意号码上报入口；提交不自动加入黑名单、不自动重新查询、不自动补领 Token。
- 原质量反馈状态与上报状态独立；撤回／删除不依赖原 Token、不撤销其他设备的贡献。
- 有效记录先撤回，再删除历史；已撤回历史保留至用户删除；重新上报必须提供可用 Token。
- 设置页和首页操作菜单共用“我的上报”二级页；不新增底部导航、不引入 Navigation Compose。
- 官方管理请求不读取活动 Backend 决定目标，不发送自建 URL、Token 或 Pin，不改变官方同步链路。
- 通讯录、号码名单、国际号码门禁、本地优先及既有标签／归属地规则保留；网络失败放行。
- 社区默认只提示；开关 `block_community_reports` 默认 false，不能设置 `forceBlock`，不能绕过全局仅提示或重复来电策略。
- 本地查询目标 50ms，超过 100ms warning，硬上限 3 秒；网络 1–10 秒、默认 5 秒，不借本任务改上限。
- 社区响应是可选增强，格式异常不使原本有效的 Provider 结果整体失败；自建同名字段不可信。
- 现有查询复用窗口最多 60 秒；旧历史不能成为实时拦截依据，本机撤回应失效缓存并阻止迟到结果回灌。
- 公开文档不包含私有仓库地址、内部结构、具体票数阈值或风控参数；日志不得包含号码、设备 ID、Token、请求／响应正文或不可信异常消息。
- 中文文档、注释和 KDoc，英文日志；中英文 UI 资源齐全。不新增权限、不无关升级依赖。
- 默认不创建 `src/test`，不运行 `test`、`testDebugUnitTest` 或包含单元测试的 `check`。Windows 构建使用仓库根目录的 `.\gradlew.bat`。
- 无另行授权不卸载应用、不清数据、不修改正式服务器数据、不提交、不推送、不发布。

## Review Focus

1. 系统来电原始号码与官方查询号码不同，或多个云端记录在本地归一化后相同：使用 Token 号码快照核对，列表按 report_id 区分（任务 1、2、4、6）。
2. 质量反馈完成、重查替换 Token、详情切换／关闭和上报完成交叉：只更新对应 Token 的用途状态，不覆盖新凭据或另一条记录 UI（任务 2、5）。
3. 撤回成功时仍有共享查询、Direct/force 路径或 Provider 回查在途：清缓存之外还检查代次，不能用旧社区结果拦截（任务 3、4、7）。
4. 刷新／加载更多与撤回／删除并发，或服务端已提交但响应丢失：不复活行、不重复扣票、不把未知结果显示为确定失败（任务 4、6）。
5. 设备 ID 损坏、旧版本数据库升级、系统迁移及手动恢复交叉：不静默换身份、不恢复认证权，历史社区快照只供展示（任务 2、4、8）。

这些重点的验证步骤归入对应任务，采用受控协议样本及设备场景，不以未执行的检查宣称通过。

## 文件与职责地图

下列 Kotlin 路径的共同前缀是 `app/src/main/java/vip/mystery0/pixel/telo/`；所有条目组合该前缀即为完整仓库相对路径。文件以职责划分，不为行数进行无关重构。

| 范围 | 新建／修改路径 | 职责 |
| --- | --- | --- |
| 协议 | 新建 `data/remote/OfficialCommunityReportApi.kt`, `data/remote/CommunityReportDtos.kt`；修改 `data/remote/QueryApi.kt` | 四个 POST 与可选查询字段 |
| 模型与容错 | 新建 `data/model/CommunityModels.kt`, `data/query/CommunityQueryMetadata.kt`；修改 `data/query/QueryBackend.kt` | 稳定状态、时间解析、官方可信边界 |
| DI | 修改 `di/AppModule.kt` | 官方管理 Client/API、Repository、身份及状态单例 |
| 持久化 | 修改 `data/AppDatabase.kt`, `data/entity/BlockedCall.kt`, `data/dao/BlockedCallDao.kt`, `data/repository/BlockedCallRepository.kt` | v12、独立用途状态、条件更新 |
| 备份 | 修改 `data/dto/BackupData.kt`, `data/repository/BackupRepository.kt` | v7 历史快照及旧格式兼容 |
| 身份 | 新建 `data/ReportDeviceIdentityStore.kt` | AtomicFile、互斥、不可备份随机身份 |
| 社区查询 | 新建 `data/query/CommunityQueryEpoch.kt`, `data/preferences/CommunityReportPreferences.kt`；修改 `data/repository/QueryRepository.kt`, `data/repository/SpamNumberRepository.kt` | 代次、原始结果复用、社区判定 |
| 变更失效 | 新建 `data/repository/CommunityResultInvalidator.kt` | 官方缓存失效与系统展示刷新通知 |
| 上报管理 | 新建 `data/repository/CommunityReportRepository.kt`, `data/repository/CommunityReportFailure.kt` | 提交／管理、输入输出校验、错误与冷却 |
| 记录上报 UI | 新建 `viewmodel/CallReportViewModel.kt`, `ui/components/CommunityReportSection.kt`；修改 `viewmodel/HomeViewModel.kt`, `ui/screen/HomeScreen.kt` | 独立上报状态与安全详情交互 |
| 管理页 | 新建 `viewmodel/MyReportsViewModel.kt`, `ui/screen/MyReportsScreen.kt`；修改 `MainActivity.kt`, `ui/screen/SettingsScreen.kt` | 双入口、分页、撤回和删除 |
| 设置 | 修改 `viewmodel/SettingViewModel.kt`, `ui/screen/settings/OnlineQueryPreferences.kt`, `ui/screen/settings/BackupRestorePreferences.kt` | 社区拦截设置、备份说明 |
| 系统展示 | 修改 `service/TeloCallScreeningService.kt`, `provider/TeloDirectoryProvider.kt`, `service/IncomingCallOverlay.kt`, `service/IncomingCallOverlayFormatter.kt`, `data/model/NumberLabelPresentation.kt` | 历史快照、独立提示及默认只提示 |
| 资源 | 修改 `app/src/main/res/values/strings.xml`, `app/src/main/res/values-zh/strings.xml`, `app/src/main/res/xml/backup_rules.xml`, `app/src/main/res/xml/data_extraction_rules.xml` | 文案和备份排除 |
| 长期文档 | 修改 `README.md`, `docs/prd/requirements.md`, `docs/architecture/mvvm-structure.md`, `docs/architecture/native-integration.md`, `docs/ui/main-screen.md`, `docs/README.md` | 仅实现完成后更新系统事实 |

`AndroidManifest.xml` 已引用两份备份规则且保留 `allowBackup=true`，本任务只核对配置，不默认增加权限或改为全量禁用备份。`receiver/FeedbackActionReceiver.kt`、`receiver/QueryFeedbackNotifier.kt` 和 Smartspacer 是回归核对入口，不为社区功能扩展原质量反馈通知。

## 共享接口与状态约定

由各所属任务实现，下游不得自行另定义同义类型。

- `CommunityStatus`：`UNKNOWN`, `ACTIVE`, `INACTIVE`, `UNAVAILABLE`。`UNKNOWN` 表示没有提供该信息；持久化与备份使用小写稳定 codec，不依赖 enum.name。
- `CommunityReportStatus`：`ACTIVE`, `WITHDRAWN`, `UNKNOWN`；未知状态禁用撤回／删除，仍可刷新。
- `CommunityReport(reportId: String, phone: String, status: CommunityReportStatus, reportedAt: Instant, withdrawnAt: Instant?, communityStatus: CommunityStatus)`。
- `CommunityReportPage(items: List<CommunityReport>, nextCursor: String?)`；`CommunitySubmission(report: CommunityReport, created: Boolean)`。
- `ReportResult<out T>`：`Success<T>(value: T)` / `Failure(reason: ReportFailure)`；`ReportFailure(kind: ReportFailureKind, retryAfterMillis: Long? = null, mayHaveCommitted: Boolean = false)`，不携带原始响应或 Throwable。
- `ReportFailureKind`：`INVALID_REQUEST`, `INVALID_TOKEN`, `EXPIRED_TOKEN`, `USED_TOKEN`, `NOT_WITHDRAWN`, `NOT_FOUND`, `RATE_LIMITED`, `UNAVAILABLE`, `UNSUPPORTED`, `NETWORK`, `INVALID_RESPONSE`, `IDENTITY_UNAVAILABLE`, `LOCAL_STATE`, `INTERNAL`。Coroutine cancellation 始终重抛，不包装成失败。
- `ReportTokenState`：`UNKNOWN`, `ACCEPTED`, `USED`, `EXPIRED`, `INVALID`，只描述当前本地 Token 的上报用途，不是云端记录状态；原 `FeedbackStatus` 完全保留。`BlockedCall` 的 `communityStatus`、`reportTokenState` 使用 String 列；模型文件统一提供两种枚举的 `toStorageValue(): String` 与 `parseCommunityStatus(value: String?): CommunityStatus` / `parseReportTokenState(value: String?): ReportTokenState`，未知字符串映射 UNKNOWN，避免散落 enum.valueOf。
- `CommunityQueryMetadata(status: CommunityStatus, feedbackPhone: String?, feedbackTokenExpiresAt: Long?)`；到期时间为 epoch milliseconds。安全解析函数 `decodeCommunityQueryMetadata(response: QueryResponse, official: Boolean): CommunityQueryMetadata`。
- `CommunityQueryEpoch.current(): Long` / `advance(): Long`，进程级 `AtomicLong`；`BackendQueryResponse` 记录请求开始的 `communityEpoch: Long` 与成功接收时间 `receivedAtElapsedMs: Long`，复用不刷新时间。
- `CommunityReportPreferences.blockEnabled: StateFlow<Boolean>` / `setBlockEnabled(enabled: Boolean): Unit`；单一键 `block_community_reports`。
- `ReportDeviceIdentityStore.getOrCreate(): ReportDeviceIdentity` / `resetAfterConfirmation(): ReportDeviceIdentity` 为 suspend；`ReportDeviceIdentity` 仅内部使用，不以默认 toString 暴露 ID。身份重建与请求由 Repository 互斥隔离，页面另以代次隔离发布；不向公共协议添加内部代次。
- `CommunityReportRepository.submit(callId: Long): ReportResult<CommunitySubmission>`、`list(cursor: String? = null, limit: Int = 20): ReportResult<CommunityReportPage>`、`withdraw(report: CommunityReport): ReportResult<CommunityReport>`、`delete(reportId: String): ReportResult<Unit>`、`resetIdentityAfterConfirmation(): ReportResult<Unit>`（均 suspend）。仅 Repository 取得设备 ID，UI／ViewModel 不接收 Token 或 ID；重建方法封装身份存储及本地提示清理。
- `CommunityResultInvalidator.onOfficialReportChanged(): Unit` 为 suspend；先使旧社区响应失效，再清除官方查询复用并请求 Directory 刷新。`SpamNumberRepository.invalidateOfficialQueryReuse(): Unit` 为 suspend，不依赖上报 Repository，避免 DI 环。
- `CallReportViewModel.open(callId: Long)` / `submit()` / `close()`；公开 `StateFlow<CallReportUiState>` 只包含目标 ID、展示号码、资格原因、提交状态及安全错误。
- `MyReportsViewModel.onEnter()` / `refresh()` / `loadMore()` / `withdraw(reportId: String)` / `delete(reportId: String)` / `resetIdentityAfterConfirmation()`；公开 `StateFlow<MyReportsUiState>` 含 items、游标、加载／操作状态与安全错误，不含设备 ID／Token。

---

## 任务 1：实现 CR-1 协议、可选字段容错与可信 Backend 边界

**文件：** 文件地图中的协议、模型、容错文件及 `data/query/CommunityQueryEpoch.kt`、`di/AppModule.kt`、`data/repository/QueryRepository.kt`。

**接口：** 产出上述 Community 模型、`decodeCommunityQueryMetadata()` 及 `OfficialCommunityReportApi`。本任务不发起实际管理请求。

- [x] 实施前重新读取 App 工作区、设计与当时服务端接口／DTO，核对四路由、成功外层字段、nullable 时间、分页和错误码；若发现差异，先同步 App 契约，不修改服务端。不要读取或复制生产配置秘密。
- [x] 定义 `ReportSubmitRequest(deviceId, feedbackToken)`、`ReportListRequest(deviceId, cursor?, limit=20)`、`ReportActionRequest(deviceId, reportId)`；所有字段明确 `@SerialName`。创建不带 phone；limit 固定 20、空游标省略；不加调试字段。
- [x] 定义 API suspend 方法 `submit(ReportSubmitRequest): ReportSubmitResponse`、`list(ReportListRequest): ReportListResponse`、`withdraw(ReportActionRequest): ReportWithdrawResponse`、`delete(ReportActionRequest): ReportDeleteResponse`；DTO 与设计 CR-1 一一对应，delete 成功必须验证 `status == "deleted"`。
- [x] 将新查询字段 `community` 和 `feedback_token_expires_at` 以可空 `JsonElement` 接收，再做独立结构与字符串类型校验；仅字符串 active 才判 ACTIVE，错误类型／未知值降级 UNAVAILABLE。缺字段为 UNKNOWN／null；原 phone/is_spam/confidence 继续严格解码，不全局放宽 Json。
- [x] 使用 `Instant.parse()` 兼容 RFC3339 的秒、小数秒和 UTC；管理记录缺失必需字段、非法时间判 INVALID_RESPONSE，不以空列表掩盖；未知 report.status 只禁用操作。设备 UI 用本地时区显示。
- [x] 创建并注入最小 `CommunityQueryEpoch` 单例，提供 current/advance。`BackendQueryResponse.from()` 以已验证 Backend 身份过滤自建的 Token、expiry、community 元数据；禁止通过响应自报 source 或 backend 字段建立官方信任。在 `QueryRepository` 请求发起时记录代次，成功时记录 elapsedRealtime，不解码 Token；任务 3 在此基础上增加失效行为。
- [x] Koin 新增 `officialCommunity` qualifier。使用官方 Client 派生的专用管理 Client，关闭 `followRedirects`、`followSslRedirects`、`retryOnConnectionFailure`，`callTimeout=15 秒`；不修改原查询／同步客户端，不安装 body logger。API 沿用固定官方 HTTPS base URL，不接受用户输入地址。
- [ ] 运行 `.\gradlew.bat :app:assembleDebug`，期望 BUILD SUCCESSFUL；静态核对 Retrofit 字段无多余入参。把合法、缺字段、未知状态、错误类型、RFC3339 小数秒、自建伪造社区字段加入最终受控联调样本（任务 8），不得把未联调写成通过。

## 任务 2：落地 Room v12、独立用途状态及备份 v7

**文件：** `data/AppDatabase.kt`、`data/entity/BlockedCall.kt`、`data/dao/BlockedCallDao.kt`、`data/repository/BlockedCallRepository.kt`、`data/dto/BackupData.kt`、`data/repository/BackupRepository.kt`、`di/AppModule.kt`、两份备份 XML。

**接口：** 扩展 insert 的末尾可选参数 `feedbackPhone: String? = null`, `feedbackTokenExpiresAt: Long? = null`, `communityStatus: CommunityStatus = UNKNOWN`, `communityBlockApplied: Boolean = false`；不打断现有调用。新增 `updateReportTokenState(expected: BlockedCall, state: ReportTokenState): BlockedCall?` 和 `clearReportTokenStates(): Unit`（均 suspend）。

- [x] 在确认版本未占用后新增 `MIGRATION_11_12` 并注册；新增列 `feedbackPhone TEXT`、`feedbackTokenExpiresAt INTEGER`、`communityStatus TEXT NOT NULL DEFAULT 'unknown'`、`communityBlockApplied INTEGER NOT NULL DEFAULT 0`、`reportTokenState TEXT NOT NULL DEFAULT 'unknown'`；实体用一致默认值，旧记录不猜测历史社区结论。
- [x] 单独维护 Token 的手机号快照与到期信息。无官方 Token 时清除其绑定元数据及用途状态；从官方 query.response.phone 保存快照，不能由来电原始号码覆盖。
- [x] 为 `updateReportTokenState()` 增加 DAO 条件更新：只匹配期望官方归属及 Token，可同步标记同一复用 Token 的其他官方记录，但不更新质量反馈状态；原记录已删除时返回 null，不重建记录。状态不可误用于另一个新 Token。
- [x] `attachQueryResult()` 改为局部更新当前查询元数据，Token 改变时重置上报用途状态，同 Token 保留；不覆盖来电发生时的社区快照或并发编辑的 remark/label。重查后的备注编辑同样用 DAO 局部更新，避免旧实体整行回写；现有质量反馈 CAS 保持。
- [x] `communityBlockApplied` 只记录实际由于社区新增条件而拒接的历史原因，不把“社区 active”本身当成实际拦截；重查展示新状态不重写历史原因。
- [x] 手动备份升至 v7，`BlockedCallDto` 新增可选 `community_status`（默认 unknown）及 `community_block_applied`（默认 false）。导出明确写 version=7；v1–v6 缺省仍可恢复，未知状态降级 unknown，快照只用于历史 UI。恢复时 Token、feedbackPhone、expiry、设备／上报用途状态都用无资格默认值，不能填充查询复用缓存。
- [x] 两份备份 XML 分别覆盖旧 full-backup 与新 cloud-backup/device-transfer，排除 database 域的 `app-database`、`app-database-wal`、`app-database-shm`、`app-database-journal`；保留已有自建凭据排除。不改 allowBackup、不排除整个离线库或其他配置。纠正 XML 现有关于 API 生效范围的误导性模板注释。
- [ ] 运行 `.\gradlew.bat :app:assembleDebug`，期望成功；用获准的可丢弃设备覆盖安装升级 v11→v12，检查原数据保留、新列默认值和 Room 校验；未获设备授权不卸载／清数据。受控 v1–v7 ZIP 导入导出及系统备份验证归任务 8。

## 任务 3：社区决策、设置单一来源与迟到结果失效

**文件：** `data/query/CommunityQueryEpoch.kt`、`data/preferences/CommunityReportPreferences.kt`、`data/repository/SpamNumberRepository.kt`、`data/repository/QueryRepository.kt`、`data/query/QueryBackend.kt`、`data/repository/CommunityResultInvalidator.kt`、`di/AppModule.kt`。

**接口：** 实现共享代次、设置和失效接口。扩展 `CheckResult`：`communityStatus: CommunityStatus = UNKNOWN`、`communityBlockCandidate: Boolean = false`、`feedbackPhone: String? = null`、`feedbackTokenExpiresAt: Long? = null`，以及仅内存的社区代次与接收时间供最终展示检查。

- [x] 初始化进程级 `CommunityReportPreferences`，键 `block_community_reports` 默认 false，设置读写集中在此处；SpamNumberRepository 每次由原始结果和当前开关计算，不缓存已计算的拦截决定。
- [x] 官方网络响应经过已有归属与名单流程后附带社区元数据；前置通讯录／号码规则／国际门禁／离线命中路径不增加网络访问。
- [x] 保留既有归属地和标签规则命中的结果及 forceBlock。仅当前面规则未要求拦截、也未明确白名单放行且 Provider 非骚扰时，才评估社区新增拦截条件；ACTIVE + 开关开设置 shouldBlock=true、resultType=INTERCEPT、communityBlockCandidate=true，forceBlock=false。开关关仅附带提示状态，不影响已有 Provider 拦截。
- [x] 不把社区提示写进 Provider 的 label、source 或手写标签，不参与标签匹配。明确保持标签白名单的既有语义，不借本任务调换黑白规则优先级。
- [x] 请求记录代次与接收时间，已完成复用仍受原有 60 秒 TTL 限制。每次使用社区数据都核对代次与有效窗口；过期或旧代次只丢弃社区增强，不把仍有效的 Provider 结果误记为网络故障。
- [x] `invalidateOfficialQueryReuse()` 在失效代次推进后，通过现有 mutex 移除所有官方 completed/in-flight 缓存关联（容量原本有界），不清除自建项、不追加网络请求。移除不能取消所有调用方而导致误改变 Provider 判断；已在等待的调用通过代次丢弃旧社区值，后台 observer 也不能回灌。
- [x] 覆盖共享请求、容量满 Direct、forceNetworkQuery、手动 queryNetwork 的所有返回路径，而不是只修 completed cache。复用不能刷新 receivedAtElapsedMs。采用进程统一代次避免建立无界号码版本表，失效官方整个小缓存也避免号码别名漏清理。
- [x] `CommunityResultInvalidator` 先 advance，再清理复用，最后对当前 App Directory authority 发出受控 notifyChange；Provider 当前没有独立应用内缓存，不虚构清缓存 API。通知失败只记安全 warning，不把已完成的云端操作显示为失败；系统拨号器何时刷新仍需真机验证。
- [ ] 运行 `.\gradlew.bat :app:assembleDebug`；受控设备验证开关不改变前置门禁，设计慢响应夹在撤回前后，确认旧社区值不再参与新决策且 Provider 原结果保留（依赖任务 4 完成后的联调）。

## 任务 4：安装身份与官方上报 Repository

**文件：** `data/ReportDeviceIdentityStore.kt`、`data/repository/CommunityReportRepository.kt`、`data/repository/CommunityReportFailure.kt`、`di/AppModule.kt`。

**接口：** 实现共享 Store 及 Repository 四方法；依赖任务 1 DTO、任务 2 本地凭据读取／用途更新及任务 3 invalidator。

- [x] 以 `AtomicFile` 在 `noBackupFilesDir/community_report_device_id` 保存 32 字节随机原始值；读时验证长度，发请求时用 `Base64.URL_SAFE | NO_WRAP | NO_PADDING` 编码。使用单例 mutex、IO dispatcher、写完持久化成功才返回；并发首次访问不得生成多个有效 ID。
- [x] 区分首次不存在与读取／内容损坏。AtomicFile 先按其恢复机制处理未完成写入，损坏时返回 IDENTITY_UNAVAILABLE，不静默重新生成。重建仅由显式确认操作调用，重建前先清除本地上报用途提示（失败仅需重新由服务端校验），新 ID 保存成功后清空管理页缓存并推进页面代次；不能自动向旧身份发送删除。
- [x] `submit(callId)` 从 Room 重新读取记录；核对官方归属、非空 Token、独立上报用途状态，禁止以 FeedbackStatus.POSITIVE/NEGATIVE/ALREADY_SUBMITTED 阻止上报。expiry 仅提示，服务端错误权威；缺少 expiry 不判过期。UI 已知过期不自动补领。
- [x] 请求只携带设备 ID 与该记录 Token。成功校验 DTO、状态、report.phone；优先与 feedbackPhone 精确比较，旧记录使用原查询标准化函数推导核对值。响应不匹配／格式错误时标记 mayHaveCommitted=true，提示列表核实，不重发到另一号码。
- [x] 成功及明确 USED/EXPIRED/INVALID 错误按期望 Token 条件更新独立用途状态；本地记录删除或写入失败不能把已确认的服务端接受降格为“未上报”。ACCEPTED 是本 Token 已提交提示，不代表云端记录仍有效；云端状态由列表决定。
- [x] `list()` 不依赖任何本地来电行；withdraw/delete 只发送设备 ID + report_id，不要求 Token。撤回成功必须校验响应 ID／号码与目标一致且状态为 withdrawn，再更新本地视图；delete 必须确认 deleted 响应才移除。创建与撤回确认成功后在短小不可取消本地收尾中调用 invalidator，避免退出页面漏失效；若请求已发送但响应丢失或被取消，也保守失效官方缓存，不宣称云端已成功。不在不可取消段执行网络。
- [x] 错误体最多读取 4 KiB，按 HTTP + 已知 code 映射 ReportFailure，未知原文不展示、不记录。无 code 的 404 为 UNSUPPORTED，业务 NOT_FOUND 则刷新；空 items 是成功数据，不可由异常伪造。
- [x] 正确解析 Retry-After 的秒或 HTTP-date，按客户端保护规则夹紧到 1–3600 秒；缺失、非法或已过期时使用 1 秒最低本地防连点冷却，不视为服务端承诺的等待时长。使用 elapsedRealtime 保存本次冷却，按操作区分，显示重试入口但不自动发送写请求。503 提交失败不永久关闭 list/withdraw/delete。
- [x] 保留 Coroutine cancellation；传输中断和提交响应解析异常标记结果可能已落库。设备 ID／Token 不出现在公开 StateFlow、SavedState、toString 日志或异常信息；身份更换后的旧响应不能回写新身份状态，不能假定取消意味着服务端未提交。
- [ ] 运行 `.\gradlew.bat :app:assembleDebug`；用受控 CR-1 环境核对重复提交 created=false、Token 过期仍可管理、删除幂等、不同号码原字符串及 307/308 不跟随。无受控环境时保持该联调未完成，不对正式号码制造记录。

## 任务 5：来电记录详情上报与独立 UI 状态

**文件：** `viewmodel/CallReportViewModel.kt`、`ui/components/CommunityReportSection.kt`、`ui/screen/HomeScreen.kt`、`viewmodel/HomeViewModel.kt`、`MainActivity.kt`、中英文 strings。

**接口：** 实现共享 `CallReportViewModel`；`CommunityReportSection(state: CallReportUiState, onSubmit: () -> Unit, onOpenReports: () -> Unit)`。按现有 Activity `by viewModels()` + KoinComponent 注入方式创建，不为此迁移全部 ViewModel。

- [x] `open(callId)` 读取最新记录生成展示与资格状态；UI 不接收反馈 Token 或设备 ID。记录已删除时关闭目标，不自行创建临时上报记录。
- [x] 在现有 BottomSheet 的质量反馈区之外添加上报区，明确区分“结果不准确”和“上报骚扰”；所有记录保留可理解的资格说明，无 Token／自建／过期／已用时不引导后台补查。
- [x] 首次每次实际提交前确认向官方发送该号码及当前安装标识、不上传通讯录或录音、不自动入黑名单；点击确认后调用 Repository.submit(callId)，不从 UI 传任意号码。
- [x] 提交过程防重复点击，以目标 callId + 操作代次隔离。关闭详情／切换其他记录后旧响应不弹入新详情；服务器可能已接受时仍能从“我的上报”核实。不通过全局取消共享来电查询来取消上报 UI。
- [x] 成功显示已提交并提供“我的上报”；created=false 不显示成新增一票；mayHaveCommitted 失败文案为结果待核实而不是确定未保存。重试遵循冷却，不自动重新联网查询号码。
- [x] 联动本地用途状态刷新时只更新对应 Token 的提示，不重置原质量反馈、用户备注或最新重查结果。已提交质量反馈仍可上报，已上报仍可质量反馈；既有通知 Receiver 流程保持。
- [ ] 运行 `.\gradlew.bat :app:assembleDebug`；设备覆盖详情 A 提交后切到 B、提交时重查替换 Token、删除原记录、关闭 sheet、旋转／进程恢复。只把已实际验证的场景标完成，不记录带 Token 的完整 UI 状态。

## 任务 6：我的上报列表、双入口与身份恢复失败交互

**文件：** `viewmodel/MyReportsViewModel.kt`、`ui/screen/MyReportsScreen.kt`、`MainActivity.kt`、`ui/screen/SettingsScreen.kt`、`ui/screen/HomeScreen.kt`、中英文 strings。

**接口：** 实现共享 MyReportsViewModel；`MyReportsScreen(viewModel: MyReportsViewModel, modifier: Modifier = Modifier)`。SettingsScreen 增加 `onNavigateToMyReports: () -> Unit`；HomeScreen 的上报结果也复用同一导航回调。

- [x] 首次进入页才触发 list，禁止 HomeViewModel 初始化、来电 Service 或应用启动后台加载上报列表。页面离开后不继续无界翻页，重进页面刷新当前服务器状态。
- [x] 每页 20，按 report_id 去重并保留服务端顺序，不自行按号码合并。refresh 从第一页重新建立列表；loadMore 只使用当前代次 cursor，next_cursor=null 明确停止；重复游标不得无限加载。
- [x] ViewModel 以列表代次隔离刷新与翻页，行操作状态以 reportId 区分。撤回／删除开始即阻止旧页面响应覆盖该行，成功后废弃旧在途分页并重新取得第一页；失败保留原行与安全错误，不伪造成功。
- [x] 已加载列表遇到刷新失败保留内容并标明非最新；不同失败分别提供重试、冷却、服务不支持、身份不可用状态，不显示假空列表。最多保留 5 页（100 行），向后翻页时淘汰最早的已离屏页并按 reportId 保持可见锚点；提供刷新回到最新记录的动作，不复制成 Room 云端表。
- [x] 行显示号码、上报／撤回时间、我的上报状态与独立社区状态。active 只可撤回，withdrawn 只可删除；未知状态禁用写操作。无“直接恢复”按钮，重新上报仍从已有来电记录取得新资格。
- [x] 撤回与删除均二次确认；回调只提交 reportId，从当前 ViewModel 条目解析对象，不接受 UI 临时改写 phone。网络结果不确定时提供刷新核实，重复撤回不重复扣票，删除 missing 的成功结果也安全移除。
- [x] 复用现有二级页面模式，首页顶部增加操作菜单“我的上报”，即使本地通话记录为空也可进入；原清空记录动作保留。设置页同名入口在离线模式、自建选择或上报禁用时仍可进入，明确它管理的是官方历史。
- [x] MainActivity 在本页隐藏底部导航，标题和返回键一致，返回原首页／设置页；与本地标签管理和配对页互斥，不能同时打开两层。打开管理页前关闭来电详情目标，不引入新的导航框架。
- [x] 身份损坏时显示明确的新身份确认，说明无法管理旧记录；繁忙操作期间禁用重建。确认后取消旧页面操作并推进本地身份／列表代次、清空旧设备视图，不上传旧记录作恢复。没有 ID 导出或查看按钮。
- [ ] 运行 `.\gradlew.bat :app:assembleDebug`；设备覆盖空本地记录却有云端历史、双入口返回、旋转、网络中断、慢分页夹着撤回／删除、同号码多条历史和身份重建后的迟到响应。

## 任务 7：贯通来电记录、Directory、Overlay 与设置展示

**文件：** `service/TeloCallScreeningService.kt`、`provider/TeloDirectoryProvider.kt`、`service/IncomingCallOverlay.kt`、`service/IncomingCallOverlayFormatter.kt`、`data/model/NumberLabelPresentation.kt`、`viewmodel/SettingViewModel.kt`、`ui/screen/settings/OnlineQueryPreferences.kt`、`ui/screen/settings/BackupRestorePreferences.kt`、`ui/screen/SettingsScreen.kt`、`ui/screen/HomeScreen.kt`、中英文 strings。

**接口：** `NumberLabelPresentation` 增加可空 `communityLabel`（默认 null），组合顺序为本地标签、原来源标签、社区提示。`IncomingCallOverlayContent` 增加 `communityLabelText: String?`；`SettingViewModel` 暴露社区开关 StateFlow 和 `updateBlockCommunityReports(enabled: Boolean)`，委托单例 Preferences。

- [x] 审查 Service 所有 insert 分支及 SettingViewModel.saveTestResult，将反馈号码／expiry 和社区快照传入任务 2 的 insert；不只修改普通拦截分支。实际被社区新增条件拒接时才写 communityBlockApplied=true；全局仅提示／重复来电放行及手动测试保存均为 false（手动测试没有真实拒接）。
- [x] 保持既有记录范围，不为取得 Token 自动记录所有放行电话；社区默认提示通过 Provider 展示，只有原有应落库条件成立才保存历史。手动测试保存沿用原入口，不新增任意号码上报表单。
- [x] 社区拦截使用现有 ResultType.INTERCEPT，避免漏改 Smartspacer 与系统拒接计数；标记理由通过独立快照说明。重复来电策略继续依赖当前 shouldBlock，不把旧社区标记留作后续永久触发依据。
- [x] Directory 在社区仅提示且无其他标签时也返回可展示行，不依赖 shouldBlock=true；保留原机构名称及本地标签，在所有 DISPLAY_NAME/LABEL 列使用同一组合文本。不要把社区提示写回 result.label，也不要为展示新增网络调用。
- [x] Provider 最终组装及 Overlay 展示前校验瞬时社区代次／有效期；失效后保留原来源／本地标签，去掉社区部分。系统 notifyChange 是刷新请求而非拨号器即时刷新保证。
- [x] Overlay 卡片／简洁两种样式新增独立社区文本行，空值不留空白；保持默认关闭、权限门禁、仅联网结果且不展示拒接来电的原约束。不要因社区功能自动开启 Overlay 或延长已有窗口寿命。
- [x] 首页记录／详情显示历史社区快照与实际拦截原因；手动重查对话框显示本次查询的新社区状态，不能改写历史“当时被拦截”事实。已恢复备份的历史不能展示“当前公共标记已生效”。
- [x] 在线查询设置新增默认关闭的“拦截社区标记号码”，说明仅官方在线结果有效，仍受全局仅提示限制；切换不用重启，不能用旧已计算决定。自建／离线下不强行切回官方；管理页入口不受此开关控制。
- [x] 备份设置补充系统不会自动迁移运行时记录／名单／本地标签的说明，提示使用手动备份；手动备份包含历史社区展示但不包含安装身份或上报管理权。补齐所有新增中文和英文资源及无障碍 contentDescription。
- [ ] 运行 `.\gradlew.bat :app:assembleDebug` 和 `.\gradlew.bat lint`，期望成功；用受控号码核对完整决策矩阵，并在 Google Pixel 真机验证 Directory 默认只提示、撤回后刷新。没有真机时将原生显示列为未验收，不能用模拟器代替结论。

## 任务 8：完整联调、备份迁移及文档验收

**文件：** 实际受影响代码、设计、本文、文档地图中的既有长期文档与索引；不新增单元测试目录或临时审计报告。

- [ ] 再次只读核对当时服务端实际 CR-1 实现／部署状态与本 App；记录版本和环境作为私有联调证据，不把私有地址、凭据、真实号码或请求体粘进公开方案。旧服务、禁用服务与未部署接口分别验证。
- [ ] 仅在授权的测试服务／模拟响应环境准备协议样本：有效官方 Token、已质量反馈、已上报、过期、未知状态、不同精度时间、错误字段类型、404 无 code、各业务 code、Retry-After、超时及响应丢失。禁止通过真实用户号码或正式上报池制造样本；环境不可用时明确联调阻塞。
- [ ] 跑通“官方查询→保存来电→上报→列表→撤回→保留历史→删除”及重新上报；原质量反馈双向独立，重新查询同号码不重复贡献，撤回后原 Token 不恢复历史。以上是客户端观察验收，不用 App 推算具体票数。
- [ ] 决策矩阵覆盖：通讯录、号码／标签／归属地名单、国际门禁、本地命中、官方成功、自建伪造扩展、网络失败；社区开关开／关、全局仅提示、重复来电、Provider 同时骚扰／正常。核对系统 CallResponse 和 App 记录一致。
- [ ] 缓存矩阵覆盖：completed 复用、共享 in-flight、Direct 容量分支、force 查询、手动 retry、Backend 切换；撤回前发出的响应不能在撤回后恢复社区状态。复用超过 60 秒不成为新来电的依据，当前设备变化不依赖额外官方查询刷新。
- [ ] 在已授权可丢弃环境验证安装身份：首次并发、损坏、AtomicFile 中断、重建、重装／清数据；无授权不执行破坏性操作。核对系统 full backup 和新 cloud/device-transfer 不含 app-database 及附属文件、noBackup 身份，也不承诺清除了历史系统备份。
- [ ] v11→v12 覆盖升级保留旧数据；手动 v1–v6 归档兼容，v7 历史状态往返正确。检查 ZIP JSON 无 device_id、feedback_token、feedbackPhone、expiry、reportTokenState，恢复不能提交上报或进入查询缓存；非记录备份范围保持原行为。
- [ ] UI 验证冷启动、双入口、空列表、错误保留旧数据、旋转和进程恢复、大列表、分页期间变更、退出页面后的响应隔离；设备 ID／Token 不在 SavedState、剪贴板、日志或崩溃数据。R8 相关 DTO 至少静态核对序列化保留方式，不为验证擅自签名／发布 Release。
- [x] 只有后续新修改或失败才重跑已有通过验证。最终执行 `.\gradlew.bat :app:assembleDebug`、`.\gradlew.bat lint`、`git diff --check`；命令均在远端 Windows 项目根运行。失败明确记录原因，不运行单元测试或 check 替代。
- [x] 实现后更新 README 的备份版本和功能说明，以及 PRD、MVVM、原生集成、主页文档；区分“我的上报状态”“历史社区快照”“当前社区状态”。公开内容不引入后台私有实现或人工申诉承诺。
- [x] 自审以上五项 Review Focus、设计需求覆盖、所有 insert/update 调用点、依赖方向与未完成环境验证。更新本文勾选及设计状态，确认仅 App 授权范围变更；不自动提交、推送或发布。

## 执行与发布边界

1. 任务依赖：1 → 2；1/2 → 3 → 4；4 → 5/6；2/3/5/6 → 7；全部 → 8。5/6 涉及同一 UI 文件，若未来并行须明确所有权，不能让两个执行者直接同时编辑 MainActivity/HomeScreen。
2. 每任务以可构建成果和具体联调场景验收；本项目不使用单元测试 RED/GREEN，不将本计划当作新增测试框架授权。
3. 服务端功能可用且受控联调通过后才考虑发布 App；服务端尚在开发时可完成客户端实现，但不能声称端到端已验证。
4. 不在本任务实施后台管理能力，不维护服务端代码，不把私有方案复制到开源仓库。
5. 公开协议变化需两端对齐；不偷偷通过放宽 Token 要求、自动补查、自动切官方或丢弃错误来“兼容”。
6. 对已产生 v12 数据的新版本不承诺可直接降级安装旧版；必要回退通过正常版本发布及受控手动备份方案另行确认，不卸载清数据规避迁移问题。

## 计划自检与验收记录

- 设计覆盖：产品／身份 → 任务 4/5/6；CR-1 → 任务 1/4；来电／缓存 → 任务 3/7；持久化／备份 → 任务 2/8；发布／验证 → 任务 8。
- 五项 Review Focus 均有对应设备／协议场景；不存在自动提交或单元测试任务。
- 共享 DTO、枚举、Repository 方法和设置键在本文统一定义；下游不复制服务器计票或号码合并逻辑。
- 本轮客户端实现完成，验证记录如下；未执行场景保留未勾选，不作为发布依据。

### 本次实施验收记录

- 范围：仅 App 工作区，保留原有方案与索引变更；无服务端修改、单元测试、提交、推送、发布或设备清数据。
- 已通过：分阶段 `:app:assembleDebug`；最终 `:app:assembleDebug lint`（BUILD SUCCESSFUL），Lint 0 错误、55 条警告；新增社区功能文件无 Lint 告警。首次任务 7 构建发现历史 String codec 与 UI 枚举混用，已统一解析后重新通过。
- 已通过静态核对：CR-1 四路由与请求字段、DAO Token 条件更新、所有 Service／手动保存分支、两份备份排除 XML、中英文资源键与引用、备份 DTO 凭据排除、本地文档链接与 `git diff --check`。
- 自审修正：管理请求使用一次性请求体防止 OkHttp 对 503 隐式重发；手动查询最终发布也检查代次；自建缓存键不随官方失效推进；分页检测非相邻重复游标；列表错误不误报成写入不确定；Service 在最终系统响应前复核社区依据，响应发出后再确认历史实际拒接原因；Overlay 已显示行也随代次／期限失效。
- 执行调整：任务 5/6 共享导航接线后合并构建；新中英文资源集中在各语言 `community_reports.xml`。身份请求／重建以 Repository mutex 串行化而不另设 Store 代次；先清用途提示再写新身份，避免新身份成功而提示清理失败后显示旧提示。
- 环境限制：ADB 无连接设备；再次只读检索服务端时仍未发现 CR-1 产品实现文件，没有获准的可用联调环境。未请求正式服务制造上报数据。
- **未验证**：完整上报／撤回／删除链路、错误与超时响应样本、慢响应竞态、真实来电决策矩阵、Pixel Directory 刷新、Overlay／旋转／分页交互、Room v11→v12 覆盖升级、手动 v1–v7 ZIP 往返与系统迁移、身份损坏及重建场景。上述是发布前门禁。
- 当前工具无独立审查代理，完成的是按五项 Review Focus 的静态自审，不宣称已有独立审查或端到端验收。
