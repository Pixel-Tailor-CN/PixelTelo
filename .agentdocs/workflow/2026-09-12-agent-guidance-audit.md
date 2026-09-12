# 协作规则审计：GPT-6 Astra

审计日期：2026-09-12。范围是 Pixel Telo 仓库中的协作指令、技能入口与工作流，不修改应用功能或个人 Codex 配置。

## 官方依据

本次实际读取了以下官方页面；结论按仓库情况适配，不把示例提示词整段复制进常驻规则。

- [GPT-6 Astra 模型指导](https://developers.openai.com/api/docs/guides/latest-model?model=gpt-6-astra)：明确任务完成边界，减少已授权工作的停顿，识别技能冲突，校准验证范围。
- [重新思考 Astra 的技能与提示词](https://learn.chatgpt.com/blog/rethinking-skills-and-prompts-for-gpt-6-astra)：减少宽泛触发和过度编排，按需加载资料，重新检查旧模型积累的限制。
- [AGENTS.md 发现与层级](https://learn.chatgpt.com/docs/agent-configuration/agents-md)：规则按目录作用域加载，根文件只保留普遍适用的信息。
- [Skills 官方规范](https://learn.chatgpt.com/docs/build-skills)：技能有独立目录及包含 name/description 的 SKILL.md；仓库位置是 `.agents/skills/`，描述应界定触发范围，正文按需加载。
- [Codex 最佳实践](https://learn.chatgpt.com/guides/best-practices)：短而准确的项目说明、可执行验证和按任务拆分文档优于冗长通用规范。

## 发现与处理

| 原状 | 影响 | 处理 |
| --- | --- | --- |
| 根 AGENTS.md 271 行，技术栈、架构、测试政策、验证流程多次重复 | 常驻上下文重复，更新容易漂移 | 精简到 59 行，合并为项目入口、协作方式、关键边界、验证表与按需文档 |
| 固定的基础架构搭建顺序、单文件 1000 行软上限 | 容易把成熟项目的小改动扩成重构 | 删除已过期搭建顺序，按职责与当前任务决定拆分 |
| “Strict mode”未定义，当前 compilerOptions 未见对应设置 | 无法作为可验证的工程标准 | 使用现有 Kotlin 风格与 Gradle 配置作为依据 |
| 根规则权限列表缺少 Manifest 中已有的权限，并列出未声明的权限 | 可能误导权限审查 | 指向 Manifest 和已对齐的 PRD，保留新增权限说明要求 |
| 每次收尾检查稳定依赖，但版本目录已有 Material3 `1.5.0-alpha27`，构建开启 Experimental API opt-in | 无关任务也可能升级或降级依赖 | 依赖改动时检查兼容性，保留既有预发布选择；不宣称所有依赖均稳定 |
| 真机、Lint 与编译要求分散，文档任务也可能进入构建循环 | 额外耗时、环境不足时过早停止 | 按改动范围验证，保留 Provider 的 Pixel 真机验收要求 |
| 10 份历史实施计划包含 REQUIRED SUB-SKILL 入口 | 阅读历史资料可能触发强制技能链 | 替换入口声明，保留原设计、实现步骤与历史结果；旧提交命令不视为当前授权 |
| `.agentdocs/workflow/` 仅有索引占位 | 缺少按任务使用的协作细节 | 新增 development.md，区分开发、技能维护与 CI |
| 未发现仓库自定义 skill.md / SKILL.md | 不能声称优化了并不存在的技能文件 | 不新增通用包装技能，给出窄触发、渐进加载和验证原则 |

## 技能与外部指令边界

仓库检索包含隐藏路径，以及被忽略的 `.claude/`、`.superpowers/` 文件清单。
`.claude/` 仅发现本地设置文件；`.superpowers/` 是历史任务报告、差异和可视化工件，未发现技能定义。
没有读取本地设置中的凭据或将临时工件纳入版本管理。`CLAUDE.md` 已是 AGENTS.md 的简短入口，保留。

对本会话可用、位于 `~/.codex/skills/` 的相关共享技能做了只读检查：

- `using-superpowers/SKILL.md` 使用极低相关性阈值及全任务触发；其 Codex 工具参考还有固定路由与等待策略，不能代替会话真实工具定义。
- `brainstorming/SKILL.md` 包含所有路径均需设计批准的门禁。
- `writing-plans/SKILL.md` 模板强制技能链、TDD 和频繁提交；`executing-plans/SKILL.md` 包含固定收尾技能。

这些是仓库外来源，未修改、卸载或禁用。项目内明确任务授权、单元测试例外及按需流程，
不能改变宿主更高优先级的指令或真实技能发现机制；若未来仍有额外停顿，应先查看其具体来源。
本次不添加同名仓库技能来覆盖共享技能，因为同名技能不保证合并或覆盖。

## 保留的关键约束

中文沟通及注释、英文脱敏日志；MVVM 与双数据库；空库初始化提示；Directory Provider 优先及 Overlay 显式启用；
轻量 CallScreeningService；通讯录与号码规则门禁；本地 50ms 目标、100ms 警告、3 秒硬上限；
网络 1–10 秒且默认 5 秒、失败放行；官方/自建边界；最小权限；默认不新增、不维护、不运行单元测试。

## CI 与技术文档审阅范围

已读取 `.github/workflows/release.yml` 和 `auto-assign-issue.yml`。前者由 `v*` tag 触发签名构建与发布，
后者为新 issue 分配负责人；均不含模型提示词，不属于本次性能优化的修改点。
未更改 Actions 版本、签名流程或远程权限，也未实际触发工作流；这不构成 CI 安全审计或发布可用性认证。

交叉核对了 PRD、架构、UI 文档及 Gradle/Manifest/查询入口。同步文档中的 `runBlocking` 建议和
Service 中现有 `runBlocking(Dispatchers.IO)` 需要在专门的来电线程审查中评估，不能凭文档精简宣称其性能达标。
本次保留现有代码与功能文档，不把目标性能写成已经测得的结果。

## 验证与效果边界

文档变更以最终差异、相对链接、残留强制技能入口和 `git diff --check` 验证。
按文档改动范围不运行 Android 构建、Lint、单元测试或设备测试；未修改依赖版本。
已检查 47 个本地 Markdown 链接，全部可解析；10 份计划的强制技能入口已清除。
独立文档审查未发现关键约束遗漏；发现的原始行数记录错误已修正。根文件由 271 行缩减为 59 行，
按统一换行口径字符数约减少 48%，这不是 token 节省或性能提升的测量值。
未来可用文档修正、普通 Kotlin 修复、跨模块功能三个同类任务，
比较额外确认次数、重复工具调用、完成率和实际 token/耗时；没有 A/B 测量前不声称模型速度或质量提升比例。
