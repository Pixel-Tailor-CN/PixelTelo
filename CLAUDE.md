# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this
repository.

## Project Overview

Pixel Telo is a lightweight, privacy-focused caller ID and call blocking app designed for Google
Pixel devices (Android 10+). It integrates deeply with the native system dialer using
`Directory Provider` API and intercepts calls via `CallScreeningService`.

## Build & Test Commands

- **Build Debug APK**: `./gradlew :app:assembleDebug`
- **Run Lint Checks**: `./gradlew lint`
- **Run Unit Tests**: `./gradlew test`
- **Run Instrumentation Tests**: `./gradlew connectedAndroidTest`
- **Clean Project**: `./gradlew clean`

## Architecture

- **Pattern**: MVVM (Model-View-ViewModel) with Clean Architecture principles.
- **UI**: 100% Jetpack Compose + Material3 + Monet (Dynamic Color).
- **Dependency Injection**: Koin (Koin-Android, Koin-Compose).
- **Data Layer**:
    - **Local**: Room Database (SQLite). Pre-populated or synced from cloud.
    - **Remote**: Retrofit + OkHttp.
- **Concurrency**: Kotlin Coroutines + Flow.
- **Key Components**:
    - `CallScreeningService`: Intercepts calls. **Critical**: Must query DB < 50ms.
    - `DirectoryProvider`: Injects caller ID info into system dialer.
    - `WorkManager`: Handles background data synchronization.

## Code Style & Standards

- **Language**: Kotlin (Strict mode).
- **JDK**: JVM Target 21.
- **Structure**:
    - `app/src/main/java/vip/mystery0/pixel/telo/`
    - Files should generally not exceed 1000 lines.
    - Business logic belongs in ViewModels or UseCases, not Activities/UI.
- **Performance Constraints**:
    - Incoming call DB lookups must complete within **50ms**.
    - Network queries must timeout after **2s**.
- **Privacy**:
    - Minimal permissions (`READ_CALL_LOG`, `READ_CONTACTS` only for Provider).
    - No unnecessary overlays; prefer native system integration.

## Documentation

- See `.agentdocs/` for detailed documentation on PRD, architecture, and UI specs.
- See `GEMINI.md` for original project guidelines and detailed specialized instructions.
