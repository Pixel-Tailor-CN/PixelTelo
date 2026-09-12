# Pixel Telo 文档索引

按当前任务选择下列文档。协作约定与验证要求见 [AGENTS.md](../AGENTS.md)，无需每次预读全部文档。

## 文档结构

长期维护的规范按主题放入 `prd/`、`architecture/`、`ui/` 与 `workflow/`；
功能设计和实施记录放入 `plans/`，一次性审计与评估放入 `reports/`。
根目录的 `AGENTS.md` 保留协作约定，本文作为统一文档入口。

### 产品与需求 (`prd/`)

* [需求规范](prd/requirements.md): 详细的功能和非功能需求，包括 Call Guardian, Data Sync 和性能约束。

### 设计与实施计划 (`plans/`)

以下文档保留当时的设计与实施记录。继续工作前先核对当前代码和用户目标；旧命令、检查清单及验证结果不自动适用于新任务。

* GitHub Actions Release：
  [设计](plans/2026-02-27-github-actions-release-design.md) ·
  [实施](plans/2026-02-27-github-actions-release.md)
* 用户黑白名单：
  [设计](plans/2026-03-07-user-blackwhitelist-design.md) ·
  [实施](plans/2026-03-07-user-blackwhitelist-impl.md)
* source 优先级与查询反馈：
  [设计](plans/2026-07-10-source-priority-query-feedback-client-design.md) ·
  [实施](plans/2026-07-10-source-priority-query-feedback-client-plan.md)
* 拦截记录信息增强：
  [设计](plans/2026-07-26-blocked-call-record-enrichment-design.md) ·
  [实施](plans/2026-07-26-blocked-call-record-enrichment-implementation.md)
* 自建查询客户端：
  [设计](plans/2026-08-08-self-hosted-query-client-design.md) ·
  [实施](plans/2026-08-08-self-hosted-query-client-implementation.md)
* 来电查询结果复用：
  [实施](plans/2026-08-09-call-query-result-reuse-implementation.md)
* 通话状态震动：
  [设计](plans/2026-08-09-call-state-vibration-design.md) ·
  [实施](plans/2026-08-09-call-state-vibration-implementation.md)
* 自建服务摘要布局：
  [设计](plans/2026-08-09-self-hosted-summary-layout-design.md) ·
  [实施](plans/2026-08-09-self-hosted-summary-layout-implementation.md)
* 国际号码规则匹配：
  [设计](plans/2026-08-29-international-number-rule-matching-design.md) ·
  [实施](plans/2026-08-29-international-number-rule-matching-implementation.md)
* 持久化本地号码标签：
  [设计](plans/2026-08-29-local-number-label-design.md) ·
  [实施](plans/2026-08-29-local-number-label-implementation.md)（AppDatabase schema v10）

### 架构与技术 (`architecture/`)

* [MVVM 架构](architecture/mvvm-structure.md): 详细解释 MVVM、Backend Snapshot、网络 Client 隔离、
  source/反馈归属、本地号码标签边界和 Repository 数据流。
* [原生集成](architecture/native-integration.md): `Directory Provider`、本地标签组合展示和
  `CallScreeningService` 的实现细节。
* [同步策略](architecture/sync-strategy.md): 数据库初始化、官方离线同步固定边界和实时查询 Fail Open 策略。

### 界面与体验 (`ui/`)

* [主页规范](ui/main-screen.md): 主页面的空/已填充状态、数据完整性检查、source 下线提示、
  自建安全告警和本地号码标签展示。
* [通知规范](ui/notifications.md)：自动检查更新、通知权限及待验证的通知展示方案。

### 工作流 (`workflow/`)

* [开发与技能维护](workflow/development.md)：任务推进、按范围验证、Skills 适用边界与 CI 区分。

### 审计与评估 (`reports/`)

* [2026-09-12 协作规则审计](reports/2026-09-12-agent-guidance-audit.md)：官方依据、发现、修改范围与验证记录；仅在追溯本次调整时阅读。

## 快速链接

* [项目 README](../README.md)
