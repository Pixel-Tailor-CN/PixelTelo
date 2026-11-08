# 用户自定义黑白名单实施记录

状态：已实现，历史计划已于 2026-11-08 精简归档。保留[初始设计](2026-03-07-user-blackwhitelist-design.md)作为决策背景，当前规则以[原生集成](../architecture/native-integration.md)和[架构说明](../architecture/mvvm-structure.md)为准。

## 实施结果

- 增加 `UserListEntry`、`UserListDao` 和 `UserListRepository`，初始迁移为 AppDatabase v1→v2。
- 号码规则支持精确和前缀匹配，识别先检查白名单、再检查黑名单，之后才访问骚扰库和实时查询。
- 增加独立名单页、首页快捷添加以及手动备份恢复中的名单范围。
- 后续版本补充标签与归属地规则、独立 `forceBlock`、标签包含匹配、国际格式语义、编辑及搜索；这些能力不应按初始模板重新实现。
- 当前通讯录最优先放行，`forceBlock=false` 的规则遵循全局仅提示与重复来电策略；初始设计中的直接拒接描述不再代表全部规则。

## 当前实现入口

代码位于 `app/src/main/java/vip/mystery0/pixel/telo/`：

| 职责 | 入口 |
| --- | --- |
| 实体、迁移和号码查询 | `data/entity/UserListEntry.kt`、`data/AppDatabase.kt`、`data/dao/UserListDao.kt` |
| 规则校验与匹配 | `data/PhoneNumberRuleMatcher.kt`、`data/repository/UserListRepository.kt` |
| 编辑、搜索与展示 | `viewmodel/ListViewModel.kt`、`ui/screen/ListScreen.kt` |
| 查询与系统响应 | `data/repository/SpamNumberRepository.kt`、`service/TeloCallScreeningService.kt` |
| 手动备份恢复 | `data/repository/BackupRepository.kt` |

本记录不复制作废的整份实现代码和发布命令。当前快照的构建与设备验收应重新执行，不能从旧计划的勾选状态推断。
