# 拦截记录信息增强实施记录

状态：已实现，历史计划已于 2026-11-08 精简归档。设计依据见[信息增强设计](2026-07-26-blocked-call-record-enrichment-design.md)，当前展示规范见[主页规范](../ui/main-screen.md)。

## 保留的实现决定

- AppDatabase v7→v8 增加可空的 `province`、`city`；旧记录不自动联网回填，不保存或展示运营商。
- Room `PagingSource` 按时间倒序提供记录，Paging 配置为每页 30 条、初始 30 条、预取 10 条、最多 90 条，关闭占位。
- `ContactRepository` 只解析已加载窗口中的去重号码，姓名不写数据库、备份或日志。
- 联系人变更使有界缓存失效，旧查询使用代次保护；页面恢复重新注册观察器，处理后续授权。
- 省市相同只显示一次，空值隐藏；联系人姓名、完整号码、归属地与实际处理结果保持独立语义。
- 初始手动备份格式为 v4；当前格式和完整字段以 `data/dto/BackupData.kt` 为准。

## 历史验证边界

原实施记录报告过 Debug 构建、lint、保留数据升级、联系人增删与授权变化、分页滚动及模拟器展示验证。它们属于当时快照，不能代替当前代码验证，也不代表 Google Pixel 真机链路已验证。

一次性的滚动采样值、临时联系人、数据导入命令、设备编号和现场清理输出已移出长期记录；不保留重复源码模板。

## 当前入口

- `BlockedCallDao`、`BlockedCallRepository`：分页和持久化。
- `ContactRepository`、`HomeViewModel`：窗口姓名解析与缓存。
- `CallRecordItem`、`HomeScreen`：列表和详情展示。
- `BackupRepository`：旧备份兼容。
