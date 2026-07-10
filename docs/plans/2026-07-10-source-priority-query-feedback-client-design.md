# 数据源优先级查询与质量反馈客户端设计

**日期**: 2026-07-10  
**状态**: 已批准，待实现

## 一、目标与范围

本期在 Pixel Telo 客户端接入 mast 服务端的 v2 查询能力，实现：

1. 用户可启用、停用并排序任意可用 source，至少保留一个当前可用 source。
2. 首次初始化跟随服务端默认顺序；后续新增 source 只进入可选列表，不自动启用。
3. 用户已启用的 source 被服务端下线后，打开应用时在首页显示 WarningCard。
4. 实时联网查询使用用户配置的有序 source 列表，并保存服务端返回的命中 source 与反馈 token。
5. 用户可在已保存的通话记录中提交一次“结果准确”或“结果不准确”反馈。

本期不改变现有通话记录策略。普通非骚扰来电仍只在“始终记录”开启时落库，不为扩大反馈覆盖面静默保存全部来电。

## 二、服务端接口

客户端新增独立 `QueryApi`，负责以下接口：

- `GET /api/v2/sources`：获取默认 source 顺序和当前可用 source。
- `POST /api/v2/query`：提交号码与有序 source 列表，获取查询结果和反馈 token。
- `POST /api/v2/query/feedback`：提交一次正向或负向反馈。

现有 `SyncApi` 只保留离线数据库同步接口，避免离线下载与实时查询职责继续耦合。

## 三、客户端分层

### 3.1 `QueryRepository`

新增 Koin 单例 `QueryRepository`，统一负责：

1. 调用 `QueryApi`。
2. 保存并合并 source 配置。
3. 暴露 source 配置 `StateFlow` 给首页与设置页。
4. 根据本地有效配置执行 v2 查询。
5. 提交反馈并映射服务端状态码。

`SpamNumberRepository` 保留黑白名单、本地离线库和最终拦截策略，只把联网查询阶段委托给 `QueryRepository`。实时来电查询不得临时请求 source 清单。

### 3.2 source 配置存储

source 配置使用现有 `SharedPreferences`，以 JSON 保存以下信息：

- 是否完成首次初始化。
- 全部已知 source 的用户排序。
- 用户启用的 source 集合。
- 最近一次服务端默认 source 顺序。
- 最近一次服务端可用 source 集合。
- 当前已启用但不可用的 source 集合。

不为少量配置新增 Room 表或 WorkManager。

## 四、source 合并规则

每次成功获取服务端清单后按以下规则合并：

1. 尚未初始化时，使用 `default_sources` 作为启用顺序，其余可用 source 追加在后并保持停用。
2. 已初始化时，保留用户已有 source 的相对顺序和启用状态。
3. 新 source 按服务端 priority 追加到列表末尾，默认停用。
4. 已下线 source 不从本地配置删除，保留原顺序和启用状态，并标记为不可用。
5. 用户进入设置后可选择其他可用 source；保存时至少需要一个当前可用 source 处于启用状态。
6. “恢复服务端默认”使用本次拉取的 `default_sources` 重建启用顺序，不启用默认列表以外的 source。

清单请求失败时继续使用最近一次缓存，不把网络失败误判为 source 下线。

## 五、启动检查与设置界面

`HomeViewModel` 在应用启动时触发一次 source 清单刷新。刷新成功后，如存在“已启用但不可用”的 source，首页显示不可忽略的 WarningCard，列出 source ID，并提供“调整数据源”按钮。

清单刷新在后台协程执行，不阻塞首页首帧。如果客户端既没有缓存清单又刷新失败，source 设置 BottomSheet 只显示错误与重试操作，不允许用空列表覆盖现有配置。

按钮通过 `MainActivity` 协调：切换到设置页，并直接打开 source 设置 BottomSheet。BottomSheet 包含：

- source ID 与可用状态。
- 启用/停用开关。
- 上移、下移按钮。
- 恢复服务端默认操作。
- 保存与取消操作。

不可用 source 保留展示但禁止重新启用。WarningCard 在用户修正配置且不存在已启用的不可用 source 后消失。

## 六、实时查询数据流

1. `SpamNumberRepository` 继续依次检查用户白名单、用户黑名单和本地离线库。
2. 需要联网时，`QueryRepository` 读取本地 source 状态。
3. 已初始化时，仅发送“用户启用且最近已知可用”的有序 source。
4. 尚未初始化或所有已选 source 均已失效时发送空列表，由服务端执行 `v1_fallback`。
5. 客户端解析 `source`、`feedback_token`、`query_mode`、`requested_sources`、`effective_sources` 与 `warnings`。
6. 服务端返回 invalid source warning 时，同步更新本地不可用提示状态，但不在来电主链路内请求 source 清单。

联网号码查询的有效超时必须限制在 1 至 3 秒，默认 3 秒，满足项目 3 秒硬上限。source 清单和反馈接口不参与来电实时决策，失败不得影响来电放行。

## 七、反馈持久化

`AppDatabase` 从 v5 升级到 v6，`blocked_calls` 新增：

- `querySource TEXT`：最终命中的服务端 source。
- `feedbackToken TEXT`：服务端签发的一次性 opaque token。
- `feedbackStatus TEXT NOT NULL DEFAULT 'UNAVAILABLE'`：本地反馈状态。

反馈状态包括：

- `UNAVAILABLE`：没有可用 token。
- `PENDING`：可提交反馈。
- `POSITIVE`：已提交“结果准确”。
- `NEGATIVE`：已提交“结果不准确”。
- `ALREADY_SUBMITTED`：服务端返回 token 已消费，但原反馈方向未知。
- `EXPIRED`：token 已过期。
- `INVALID`：token 无效。

仅原本会写入 `BlockedCall` 的结果保存反馈信息：

1. 真实来电联网查询成功且按现有策略落库时，在插入记录时保存 source、token 和 `PENDING`。
2. 手动联网重查成功后立即把 source、token 和 `PENDING` 写回原记录，即使用户不选择“写入备注”也不丢失 token。
3. 测试拦截结果仅在用户保存测试记录时持久化反馈信息。
4. 本地库命中、黑白名单命中和网络超时没有反馈 token，状态保持 `UNAVAILABLE`。

## 八、反馈交互与错误映射

记录详情 BottomSheet 在 `PENDING` 状态显示“结果准确”和“结果不准确”操作。提交期间按钮禁用，避免同一客户端重复请求。

服务端响应映射：

- `200`：保存 `POSITIVE` 或 `NEGATIVE`。
- `409`：保存 `ALREADY_SUBMITTED`，不猜测已提交的反馈方向。
- `410`：保存 `EXPIRED`。
- `400`：保存 `INVALID`。
- 网络异常、`429`、`5xx`：保留 `PENDING`，提示失败并允许重试。

提交成功或进入不可重试状态后不再显示反馈按钮，只展示本地状态。删除通话记录时仅删除本地 token，服务端未消费 token 按服务端策略自然过期。

## 九、隐私、备份与日志

1. `feedbackToken`、`feedbackStatus` 和 `querySource` 不写入备份文件，恢复的历史记录不可再次反馈。
2. 日志不得打印 feedback token、完整 `QueryResponse` 或 source 配置 JSON。
3. 新增日志使用英文，并避免输出完整电话号码。
4. 不新增权限，不新增后台定时任务。

## 十、错误与降级

1. source 清单刷新失败：沿用缓存，不显示错误下线提示。
2. 首次启动尚未取得清单：v2 查询发送空 source 列表，使用服务端默认 fallback。
3. 部分已选 source 下线：发送剩余有效 source，并在首页提示用户调整。
4. 全部已选 source 下线：发送空列表，保证查询仍可由服务端默认逻辑完成，同时持续显示 WarningCard。
5. v2 查询失败或超时：沿用现有“允许来电并记录 NETWORK_TIMEOUT”的安全策略。
6. 反馈失败：不影响通话记录和拦截结果。

## 十一、文档更新

实现完成后同步更新：

- `.agentdocs/architecture/mvvm-structure.md`
- `.agentdocs/architecture/native-integration.md`
- `.agentdocs/ui/main-screen.md`

如现有文档实际内容与上述主题无关，则只更新直接受影响的文档，并在最终说明中注明。

## 十二、验证范围

项目默认不新增、不运行单元测试。本期执行：

1. `./gradlew :app:assembleDebug`
2. `./gradlew lint`

人工验证重点：

1. 首次 source 初始化与服务端默认顺序一致。
2. source 可任意启停和排序，且可只启用一个。
3. 新 source 不自动启用。
4. source 下线后首页 WarningCard 正确展示并可直达设置。
5. 部分和全部 source 失效时查询均能安全降级。
6. 来电记录仅按现有策略落库，并正确保存反馈 token。
7. 反馈 `200`、`409`、`410`、`400` 与可重试错误的 UI 状态正确。
8. 旧数据库通过 v5→v6 migration 正常升级。
9. 备份文件不包含 feedback token。
10. Pixel 真机验证来电查询、拦截、记录与反馈完整链路。
