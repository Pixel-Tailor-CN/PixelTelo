# source 优先级查询与质量反馈实施记录

状态：已实现，2026-11-08 精简归档。设计依据见[客户端设计](2026-07-10-source-priority-query-feedback-client-design.md)，当前行为以[架构说明](../architecture/mvvm-structure.md)和[原生集成](../architecture/native-integration.md)为准。

## 实施结果

- 分离 `QueryApi` 与官方离线 `SyncApi`，使用 v2 实时查询、source 清单和官方质量反馈接口。
- `QueryRepository` 保存 source 排序、启用及可用状态；初次使用服务端默认，后续新 source 追加但不自动启用。
- 实时识别只读缓存配置，不在来电链路刷新 source；缺少已初始化配置或所有已选 source 失效时发送空列表，由同一 Backend 服务端 fallback。
- source 下线由首页告警提示；设置草稿至少有一个可用启用项才允许保存。
- AppDatabase v5→v6 增加 source、反馈 Token 和独立质量反馈状态。反馈按服务端终态处理，可重试失败保留待提交状态。
- 后续自建接入将 source 配置按 Backend ID 隔离，并在多个层次阻止自建记录进入官方反馈接口。

## 被替代的约束

原计划的网络 1–3 秒、默认 3 秒已由 **2026-07-11 的 1–10 秒、默认 5 秒** 产品决定替代。不得从旧任务标题、源码片段或勾选状态恢复旧限制。

本地查询的 3 秒硬上限和系统来电筛查的独立响应预算不改变网络超时设置。

## 验证边界

原记录包含当时的构建检查；真实服务反馈状态、数据库升级和 Pixel Directory 显示仍需对应快照及设备证据。本文不再保留整份 Repository、ViewModel 或 Compose 的复制实现。
