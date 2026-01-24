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
    * **网络回退**: 若本地无结果，发起网络查询，超时时间 **3s**。超时则放行。
4. **决策**:
    * **放行**: 未找到匹配项或号码安全。
    * **拒绝**: 在黑名单中找到匹配项。
5. **动作**: 调用 `respondToCall`。
    * 如果被拦截，设置 `skipCallLog` 为 `false`（确保拦截记录出现在历史记录中）并设置 `disallowCall` 为
      `true`。

### 服务隔离

`CallScreeningService` 必须保持极度精简。**严禁**在此处执行网络请求或繁重的数据库写入操作。
