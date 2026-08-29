# 国际号码自定义规则匹配回归修复实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 Issue #10，使 `+国家代码` 与 `00国家代码` 国际号码可稳定命中本地自定义黑白名单，同时与无国际标记的纯数字号码保持不等价，并阻止明确国际号码进入国内数据库或任何实时查询 Backend。

**Architecture:** 新增纯 Kotlin `PhoneNumberRuleMatcher`，集中提供规则存储规范、有限候选生成、国际号码判断和 UI 内存匹配；`UserListRepository` 与 `HomeViewModel` 共同消费该组件。`SpamNumberRepository` 在本地黑白名单之后增加明确国际号码门禁，Test Intercept 改为完全复用真实来电配置。

**Tech Stack:** Kotlin、Room、Kotlin Coroutines、Jetpack Compose、Android CallScreeningService、Koin。

**Spec:** `docs/plans/2026-08-29-international-number-rule-matching-design.md`

## Global Constraints

- MinSDK 29，TargetSDK 35，JVM Target 21。
- `+国家代码` 与 `00国家代码` 明确国际格式等价；无 `+`/`00` 标记的纯数字格式与它们不等价。
- `+86`、`0086` 与现有国内号码标准化保持兼容；已确认和多号前缀行为不得回归。
- 明确非中国国际号码只允许本地自定义黑白名单匹配；未命中时不得打开 MastDatabase、获取 Backend Snapshot 或访问官方/Self-hosted Backend。
- `forceNetworkQuery` 不得绕过国际号码查询门禁。
- Test Intercept 必须遵循真实来电配置及“不联网查询”设置。
- 不修改 Room schema、数据库版本、备份版本、Manifest 权限、Retrofit API 或 Gradle 依赖。
- 号码、规则正文、URL、Token、Header 与服务端响应正文不得写入日志；日志使用稳定英文分类。
- 项目默认不新增、不运行单元测试；不得执行 `test`、`testDebugUnitTest` 或 `check`。
- 每个代码任务执行 `./gradlew :app:assembleDebug`；最终执行 `./gradlew lint`、`git diff --check` 与 Android 模拟器验收。
- 实施型子 Agent 本次临时使用精确模型 `cliproxyapi/gemini-3.7-flash-high`；审查型子 Agent继续使用 `reviewer` 角色默认模型。

---

### Task 1: 共享国际号码规则匹配器与名单一致性

**Files:**
- Create: `app/src/main/java/vip/mystery0/pixel/telo/data/PhoneNumberRuleMatcher.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/PhoneNumberNormalizer.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/UserListRepository.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/HomeViewModel.kt`

**Interfaces:**
- Produces:
  - `PhoneNumberRuleMatcher.normalizeRuleForStorage(value: String): String`
  - `PhoneNumberRuleMatcher.matchCandidates(phoneNumber: String): List<String>`
  - `PhoneNumberRuleMatcher.ruleMatches(rule: String, isPrefix: Boolean, phoneNumber: String): Boolean`
  - `PhoneNumberRuleMatcher.isExplicitInternational(phoneNumber: String): Boolean`
- Consumes: existing `PhoneNumberNormalizer.normalizeForLookup()` and `UserListDao.findMatch()`.
- Later tasks consume `isExplicitInternational()` as the query gate and depend on Repository/UI matching parity.

- [ ] **Step 1: Extend country-code normalization for `0086` without changing explicit foreign numbers**

Update `PhoneNumberNormalizer.normalizeCountryCode()` so compact `0086...` inputs remove `0086` in the same domestic path as `+86...`. Preserve non-China `00...` values unchanged. Keep the existing North American full-number compatibility in this legacy lookup normalizer; the new rule matcher must not use that behavior for explicit international rules.

Expected examples:

```text
+8613800138000 -> 13800138000
008613800138000 -> 13800138000
0015197292346 -> 0015197292346
+15197292346 -> 15197292346  // legacy domestic lookup behavior remains here
```

- [ ] **Step 2: Implement `PhoneNumberRuleMatcher` with fixed international semantics**

Create the object with these rules:

```kotlin
object PhoneNumberRuleMatcher {
    fun normalizeRuleForStorage(value: String): String
    fun matchCandidates(phoneNumber: String): List<String>
    fun ruleMatches(rule: String, isPrefix: Boolean, phoneNumber: String): Boolean
    fun isExplicitInternational(phoneNumber: String): Boolean
}
```

Implementation requirements:

- private compacting removes whitespace, `-`, `(` and `)` but preserves leading `+`;
- `+...` excluding `+86...` is explicit international;
- `00...` excluding `0086...` is explicit international;
- explicit international canonical form is `+` plus digits after the `00` marker when present;
- explicit international candidates are ordered `[+canonical, 00-compatible]`, distinct, and never include the markerless digits;
- non-explicit candidates reproduce current domestic behavior: normalized lookup value first, then the country-code-normalized pre-和多号 value only when different;
- `normalizeRuleForStorage()` stores explicit `00...` as `+...`, keeps explicit `+...`, and delegates domestic/markerless values to `normalizeForLookup()`;
- `ruleMatches()` compares the compact stored rule directly against every candidate using exact or `startsWith` semantics;
- blank rule/candidate never matches;
- no phone number or rule text is logged.

Required behavior table:

```text
rule +1519 prefix, phone +15197292346 -> true
rule +1519 prefix, phone 0015197292346 -> true
rule 001519 prefix, phone +15197292346 -> true
rule 1519 prefix, phone +15197292346 -> false
rule 1519 prefix, phone 0015197292346 -> false
rule 1519 prefix, phone 15197292346 -> true
rule +15197292346 exact, phone 0015197292346 -> true
rule 15197292346 exact, phone +15197292346 -> false
```

- [ ] **Step 3: Route Room rule storage and lookup through the matcher**

In `UserListRepository`:

- `add()` uses `normalizeRuleForStorage()` only for number rules;
- tag/location rules retain `trim()` behavior;
- `findNumberMatch()` iterates `matchCandidates(phone)` in order and returns the first `dao.findMatch(candidate, type)` result;
- both black and white lists share the same private path;
- remove duplicated use of `normalizeCountryCode()`/`normalizeForLookup()` from this Repository.

Do not change DAO SQL, Entity, Room version or backup restore storage.

- [ ] **Step 4: Route UI current-list state through the same matcher**

Replace `HomeViewModel` file-level `UserListEntry.matchesPhone()` normalization logic with:

```kotlin
private fun UserListEntry.matchesPhone(incomingPhoneNumber: String): Boolean {
    if (tagMatch || locationMatch) return false
    return PhoneNumberRuleMatcher.ruleMatches(
        rule = this.phoneNumber,
        isPrefix = isPrefix,
        phoneNumber = incomingPhoneNumber,
    )
}
```

Use the exact named arguments so the stored rule and incoming number cannot be reversed. `CurrentListState` must agree with actual `UserListRepository` matching for black/white, exact/prefix and `+`/`00` variants.

- [ ] **Step 5: Build and inspect scope**

Run:

```bash
./gradlew :app:assembleDebug
git diff --check
git diff -- app/src/main/java/vip/mystery0/pixel/telo/data/PhoneNumberRuleMatcher.kt \
  app/src/main/java/vip/mystery0/pixel/telo/data/PhoneNumberNormalizer.kt \
  app/src/main/java/vip/mystery0/pixel/telo/data/repository/UserListRepository.kt \
  app/src/main/java/vip/mystery0/pixel/telo/viewmodel/HomeViewModel.kt
```

Expected: build succeeds; no Room schema, test, dependency or permission files changed.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/data/PhoneNumberRuleMatcher.kt \
  app/src/main/java/vip/mystery0/pixel/telo/data/PhoneNumberNormalizer.kt \
  app/src/main/java/vip/mystery0/pixel/telo/data/repository/UserListRepository.kt \
  app/src/main/java/vip/mystery0/pixel/telo/viewmodel/HomeViewModel.kt
git commit -m "fix: 统一国际号码本地规则匹配语义"
```

---

### Task 2: 国际号码查询门禁与 Test Intercept 真实语义

**Files:**
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/data/repository/SpamNumberRepository.kt`
- Modify: `app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

**Interfaces:**
- Consumes: `PhoneNumberRuleMatcher.isExplicitInternational(phoneNumber: String): Boolean` from Task 1.
- Preserves: `SpamNumberRepository.checkSpam(phoneNumber: String, forceNetworkQuery: Boolean = false): CheckResult` public signature for existing callers.
- Produces: Test Intercept calls `checkSpam(testPhoneNumber)` without force override.

- [ ] **Step 1: Add the explicit-international early-return gate**

In `SpamNumberRepository.checkSpam()` preserve this exact order:

1. white-list number match;
2. black-list number match;
3. `PhoneNumberRuleMatcher.isExplicitInternational(phoneNumber)`;
4. MastDatabase lookup;
5. offline setting and real-time query.

When step 3 is true and no number rule matched, return:

```kotlin
CheckResult(
    shouldBlock = false,
    label = "",
    resultType = ResultType.PASS_BUT_NOTIFY,
    localCost = System.currentTimeMillis() - start,
    networkCost = 0,
    locationLookupAttempted = false,
)
```

Log only stable English text such as `Explicit international number skipped`; do not log the number. The return must happen before `syncRepository.getDb()`, preference-based networking decisions, `queryBackendProvider.snapshot()` or `forceNetworkQuery` handling.

- [ ] **Step 2: Make Test Intercept use the production decision path**

Change `SettingViewModel.testBlock()` from:

```kotlin
spamNumberRepository.checkSpam(testPhoneNumber, forceNetworkQuery = true)
```

to:

```kotlin
spamNumberRepository.checkSpam(testPhoneNumber)
```

Catch `CancellationException` separately and rethrow it. For actual failure, log a stable English category without attaching the throwable and show a generic localized `msg_test_failed_safe` string without `e.message`. Add matching English and Chinese resources.

- [ ] **Step 3: Confirm force-network callers cannot bypass the gate**

Search all `forceNetworkQuery` callers. Confirm the explicit-international return occurs before the force branch. Retain the flag only for existing domestic/internal use; do not introduce a second bypass parameter.

- [ ] **Step 4: Build and inspect privacy boundaries**

Run:

```bash
./gradlew :app:assembleDebug
git diff --check
rg -n "forceNetworkQuery|Explicit international|Test block failed|e\.message" \
  app/src/main/java/vip/mystery0/pixel/telo/data/repository/SpamNumberRepository.kt \
  app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt
```

Expected: build succeeds; Test Intercept has no force override; no new number/error-body logging; international gate precedes all database/Backend access.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/vip/mystery0/pixel/telo/data/repository/SpamNumberRepository.kt \
  app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt \
  app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "fix: 国际号码仅执行本地规则并对齐测试拦截"
```

---

### Task 3: 架构文档与静态发布门禁

**Files:**
- Modify: `.agentdocs/architecture/mvvm-structure.md`
- Modify: `.agentdocs/architecture/native-integration.md`
- Modify: `.agentdocs/ui/main-screen.md`
- Modify: `.agentdocs/index.md` only if an index entry or summary must be updated
- Modify: `docs/plans/2026-08-29-international-number-rule-matching-implementation.md`

**Interfaces:**
- Consumes final Task 1–2 behavior and actual command results.
- Produces durable architecture and validation summary for final review and Task 4 emulator acceptance.

- [x] **Step 1: Document the two-domain number architecture**

Update architecture docs to state:

- `PhoneNumberRuleMatcher` owns custom-rule storage/matching semantics;
- explicit `+`/`00` international formats are equivalent but markerless digits are separate;
- `UserListRepository` and history UI use the same matcher;
- custom local rules run before the explicit-international gate;
- unmatched explicit international numbers skip MastDatabase and every real-time Backend;
- China/和多号 lookup continues through `PhoneNumberNormalizer`;
- Test Intercept uses the same settings and path as CallScreening.

Do not claim simulator results before Task 4 executes them.

- [x] **Step 2: Run final static gates**

Run:

```bash
./gradlew :app:assembleDebug
./gradlew lint
git diff --check
git diff --name-only 68e478d..HEAD
git diff 68e478d..HEAD -- app/src/test app/src/androidTest \
  gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml
```

Expected:

- build and Lint succeed;
- Lint has zero errors;
- no unit/instrumentation test files were added;
- no dependency, permission, Room schema or backup-version changes exist.

- [x] **Step 3: Record actual static results**

Append the real build/Lint/diff evidence to this implementation plan. Include warning count, changed-file boundary and any unverified runtime requirements. Do not invent emulator results.

- [x] **Step 4: Commit**

```bash
git add .agentdocs docs/plans/2026-08-29-international-number-rule-matching-implementation.md
git commit -m "docs: 更新国际号码规则匹配架构与验证记录"
```

#### Task 3 静态验证记录（2026-08-30；fix round 1 更新）

以下结果来自本工作树实际执行，未包含模拟器或真机通过结论：

* `./gradlew :app:assembleDebug`：通过；`BUILD SUCCESSFUL in 5s`，39 个 actionable tasks（13 executed，26 up-to-date）。
* `./gradlew lint`：通过；`BUILD SUCCESSFUL in 56s`，30 个 actionable tasks（10 executed，20 up-to-date）。Lint 报告中错误数为 0，警告数为 45（`app/build/reports/lint-results-debug.xml` 中的 `Warning` issue）。
* `git diff --check`：通过，无输出（fix round 1 后再次执行）。
* `git diff --name-only 8fee857..e4f9e32`：Task 1–2 已审查代码边界实际为 8 个文件：`PhoneNumberNormalizer.kt`、`PhoneNumberRuleMatcher.kt`、`SpamNumberRepository.kt`、`UserListRepository.kt`、`HomeViewModel.kt`、`SettingViewModel.kt`、`values-zh/strings.xml`、`values/strings.xml`。
* `git diff --name-only e4f9e32..HEAD`：Task 3 文档边界实际为 5 个文件：三份 `.agentdocs` 架构/UI 文档、实施计划和 `task-3-report.md`。
* `git diff --name-only 68e478d..HEAD`：最终 HEAD 完整边界实际为以下 13 个文件，不能再描述为提交前的 9 文件代码边界：
  ```text
  .agentdocs/architecture/mvvm-structure.md
  .agentdocs/architecture/native-integration.md
  .agentdocs/ui/main-screen.md
  .superpowers/sdd/2026-08-29-international-number-rule-matching-implementation/task-3-report.md
  app/src/main/java/vip/mystery0/pixel/telo/data/PhoneNumberNormalizer.kt
  app/src/main/java/vip/mystery0/pixel/telo/data/PhoneNumberRuleMatcher.kt
  app/src/main/java/vip/mystery0/pixel/telo/data/repository/SpamNumberRepository.kt
  app/src/main/java/vip/mystery0/pixel/telo/data/repository/UserListRepository.kt
  app/src/main/java/vip/mystery0/pixel/telo/viewmodel/HomeViewModel.kt
  app/src/main/java/vip/mystery0/pixel/telo/viewmodel/SettingViewModel.kt
  app/src/main/res/values-zh/strings.xml
  app/src/main/res/values/strings.xml
  docs/plans/2026-08-29-international-number-rule-matching-implementation.md
  ```
* 原保护命令 `git diff 68e478d..HEAD -- app/src/test app/src/androidTest gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml` 实际无输出，但其 pathspec 未覆盖 Room/备份文件，不能据此推导 Room schema 或备份版本无变更。
* 扩大保护命令 `git diff 68e478d..HEAD -- app/src/test app/src/androidTest gradle/libs.versions.toml app/build.gradle.kts app/src/main/AndroidManifest.xml app/src/main/java/vip/mystery0/pixel/telo/data/AppDatabase.kt app/src/main/java/vip/mystery0/pixel/telo/data/MastDatabase.kt app/src/main/java/vip/mystery0/pixel/telo/data/dto/BackupData.kt app/src/main/java/vip/mystery0/pixel/telo/data/repository/BackupRepository.kt app/src/main/java/vip/mystery0/pixel/telo/data/dao app/src/main/java/vip/mystery0/pixel/telo/data/entity app/src/main/java/vip/mystery0/pixel/telo/ui/screen/settings/BackupRestorePreferences.kt` 实际无输出；该完整保护范围未发现测试、依赖、权限、Room schema、备份 DTO/repository/entity/DAO 或备份设置变更。

尚未验证的运行时要求：Task 4 的 Android 模拟器规则矩阵、真实 `CallScreeningService` 来电路径、数据库/Backend 请求边界、Test Intercept 配置一致性、中国号码与和多号回归、备份恢复及运行时清理。

---

### Task 4: Android 模拟器回归与 Issue #10 验收

**Files:**
- Modify: `docs/plans/2026-08-29-international-number-rule-matching-implementation.md`
- Runtime-only: emulator application database/preferences/contacts/logcat; no business source edits

**Interfaces:**
- Consumes final APK from Tasks 1–3.
- Produces durable simulator evidence and cleanup status.

- [ ] **Step 1: Prepare a reversible emulator environment**

Use the connected Android emulator. Before modifying runtime data:

```bash
adb devices
./gradlew :app:assembleDebug
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
```

Back up the current Debug `app-database` and relevant SharedPreferences with `run-as`; record original `no_network_query`, `notify_only` and test-number state. Do not use `pm clear`.

- [ ] **Step 2: Validate the black/white exact/prefix matrix through Test Intercept**

Create synthetic, non-user rules and validate at least:

```text
+1519 prefix  ↔ +15197292346      hit
+1519 prefix  ↔ 0015197292346     hit
001519 prefix ↔ +15197292346      hit
1519 prefix   ↔ +15197292346      miss
1519 prefix   ↔ 0015197292346     miss
1519 prefix   ↔ 15197292346       hit
+15197292346 exact ↔ 0015197292346 hit
15197292346 exact  ↔ +15197292346 miss
```

Repeat representative `+`/`00` cases for both BLACK and WHITE. Verify `ResultType.BLACK_LIST`/`WHITE_LIST`, `shouldBlock`, and UI current-list state agree.

- [ ] **Step 3: Validate real CallScreening behavior**

For representative black-list and white-list international rules, use `adb emu gsm call` with supported emulator number forms or the closest system-delivered representation. Record the actual number delivered to `CallScreeningService`, decision, `ResultType`, and whether the call was rejected/allowed. If the emulator strips `+`, document that limitation and use the Test Intercept matrix as format-level evidence rather than claiming unsupported delivery behavior.

- [ ] **Step 4: Prove international numbers skip all databases and Backends**

With an unmatched explicit international number and both `no_network_query=false` and `true`:

- verify `PASS_BUT_NOTIFY`, `networkCost=0`, `locationLookupAttempted=false`;
- verify no MastDatabase lookup success/failure log for that request;
- verify no official or Self-hosted query request using controlled Backend counters/logs or a temporary unreachable Self-hosted configuration;
- verify `forceNetworkQuery=true` through any retained internal/debug path still cannot cross the gate;
- do not weaken TLS or modify production source to manufacture evidence.

- [ ] **Step 5: Validate Test Intercept configuration parity**

With `no_network_query=true`:

- domestic local-rule hit still returns the rule result;
- domestic local miss returns offline `PASS_BUT_NOTIFY`, not a network failure;
- explicit international local miss returns the international early-pass result;
- no network request occurs.

With `no_network_query=false`, confirm a domestic local miss still uses the configured Backend. This proves removal of the old forced-online behavior did not disable normal online testing.

- [ ] **Step 6: Validate China and 和多号 regressions**

Verify representative existing behavior:

```text
+8613800138000 / 008613800138000 / 13800138000 -> same domestic rule
12583113800138000 -> same confirmed 和多号 lookup target
```

Confirm domestic MastDatabase/online query still works, and tag/location/`forceBlock` rules remain unchanged.

- [ ] **Step 7: Validate backup compatibility**

Back up and restore synthetic existing rules stored as `+1519`, `001519` and `1519`. Confirm after restore:

- `+1519` and `001519` each match explicit `+`/`00` international calls;
- `1519` does not match explicit international calls;
- backup version remains unchanged;
- unrelated backup scopes and counts remain intact.

- [ ] **Step 8: Clean runtime state and collect final evidence**

Remove all synthetic rules, calls, contacts, ZIP files, temporary databases and Backend configuration. Restore original preferences and application data. Confirm:

```text
no active gsm call
no active Overlay
no synthetic user_list rows
no synthetic blocked_calls rows
no temporary Download/data-local-tmp files
logcat has no FATAL EXCEPTION, ANR, or number-rule lookup failure
```

Run:

```bash
git diff --check
git status --short
git log --oneline -10
```

- [ ] **Step 9: Record and commit actual emulator results**

Append the real matrix, limitations, cleanup output and logcat summary to this implementation plan. Runtime validation must not modify business source. Commit only the durable documentation update:

```bash
git add docs/plans/2026-08-29-international-number-rule-matching-implementation.md
git commit -m "docs: 记录国际号码规则匹配模拟器验收结果"
```
