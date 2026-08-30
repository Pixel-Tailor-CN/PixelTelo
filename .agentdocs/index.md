# Pixel Telo 文档索引

欢迎阅读 Pixel Telo 项目文档。本文档旨在帮助您理解项目的需求、架构和开发指南。

## 文档结构

### 产品与需求 (`prd/`)

* [需求规范](prd/requirements.md): 详细的功能和非功能需求，包括 Call Guardian, Data Sync 和性能约束。

### 设计与实施计划 (`../docs/plans/`)

* GitHub Actions Release：
  [设计](../docs/plans/2026-02-27-github-actions-release-design.md) ·
  [实施](../docs/plans/2026-02-27-github-actions-release.md)
* 用户黑白名单：
  [设计](../docs/plans/2026-03-07-user-blackwhitelist-design.md) ·
  [实施](../docs/plans/2026-03-07-user-blackwhitelist-impl.md)
* source 优先级与查询反馈：
  [设计](../docs/plans/2026-07-10-source-priority-query-feedback-client-design.md) ·
  [实施](../docs/plans/2026-07-10-source-priority-query-feedback-client-plan.md)
* 拦截记录信息增强：
  [设计](../docs/plans/2026-07-26-blocked-call-record-enrichment-design.md) ·
  [实施](../docs/plans/2026-07-26-blocked-call-record-enrichment-implementation.md)
* 自建查询客户端：
  [设计](../docs/plans/2026-08-08-self-hosted-query-client-design.md) ·
  [实施](../docs/plans/2026-08-08-self-hosted-query-client-implementation.md)
* 来电查询结果复用：
  [实施](../docs/plans/2026-08-09-call-query-result-reuse-implementation.md)
* 通话状态震动：
  [设计](../docs/plans/2026-08-09-call-state-vibration-design.md) ·
  [实施](../docs/plans/2026-08-09-call-state-vibration-implementation.md)
* 自建服务摘要布局：
  [设计](../docs/plans/2026-08-09-self-hosted-summary-layout-design.md) ·
  [实施](../docs/plans/2026-08-09-self-hosted-summary-layout-implementation.md)
* 国际号码规则匹配：
  [设计](../docs/plans/2026-08-29-international-number-rule-matching-design.md) ·
  [实施](../docs/plans/2026-08-29-international-number-rule-matching-implementation.md)
* 持久化本地号码标签：
  [设计](../docs/plans/2026-08-29-local-number-label-design.md) ·
  [实施](../docs/plans/2026-08-29-local-number-label-implementation.md)（AppDatabase schema v10）

### 架构与技术 (`architecture/`)

* [MVVM 架构](architecture/mvvm-structure.md): 详细解释 MVVM、Backend Snapshot、网络 Client 隔离、
  source/反馈归属、本地号码标签边界和 Repository 数据流。
* [原生集成](architecture/native-integration.md): `Directory Provider`、本地标签组合展示和
  `CallScreeningService` 的实现细节。
* [同步策略](architecture/sync-strategy.md): 数据库初始化、官方离线同步固定边界和实时查询 Fail Open 策略。

### 界面与体验 (`ui/`)

* [主页规范](ui/main-screen.md): 主页面的空/已填充状态、数据完整性检查、source 下线提示、
  自建安全告警和本地号码标签展示。

### 工作流 (`workflow/`)

* 此目录包含任务流文档（待补充）。

## 快速链接

* [项目 README](../README.md)
