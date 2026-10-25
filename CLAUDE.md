# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Pool Print (Gradle project `PoolPrint`, package `com.yahyafati.poolprint`) is a Kotlin Multiplatform + Compose Multiplatform app targeting **Android** and **macOS desktop (JVM)**. It prints PDFs on the Uni Freiburg TF pool printers by running `lpr` over SSH on `login.informatik.uni-freiburg.de`, streaming the PDF into its stdin. The code is still the template (greeting sample); the design is fully documented in `docs/`.

## Read before working

- `docs/development.md` — **working rules** (milestone-by-milestone, stop and report, dependency policy, testing strategy, report format). Follow it.
- `docs/roadmap.md` — milestones, tasks, acceptance criteria, manual checklists. Tick tasks as they land.
- `docs/Project Overview.md` — product, environment facts, hard constraints.
- `docs/architecture.md` — target source sets (incl. planned `jvmCommonMain`), packages, interfaces, flows.
- `docs/print-options.md` — CUPS option mapping, validation, `lpstat`/`lpoptions`/`lpq` parsing, cost formula.
- `docs/security.md` — key storage, host-key TOFU, command-construction rules, required injection tests.
- `docs/ux.md` — screens and error catalogue. `docs/decisions.md` — decision log and open questions.

## Hard rules (summary — details in the docs)

- No backend/cloud/telemetry; no password auth ever (publickey only); no remote file copies; no filenames or free text in remote commands.
- Remote commands only via the `core.command` builders from typed, allow-listed values, every token shell-quoted. Any change there needs injection tests.
- `commonMain` stays platform-free; SSH code lives in `jvmCommonMain`.
- No secret prompts in regular use: after onboarding, printing never asks for a password/passphrase/unlock.
- Libraries may be added as needed; register each one in `docs/decisions.md` (dependency register).
- The real pool server is unreachable from here: never claim remote behaviour works; put it on the manual checklist.
- Keep docs in sync with design changes in the same change.

## Commands

- Android debug APK: `./gradlew :androidApp:assembleDebug`
- Desktop run: `./gradlew :desktopApp:run`
- Desktop with hot reload: `./gradlew :desktopApp:hotRun --auto`
- Desktop native installers (Dmg/Msi/Deb): `./gradlew :desktopApp:packageDistributionForCurrentOS`
- Shared tests on JVM: `./gradlew :shared:jvmTest`
- Shared tests on Android host (local JVM): `./gradlew :shared:testAndroidHostTest`
- Single test: `./gradlew :shared:jvmTest --tests "com.yahyafati.poolprint.SharedCommonTest"` (append `.methodName` for one method)

No lint/formatter is configured beyond `kotlin.code.style=official`. Gradle configuration cache and build cache are enabled (`gradle.properties`), so build logic must stay configuration-cache compatible.

## Architecture

Three Gradle modules:

- **`:shared`** — KMP library holding all app logic and UI. Entry composable is `App()` in `commonMain`. Uses the newer `com.android.kotlin.multiplatform.library` plugin (configured via `kotlin { android { ... } }`, not a separate `android {}` block). Targets: `jvm()` and `android`.
  - `commonMain` — shared code and Compose UI; resources live in `commonMain/composeResources` and are accessed through the generated `poolprint.shared.generated.resources.Res`.
  - `androidMain` / `jvmMain` — platform `actual` implementations for `expect` declarations (e.g. `getPlatform()`). Platform-specific functionality (printing APIs will differ per OS) belongs behind `expect`/`actual` or interfaces here.
  - Tests: `commonTest` (runs on both targets), `jvmTest`, `androidHostTest`.
- **`:androidApp`** — thin Android application shell; `MainActivity` just hosts `App()`.
- **`:desktopApp`** — thin Kotlin/JVM Compose Desktop shell; `main.kt` opens a `Window` hosting `App()`. Main class `com.yahyafati.poolprint.MainKt`; macOS bundle id `com.yahyafati.poolprint`.

Keep the app modules thin; new features go in `:shared`.

Dependency and plugin versions are centralized in `gradle/libs.versions.toml` (reference via `libs.*`). JVM target is 11; Android minSdk 29, compile/target SDK 37.
