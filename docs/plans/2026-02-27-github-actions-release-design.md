# GitHub Actions 发布设计

状态：2026-11-08 对照当前 workflow 更新。最初的私有仓库跨仓发布与独立 AppRelease README 更新方案已被本仓发布替代，不再要求跨仓 PAT。

## 触发与产物

以 [.github/workflows/release.yml](../../.github/workflows/release.yml) 为事实来源：推送 `v*` tag 后，在本仓构建并发布 Release。Checkout 读取完整历史以计算 versionCode，构建环境为 JDK 21 和 Gradle Wrapper。

发布产物为 Release AAB 与从同一 AAB 提取、使用正式密钥签名的 Universal APK，正文来自 `CHANGELOG.md`。当前产物路径为：

- AAB：`app/build/outputs/bundle/release/app-release.aab`。
- 归档产物：`apk-output/PixelTelo-<tag>.aab`、`apk-output/PixelTelo-<tag>.apk`。

## 签名与临时文件

CI 从以下 Secrets 注入签名：`SIGN_KEY_STORE_BASE64`、`SIGN_KEY_STORE_PASSWORD`、`SIGN_KEY_ALIAS`、`SIGN_KEY_PASSWORD`。解码后的 keystore 仅保存在 runner 临时目录，任务结束时清理，不提交仓库。

本地签名字段逐项优先读取 `local.properties`，缺失或空值再读环境变量；未配置 keystore 路径时可用 debug 签名验证 Release。正式发布必须提供完整的正式签名配置，细节见 [README](../../README.md#签名配置)。

## 发布边界

当前使用 `softprops/action-gh-release` 在本仓发布，不读取旧 `APP_RELEASE_TOKEN`。本地文件和 YAML 检查不能证明远程发布成功，也不授权创建 tag 或推送。历史实施摘要见[实施记录](2026-02-27-github-actions-release.md)。
