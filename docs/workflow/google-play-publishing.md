# Google Play 内部测试发布

## 触发与产物

[内部测试工作流](../../.github/workflows/google-play-internal.yml)参考 PixelText：监听 `master` 分支的 push，仅当最后一条提交信息包含 `ci` 时执行，按子串匹配、不区分大小写。例如 `ci: 发布内部测试版本` 或 `fix: 修复名单添加 [ci]`。

一次 push 包含多条提交时，只检查最后一条；其他分支、标签和分支删除不触发发布。不含 `ci` 时发布任务跳过；GitHub 的 `[skip ci]` 等跳过指令仍生效。

流程校验 Secrets，检出完整 Git 历史，使用 JDK 21 和现有 `signing.gradle` 构建 `:app:bundleRelease`，上传 `app/build/outputs/bundle/release/app-release.aab` 到包名 `vip.mystery0.pixel.telo` 的 `internal` 渠道，状态为 `completed`。内部测试人员由 Play Console 管理，不会推广到生产渠道。

发布名称为 `版本号 (versionCode) · CI 北京时间`，例如 `1.5.0 (500) · CI 2026-11-08 16:00:00`；版本号读取版本目录，versionCode 使用 Git 提交数，时间为构建 AAB 前的 UTC+8 时间。名称不改变应用内版本信息。

发布任务共用并发组，不取消进行中的发布，通过 `queue: max` 最多保留 100 个等待任务，避免 Play 编辑事务并发。队列不保证按提交先后顺序发布，也不能解决重复版本号。

此流程不依赖 PixelText 的资源同步：PixelTelo 离线号码库仍由应用初始化下载。现有 [标签发布流程](../../.github/workflows/release.yml)继续负责 `v*` 标签的 GitHub Release、APK 和 AAB。

## 一次性配置

1. 在 Google Cloud 项目中启用 Google Play Android Developer API，创建服务账号并下载 JSON 密钥。已有 PixelText 服务账号也可复用，但必须单独授予 PixelTelo 应用权限。无需给服务账号 Cloud 项目 Owner/Editor 权限。
2. 在 Play Console 的“用户和权限”邀请该服务账号邮箱，仅授予 PixelTelo（`vip.mystery0.pixel.telo`）的应用信息读取及测试渠道发布权限。
3. 确保应用已经在 Play Console 创建并完成首次手动上传，配置内部测试人员和测试加入链接；核对上传证书与 CI 签名 keystore 一致。
4. 在[仓库 Actions Secrets](https://github.com/Pixel-Tailor-CN/PixelTelo/settings/secrets/actions)配置以下内容。

| Secret | 内容 |
| --- | --- |
| `GOOGLE_PLAY_SERVICE_ACCOUNT_JSON` | 服务账号 JSON 文件完整原文，不做 Base64 编码 |
| `SIGN_KEY_STORE_BASE64` | 现有上传签名 keystore 的 Base64 内容 |
| `SIGN_KEY_STORE_PASSWORD` | keystore 密码 |
| `SIGN_KEY_ALIAS` | 密钥别名 |
| `SIGN_KEY_PASSWORD` | 密钥密码 |

四个签名 Secret 与标签发布流程共用。服务账号私钥保存在仓库外，不提交到 Git、不输出到日志。可以在已登录 `gh` 的 PowerShell 中从文件配置，替换示例路径：

```powershell
Get-Content -Raw -LiteralPath 'C:\secure\pixeltelo-publisher.json' | gh secret set GOOGLE_PLAY_SERVICE_ACCOUNT_JSON --repo Pixel-Tailor-CN/PixelTelo
```

Google Cloud 的创建项目、启用 API 和服务账号授权说明见[官方 API 入门文档](https://developers.google.com/android-publisher/getting_started)；Play 应用权限见[权限说明](https://support.google.com/googleplay/android-developer/answer/9844686?hl=zh-Hans)。

## 验收与重跑

配置完成后，将最后一条提交信息含 `ci` 的提交推送到 `master`，在 Actions 查看签名 AAB 构建和 Play 上传结果，再在 Console 核对版本号与内部测试发布状态，由测试人员通过 Play 安装或升级。静态检查、API 上传成功与测试设备收到更新是不同验证阶段。

`versionCode` 继续使用 `git rev-list HEAD --count`，新版本必须使用未占用且满足升级要求的版本号。保持主分支历史递增；同一提交重跑不会自动生成新版本号，重写历史或队列发布次序变化可能导致重复或较低版本号。

凭据校验或构建失败可以在修复配置后重跑；若上传已经成功、渠道提交失败，先检查 Console 状态，必要时复用已上传的包完成发布，不盲目重新上传。工作流不会静默跳过重复上传。

常见失败：缺少 Secret 时先补齐名称和内容；`403` 时检查 API 是否启用及此应用的测试发布权限；签名不匹配时核对上传证书；应用仍为草稿时先完成 Console 所需配置；编辑冲突时检查是否有人同时修改 Console 发布。以实际 API 返回和 Console 状态为准。

工作流使用的[上传 Action](https://github.com/r0adkll/upload-google-play)及[GitHub 并发队列规则](https://docs.github.com/en/actions/how-tos/write-workflows/choose-when-workflows-run/control-workflow-concurrency)是排查入口。
