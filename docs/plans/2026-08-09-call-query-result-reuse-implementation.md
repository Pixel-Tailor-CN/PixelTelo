# 来电查询结果复用实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将同一次来电触发的 CallScreeningService 与 Directory Provider 联网查询合并为一次，并在 1 分钟内复用结果。

**Architecture:** 在单例 `SpamNumberRepository` 的联网边界维护小容量进程内缓存和进行中请求表。缓存键包含规范化号码、Backend ID、activationId 和网络超时，避免跨 Backend 复用并规避进程启动时 source 状态发布竞态；手动强制联网查询绕过复用层。

**Tech Stack:** Kotlin、Kotlin Coroutines、Koin、Retrofit

## Global Constraints

- 缓存有效期固定为 1 分钟，只存在于当前 App 进程。
- 缓存不得持久化号码、响应或 Token，也不得在日志中输出完整号码。
- 同一缓存键的并发请求采用 single-flight，共享一个 `Deferred`。
- `forceNetworkQuery=true` 必须绕过缓存，保留用户手动重试语义。
- Backend activation 或超时设置变化后不得命中旧结果；source 设置变化最多复用剩余缓存期内的旧联网响应。
- 不新增或运行单元测试；执行编译、Debug 构建、Lint 和模拟器复现。

---

### Task 1: 实现联网结果复用

**Files:**
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/SpamNumberRepository.kt`

**Interfaces:**
- Consumes: `QueryBackendProvider.snapshot()`、`QueryRepository.sourceState`、`networkTimeoutMs()`
- Produces: `queryNetworkForCheck(phone: String): ReusedNetworkResult`

- [x] **Step 1: 定义缓存模型和生命周期参数**

增加 60 秒 TTL、32 条完成缓存和进行中请求容量限制、基于 `SystemClock.elapsedRealtime()` 的缓存时间，以及包含 canonical number 和 Backend 查询上下文的缓存键；canonical number 与真实网络请求参数完全一致。

- [x] **Step 2: 合并进行中请求**

使用 `Mutex`、`CoroutineScope(SupervisorJob() + Dispatchers.IO)` 和 `Deferred`。同一键只有首个调用发起 `queryRepository.queryNumber()`，其他调用等待同一结果，调用方取消不得取消共享请求。

- [x] **Step 3: 保存并复用完成结果**

成功响应和非取消异常均缓存 1 分钟；超时保持原异常类型。缓存命中返回原始联网耗时，并打印不含号码的英文复用日志。

- [x] **Step 4: 接入 checkSpam**

普通来电查询使用复用入口；`queryNetwork()` 和 `forceNetworkQuery=true` 继续直接请求。现有 `CheckResult` 构建、规则判断与 Fail Open 行为保持不变。

### Task 2: 文档与验证

**Files:**
- Modify: `docs/plans/2026-08-08-self-hosted-query-client-design.md`
- Modify: `docs/plans/2026-08-08-self-hosted-query-client-implementation.md`

**Interfaces:**
- Consumes: Task 1 的缓存策略
- Produces: 与代码一致的架构和验证记录

- [x] **Step 1: 更新查询复用设计**

记录 CallScreeningService 与 Directory Provider 会重复查询、1 分钟缓存、single-flight、缓存键和绕过规则。

- [x] **Step 2: 执行静态与构建验证**

运行 `git diff --check`、`:app:compileDebugKotlin --rerun-tasks`、`:app:assembleDebug` 和 `lint`。

- [x] **Step 3: 模拟器复现**

安装 Debug APK，清空 Logcat 后再次呼叫同一号码；确认只有首个调用打印真实联网结果，后续 Provider 调用打印缓存复用日志，服务端仅收到一个查询请求。
