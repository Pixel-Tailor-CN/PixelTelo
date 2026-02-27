# GitHub Actions 跨仓库发布 Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** 在私有仓库推送 `v*` tag 时，自动构建 Release AAB + Universal APK 并发布到公开仓库 `PixelTelo/AppRelease`。

**Architecture:** GitHub Actions 工作流在私有仓库运行，使用 bundletool 从 AAB 提取 Universal APK，通过存储在私有仓库 Secrets 中的 PAT 调用 `gh` CLI 跨仓库创建 Release 并上传产物。

**Tech Stack:** GitHub Actions, Gradle (Kotlin DSL), bundletool, gh CLI

---

### Task 1: 创建 GitHub Actions 工作流文件

**Files:**
- Create: `.github/workflows/release.yml`

**Step 1: 创建目录**

```bash
mkdir -p .github/workflows
```

**Step 2: 写入工作流文件**

```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  build-and-release:
    runs-on: ubuntu-latest
    steps:
      - name: Checkout
        uses: actions/checkout@v4
        with:
          fetch-depth: 0  # 需要完整 commit 历史以正确生成 versionCode

      - name: Setup JDK 21
        uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Setup Gradle
        uses: gradle/actions/setup-gradle@v4

      - name: Decode Keystore
        run: |
          echo "${{ secrets.SIGN_KEY_STORE_BASE64 }}" | base64 -d > $RUNNER_TEMP/release.jks

      - name: Build Release Bundle
        env:
          SIGN_KEY_STORE_FILE: ${{ runner.temp }}/release.jks
          SIGN_KEY_STORE_PASSWORD: ${{ secrets.SIGN_KEY_STORE_PASSWORD }}
          SIGN_KEY_ALIAS: ${{ secrets.SIGN_KEY_ALIAS }}
          SIGN_KEY_PASSWORD: ${{ secrets.SIGN_KEY_PASSWORD }}
        run: ./gradlew bundleRelease

      - name: Download bundletool
        run: |
          BUNDLETOOL_VERSION=1.17.2
          curl -L "https://github.com/google/bundletool/releases/download/${BUNDLETOOL_VERSION}/bundletool-all-${BUNDLETOOL_VERSION}.jar" \
            -o bundletool.jar

      - name: Extract Universal APK
        run: |
          mkdir -p apk-output
          java -jar bundletool.jar build-apks \
            --bundle=app/build/outputs/bundle/release/app-release.aab \
            --output=apk-output/app.apks \
            --mode=universal \
            --ks=$RUNNER_TEMP/release.jks \
            --ks-pass=pass:${{ secrets.SIGN_KEY_STORE_PASSWORD }} \
            --ks-key-alias=${{ secrets.SIGN_KEY_ALIAS }} \
            --key-pass=pass:${{ secrets.SIGN_KEY_PASSWORD }}
          unzip apk-output/app.apks universal.apk -d apk-output/
          # 重命名为有意义的文件名
          VERSION_NAME=$(./gradlew -q printVersionName 2>/dev/null || echo "${{ github.ref_name }}")
          mv apk-output/universal.apk "apk-output/PixelTelo-${GITHUB_REF_NAME}.apk"
          cp app/build/outputs/bundle/release/app-release.aab "apk-output/PixelTelo-${GITHUB_REF_NAME}.aab"

      - name: Create Release in AppRelease
        env:
          GH_TOKEN: ${{ secrets.APP_RELEASE_TOKEN }}
        run: |
          gh release create "${{ github.ref_name }}" \
            --repo PixelTelo/AppRelease \
            --title "PixelTelo ${{ github.ref_name }}" \
            --notes "详见 [更新日志](https://github.com/PixelTelo/PixelTelo/releases/tag/${{ github.ref_name }})" \
            "apk-output/PixelTelo-${GITHUB_REF_NAME}.apk" \
            "apk-output/PixelTelo-${GITHUB_REF_NAME}.aab"

      - name: Cleanup Keystore
        if: always()
        run: rm -f $RUNNER_TEMP/release.jks
```

**Step 3: Commit**

```bash
git add .github/workflows/release.yml
git commit -m "ci: add GitHub Actions release workflow"
```

---

### Task 2: 更新 AppRelease 仓库 README.md

**通过 gh CLI 直接更新远程仓库文件。**

**Step 1: 获取当前 README SHA（gh API 需要）**

```bash
gh api repos/PixelTelo/AppRelease/contents/README.md --jq '.sha'
```

**Step 2: 写入新的 README 内容并推送**

```bash
gh api repos/PixelTelo/AppRelease/contents/README.md \
  --method PUT \
  --field message="docs: update README" \
  --field sha="<上一步的SHA>" \
  --field content="$(cat <<'EOF' | base64 -w0
<README内容>
EOF
)"
```

（具体内容见 Task 3）

---

### Task 3: README 内容

AppRelease README.md 内容：

```markdown
# Pixel Telo - 应用发布

> 专为 Google Pixel 设备设计的来电识别与拦截应用

## 下载安装包

前往 [Releases 页面](https://github.com/PixelTelo/AppRelease/releases/latest) 下载最新版本的 `.apk` 文件安装到设备。

每个 Release 包含：
- `.apk` — Universal APK，直接安装到手机
- `.aab` — Android App Bundle，供开发者使用

## 反馈问题

如遇到 Bug 或有功能建议，欢迎在 [Issues](https://github.com/PixelTelo/AppRelease/issues) 提交反馈。

## 隐私说明

Pixel Telo 隐私优先：不收集任何个人数据，本地处理所有来电信息。
```

---

### Task 4: 告知用户需要配置的 Secrets

用户需要在私有仓库 `Settings → Secrets → Actions` 中添加以下 5 个 Secrets，以及创建 PAT。
