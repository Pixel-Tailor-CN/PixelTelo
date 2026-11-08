# Pixel Telo 文档索引

按当前任务选择下列文档。协作约定与验证要求见 [AGENTS.md](../AGENTS.md)，无需每次预读全部文档。

## 文档结构

长期维护的规范按主题放入 `prd/`、`architecture/`、`ui/` 与 `workflow/`；
功能设计和实施记录放入 `plans/`；一次性审计、评估和验收报告仅保存在仓库外临时目录，不纳入 Git 或本索引。
根目录的 `AGENTS.md` 保留协作约定，本文作为统一文档入口。

### 产品与需求 (`prd/`)

* [需求规范](prd/requirements.md): 详细的功能和非功能需求，包括 Call Guardian, Data Sync 和性能约束。

### 设计与实施计划 (`plans/`)

以下文档保留当时的设计与实施记录。继续工作前先核对当前代码和用户目标；旧命令、检查清单及验证结果不自动适用于新任务。

* GitHub Actions Release：
  [设计](plans/2026-02-27-github-actions-release-design.md) ·
  [实施记录](plans/2026-02-27-github-actions-release.md)（现为本仓发布，旧跨仓方案已停用）
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
* 自建服务配对体验：
  [设计](plans/2026-09-27-self-hosted-pairing-design.md) ·
  [实施](plans/2026-09-27-self-hosted-pairing-implementation.md)（Issue #14，独立配对页面、JSON 粘贴与二维码扫描）
* 社区骚扰上报与本机上报管理：
  [App 设计](plans/2026-10-15-community-reports-app-design.md) ·
  [App 实施计划](plans/2026-10-15-community-reports-app-plan.md)（Issue #16，客户端已实现；服务端联调、迁移与 Pixel 真机待验收）
* Argon 首页与来电详情扁平化：
  [实施计划](plans/2026-10-17-argon-ui-refinement-plan.md)（已完成本轮 UI 实施及模拟器范围审查；缺样本、真机和 TalkBack 未验证项见计划）

* 实时查询服务器配置与扫码交互：
  [设计](plans/2026-10-18-query-server-ui-design.md) ·
  [实施计划](plans/2026-10-18-query-server-ui-implementation.md)（已实现；验证边界见计划）

* 支持开发者与评价入口：
  [设计与实施](plans/2026-11-08-support-and-review.md)

* 全仓审查问题修复：
  [实施计划](plans/2026-11-08-audit-cleanup.md)（来电响应预算、离线库安全更新、持久下载及遗留清理）

早期黑白名单、source 反馈、记录信息增强、本地号码标签及国际号码的实施计划已精简为结果与决策索引。
重复源码、固定设备编号、本机临时报告路径和一次性现场输出不再保留；完整历史仍可由 Git 追溯。

### 架构与技术 (`architecture/`)

* [MVVM 架构](architecture/mvvm-structure.md): 详细解释 MVVM、Backend Snapshot、网络 Client 隔离、
  source/反馈归属、社区上报及缓存时效、本地号码标签边界和 Repository 数据流。
* [原生集成](architecture/native-integration.md): `Directory Provider`、本地标签组合展示和
  `CallScreeningService` 的实现细节。
* [同步策略](architecture/sync-strategy.md): 数据库初始化、官方离线同步固定边界和实时查询 Fail Open 策略。

### 界面与体验 (`ui/`)

* [主页规范](ui/main-screen.md): 主页面的空/已填充状态、数据完整性检查、source 下线提示、
  自建安全告警、本地号码标签展示和“我的上报”管理。
* [设置页规范](ui/settings-screen.md): 分组顺序、行交互、权限状态及设置门禁和备份边界。
* [通知规范](ui/notifications.md)：自动检查更新、通知权限及待验证的通知展示方案。

### 工作流 (`workflow/`)

* [开发与技能维护](workflow/development.md)：任务推进、按范围验证、Skills 适用边界与 CI 区分。
* [Google Play 内部测试发布](workflow/google-play-publishing.md)：触发条件、签名与服务账号配置、版本号和首次验收。

## 快速链接

* [项目 README](../README.md)
