# MVVM 架构与技术栈

## 架构概览

Pixel Telo 遵循 **MVVM (Model-View-ViewModel)** 架构模式，严格遵守 **Modern Android Development (MAD)
** 指南。

### 分层设计

1. **UI 层 (View)**
    * **框架**: Jetpack Compose (Material3)。
    * **动态主题**: 支持 Material You (Monet) 进行动态取色。
    * **职责**: 渲染 UI 状态并处理用户交互。Activity/Composable 中**严禁包含业务逻辑**。

2. **表现层 (ViewModel)**
    * **组件**: `ViewModel`。
    * **状态管理**: 使用 `StateFlow` 向 UI 暴露不可变状态。
    * **职责**: 持有 UI 状态，处理 UI 事件，并与 Repository 层交互。

3. **数据层 (Repository)**
    * **模式**: Repository 模式。
    * **职责**: 数据的单一事实来源 (Single Source of Truth)。在本地存储 (**Room**) 和远程数据
      (**Retrofit**) 之间进行调度。
    * **组件**:
        * **本地**: Room Database (SQLite)。
        * **远程**: Retrofit + OkHttp。

## 联网查询数据流（官方与自建 Backend）

### 网络 Client 隔离

网络层按用途划分为三个隔离边界：

* **官方离线同步 Client**：固定 Base URL，只创建 `SyncApi`，只供 `SyncRepository` 下载和检查
  `mast.db`；不接收 Backend、Token、Pin 或自建 URL。
* **官方实时查询 Client**：固定 Base URL，创建 `QueryApi` 与 `OfficialFeedbackApi`。反馈 API 与官方
  查询共用该官方网络栈，但不进入自建 Client。
* **自建实时查询 Client**：由 `SelfHostedQueryClientFactory` 根据已验证配置动态创建，不注册为固定
  Koin Retrofit 单例，也不复用官方 Client 的 Dispatcher、ConnectionPool、Cookie、Authenticator 或
  TLS 状态。示例 Base URL 只使用 `https://mast.example.com/`。

`AppModule` 使用 `officialSync`、`officialQuery`、`officialFeedback` qualifier 明确依赖归属；其中
`officialFeedback` 是官方查询 Retrofit 创建的独立 API 契约。自建 Client 关闭自动 Redirect，Bearer
Token 只发送到与已验证配置完全相同的 scheme、host 和有效端口。

### Backend Snapshot 与查询链路

* `QueryBackendProvider` 是活动 Backend 的单一事实来源。每次激活都会发布新的不可变
  `QueryBackendSnapshot`，包含 Backend ID、唯一 `activationId`、`QueryApi`、反馈能力和可选自建身份。
* `snapshot()` 只在短锁内读取已构造引用；查询与 source 刷新使用 `QueryBackendLease` 在完整操作期间持有
  对应 Snapshot/Client。普通切换只退役旧 Client，最后一个 lease 释放后才清理 Token 与网络资源；
  安全阻止则立即 revoke/cancel。配置验证、Keystore 和磁盘操作位于独立命令边界。
* 运行期版本、API Version、Instance ID 或身份 Header 校验失败时，Provider 撤销自建 Snapshot、持久化
  `Blocked` 状态，并让后续调用直接得到不可用状态。来电路径对此 **Fail Open**，且不会读取官方
  Snapshot 或再次请求官方实时查询。
* `QueryRepository.queryNumber()` 一次读取 Snapshot 和该 Backend 专属 source 配置，只发送用户启用且
  最近已知可用的 source；实时来电查询不会临时刷新 source 清单。
* `SpamNumberRepository` 继续负责黑白名单、本地离线库和最终拦截决策。联网查询沿用用户配置并夹紧到
  1 至 10 秒的超时；自建失败统一放行并只记录稳定分类与耗时。

### source 与反馈隔离

* source 配置按 Backend ID 以 JSON Map 保存到 `query_source_configs`。旧
  `query_source_config` 只迁移到 `official`；自建 ID 为规范化 Instance ID 派生的
  `selfhost:<uuid>`，不会继承官方 source。
* `sourceState` 始终归属于当前 Snapshot。Backend 切换先发布目标缓存或空状态，再异步刷新；所有迟到
  发布都要同时通过 Snapshot 引用和 `activationId` 门禁，避免跨 Backend 及 ABA 污染。首页 source
  WarningCard 还会校验 `sourceState.backendId` 与当前 Ready Backend ID，切换窗口不展示旧告警。
* `BackendQueryResponse` 从 Snapshot 派生可信 Backend ID 和反馈能力。自建响应即使返回反馈 Token，
  也会在这一边界被强制清除。
* `BlockedCall`（Room v9）持久化 `queryBackendId`。Repository、首页 UI 与
  `FeedbackActionReceiver` 三层都只允许 `queryBackendId == "official"` 的有效 Token 进入官方反馈 API；
  自建记录固定为 `UNAVAILABLE`。

### 凭据与配置

* `SelfHostedCredentialStore` 使用 Android Keystore AES-GCM 保存 Token 密文；凭据 SharedPreferences
  同时从 Auto Backup 与设备迁移中排除。
* `SelfHostedConfigRepository` 只持久化规范化 URL、TLS 模式、Pin、服务身份、验证时间、阻止原因和
  Backend 指针，不序列化明文 Token。Backend 与 TLS 模式使用稳定磁盘 codec，不依赖 enum 源码名称。
* 新配置先写候选凭据和候选配置，再通过 journal 原子切换活动指针；恢复无法可靠裁决、Keystore
  失效或配置损坏时保持自建选择但阻止联网，要求用户重新完整验证，不自动切回官方。
* 普通存储提交失败保留当前进程旧 Snapshot、Client 与活动选择；journal 仅用于下次启动裁决。
  运行期安全阻止另写 no-backup AtomicFile 哨兵；哨兵未可靠写入时，在任何活动记录提交前先销毁并
  复核 Keystore 主密钥不存在，使 journal 可能引用的全部槽位密文不可恢复。密钥销毁失败时不提交可能
  刷盘受污染活动指针的记录；只有完整重验证和新活动指针提交成功才解除哨兵。
* Compose/ViewModel 公开状态只保存非敏感草稿。Dialog 将 Token 一次性移交为 `CharArray`，验证调用的
  `finally` 与 Job 完成回调共同覆盖成功、失败、取消、拒绝及协程体尚未开始即取消的清零路径，不进入
  `mutableStateOf`、StateFlow 或 SavedState。

## 拦截记录分页与联系人解析

* `BlockedCall` 在 Room v8 新增可空的 `province`、`city`，只持久化联网结果中的省份和城市。
* 拦截记录由 Room `PagingSource` 分页加载，`HomeViewModel` 组合黑白名单状态后输出
  `PagingData<BlockedCallListItem>`，不再为 UI 持有全部历史记录。
* `ContactRepository` 只解析 Paging 当前已加载窗口中的去重号码，依次尝试原始号码、
  去国家码号码和 `PhoneNumberNormalizer.normalizeForLookup()` 的标准号码。
* 联系人姓名仅保存在有界内存缓存中；未命中结果缓存 30 秒，Provider 查询异常不缓存。
  缓存使用代次保护，联系人变化后未完成的旧查询不能回灌已失效结果。
* 页面恢复时重新建立联系人 ContentObserver，兼容首次无权限、后续授权的场景。
* 联系人姓名不持久化、不进入备份，也不进入 `CallScreeningService` 的实时查询链路。

## 本地号码标签

本地号码标签是独立于识别结果和拦截记录的用户备注，使用 Room v10 的
`local_number_labels` 表持久化。每个归一化号码最多一条标签；号码唯一键由
`PhoneNumberNormalizer.normalizeForLookup()` 产生。

* `LocalNumberLabelRepository` 是本地标签的单一事实来源：号码归一化、40 字符校验、
  空标签删除、写入结果和备份恢复冲突规则都集中在此，UI、Directory Provider 和备份
  不得复制实现。
* `SpamNumberRepository` 与 `CheckResult` 不读取本地标签，也不修改 `CheckResult.label`
  语义。识别、拦截、名单、反馈 Token、`ResultType` 和查询耗时保持原链路。
* 本地标签不得写入或覆盖 `BlockedCall.label`。历史记录只按当前 Paging 窗口动态关联，
  清空拦截记录只清窗口映射，不删除标签表。
* `HomeViewModel` 只观察 Paging 当前已加载窗口中的去重号码；显示开关关闭或窗口为空时
  不建立普通展示订阅，并把 `localLabels` 置为空 Map。详情/管理页的单号码编辑观察不受
  该门禁影响。
* 显示开关由进程级 `LocalNumberLabelPreferences` 暴露 `StateFlow`，键
  `show_local_number_labels`，默认关闭。关闭只隐藏 Directory、Overlay 和历史列表的普通
  展示，不影响编辑、统一管理、存储和备份恢复。
* 统一管理页通过 `LocalNumberLabelsViewModel` 观察全部标签，支持按号码/标签搜索、编辑和
  删除；不提供任意号码新增。设置页以现有 HorizontalPager 的非底部二级页面进入，不增加
  `AppDestinations` 项，也不引入 Navigation Compose。
* 备份格式 v5 把本地标签作为第四个独立选择范围。`BackupData.localNumberLabels` 缺省空列表
  以兼容 v1–v4；新导出显式写入 `version = 5`。显示开关不进入备份。未选择该范围时恢复
  不读取、不修改标签表。

## 依赖注入

我们使用 **Koin** 进行依赖注入。

* **Modules**: 为 Network, Database, Repository 和 ViewModel 定义模块。
* **Scopes**: 使用适当的作用域 (Singleton vs Factory/ViewModel)。

## 异步编程

* **Coroutines**: 用于所有异步操作。
* **Flow**: 用于响应式数据流（数据库观察，Service 事件）。
