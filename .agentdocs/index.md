# Pixel Telo 文档索引

欢迎阅读 Pixel Telo 项目文档。本文档旨在帮助您理解项目的需求、架构和开发指南。

## 文档结构

### 产品与需求 (`prd/`)

* [需求规范](prd/requirements.md): 详细的功能和非功能需求，包括 Call Guardian, Data Sync 和性能约束。

### 架构与技术 (`architecture/`)

* [MVVM 架构](architecture/mvvm-structure.md): 详细解释 MVVM 实现、数据流和 Repository 模式。
* [原生集成](architecture/native-integration.md): `Directory Provider` 和 `CallScreeningService`
  的实现细节。
* [同步策略](architecture/sync-strategy.md): 数据库初始化、云端更新逻辑和数据同步策略。

### 界面与体验 (`ui/`)

* [主页规范](ui/main-screen.md): 主页面的状态（空数据 vs 已填充）、数据完整性检查和 UI 组件。

### 工作流 (`workflow/`)

* 此目录包含任务流文档（待补充）。

## 快速链接

* [项目 README](../GEMINI.md)
