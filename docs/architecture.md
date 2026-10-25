# Architecture

> Status: **M1 implemented**; later milestones are still design. Update this document whenever the
> implementation diverges.
>
> Where M1 differs from the sketches below: `service` (`PoolServices`, `ErrorCatalogue`) and `ui`
> (`OnboardingModel`, `PrintModel`, `SettingsModel` plus the screens) exist as extra packages; the key
> store, host-key store and settings are DataStore-backed in `commonMain`; `buildAppDependencies`
> (`jvmCommonMain`) wires the real implementations for both platforms; `rememberPdfPicker` and
> `rememberTextSharer` are `expect`/`actual` composables.

## Big picture

```mermaid
flowchart LR
    subgraph Device["Android phone / Mac"]
        IN["PDF in<br/>(share intent, picker,<br/>drag & drop, Open With)"] --> UI["Compose UI<br/>(commonMain)"]
        UI --> CORE["Core<br/>models · validation ·<br/>command builder · cost"]
        CORE --> SSH["SshClient<br/>(jvmCommonMain impl)"]
        KEYS["Key store<br/>Android Keystore / macOS Keychain"] --> SSH
        HK["Host-key store<br/>(TOFU pin)"] --> SSH
    end
    SSH -- "SSH exec channel<br/>stdin = PDF bytes" --> LOGIN["login.informatik.uni-freiburg.de<br/>lpr / lpq / lprm / lpoptions"]
    LOGIN --> PRN["tfppr1 … tfppr4"]
```

No component talks to anything other than the pool login server.

## Gradle modules

| Module | Role | Rules |
|---|---|---|
| `:shared` | KMP library holding **all** app logic and UI | Features go here |
| `:androidApp` | Android application shell (manifest, `MainActivity`, intent handling glue) | Keep thin |
| `:desktopApp` | Compose Desktop shell (`main.kt`, window, macOS packaging) | Keep thin |

## Source sets in `:shared`

```
commonMain            pure Kotlin + Compose: models, validation, command builder, parsers,
                      cost estimator, interfaces (SshClient, KeyStore, …), view models, UI
   └── jvmCommonMain  NEW intermediate source set shared by Android and desktop JVM:
         │            real SshClient (SSH library), Ed25519 key generation, PDF byte streaming
         ├── androidMain   Android Keystore encryption, PdfRenderer page count, content-URI reading
         └── jvmMain       macOS Keychain / ~/.ssh key import, PDFBox page count, file reading
commonTest            unit tests for everything in commonMain + FakeSshClient
jvmCommonTest         (optional) tests for the real SshClient against an in-process SSH server
jvmTest / androidHostTest   platform-specific tests
```

`jvmCommonMain` has to be wired up explicitly. The default hierarchy template has no
"Android + JVM" group, so `shared/build.gradle.kts` adds one with `applyDefaultHierarchyTemplate { common {
group("jvmCommon") { withJvm(); withCompilations { it.platformType == KotlinPlatformType.androidJvm } } } }`.
(`withAndroidTarget()` doesn't match the new `com.android.kotlin.multiplatform.library` target, and manual
`dependsOn` edges switch the default template off with a warning.)

**Rule:** `commonMain` must not reference `java.*`, `android.*` or any SSH/PDF library. If something
needs the platform, define an interface in `commonMain` and implement it lower down.

## Package layout

Root package: `com.yahyafati.poolprint`.

| Package | Source set | Contents |
|---|---|---|
| `core.model` | common | `Printer`, `PrinterType` (BW/COLOR), `PrintOptions`, `PrintJob`, `Duplex`, `PagesPerSheet`, `Orientation`, `PaperSize`, `PageRanges` |
| `core.validation` | common | Validators that turn raw UI input into typed values or a `ValidationError` |
| `core.command` | common | `ShellQuote`, `RemoteCommand`, `LprCommandBuilder`, `LpqCommandBuilder`, `LprmCommandBuilder` |
| `core.cost` | common | `CostEstimator`, `CostEstimate` (integer cents) |
| `core.discovery` | common | `LpstatParser`, `LpoptionsParser`, `PrinterCapabilities`, `DiscoveredOption` |
| `core.queue` | common | `LpqParser`, `QueueEntry` |
| `transport` | common (interface), jvmCommon (impl) | `SshClient`, `SshResult`, `SshError`, `ConnectionConfig`, `ByteSource` |
| `keys` | common (interface), platform (impl) | `KeyManager`, `PrivateKeyStore`, `PublicKeyFormatter` |
| `hostkeys` | common (interface), platform (impl) | `HostKeyStore`, `HostKeyVerdict` |
| `pdf` | common (interface), platform (impl) | `PdfDocumentSource`, `PdfPageCounter` |
| `settings` | common (interface), platform (impl) | Last-used options (`StoredPrintOptions`), presets, discovery cache, username |
| `ui.*` | common | Screens, view models, theme |

## Key interfaces (sketch)

These are design intent, not final signatures.

```kotlin
// commonMain — streaming without java.io
interface ByteSource : AutoCloseable {
    /** Reads up to buffer.size bytes; returns -1 at end. */
    fun read(buffer: ByteArray): Int
}

/** A command that is already validated and quoted. Only the core.command builders can create one. */
class RemoteCommand internal constructor(val value: String)

interface SshClient {
    suspend fun exec(
        command: RemoteCommand,
        stdin: ByteSource? = null,
        onUploaded: (Long) -> Unit = {},          // bytes sent so far, for the progress bar
        timeoutMs: Long = DEFAULT_TIMEOUT_MS,
    ): SshResult
}

data class SshResult(val exitCode: Int, val stdout: String, val stderr: String)

sealed interface SshError {
    data object HostUnreachable : SshError         // DNS, no route, timeout on connect
    data object AuthRejected : SshError            // key not in authorized_keys
    data class HostKeyUnknown(val keyType: String, val fingerprint: String) : SshError   // first connect: ask user
    data class HostKeyChanged(val expected: String, val actual: String) : SshError  // loud warning
    data object Timeout : SshError
    data class Protocol(val message: String) : SshError
}
```

- `exec` returns `SshResult` even when the exit code isn't 0. A non-zero exit is a remote failure that
  gets shown to the user with stderr, not a transport error.
- Transport failures are thrown as `SshException(SshError)` (D-10). `PoolServices` catches them once and
  returns typed results with a `UserFailure` for the UI. One `SshClient` is created per operation
  through an `SshClientFactory`.
- `RemoteCommand` has an `internal` constructor, so the UI layer can't build a raw command string by
  accident.
- `FakeSshClient` (in `commonTest`) records commands and stdin bytes and returns scripted results or
  errors. All flow and view-model tests use it.

## Main data flow: printing

1. **Input.** Platform glue turns a share intent, picker result, drop or Open-With event into a
   `PdfDocumentSource`: display name, size, a way to open a `ByteSource`. The page count comes from `PdfPageCounter` (counted on the device, off the UI thread). The display
   name is shown in the UI only. It never reaches the remote command.
2. **Options.** The UI pre-fills from the last-used options or the selected preset. Every change goes
   through `core.validation`.
3. **Estimate.** `CostEstimator.estimate(options, pageCount, policy)` → `CostEstimate`, shown on the Print button.
4. **Build.** `LprCommandBuilder.build(job, capabilities)` → `RemoteCommand`, or a validation error.
5. **Execute.** `SshClient.exec(command, stdin = document.openByteSource())` opens an exec channel and
   streams the bytes into stdin. When the bytes are done it closes stdin (EOF), then waits for the
   exit status.
6. **Report.** Exit 0 means success; CUPS usually prints `request id is tfppr1-123 (1 file(s))` to
   stdout, so parse the job id if it's there. A non-zero exit or an `SshError` gets mapped to a
   user-facing message ([ux.md](ux.md#error-catalogue)), with the raw stderr behind a "Details" toggle.

## Other flows

- **Test connection:** `exec("echo ok")` → expects stdout `ok`, exit 0.
- **Discovery (M3):** `lpstat -p` → printer list; per printer `lpoptions -p <printer> -l` → options.
  Results are cached per printer with a timestamp, and can be refreshed manually. If anything fails,
  the app silently falls back to the hardcoded printers and standard options, with a small "Using
  defaults" hint.
- **Queue (M3):** `lpq -P <printer>` → `LpqParser` → entries; the user's own entries get a Cancel action →
  `lprm -P <printer> <jobId>`.

## Platform layer

### Android (`:androidApp` + `androidMain`)

- Manifest intent filter: `ACTION_SEND`, MIME `application/pdf`, so Pool Print shows up in the share
  sheet. (`ACTION_VIEW` for PDFs is a possible later extra.)
- In-app picking: `ACTION_OPEN_DOCUMENT` through the Activity Result API, with MIME `application/pdf`.
- Read content URIs through `ContentResolver`. No storage permission is needed.
- Page count: `android.graphics.pdf.PdfRenderer` (built in). It needs a seekable file descriptor, so
  copy non-seekable streams into app cache first, and delete the copy after printing.
- Private key: encrypted with an AES-GCM key that lives in Android Keystore. See [security.md](security.md).
- Network: `INTERNET` permission only.

### macOS desktop (`:desktopApp` + `jvmMain`)

- File picker: AWT `FileDialog`, which looks native on macOS, filtered to `.pdf`.
- Drag and drop onto the window: Compose Desktop drag-and-drop target modifier.
- "Open With": `java.awt.Desktop.setOpenFileHandler` and `CFBundleDocumentTypes` for `com.adobe.pdf`
  in the app's `Info.plist`, through `compose.desktop.application.nativeDistributions.macOS.infoPlist`.
  Files can arrive before the window is created, so buffer them.
- Page count: Apache PDFBox (the allowed "PDF page-count library").
- Private key: macOS Keychain through JNA and the Security framework, or a user-chosen existing key
  from `~/.ssh` (with its passphrase, if any, kept in the Keychain). See
  [decisions.md](decisions.md#d-04-macos-keychain-access).
- Packaging: `.dmg` through `packageDistributionForCurrentOS` (M4). Code signing and notarisation are out of scope unless requested.

## State and persistence

| Data | Where | Notes |
|---|---|---|
| Pool username | settings | Not secret |
| Private key | platform secure store | Never logged, never exported in plaintext |
| Public key | settings (or derived) | Shown during onboarding and in Settings |
| Pinned host key | host-key store | SHA-256 fingerprint plus key type, per `host:port` |
| Last-used printer and options | settings | M2 |
| Presets | settings | M3 |
| Discovery cache | settings/cache | Per printer, with timestamp, M3 |

Non-secret state is stored with AndroidX DataStore plus `kotlinx-serialization-json`
([decisions.md](decisions.md#d-03-persistence-for-settings-presets-and-caches)). Secrets go only in
the platform secure store.

## Concurrency

- All SSH calls are `suspend` and run on `Dispatchers.IO` inside the implementation.
- One SSH connection per operation is fine for M1. Connection reuse is an optimisation for later.
- Printing can be cancelled while bytes are uploading (closing the channel); after `lpr` has accepted
  the job, cancelling means `lprm`.
