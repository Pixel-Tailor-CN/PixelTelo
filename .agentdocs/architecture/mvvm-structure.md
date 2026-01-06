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

## 依赖注入

我们使用 **Koin** 进行依赖注入。

* **Modules**: 为 Network, Database, Repository 和 ViewModel 定义模块。
* **Scopes**: 使用适当的作用域 (Singleton vs Factory/ViewModel)。

## 异步编程

* **Coroutines**: 用于所有异步操作。
* **Flow**: 用于响应式数据流（数据库观察，Service 事件）。
