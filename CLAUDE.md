# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

EasyPrinter is a Kotlin Multiplatform + Compose Multiplatform app targeting **Android** and **Desktop (JVM)**. It is currently at the template stage (greeting sample); `docs/Project Overview.md` is reserved for the product description but is empty so far.

## Commands

- Android debug APK: `./gradlew :androidApp:assembleDebug`
- Desktop run: `./gradlew :desktopApp:run`
- Desktop with hot reload: `./gradlew :desktopApp:hotRun --auto`
- Desktop native installers (Dmg/Msi/Deb): `./gradlew :desktopApp:packageDistributionForCurrentOS`
- Shared tests on JVM: `./gradlew :shared:jvmTest`
- Shared tests on Android host (local JVM): `./gradlew :shared:testAndroidHostTest`
- Single test: `./gradlew :shared:jvmTest --tests "com.yahyafati.easyprinter.SharedCommonTest"` (append `.methodName` for one method)

No lint/formatter is configured beyond `kotlin.code.style=official`. Gradle configuration cache and build cache are enabled (`gradle.properties`), so build logic must stay configuration-cache compatible.

## Architecture

Three Gradle modules:

- **`:shared`** — KMP library holding all app logic and UI. Entry composable is `App()` in `commonMain`. Uses the newer `com.android.kotlin.multiplatform.library` plugin (configured via `kotlin { android { ... } }`, not a separate `android {}` block). Targets: `jvm()` and `android`.
  - `commonMain` — shared code and Compose UI; resources live in `commonMain/composeResources` and are accessed through the generated `easyprinter.shared.generated.resources.Res`.
  - `androidMain` / `jvmMain` — platform `actual` implementations for `expect` declarations (e.g. `getPlatform()`). Platform-specific functionality (printing APIs will differ per OS) belongs behind `expect`/`actual` or interfaces here.
  - Tests: `commonTest` (runs on both targets), `jvmTest`, `androidHostTest`.
- **`:androidApp`** — thin Android application shell; `MainActivity` just hosts `App()`.
- **`:desktopApp`** — thin Kotlin/JVM Compose Desktop shell; `main.kt` opens a `Window` hosting `App()`. Main class `com.yahyafati.easyprinter.MainKt`.

Keep the app modules thin; new features go in `:shared`.

Dependency and plugin versions are centralized in `gradle/libs.versions.toml` (reference via `libs.*`). JVM target is 11; Android minSdk 29, compile/target SDK 37.
