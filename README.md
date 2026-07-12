# Pixel Telo

<p align="center">
  <img src="app/src/main/ic_launcher-playstore.png" width="200" alt="Pixel Telo Logo"/>
</p>

<p align="center">
  <strong>专为 Google Pixel 设计的来电识别与拦截应用</strong>
</p>
<p align="center">
  <strong>Caller ID and spam blocking app designed for Pixel and Native Android.</strong>
</p>

<p align="center">
    <a href="https://github.com/Pixel-Tailor-CN/PixelTelo/releases/latest"><img src="https://img.shields.io/github/v/release/Pixel-Tailor-CN/PixelTelo" alt="GitHub Release"></a>
    <a href="https://play.google.com/store/apps/details?id=vip.mystery0.pixel.telo"><img src="https://img.shields.io/badge/Google_Play-PixelTelo-green?logo=google-play&logoColor=white" alt="Google Play"></a>
    <a href="LICENSE"><img src="https://img.shields.io/github/license/Pixel-Tailor-CN/PixelTelo" alt="License"></a>
</p>

[English](#english) | [中文](#中文)

---

## 中文

### 📱 项目简介

Pixel Telo 是一款专为 Google Pixel 系列及类原生 Android (AOSP) 设备设计的来电识别与拦截应用。采用原生
Android 设计风格，强调隐私保护和极速响应，提供零打扰的纯净体验。

**核心特性**

- 🎨 **原生设计**: Material3 + Monet 动态取色，完美融入 Pixel 系统
- 🚀 **极速响应**: 本地查询 < 100ms，网络查询 < 5s
- 🔒 **隐私优先**: 本地数据库，可选离线模式，无需上传通讯录
- 📞 **系统集成**: 通过 Directory Provider 在系统拨号器中显示来电信息
- 🛡️ **智能拦截**: 支持本地数据库 + 实时网络查询双重验证
- 📋 **黑白名单**: 支持精确匹配、前缀匹配、标签匹配三种模式

### 🎯 核心功能

#### 来电拦截

- 自动识别并拦截骚扰电话
- 支持"仅提示不拦截"模式
- 拦截记录自动写入系统通话记录
- 可选"始终记录"模式，记录所有来电

#### 号码查询

- **本地查询**: 离线数据库，响应速度 < 100ms
- **网络查询**: 实时查询，超时限制 3s
- **手动查询**: 支持手动输入号码测试拦截逻辑

#### 数据管理

- **离线数据库**: 从云端下载骚扰号码库，支持增量更新
- **备份恢复**: 支持拦截记录、黑名单、白名单的备份与恢复
- **黑白名单**: 自定义拦截规则，支持精确/前缀/标签匹配

#### 系统集成

- **Directory Provider**: 在系统拨号器中显示来电标签
- **CallScreeningService**: 系统级来电拦截服务
- **通话记录**: 拦截记录自动写入系统 Blocked Calls

### 🛠️ 技术栈

- **UI 框架**: Jetpack Compose + Material3
- **架构模式**: MVVM + Repository
- **依赖注入**: Koin
- **数据库**: Room (SQLite)
- **网络请求**: Retrofit + OkHttp
- **序列化**: Kotlinx Serialization
- **异步处理**: Kotlin Coroutines + Flow
- **构建工具**: Gradle (Kotlin DSL) + KSP

### 📋 系统要求

- **最低版本**: Android 10 (API 29)
- **目标版本**: Android 15 (API 37)
- **推荐设备**: Google Pixel 系列及类原生 Android 设备

### 🚀 快速开始

#### 构建项目

```bash
# 编译 Debug 版本
./gradlew assembleDebug

# 编译 Release 版本
./gradlew assembleRelease

# 安装到设备
./gradlew installDebug
```

#### 运行测试

```bash
# 运行单元测试
./gradlew test

# 运行 Lint 检查
./gradlew lint

# 运行所有检查
./gradlew check
```

### 📖 使用指南

1. **设置为默认应用**: 在系统设置中将 Pixel Telo 设为默认的"来电显示与骚扰拦截应用"
2. **授予权限**: 授予必要的通话记录、电话状态、联系人读取权限
3. **下载数据库**: 首次使用时下载离线骚扰号码库（可选）
4. **配置拦截规则**: 根据需要配置黑白名单和拦截模式

### 🔗 相关链接

- **Pixel Tailor CN**: [https://pixel.mystery0.app](https://pixel.mystery0.app)  
  “为不完美的体验，做精细的缝补。” 专注于提升 Google Pixel 国内使用体验的开源工具集，强调原生 Android
  体验、隐私至上与 Material You 适配。

- **问题反馈**: [GitHub Issues](https://github.com/Pixel-Tailor-CN/PixelTelo/issues/new)

- **Telegram 频道**: [t.me/pixel_tailor_cn](https://t.me/pixel_tailor_cn)

### 📄 开源协议

本项目采用 [Apache License 2.0](LICENSE) 开源协议。

---

## English

### 📱 Introduction

Pixel Telo is a caller ID and spam blocking app designed specifically for Google Pixel and
AOSP-based Android devices. It features native Android design, emphasizes privacy protection and
ultra-fast response, providing a clean and distraction-free experience.

**Key Features**

- 🎨 **Native Design**: Material3 + Monet dynamic theming, perfectly integrated with Pixel system
- 🚀 **Ultra-Fast**: Local query < 100ms, network query < 3s
- 🔒 **Privacy First**: Local database, optional offline mode, no contact upload required
- 📞 **System Integration**: Display caller info in system dialer via Directory Provider
- 🛡️ **Smart Blocking**: Dual verification with local database + real-time network query
- 📋 **Block/Allow Lists**: Support exact match, prefix match, and tag match

### 🎯 Core Features

#### Call Blocking

- Automatically identify and block spam calls
- Support "notify only" mode (no blocking)
- Blocked calls automatically logged to system call log
- Optional "always record" mode to log all incoming calls

#### Number Lookup

- **Local Query**: Offline database, response time < 100ms
- **Network Query**: Real-time lookup, 3s timeout limit
- **Manual Query**: Test blocking logic with manual number input

#### Data Management

- **Offline Database**: Download spam number database from cloud, support incremental updates
- **Backup & Restore**: Backup and restore blocked calls, blacklist, and whitelist
- **Block/Allow Lists**: Custom blocking rules with exact/prefix/tag matching

#### System Integration

- **Directory Provider**: Display caller labels in system dialer
- **CallScreeningService**: System-level call screening service
- **Call Log**: Blocked calls automatically logged to system Blocked Calls

### 🛠️ Tech Stack

- **UI Framework**: Jetpack Compose + Material3
- **Architecture**: MVVM + Repository
- **Dependency Injection**: Koin
- **Database**: Room (SQLite)
- **Networking**: Retrofit + OkHttp
- **Serialization**: Kotlinx Serialization
- **Async**: Kotlin Coroutines + Flow
- **Build Tool**: Gradle (Kotlin DSL) + KSP

### 📋 Requirements

- **Minimum SDK**: Android 10 (API 29)
- **Target SDK**: Android 15 (API 37)
- **Recommended**: Google Pixel series and AOSP-based devices

### 🚀 Quick Start

#### Build

```bash
# Build debug version
./gradlew assembleDebug

# Build release version
./gradlew assembleRelease

# Install to device
./gradlew installDebug
```

#### Testing

```bash
# Run unit tests
./gradlew test

# Run lint checks
./gradlew lint

# Run all checks
./gradlew check
```

### 📖 Usage Guide

1. **Set as Default App**: Set Pixel Telo as the default "Caller ID & Spam" app in system settings
2. **Grant Permissions**: Grant necessary permissions for call log, phone state, and contacts
3. **Download Database**: Download offline spam number database on first use (optional)
4. **Configure Rules**: Configure block/allow lists and blocking mode as needed

### 🔗 Related Links

- **Issue Tracker**: [GitHub Issues](https://github.com/Pixel-Tailor-CN/PixelTelo/issues/new)

### 📄 License

This project is licensed under the [Apache License 2.0](LICENSE).

---

<div align="center">

**Made with ❤️ by [Pixel Tailor CN](https://pixel.mystery0.app)**

</div>
