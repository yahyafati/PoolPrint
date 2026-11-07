# Development guide

Rules for anyone working on this repo: human contributors and AI coding agents. CLAUDE.md links here.

## Ground rules

1. **Milestone by milestone.** Follow [roadmap.md](roadmap.md). Don't start the next milestone until
   the current one has been reported and the owner has acknowledged it.
2. **Respect the hard constraints** in [Project Overview.md](Project%20Overview.md#non-goals-and-hard-constraints):
   no backend, no password auth (except the one-time key install, D-20), no telemetry, no remote file copies,
   no filenames in commands.
3. **Security rules are requirements:** see [security.md](security.md). Any change to command
   construction needs injection tests.
4. **Add dependencies as needed, but register them.** Every new library goes in the
   [dependency register](decisions.md#dependency-register) with its purpose. Use the version catalog
   and check for the latest stable version. Be extra careful with anything that touches crypto or
   credentials.
5. **Keep `commonMain` platform-free.** No `java.*`, `android.*`, SSH or PDF library imports. Use
   interfaces in `commonMain` and implementations in `jvmCommonMain`, `androidMain` or `jvmMain`.
6. **Keep the app modules thin.** Features go in `:shared`. `:androidApp` and `:desktopApp` hold only
   entry points, manifest and plist setup, and intent or file-open glue.
7. **Never claim a remote feature works against the real server.** Nobody in CI or agent sessions can
   reach `login.informatik.uni-freiburg.de`. Remote behaviour is verified by unit tests with
   `FakeSshClient` and, where useful, a local test SSH server. Real-server behaviour goes on the
   manual checklist.
8. **Keep the docs current.** When the design changes, update the relevant doc in the same change.
   When a milestone task is done, tick it in the roadmap.

## Build and run

| Task | Command |
|---|---|
| Android debug APK | `./gradlew :androidApp:assembleDebug` |
| Install on device/emulator | `./gradlew :androidApp:installDebug` |
| Desktop run | `./gradlew :desktopApp:run` |
| Desktop hot reload | `./gradlew :desktopApp:hotRun --auto` |
| macOS `.dmg` | `./gradlew :desktopApp:packageDmg` (or `packageDistributionForCurrentOS`) |
| Shared tests (JVM) | `./gradlew :shared:jvmTest` |
| Shared tests (Android host) | `./gradlew :shared:testAndroidHostTest` |
| One test class | `./gradlew :shared:jvmTest --tests "com.yahyafati.poolprint.core.command.LprCommandBuilderTest"` |
| Standalone test SSH server (emulator at `10.0.2.2:2222`) | `./gradlew :shared:runTestSshServer` |
| Build-logic tests (version parsing) | `./gradlew :buildSrc:test` (`make test-build`; `make test-all` runs every suite) |
| Release files (tests, release-key APK, DMG, checks, checksums, notes) | `make release` → `build/release/<version>/` ([release.md, Phase 4](release.md#how-the-release-script-works)) |
| Draft GitHub release from those files | `make release-draft` (needs `gh`; publishing stays manual) |
| Release with a Windows Msi built elsewhere | `make release WINDOWS_MSI=<file.msi>` (`scripts/release.sh build --windows-msi`; [release.md, Windows](release.md#windows-the-msi-d-25d-31)) |
| Windows Msi | `.\gradlew.bat :desktopApp:packageReleaseMsi` / `make msi`, Windows only; from a Mac use the manual `.github/workflows/windows.yml` (*Actions → Windows → Run workflow*, D-31) |
| CI (GitHub Actions) | `.github/workflows/ci.yml`: every test suite on pushes to `main` and on PRs |
| Release in GitHub Actions (optional) | push a tag `v<version>` → `.github/workflows/release.yml` makes a draft release ([release.md, Phase 9](release.md#phase-9-optional--automate-with-github-actions)) |
| Windows Credential Manager test (touches the real store; Windows only) | `POOLPRINT_WINCRED_TEST=1 ./gradlew :shared:jvmTest --tests '*WindowsCredential*'` |
| macOS Keychain test (touches the real login keychain) | `POOLPRINT_KEYCHAIN_TEST=1 ./gradlew :shared:jvmTest --tests '*MacKeychain*'` |

**Release signing (Android).** `release` builds are signed with the release key when the four
`poolprint.signing.*` keys are in the root `local.properties` (or the `POOLPRINT_SIGNING_*` environment variables
are set, for CI); see [release.md](release.md#phase-1--android-release-signing-key). Without them, `release` falls
back to the debug key and Gradle warns "Release APK is debug-signed, not for distribution": fine for your own
devices, refused by the release script. A partial setup or a missing keystore file fails the build. Never print,
log or commit the signing values; `*.jks`, `*.keystore` and `keystore.properties` are git-ignored.

**Version.** The app version lives only in `gradle.properties` (`poolprint.versionName`, `poolprint.versionCode`);
both app modules read it through `buildSrc/PoolPrintVersion.kt`. How to bump it:
[release.md, patch routine](release.md#patch-release-routine).

The configuration cache and build cache are on, so build logic must stay configuration-cache compatible.
Versions live in `gradle/libs.versions.toml`; reference them through `libs.*`.

## Testing strategy

| Layer | What | Where | Tooling |
|---|---|---|---|
| Core logic | Command builder, quoting, validation, cost estimator, parsers | `commonTest` | `kotlin.test` |
| Security | Injection payloads for every field; quoting round-trip through `/bin/sh` | `commonTest` and `jvmTest` | Parameterised tests |
| Flows and view models | Onboarding, test connection, print, discovery fallback, queue/cancel | `commonTest` | `FakeSshClient` |
| Real SSH implementation | Exec, stdin streaming, host-key callback, auth-method restrictions | `jvmTest` (or `jvmCommonTest`) | In-process SSH server (Apache MINA SSHD, test-only) |
| Platform | Keystore encryption, PdfRenderer, intents | `androidHostTest` / device tests | Robolectric if needed |
| Real server | Everything remote | Manual | Roadmap checklists |

Conventions:
- Tests are named for behaviour: `rejects page range containing semicolon`.
- Fixtures (sample `lpstat` / `lpoptions` / `lpq` output) live under `shared/src/commonTest/`. Mark
  synthetic ones as synthetic in a header comment.
- No test may open a real network connection to an external host.

## Code style

- `kotlin.code.style=official`. No formatter or linter is configured.
- Prefer small, pure functions in the core. Return typed results (`sealed` error types) over exceptions
  for expected failures such as validation or auth.
- Money is in integer cents. Format it only in the UI.
- User-facing strings go in Compose resources (`commonMain/composeResources`), not hardcoded.

## Milestone reports

At the end of every milestone, write a short report (in chat or in the PR description) with:

1. **Summary:** what was built and what changed in the docs.
2. **Test status:** which test suites pass, with the commands to run them.
3. **Manual test checklist:** copied from [roadmap.md](roadmap.md) and extended with anything new.
4. **Uncertainties:** assumptions made, open questions, anything that couldn't be verified.
   Add these to [decisions.md](decisions.md) as well.

Then stop and wait for the owner.

## Git

- Small, focused commits. Branch per milestone or feature (`m1/ssh-spike`, `m2/cost-estimator`, …).
- Never commit private keys, `local.properties`, or real usernames or fingerprints in fixtures
  (anonymise sample `lpq` owners).
