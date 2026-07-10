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

### 验证

* **测试**: 使用特定的测试号码（例如在拨号器搜索栏输入号码），验证 Provider 是否返回了正确的标签。

## 2. CallScreeningService

### 目的

拦截来电，并根据本地数据库决定是允许、静音还是拒绝通话。

### 逻辑流程

1. **来电**: 系统绑定 `CallScreeningService`。
2. **查询**: Service 查询本地 **Room** 数据库中的电话号码。
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
5. **动作**: 调用 `respondToCall`。
    * 如果被拦截，设置 `skipCallLog` 为 `false`（确保拦截记录出现在历史记录中）并设置 `disallowCall` 为
      `true`。
6. **落库**: 按现有记录策略写入 `BlockedCall` 时，一并保存 v2 响应中的 `querySource` 与
   `feedbackToken`（状态 `PENDING`）；本地命中、黑白名单命中与超时没有 token，状态保持
   `UNAVAILABLE`。不为反馈扩大记录范围。
7. **通话结束反馈提醒**: 放行且记录带 token 的来电，筛查时把记录 id 写入 `SharedPreferences`
   标记；`receiver/CallStateReceiver`（需 `READ_PHONE_STATE`）监听 `PHONE_STATE` 回到 IDLE 后，
   经 `QueryFeedbackNotifier` 弹出通知，内容含号码、标签与数据源，提供“结果准确/结果不准确”
   两个按钮；`receiver/FeedbackActionReceiver` 直接提交反馈并把终态写回 Room。被拒接的来电
   不响铃、不提醒。设置项 `feedback_notification`（默认开启）可关闭；标记有效期 2 小时，
   通知或权限缺失时静默跳过，不影响来电链路。

### 服务隔离

`CallScreeningService` 必须保持极度精简。**严禁**在此处执行网络请求或繁重的数据库写入操作。
