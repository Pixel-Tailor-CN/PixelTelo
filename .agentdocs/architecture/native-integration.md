# 原生集成策略

Pixel Telo 致力于通过深度集成 Android 系统 API 来提供原生体验，而不是作为一个外挂层存在。

## 1. Directory Provider API

### 目的

将来电显示的标签（例如“骚扰电话”、“快递外卖”、“企业名称”）直接注入到系统的原生拨号器 (Google Phone app)
中。

### 实现

* **Provider**: 实现一个 `ContentProvider`，用于响应系统对来电号码的查询。
* **Permissions**: 需要申请 `READ_CONTACTS` 权限（专门用于 Provider 读取访问）并在
  `AndroidManifest.xml` 中配置元数据。
* **约束**: 这是**主要且唯一**的显示方式。
    * **严禁**: 将悬浮窗 (Overlay) 作为默认行为。Overlay 仅在特定 ROM 被验证不支持 Directory Provider
      时，作为备选降级方案。

### 本地号码标签组合

* Directory Provider 必须完整执行现有 `SpamNumberRepository.checkSpam()` 识别查询，本地标签
  不得短路、缩短或替代离线/联网查询，也不得回写 `CheckResult`。
* 显示开关开启时，本地标签与识别查询并行读取；本地标签只参与最终 Cursor 展示。
* 组合显示名由 `NumberLabelPresentation.directoryDisplayName()` 生成，格式为
  `本地 · 数据源`。空白标签丢弃；仅有一项时只显示该项。所有 `DISPLAY_NAME` 与自定义
  `LABEL` 字段使用同一个显示名。
* 是否返回 Directory 行与组合显示名分离：
    * 现有识别结果应拦截时，即使组合名为空也必须返回身份记录，显示名回退为原
      `result.label`（允许空字符串）。
    * 仅有本地标签的安全号码（`shouldBlock == false`）可以返回 Directory 身份记录。
    * 既不应拦截又无本地标签时返回空 Cursor。
* 本地标签查询失败按无标签降级，不得中断识别或影响来电侧 `respondToCall()`。

### 验证

* **测试**: 使用特定的测试号码（例如在拨号器搜索栏输入号码），验证 Provider 是否返回了正确的标签。

### 悬浮窗降级展示

* `IncomingCallOverlay` 仅在用户主动开启并授予 `SYSTEM_ALERT_WINDOW` 后显示联网归属地结果。
* 显示方式支持“固定时长”和“显示至通话结束”。固定时长允许配置 3 至 30 秒，默认 6 秒；
  显示至通话结束时由 `CallStateReceiver` 监听 `PHONE_STATE` 回到 `IDLE` 后立即移除悬浮窗，
  并保留 12 小时安全兜底，避免系统未送达广播时悬浮窗永久残留。
* 样式支持默认“卡片”和可选“简洁文本”。简洁文本不显示背景、边框、图标及重复电话号码，
  只居中显示归属地与标签，并通过文字阴影保证基本可读性。
* 本地标签只参与最终 Overlay 展示，不改变 Overlay 触发条件、拦截语义或 `BlockedCall`
  快照。`CallScreeningService` 仅在显示开关、Overlay 开关均开启且允许联网查询时并行读取
  本地标签；拒接来电仍不展示 Overlay。
* Overlay 分别接收 `localLabel` 与数据源标签，不拼接成一行。本地标签使用主强调色，数据源
  标签沿用原样式；两者都有时分两行，单项为空不留空白行。数据源标签仍排除
  `NETWORK_TIMEOUT`。
* 本地标签查询失败只隐藏 Overlay 本地标签，不得进入 Service 外层 `catch` 改变已决定的
  拦截结果，也不影响 `respondToCall()`。
* 悬浮窗控制器由 Koin 以进程级单例提供，使 `CallScreeningService` 与 `CallStateReceiver`
  操作同一个窗口实例；设置页的位置预览仍使用独立实例，避免影响真实来电状态。

### 通话状态震动

* 设置页“应用功能”提供“通话状态震动”开关，默认关闭，并明确说明 Android 对去电状态的限制。
* `CallStateVibrationController` 仅把 `RINGING → OFFHOOK` 识别为可靠的来电接通，并在该通话
  `OFFHOOK → IDLE` 时识别为挂断；未接、拒接、拦截和去电均不震动。
* 状态保存在 `SharedPreferences` 中，避免 Receiver 重建或重复广播造成重复震动；关闭开关时立即
  清除瞬时状态。
* 功能使用普通 `VIBRATE` 权限以及系统单次震动效果，不需要运行时授权，不启动常驻 Service。
  设备不支持震动或震动调用失败时只记录 warning 日志，不得阻断悬浮窗移除与通话结束反馈流程。
* 启用开关前必须取得 `READ_PHONE_STATE` 运行时权限，权限缺失或被撤销时保持关闭；瞬时状态按
  subscription 隔离，并通过 wall clock 与 elapsed realtime 双重校验清理跨重启状态。等待接听
  状态最多保留 12 小时，已接通状态在同一启动周期内不因超长通话而过期；应用启动时会再次校验
  权限并持久化关闭已经失去权限的开关。

## 2. CallScreeningService

### 目的

拦截来电，并根据本地数据库决定是允许、静音还是拒绝通话。

### 逻辑流程

1. **来电**: 系统绑定 `CallScreeningService`。
2. **号码标准化与查询**:
    * Service 保留系统提供的原始号码用于界面展示和 `BlockedCall` 记录。
    * 查询前由 `PhoneNumberNormalizer` 统一去除 `+86`；对于中国移动“和多号/一卡多号”，
      仅去除已确认的 `125831`、`125832`、`125833` 前缀，并保留后续号码格式（包括境外号码的
      `00` 国家代码）。
    * 标准化号码用于本地 **Room** 数据库、联网接口、号码黑白名单和重复来电判断。
      号码名单优先匹配标准化号码；若发生了和多号前缀转换且未命中，再回退匹配升级前可能保存的
      原始号码规则。
3. **性能约束 (CRITICAL)**:
    * **本地查询**: 必须在 **100ms** 内完成。
    * **网络回退**: 若本地无结果，通过 `QueryRepository.queryNumber()` 发起 v2 联网查询，
      有效超时可在设置中配置（**1 至 10 秒**，默认 5 秒，`withTimeout` 强制执行）。
      超时则放行并记录 `NETWORK_TIMEOUT`。
    * **source 清单**: 联网查询只读取本地缓存的 source 配置，**严禁在来电主链路内请求
      source 清单**；source 清单与反馈接口的失败不得影响来电放行。
4. **决策**:
    * **放行**: 未找到匹配项或号码安全。白名单（号码/标签/归属地）永远放行，不受任何开关影响。
    * **拒绝**: 在黑名单中找到匹配项，或自动识别为骚扰。
    * **“仅提示不拦截”叠加规则**: 每条黑名单规则自带 `forceBlock` 配置（UI 中的
      “忽略‘仅提示不拦截’”开关，新建默认开启）。`forceBlock=true` 的规则命中时直接挂断，
      无视“仅提示不拦截”与“短时间重复来电”；`forceBlock=false` 的规则与自动识别结果一样
      遵循全局开关（仅提示时放行并记录）。v6→v7 迁移时既有标签/归属地黑名单补为 true
      （保持原强制行为），既有号码黑名单保持 false（保持原软行为）。
    * **重复来电策略**: 对设定窗口内再次呼入的已标记号码提供“不修改”“放行但静音”“完全放行”
      三种策略。“不修改”不参与重复来电判断；“放行但静音”记录为 `PASS_BUT_NOTIFY`；“完全放行”
      正常响铃并记录为 `PASS`。两种放行策略的记录备注必须明确标注实际处理方式。
5. **动作**: 调用 `respondToCall`。
    * 如果被拦截，设置 `skipCallLog` 为 `false`（确保拦截记录出现在历史记录中）并设置 `disallowCall` 为
      `true`。
6. **落库**: 按现有记录策略写入 `BlockedCall` 时，一并保存 v2 响应中的 `querySource` 与
   `feedbackToken`（状态 `PENDING`）；本地命中、黑白名单命中与超时没有 token，状态保持
   `UNAVAILABLE`。不为反馈扩大记录范围。本地标签不得写入或覆盖 `BlockedCall.label`。
7. **通话结束反馈提醒**: 放行且记录带 token 的来电，筛查时把记录 id 写入 `SharedPreferences`
   标记；`receiver/CallStateReceiver`（需 `READ_PHONE_STATE`）监听 `PHONE_STATE` 回到 IDLE 后，
   经 `QueryFeedbackNotifier` 弹出通知，内容含号码、标签与数据源，提供“结果准确/结果不准确”
   两个按钮；`receiver/FeedbackActionReceiver` 直接提交反馈并把终态写回 Room。被拒接的来电
   不响铃、不提醒。设置项 `feedback_notification`（默认开启）可关闭；标记有效期 2 小时，
   通知或权限缺失时静默跳过，不影响来电链路。

### 服务隔离

`CallScreeningService` 必须保持极度精简。**严禁**在此处执行网络请求或繁重的数据库写入操作。
本地标签读取失败必须 Fail Open：只影响展示，不得阻止或改写 `respondToCall()`。

## 3. Smartspacer 插件（静默拦截数量 Complication）

### 目的

在关闭“仅提示不拦截”或黑名单规则强制拦截时，来电会被直接挂断且没有任何提醒。通过
[Smartspacer](https://github.com/KieronQuinn/Smartspacer) Complication 在锁屏/AtAGlance
显示“用户不知情时被拦截的来电数量”，点按打开应用。

### 实现

* **SDK**: `com.kieronquinn.smartspacer:sdk-plugin`（mavenCentral），插件本质是导出的
  `ContentProvider`，受 Smartspacer 专属权限保护，应用自身无需新增运行时权限。
* **Provider**: `smartspacer/SilentInterceptComplicationProvider`，注册于 Manifest
  （action `com.kieronquinn.app.smartspacer.COMPLICATION`）。
* **计数口径**: `smartspacer/SmartspacerInterceptRepository`，统计 `resultType` 为
  `INTERCEPT` 或 `BLACK_LIST`（即被直接挂断、用户无感知）且 `blockTime` 晚于基线的记录数；
  `PASS_BUT_NOTIFY` 等用户可感知的记录不计入。
* **清零机制**: 基线时间戳存于 `SharedPreferences`。`MainActivity.onResume` 调用
  `acknowledge()` 更新基线（进入应用即视为已知晓），计数归零后 Complication 自动隐藏。
* **刷新推送**: `BlockedCallRepository.insert` 写入静默拦截记录后调用
  `SmartspacerIntegration.notifyChanged()`；该调用仅发送异步通知，不阻塞来电响应路径，
  且用 `runCatching` 包裹（未安装 Smartspacer 时静默跳过）。
