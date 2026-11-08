# 持久化本地号码标签实施记录

状态：已实现，历史计划已于 2026-11-08 精简归档。设计依据见[本地号码标签设计](2026-08-29-local-number-label-design.md)，当前边界见[架构说明](../architecture/mvvm-structure.md)和[主页规范](../ui/main-screen.md)。

## 实施决定

- AppDatabase v9→v10 增加独立 `local_number_labels` 表，每个归一化号码最多一个标签。
- `LocalNumberLabelRepository` 统一处理号码归一化、40 字符限制、空标签删除、观察与恢复冲突。
- `show_local_number_labels` 默认关闭；只控制普通展示，不影响标签编辑、管理、保存和备份恢复。
- 标签不进入 `CheckResult.label`、`BlockedCall.label` 或规则匹配，不改变识别、拦截、反馈和社区结论。
- `HomeViewModel` 仅观察当前 Paging 窗口；清空通话记录不删除标签。
- Directory、Overlay 和历史页面分别取得本地标签与来源标签。标签读取失败只隐藏增强展示，不能改变来电响应。
- 标签首次从既有记录创建，管理页只搜索、修改和删除；初始手动备份格式 v5 加入独立标签范围，当前版本以 `BackupData.kt` 为准。

## 历史验证与限制

当时记录报告过构建与 lint、v9→v10 空库及非空迁移、标签编辑动态关联、备份选择范围和模拟器 Directory/Overlay 展示验证。

Provider 返回更新不保证 Google Phone 立即刷新其缓存；模拟器中将拨号器提到前台不是可靠刷新触发器。Google Pixel 真机的 Provider 缓存刷新和来电显示仍须单独确认。旧模拟器结果不等于当前代码或真实设备验收通过。

## 当前实现入口

代码位于 `app/src/main/java/vip/mystery0/pixel/telo/`：

- `data/entity/LocalNumberLabel.kt`、`data/dao/LocalNumberLabelDao.kt`、`data/repository/LocalNumberLabelRepository.kt`。
- `data/preferences/LocalNumberLabelPreferences.kt`、`data/model/NumberLabelPresentation.kt`。
- `viewmodel/LocalNumberLabelEditorViewModel.kt`、`viewmodel/LocalNumberLabelsViewModel.kt`。
- `ui/components/LocalNumberLabelEditorDialogs.kt`、`ui/screen/LocalNumberLabelsScreen.kt`。
- `provider/TeloDirectoryProvider.kt`、`service/IncomingCallOverlay.kt`、`data/repository/BackupRepository.kt`。

已删除本机绝对路径、失效的外部审查报告引用、逐轮编译耗时、模拟器现场输出和重复实现代码；历史细节仍可通过 Git 历史追溯。
