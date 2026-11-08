# GitHub Actions 发布实施记录

状态：已实施，2026-11-08 按当前 workflow 归档。最初的私有仓库跨仓发布方案已停用；现状以 [release.yml](../../.github/workflows/release.yml) 为准，设计概览见[发布设计](2026-02-27-github-actions-release-design.md)。

## 当前链路

1. 推送 `v*` tag 触发本仓库发布工作流。
2. Checkout 完整 Git 历史，使用 JDK 21 和项目 Gradle Wrapper。
3. 从仓库 Secrets 解码签名到 runner 临时目录，以环境变量注入构建。
4. 构建 Release AAB，经 bundletool 生成同一签名的 Universal APK。
5. 使用 `softprops/action-gh-release` 在本仓库创建 Release，附加 AAB、APK 和 `CHANGELOG.md` 内容。
6. 无论成功失败均清理 runner 的临时 keystore。

本仓库当前流程不使用旧 `APP_RELEASE_TOKEN` 或跨仓 README 更新脚本。

## 验证边界

本地 YAML 和产物路径检查不代表远程工作流已成功执行。只有对应 tag 的 Actions 和 Release 结果才能证明发布成功；历史计划中的示例 tag 或发布命令不是当前任务授权。
