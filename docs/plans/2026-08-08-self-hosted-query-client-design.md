# 自建实时查询服务 App 接入设计

**日期**：2026-08-08

**状态**：待用户审阅

**适用项目**：Pixel Telo Android App

**最低自建服务端版本**：`0.1.2`

## 一、背景

Pixel Telo 当前使用固定官方服务完成离线号码库同步和实时号码查询。为了降低官方实时查询实例的集中流量，并允许用户使用个人部署的 Mast 实例，App 需要支持在“官方实时查询服务”和“一套用户自建服务”之间切换。

自建服务只替换实时查询 Backend，不改变官方离线数据库同步链路。自建服务发生网络、TLS、鉴权、版本、协议或上游故障时，来电必须 Fail Open，并且不得把号码自动发送到官方实时查询服务。

本设计只使用通用示例地址描述配置。私人联调实例的域名、IP、Token、证书信息和其他部署细节不得进入受版本控制的文档、代码、资源、日志、测试夹具或提交信息。

## 二、目标与非目标

### 2.1 目标

1. 默认继续使用 Pixel Telo 官方实时查询服务。
2. 用户可配置、验证、启用和修改一套自建服务。
3. 支持域名或公网 IP 的系统信任 HTTPS 证书。
4. 支持域名或公网 IP 的自签名证书，并使用精确 SPKI SHA-256 Pin 校验。
5. 自建业务接口统一使用 Bearer Token。
6. Token 使用 Android Keystore 保护，不进入普通 SharedPreferences、Room、备份、日志或崩溃信息。
7. 官方与自建 Backend 分别保存 source 顺序、启用状态、默认值和可用状态。
8. App 在配置阶段和运行阶段持续验证服务版本、API Version 和 Instance ID。
9. 配置验证失败不得破坏当前可用配置，也不得切换当前 Backend。
10. 自建查询结果不保存反馈凭证，不展示反馈入口，也不调用反馈接口。
11. 保持来电查询的 1 至 10 秒用户可配置超时和 Fail Open 行为。
12. 不新增相机权限、悬浮窗权限或其他与本功能无关的权限。

### 2.2 非目标

1. 不允许用户修改官方离线数据库同步地址。
2. 不支持多套自建配置、自动负载均衡或故障转移。
3. 不支持自建服务故障后自动回退官方实时查询。
4. 不支持 HTTP、公网裸传输、忽略 TLS 错误或自定义明文加密协议。
5. 第一版不提供二维码扫描。
6. 不在 App 中管理自建服务的 Provider、数据库或部署配置。
7. 不为自建服务增加查询反馈能力。
8. 不在后台周期轮询自建服务健康状态。

## 三、关键产品决策

1. 实时查询 Backend 为二选一：`official` 或已验证的 `selfhost:<instance_id>`。
2. 切换只影响实时查询和对应 source 设置，不影响离线数据库同步。
3. 自建服务任何失败都 Fail Open，并禁止隐式访问官方实时查询。
4. 自建配置采用“草稿验证后原子替换”，编辑过程不影响当前生效配置。
5. 自建服务最低版本首版为 `0.1.2`。
6. 最低版本由 `gradle/libs.versions.toml` 单点配置，经 `BuildConfig` 提供给运行时代码。
7. Release 构建只接受严格稳定 SemVer；Debug 构建可通过仅 Debug 可见的选项允许预发布版本。
8. 私人联调实例只在本地手动输入，不提供硬编码、预置或 Debug 默认值。

## 四、总体架构

App 提供三条互相隔离的网络链路：

1. `OfficialSyncClient`：固定官方 Base URL，仅用于离线数据库版本检查、下载和安装。
2. `OfficialQueryClient`：固定官方 Base URL，用于官方实时查询、source 刷新和查询反馈。
3. `SelfHostedQueryClient`：根据一份已经验证的自建配置构造，仅用于自建信息、source 和实时查询接口。

`OfficialSyncClient` 与 `OfficialQueryClient` 不读取自建配置。`SelfHostedQueryClient` 不共享官方 Client 的 Cookie、Authenticator、Certificate Pinner、Redirect 策略或其他连接状态。

新增 `QueryBackendProvider`，根据当前模式提供不可变的 `QueryBackendSnapshot`：

```text
QueryBackendSnapshot
├── backendId
├── backendType
├── queryApi
├── feedbackSupported
└── selfHostedIdentity（仅自建）
```

官方 Backend ID 固定为 `official`。自建 Backend ID 为 `selfhost:<instance_id>`，Instance ID 使用服务端返回的 UUID 原文规范化后生成。

`QueryRepository` 在查询或刷新 source 开始前读取一次 Snapshot，并在请求结束前始终使用同一 Snapshot。配置切换不得让正在执行的请求中途更换 Client、Backend ID 或 source 配置。

查询和 source 刷新必须同时取得 Snapshot lease。普通切换把旧自建 Client 标记为 retiring，最后一个旧
lease 释放后才清理 Token 与网络资源；运行期身份、TLS、Pin 或凭据安全错误则立即 revoke 对应代次、
取消在途请求，并拒绝消费已经返回但尚未发布的旧结果。迟到的旧代次安全回调只能撤销该旧 Client，
不得按相同 Backend ID 误伤新激活代次。

反馈不进入通用 Backend 路由。反馈始终由官方专用 API 执行，只有来源明确为官方 Backend 且具有有效反馈凭证的记录才可提交。

## 五、网络接口与数据模型

### 5.1 自建 API

自建 Client 使用以下接口：

- `GET /api/selfhost/v1/info`
- `GET /api/v2/sources`
- `POST /api/v2/query`

除不携带敏感信息的 `/api/health` 外，自建业务接口都必须发送：

```http
Authorization: Bearer <token>
```

App 的连接验证不以 `/api/health` 作为充分条件，而是必须成功调用鉴权的 info 和 sources 接口。

### 5.2 自建信息模型

新增 `SelfHostedInfoResponse`：

```kotlin
data class SelfHostedInfoResponse(
    val service: String,
    val version: String,
    val apiVersion: Int,
    val instanceId: String,
    val buildCommit: String,
    val capabilities: List<String>,
)
```

必须满足：

- `service == "pixel-telo-mast-selfhost"`。
- `version` 是受支持的严格 SemVer。
- `version >= MIN_SELFHOST_SERVER_VERSION`。
- `api_version == 2`。
- `instance_id` 是有效 UUID。
- `capabilities` 至少包含 `query_v2`。

`spki_pairing` 仅作为能力信息展示，不替代 App 对 Pin 格式和 TLS 行为的校验。

### 5.3 错误模型

扩展现有错误 DTO，兼容以下字段：

```json
{
  "error": "upstream is temporarily unavailable",
  "code": "upstream_unavailable",
  "request_id": "01J..."
}
```

App 只将稳定机器码用于分类，不向普通 UI 展示服务端堆栈、完整响应或 request body。`request_id` 可在连接诊断详情中显示或复制，但不得与完整号码、Token 一起写入日志。

## 六、自建配置与持久化

### 6.1 非敏感配置

非敏感配置保存到专用 SharedPreferences 文件：

- 当前 Backend 类型。
- 自建 Base URL。
- TLS 验证模式。
- SPKI Pin。
- 最近验证的 Instance ID。
- 最近验证的服务版本。
- API Version。
- capabilities。
- 最近验证时间。
- 当前安全告警状态。

SPKI Pin 本身不是秘密，但应与 Token 分开管理，避免普通设置导出意外形成完整配对信息。

### 6.2 Token 凭据

Token 使用 Android Keystore 中的 AES-GCM Key 加密：

- Key Alias 使用稳定且应用私有的名称。
- Key 只允许本应用进程使用，不要求用户每次认证。
- 每次保存生成新的 96-bit GCM IV。
- 密文格式携带明确版本，便于未来迁移算法。
- 解密失败、Key 丢失或数据损坏时，将自建配置标记为需要重新验证，不尝试恢复明文。

密文保存到独立凭据 SharedPreferences 文件。该文件必须在 `backup_rules.xml` 和 `data_extraction_rules.xml` 中显式排除云备份与设备迁移。Token 不进入：

- 默认 `pixel_telo` SharedPreferences。
- Room 数据库。
- App 导出备份。
- `SavedStateHandle`。
- Compose 可保存状态。
- 日志、异常消息或崩溃上报。

### 6.3 原子提交顺序

验证成功后按以下顺序提交：

1. 加密并保存新 Token 密文。
2. 保存完整非敏感自建配置，状态标记为已验证但尚未启用。
3. 初始化或加载 `selfhost:<instance_id>` 的 source 配置。
4. 原子更新当前 Backend 指针。
5. 发布新的 `QueryBackendSnapshot` 与 UI 状态。

任一步失败时保留旧配置和旧 Backend。若新 Token 密文已经写入但后续提交失败，应删除本次未引用的密文记录，不影响旧凭据。

`SharedPreferences.commit()` 返回 `false` 或抛出异常属于普通存储命令失败，不等同于安全阻止。当前进程
继续使用命令开始前的 Snapshot、Client、活动槽和 UI 状态；journal 仅保留磁盘歧义并在下次启动裁决，
不得据此关闭旧 Client 或宣称候选 Backend 已生效。切回官方的选择 journal、候选 journal 与活动指针
任一失败都遵循相同规则。

## 七、URL 校验与重定向策略

自建 Base URL 必须满足：

- Scheme 为 `https`。
- 存在明确 host。
- 端口为空或位于 `1..65535`。
- Path 为空或仅为 `/`。
- 不包含 userinfo、query 或 fragment。
- 规范化后以 `/` 结尾，供 Retrofit 使用。
- 接受 DNS 域名、IPv4 和合法的括号 IPv6 表示。

禁止：

- `http` 或未知 scheme。
- 业务子路径。
- 内嵌用户名或密码。
- HTTPS 降级到 HTTP。
- 重定向到不同 host 或不同有效端口。

自建 Client 默认关闭自动 Redirect。若未来确需支持同源 Redirect，必须另行设计，不在第一版隐式放开。

## 八、TLS 与 SPKI Pinning

### 8.1 系统信任模式

系统信任模式使用 Android 默认 TrustManager 和 Hostname Verifier：

- 完整验证证书链。
- 验证证书有效期。
- 验证域名或 IP SAN。
- 不安装自定义 CA，不绕过证书错误。

### 8.2 SPKI Pinning 模式

Pinning 模式面向自签名证书，同时仍执行证书有效期与 SAN/Hostname 校验。其安全要求为：

1. 只信任用户保存的精确 SPKI SHA-256 Pin。
2. Pin 格式规范为 OkHttp 兼容的 `sha256/<Base64>`。
3. 只为配置 Base URL 的精确 host 安装 Pin。
4. 不把 Pin 加入全局 Trust Store。
5. 不与官方 Client 共享 Certificate Pinner 或 TrustManager。
6. 禁止重定向绕过已 Pin 的 host。

由于 Android 默认 TrustManager 会拒绝自签名证书，仅配置 OkHttp `CertificatePinner` 不足以实现自签名接入。自建 Pinning Client 需要专用 TrustManager：

- 解析服务端提供的证书链。
- 校验证书当前有效期。
- 使用标准 Hostname Verifier 校验目标域名或 IP SAN。
- 从叶子证书公钥计算 SPKI SHA-256。
- 使用常量时间比较验证精确 Pin。
- 任一步失败都终止 TLS 握手。

该 TrustManager 只能由 `SelfHostedQueryClientFactory` 在 Pinning 模式创建，不能注册到应用全局网络栈。

### 8.3 Pin 变化与地址变化

- Pin 改变：连接失败并要求重新配对，不自动接受新 Pin。
- Host 或端口改变：必须重新执行完整配置验证。
- IP 改变但服务端沿用原 TLS Key：Pin 可保持不变，但仍需验证新证书 SAN 并保存新 URL。
- Instance ID 改变：即使 Pin 相同，也视为另一实例并停止使用。

## 九、Bearer Token 注入

自建 Client 使用专用 Interceptor 从一次请求对应的不可变凭据 Snapshot 中读取 Token，并添加 Bearer Header。

约束如下：

- Token 不通过 Retrofit 方法参数传递，避免进入调用层异常或调试输出。
- 不配置 OkHttp Authenticator，防止 401 后隐式重试或跨地址发送凭据。
- Token 仅发送给验证配置中的精确 scheme、host 和有效端口。
- 网络日志在所有构建类型中都不得记录 Authorization Header 或请求体中的完整号码。
- 401 不重试，直接归类为凭据失效。

## 十、版本与身份强校验

### 10.1 最低版本单点配置

`gradle/libs.versions.toml` 增加：

```toml
[versions]
selfhostMinServer = "0.1.2"
```

`app/build.gradle.kts` 将该值写入：

```kotlin
buildConfigField(
    "String",
    "MIN_SELFHOST_SERVER_VERSION",
    "\"${libs.versions.selfhostMinServer.get()}\""
)
```

运行时代码只读取 `BuildConfig.MIN_SELFHOST_SERVER_VERSION`。正式发版提高最低版本时只修改 Version Catalog，不维护第二份业务常量。

### 10.2 SemVer 规则

- 接受严格 `MAJOR.MINOR.PATCH`。
- Release 默认拒绝预发布版本和非法版本。
- Build Metadata 不参与优先级比较，但输入仍必须符合 SemVer。
- Debug 可通过仅 Debug 可见且默认关闭的选项允许预发布版本。
- 最低版本自身在应用启动时也应可被严格解析；构建配置错误需尽早暴露。

不额外引入大型 SemVer 依赖。使用独立、小型、可审查的 Kotlin 值对象实现解析和比较，并用详细中文注释说明比较规则。

### 10.3 配置阶段校验

调用 info 接口后验证正文中的 Service、Version、API Version、Instance ID 和 capability。同时验证响应 Header 与正文身份一致。

### 10.4 运行阶段校验

自建 Client 的响应 Interceptor 检查每个鉴权响应，无论成功还是错误，都必须包含：

- `X-Pixel-Telo-Server-Version`
- `X-Pixel-Telo-API-Version`
- `X-Pixel-Telo-Instance-ID`

校验内容：

- Server Version 合法且不低于最低版本。
- API Version 等于 `2`。
- Instance ID 等于已验证配置。
- Header 格式合法且无相互冲突的重复值。

运行时发现版本降级、Header 缺失、协议变化或 Instance ID 改变时：

1. 当前请求失败。
2. 标记自建 Backend 为阻止使用。
3. 后续查询直接 Fail Open，不继续发起自建网络请求。
4. 首页显示不可忽略 WarningCard。
5. 不切换到官方实时查询。

用户完成“测试连接”并再次通过完整验证后，才解除阻止状态。

安全阻止使用独立于活动配置 SharedPreferences 的 no-backup AtomicFile 哨兵，并在发布 `Blocked` 前同步
落盘。活动记录同时保存阻止原因；若两条落盘路径都失败，则销毁 Android Keystore 凭据主密钥，使旧
密文跨重启不可解密。哨兵缺失以外的读取异常或损坏一律 Fail Closed。只有完整远端重验证、候选凭据
写入和活动指针提交均成功后，才能清除哨兵。

## 十一、配置验证与启用流程

用户编辑的是内存草稿，不实时覆盖已生效配置。

“测试并启用”流程：

1. 规范化并校验 Base URL。
2. 校验 Token 非空。
3. 系统信任模式忽略 Pin 输入；Pinning 模式要求合法 Pin。
4. 根据草稿创建临时 Self-host Client。
5. 调用 `/api/selfhost/v1/info`。
6. 校验服务名称、SemVer、最低版本、API Version、Instance ID、capabilities 和版本 Header。
7. 调用 `/api/v2/sources`，确认鉴权和真实 v2 查询契约可用。
8. 建立 Backend ID 并加载对应 source 配置。
9. 安全保存凭据和非敏感配置。
10. 原子切换当前 Backend。

修改正在使用的自建配置时执行相同流程。只修改 Token、URL、TLS 模式或 Pin 中任意一项，都必须重新验证。

用户可执行“仅测试连接”，验证成功后刷新元数据与告警状态，但是否启用仍由当前页面动作明确决定。为避免含义混乱，第一版优先提供单一主操作“测试并启用”；对已启用配置提供“重新测试”。

## 十二、source 配置隔离

现有单一 `query_source_config` 改为按 Backend ID 存储。每个 Backend 独立保存：

- 是否初始化。
- 已知 source 顺序。
- 已启用 source。
- 服务端默认 source。
- 最近一次可用 source。

持久化 Key 不直接拼接未经处理的外部字符串。对 Backend ID 使用稳定编码或将配置保存为以 Backend ID 为键的序列化 Map。

切换规则：

1. 切换后立即展示目标 Backend 的缓存状态。
2. 缓存为空时异步刷新 sources。
3. 来电实时查询不得临时刷新 sources。
4. 一次查询使用同一 Backend Snapshot 下的 source 配置。
5. 官方 source 不发送给自建实例，自建 source 不覆盖官方配置。
6. 自建实例的 Instance ID 改变后使用新的 Backend ID，不继承旧实例 source 配置。

### 12.1 现有配置迁移

当前 `query_source_config` 视为官方 Backend 配置。首次升级时：

1. 如果新格式尚不存在且旧配置存在，将旧配置迁移到 `official`。
2. 成功写入并验证可反序列化后，再删除旧 Key。
3. 迁移失败时保留旧 Key，并让官方 source 进入未初始化状态以便重新刷新。
4. 不把旧官方 source 自动复制到自建 Backend。

## 十三、查询与反馈数据流

### 13.1 实时查询

```text
来电
  → 用户白名单
  → 用户黑名单
  → 本地 MastDatabase
  → 读取 QueryBackendSnapshot
  → 读取该 Backend 的 source 配置
  → 在用户设置的 1..10 秒超时内请求当前 Backend
  → 生成拦截或放行结果
```

自建查询发生任何异常时直接返回 Fail Open 结果，不获取官方 Snapshot，也不再次发起实时网络查询。

### 13.2 反馈归属

`QueryResponse` 的反馈凭证采用可空语义，不再使用空字符串掩盖来源差异。查询结果额外携带 `backendId` 或等价的可信内部来源。

- 官方结果：允许保存服务端签发的反馈 Token。
- 自建结果：无条件丢弃反馈 Token，即使异常服务端返回该字段。
- 反馈入口：仅对官方 Backend 且存在有效 Token 的记录展示。
- 反馈提交：始终调用 `OfficialQueryClient`。
- App 导出备份继续排除反馈 Token。

自建模式不应仅靠“响应没有 token”判断反馈能力，必须依赖 Backend Snapshot 的 `feedbackSupported=false`。

## 十四、错误分类与 Fail Open

新增自建错误分类，避免把安全故障混为普通网络失败：

- URL 格式错误。
- DNS 或网络不可达。
- 连接或读取超时。
- 系统证书链失败。
- Hostname/SAN 不匹配。
- SPKI Pin 不匹配。
- Token 无效。
- 服务版本过低或 SemVer 非法。
- API Version 不兼容。
- Instance ID 改变。
- 必需 Header 缺失或冲突。
- 服务限流 `429`。
- 上游不可用 `503`。
- 上游超时 `504`。
- 其他服务端或响应解析错误。

来电路径中上述错误全部 Fail Open。告警策略分两类：

### 14.1 持续安全告警

以下问题阻止继续使用自建 Backend，并在主页显示不可忽略 WarningCard：

- Token 失效。
- TLS 或 Pin 失败。
- 服务端版本低于最低要求。
- API Version 不兼容。
- Instance ID 改变。
- 版本身份 Header 缺失、非法或冲突。

### 14.2 短暂运行错误

以下问题只记录脱敏状态，不弹通知、不自动显示持续 WarningCard：

- 偶发网络超时。
- `429` 限流。
- `500`、`503`、`504`。
- Provider 查询失败或普通响应解析失败。

连续失败统计可留作未来诊断能力，第一版不据此自动切换 Backend 或发送通知。

## 十五、设置页与首页 UI

### 15.1 设置页

在“在线查询”分类新增“实时查询服务器”：

- Pixel Telo 官方服务。
- 自建服务。

自建配置页面包含：

- Base URL 输入。
- Token 密码输入。
- TLS 模式选择：系统信任 / SPKI Pinning。
- SPKI Pin 输入，仅在 Pinning 模式显示。
- “测试并启用”主操作。
- 已启用配置的“重新测试”和“修改配置”。
- “切换回官方服务”。

摘要只展示：

- 当前 Backend 类型。
- 自建服务的脱敏 Host。
- 最近验证服务版本。
- 最近验证时间。
- 当前是否需要重新验证。

不展示 Token、完整配对文本、详细堆栈或包含凭据的完整 URL。

“脱敏 Host”使用固定算法，任何解析失败都显示通用不可用文案且绝不回退原始输入：DNS 的每个 label
仅保留首字符，其他内容统一为 `***`（单字符 label 为 `*`）；IPv4 仅保留第一段，后三段显示为
`***`；IPv6 不保留地址片段，统一显示 `[IPv6]`。因此 UI 不会展示完整域名、IPv4 或 IPv6 地址。

SPKI 模式的用户可见名称为“自签名证书 / 精确 SPKI Pin”，说明只信任精确 SHA-256 SPKI Pin，同时仍校验
证书有效期与域名/IP SAN；不得描述为在系统信任链上额外追加 Pin。

支持粘贴配对文本属于第一版范围，但不增加相机权限。解析器只接受服务端已定义且可严格识别的配对格式；若自建项目尚未稳定配对文本机器格式，则第一版先提供 URL、Token 和 Pin 分项粘贴，待契约固定后再加入整段解析，避免实现猜测性协议。

### 15.2 首页告警

持续安全告警显示不可忽略 WarningCard，提供与错误相匹配的操作：

- 测试连接。
- 更新配置。
- 查看最低版本要求。
- 切换回官方服务。

WarningCard 不展示 Token、完整 URL、Pin 或服务端响应正文。

### 15.3 source 设置

source BottomSheet 展示当前 Backend 的配置。切换 Backend 后不得短暂显示上一 Backend 的 source，状态应先切换到目标 Backend 缓存或加载状态。

## 十六、并发、一致性与性能

1. Backend 配置与 source 配置分别使用 Mutex 或等价串行化边界保护。
2. `QueryBackendSnapshot` 为不可变对象，包含完成一次请求所需的全部引用。
3. 查询与 source 刷新在整个网络、解码、持久化和条件发布期间持有 Snapshot lease；普通切换等待旧
   lease 清零后关闭，安全阻止立即 revoke/cancel。
4. Client 构建只发生在配置验证或加载已验证配置时，不在每次来电时重新构建 Retrofit。
5. Token 解密在构建或刷新 Snapshot 时完成，不在每个 Interceptor 调用中访问 Keystore。
6. 明文 Token 由 Dialog 一次性转换为 `CharArray` 后直接移交验证入口，并在成功、失败、取消和拒绝路径
   的 `finally` 中清零；不得进入 `mutableStateOf`、StateFlow、SavedState 或持久化草稿。
7. 自建运行时被安全错误阻止后，来电查询直接 Fail Open，避免反复进行必然失败的 TLS 或版本请求。
8. 本地数据库 50ms 目标和 3 秒硬上限不受本功能影响。
9. 网络查询继续由 `SpamNumberRepository.networkTimeoutMs()` 夹紧在 1 至 10 秒，并用 `withTimeout` 强制执行。

## 十七、进程恢复与异常状态

App 启动时：

1. 读取当前 Backend 指针。
2. 若为官方，直接构建官方 Snapshot。
3. 若为自建，读取非敏感配置并解密 Token。
4. 配置完整且未被安全错误阻止时，构建自建 Snapshot。
5. 凭据缺失、解密失败或配置损坏时，不发起自建请求，进入阻止状态并 Fail Open。
6. 不因本地配置损坏自动切回官方实时查询。
7. 安全阻止哨兵优先于活动记录；哨兵存在、损坏或读取失败时均不得恢复自建 Client。
8. 普通激活/选择 journal 只裁决重启后的磁盘状态，不覆盖当前进程保留的旧运行态。

Android Keystore Key 因系统恢复、锁屏安全设置变化或设备迁移而不可用时，要求用户重新输入 Token 并验证配置。

## 十八、日志与隐私

日志打印使用英文，代码注释和 KDoc 使用中文。

允许记录：

- Backend 类型和脱敏 Backend ID。
- HTTP 状态码。
- 错误分类。
- 查询耗时。
- 服务版本与 API Version。
- 脱敏 request ID。

禁止记录：

- Bearer Token 或加密前凭据。
- 完整手机号。
- 完整请求体或查询响应。
- 完整配对文本。
- 私人联调实例地址。
- 证书正文或完整 Pin。

异常向上传递时使用稳定错误类型和安全消息，不把底层异常中可能包含的 URL 或证书详情直接展示到主页。

## 十九、代码组织建议

建议新增或拆分以下组件，最终命名可在实施计划中按项目现有包结构微调：

```text
data/remote/
├── QueryApi.kt
├── SelfHostedApi.kt
└── SelfHostedModels.kt

data/repository/
├── QueryRepository.kt
└── SelfHostedConfigRepository.kt

data/query/
├── QueryBackendProvider.kt
├── QueryBackendSnapshot.kt
├── SelfHostedQueryClientFactory.kt
├── SelfHostedCompatibilityInterceptor.kt
├── SelfHostedCredentialStore.kt
├── SelfHostedConfigValidator.kt
└── SemanticVersion.kt
```

保持每个组件职责单一：

- Backend Provider 只管理当前 Snapshot。
- Client Factory 只构建隔离的网络 Client。
- Credential Store 只负责 Keystore 加解密和密文存储。
- Validator 只负责草稿与服务身份验证。
- Query Repository 只负责 source 和实时查询调度。
- UI/ViewModel 不直接持有 Retrofit、OkHttpClient 或明文 Token 的持久状态。

## 二十、迁移顺序

1. 拆分现有共享 Retrofit，明确官方同步与官方查询 Client。
2. 引入最低版本 BuildConfig 和 SemVer 值对象。
3. 实现自建配置模型、Keystore 凭据存储和备份排除规则。
4. 实现 URL、系统 TLS、SPKI Pinning 和临时 Client。
5. 实现 info、Header、版本和 Instance ID 验证。
6. 引入 Backend Snapshot 与 Provider。
7. 将 `QueryRepository` 改为按 Snapshot 查询并迁移官方 source 配置。
8. 实现自建 source 隔离和原子切换。
9. 隔离反馈保存、入口与提交。
10. 增加设置页、自建配置页面和首页 WarningCard。
11. 更新架构、隐私和同步文档。
12. 执行编译、Lint 与手工联调验证。

每个阶段都必须保持官方离线同步链路可用，避免一次性替换全部网络层后难以定位回归。

## 二十一、验证与验收

项目默认不新增、不运行单元测试。本功能按仓库约定执行编译、Lint 和手工/真机验证。

### 21.1 构建检查

```bash
./gradlew :app:assembleDebug
./gradlew lint
```

### 21.2 配置验证

1. 系统信任的域名证书可成功验证和启用。
2. 系统信任的公网 IP 证书可成功验证和启用。
3. 自签名域名证书配合正确 SPKI Pin 可成功启用。
4. 自签名公网 IP 证书配合正确 SPKI Pin 可成功启用。
5. 错误 Pin、过期证书和 SAN 不匹配均被拒绝。
6. HTTP、子路径、userinfo、query、fragment 和跨主机 Redirect 均被拒绝。
7. Token 错误返回明确鉴权分类，且旧配置保持生效。
8. 服务版本低于 `0.1.2`、非法 SemVer 或 API Version 非 2 均被拒绝。
9. info 正文和 Header 身份不一致时拒绝启用。

### 21.3 运行时验证

1. 官方和自建查询分别使用自己的 Client 与 source 配置。
2. 切换 Backend 时正在执行的请求不改变目标服务器。
3. 自建超时、401、429、500、503、504、TLS 和解析错误均 Fail Open。
4. 自建失败时不产生官方实时查询请求。
5. 运行中版本降级、Header 缺失或 Instance ID 改变后停止使用自建服务并显示告警。
6. 重新验证成功后解除安全阻止状态。
7. 网络超时继续严格夹紧在 1 至 10 秒。
8. 本地号码库查询性能不发生可感知退化。

### 21.4 凭据与隐私验证

1. Token 不出现在默认 SharedPreferences、Room、备份文件和日志中。
2. 凭据 SharedPreferences 被 Auto Backup 与设备迁移规则排除。
3. Keystore Key 丢失或密文损坏时要求重新配置，不泄露明文。
4. Release 与 Debug 日志都不输出 Authorization Header、完整号码或完整响应。
5. 受版本控制文件中不存在私人联调实例地址、Token 或证书信息。

### 21.5 反馈验证

1. 官方查询结果继续保存并提交有效反馈 Token。
2. 自建结果不保存反馈 Token。
3. 自建记录不显示反馈入口。
4. 自建 Client 永不调用反馈接口。
5. 历史官方反馈只能提交到官方 Client。

### 21.6 真机验证

必须在 Google Pixel 真机上验证：

- 来电筛选 Fail Open 行为。
- Directory Provider 展示不受 Backend 切换影响。
- 进程被回收后的 Backend 与凭据恢复。
- 系统证书和 SPKI Pinning 的域名/IP 连接。
- 首页 WarningCard 与设置页状态恢复。
- 切换 Backend 后的 source 展示和来电查询目标。

私人联调实例的具体地址和凭据只通过本地人工输入完成，不写入验证文档或提交产物。

## 二十二、文档更新范围

实现完成后检查并更新：

- `.agentdocs/architecture/mvvm-structure.md`：补充 Backend Provider 与三 Client 隔离。
- `.agentdocs/architecture/sync-strategy.md`：明确官方离线同步不受自建配置影响。
- `.agentdocs/prd/requirements.md`：补充自建实时查询产品要求。
- `.agentdocs/ui/main-screen.md`：补充自建安全告警状态。
- 设置页相关文档：补充 Backend 切换、配置验证与凭据展示边界。

## 二十三、开放项

当前没有阻止实施的开放项。配对文本的机器可解析格式若尚未由服务端稳定定义，第一版采用 URL、Token、Pin 分项粘贴，不自行发明公开协议；这不影响完整实现系统信任 TLS、SPKI Pinning、凭据保护和自建实时查询。
