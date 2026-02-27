# GitHub Actions 跨仓库发布设计文档

## 概述

在私有仓库 `PixelTelo/PixelTelo` 中配置 GitHub Actions 工作流，构建 Android Release APK 后自动发布到公开仓库 `PixelTelo/AppRelease` 的 Release。同时更新 AppRelease 仓库的 README.md。

## 触发条件

推送 `v*` 格式的 git tag（如 `v1.0`、`v1.2.3`）时自动触发。

```bash
git tag v1.0
git push origin v1.0
```

## 工作流步骤

1. Checkout 代码
2. 配置 JDK 21（与项目 JVM Target 21 对齐）
3. 将 Base64 编码的 keystore 解码到临时文件
4. 设置签名环境变量（`SIGN_KEY_STORE_FILE` 等），`signing.gradle` 会自动读取
5. 执行 `./gradlew bundleRelease`，生成 AAB
6. 下载 `bundletool.jar`，从 AAB 提取 Universal APK
7. 使用 `gh` CLI + PAT 在 `PixelTelo/AppRelease` 创建 Release，同时上传 AAB 和 Universal APK

## 必要 Secrets（私有仓库）

| Secret 名称 | 说明 |
|---|---|
| `SIGN_KEY_STORE_BASE64` | keystore 文件的 Base64 编码 |
| `SIGN_KEY_STORE_PASSWORD` | keystore 密码 |
| `SIGN_KEY_ALIAS` | 密钥别名 |
| `SIGN_KEY_PASSWORD` | 密钥密码 |
| `APP_RELEASE_TOKEN` | Fine-grained PAT，对 AppRelease 仓库有 `contents: write` 权限 |

## 产物路径

- AAB: `app/build/outputs/bundle/release/app-release.aab`
- Universal APK（bundletool 提取）: `apk-output/universal.apk`

## AppRelease README

- 中文为主，简介仓库用途
- 下载链接：`https://github.com/PixelTelo/AppRelease/releases/latest`
- 反馈链接：`https://github.com/PixelTelo/AppRelease/issues`
