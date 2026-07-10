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
    * **职责**: 数据的单一事实来源 (Single Source of Truth)。在本地存储 (**Room**) 和远程数据 (*
      *Retrofit**) 之间进行调度。
    * **组件**:
        * **本地**: Room Database (SQLite)。
        * **远程**: Retrofit + OkHttp。

## 联网查询数据流（v2 查询与反馈）

* **远程契约**: `data/remote/QueryApi.kt` 独立承载 mast v2 接口：`GET /api/v2/sources`、
  `POST /api/v2/query`、`POST /api/v2/query/feedback`。`SyncApi` 只保留离线数据库同步接口。
* **QueryRepository** (Koin 单例) 统一负责：
    * 拉取并合并 source 清单，配置以 JSON 存入 `SharedPreferences`（`query_source_config`），
      通过 `sourceState: StateFlow<QuerySourceState>` 驱动首页与设置页。
    * 合并规则：首次初始化跟随服务端 `default_sources`；已初始化时保留用户顺序与启停状态；
      新 source 追加到末尾且默认停用；已下线 source 保留配置但标记不可用；清单请求失败沿用缓存。
    * `queryNumber()` 仅发送“用户启用且最近已知可用”的有序 source；未初始化或全部失效时发送空列表，
      由服务端执行 `v1_fallback`。响应中的 invalid source warning 会同步更新本地不可用状态。
    * `submitFeedback()` 映射服务端状态码：`200` 成功、`409` 已提交、`410` 过期、`400` 无效，
      其余 HTTP/IO 错误视为可重试失败。
* **SpamNumberRepository** 保留黑白名单、本地离线库与最终拦截决策，仅把联网查询阶段委托给
  `QueryRepository.queryNumber()`。**实时来电查询不得临时请求 source 清单**。
* **反馈持久化**: `BlockedCall` (Room v6) 新增 `querySource`、`feedbackToken`、`feedbackStatus`
  三个字段；只有原本会落库的记录才保存反馈凭证，token 不写入日志与备份文件。
  `BlockedCallRepository.attachQueryResult()`/`updateFeedbackStatus()` 返回更新后的实体，
  调用方必须基于返回值继续操作，避免旧对象覆盖新字段。

## 依赖注入

我们使用 **Koin** 进行依赖注入。

* **Modules**: 为 Network, Database, Repository 和 ViewModel 定义模块。
* **Scopes**: 使用适当的作用域 (Singleton vs Factory/ViewModel)。

## 异步编程

* **Coroutines**: 用于所有异步操作。
* **Flow**: 用于响应式数据流（数据库观察，Service 事件）。
