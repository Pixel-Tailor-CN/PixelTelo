# 通话状态震动实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**目标：** 增加默认关闭的通话状态震动设置，仅在可靠识别的来电接通和挂断状态转换中短震一次。

**架构：** 新建进程级 `CallStateVibrationController` 封装电话状态机与震动 API，`CallStateReceiver` 将每次 `PHONE_STATE` 广播转交给控制器，同时保留原有 `IDLE` 处理。设置由 `SettingViewModel` 和 `AppFeaturesPreferences` 管理，状态使用现有 `SharedPreferences` 持久化以抵抗 Receiver 重建与重复广播。

**技术栈：** Kotlin、Android `BroadcastReceiver`、`TelephonyManager`、`VibratorManager` / `Vibrator`、Koin、Jetpack Compose Preference。

## 全局约束

- MinSDK 29，TargetSDK 35，JVM Target 21。
- 用户沟通、代码注释与项目文档使用中文，日志使用英文。
- 开关默认关闭；去电、未接、拒接和拦截来电不震动。
- 不新增或运行单元测试，不执行包含单元测试的 Gradle 任务。
- 不修改或覆盖工作区中与本功能无关的既有变更。

---

### Task 1：设置、权限与文案

**文件：**
- 修改：`app/src/main/AndroidManifest.xml`
- 修改：`app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt`
- 修改：`app/src/main/java/vip/mystery0/pixel/telo/ui/screen/settings/AppFeaturesPreferences.kt`
- 修改：`app/src/main/res/values/strings.xml`
- 修改：`app/src/main/res/values-zh/strings.xml`

**接口：**
- 产出：`SettingViewModel.KEY_CALL_STATE_VIBRATION`、`callStateVibrationEnabled` 和 `updateCallStateVibrationEnabled(Boolean)`。

- [x] 声明普通权限 `android.permission.VIBRATE`。
- [x] 在 `SettingViewModel` 中以默认值 `false` 读写开关，关闭时清除控制器保存的瞬时状态。
- [x] 在“应用功能”分类添加开关，并在开关下方始终显示可靠识别范围说明。
- [x] 同步补齐中英文标题和说明字符串。

### Task 2：实现可靠来电状态机与震动

**文件：**
- 新建：`app/src/main/java/vip/mystery0/pixel/telo/receiver/CallStateVibrationController.kt`
- 修改：`app/src/main/java/vip/mystery0/pixel/telo/di/AppModule.kt`

**接口：**
- 消费：`SettingViewModel.KEY_CALL_STATE_VIBRATION`。
- 产出：`CallStateVibrationController.onPhoneStateChanged(String?)` 和 `clearState(SharedPreferences)`。

- [x] 用 `WAITING_FOR_ANSWER` 与 `ANSWERED` 两个持久状态实现 `RINGING → OFFHOOK → IDLE` 状态机。
- [x] 只在首次 `WAITING_FOR_ANSWER → ANSWERED` 和 `ANSWERED → IDLE` 转换时触发系统单次震动。
- [x] 对关闭状态、去电路径、重复广播和异常输入静默清理或忽略，震动失败只打印英文 warning 日志。
- [x] 在 Koin 中注册进程级单例控制器。

### Task 3：接入现有电话广播与更新架构文档

**文件：**
- 修改：`app/src/main/java/vip/mystery0/pixel/telo/receiver/CallStateReceiver.kt`
- 修改：`.agentdocs/architecture/native-integration.md`

**接口：**
- 消费：`CallStateVibrationController.onPhoneStateChanged(String?)`。

- [x] Receiver 收到每次 `PHONE_STATE` 后先交给震动控制器，再在 `IDLE` 延续悬浮窗移除与反馈提醒流程。
- [x] 确保控制器异常不会阻断现有来电结束处理。
- [x] 文档记录开关默认值、可靠状态范围、去电限制、权限和失败隔离策略。

### Task 4：静态检查与构建验证

**文件：**
- 检查：上述所有本功能文件。

- [x] 执行 `git diff --check`，确保没有空白错误。
- [x] 执行 `./gradlew :app:assembleDebug`，预期输出 `BUILD SUCCESSFUL`。
- [x] 执行 `./gradlew lint`，预期输出 `BUILD SUCCESSFUL`。
- [x] 检查 `git diff` 与 `git status`，确认没有改动依赖版本，也没有覆盖无关工作区修改。
