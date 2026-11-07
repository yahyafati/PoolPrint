# Roadmap

Work goes **milestone by milestone**. At the end of each milestone, stop and report: a short summary,
the manual test checklist, and anything that's uncertain (see [development.md](development.md#milestone-reports)).

Status legend: ⬜ not started · 🟨 in progress · ✅ done (unit-tested) · 🔶 done, needs manual check against the real server

| Milestone | Status |
|---|---|
| M1 — MVP | 🔶 |
| M2 — Options & cost | 🔶 |
| M3 — Discovery, presets, queue | 🔶 |
| M4 — macOS polish | 🔶 |
| R1 — First sideloaded GitHub release (v1.0.0) | 🔶 |
| W — Windows desktop port (W0–W4) | 🔶 (D-25–D-32; done and unit-tested on macOS, needs the checklist on a Windows PC) |

---

## M1 — MVP

**Goal:** onboarding and key generation, test connection, pick/share a PDF → printer → print, and a
readable result.

### Tasks

- ✅ **Rename:** project `PoolPrint`, package `com.yahyafati.poolprint`, app name "Pool Print" (D-08).
- ✅ **Groundwork:** Kotlin 2.4.21, Compose Multiplatform 1.12.2, Gradle 9.8.2, AGP 9.4.1 (D-05);
  `jvmCommonMain` source set; M1 libraries added and registered.
- ✅ **SSH spike on Android (do this first):** sshj accepted on desktop and the API 29 and 35 emulators
  (D-01).
- ✅ Core models: `Printer` (hardcoded table), `PrinterType`, `PrintJob`, a minimal `PrintOptions`
  (printer, copies).
- ✅ `ShellQuote` and `LprCommandBuilder` (printer, copies), plus injection tests.
- ✅ `SshClient` interface, `SshError`, `ByteSource`; the real implementation in `jvmCommonMain`
  (`SshjClient`); `FakeSshClient` in `commonTest`.
- ✅ Host-key TOFU store and its UI (trust prompt, full-width changed-key warning, Settings → forget
  with a second confirmation).
- ✅ Key generation and secure storage: Android Keystore and macOS Keychain. (The `~/.ssh` path option
  is deferred, D-12.)
- ✅ Onboarding screens and **Test connection** (`echo ok`).
- 🔶 Android: `ACTION_SEND` `application/pdf` intent filter and an in-app picker (built, not yet
  driven from another app).
- 🔶 Desktop: file picker (AWT `FileDialog`; built, the dialog itself not yet driven; drag and drop comes in M4).
- ✅ Print flow: stream the PDF to `lpr` stdin; success or failure with readable stderr (error
  catalogue). Verified against a local test SSH server with a fake `lpr`; the real server is on the manual checklist.

### Acceptance criteria

- Unit tests pass on `:shared:jvmTest` and `:shared:testAndroidHostTest`.
- Every user-controlled field reachable in M1 has injection tests.
- Flow tests with `FakeSshClient` cover success, `AuthRejected`, `HostKeyChanged`, and a non-zero exit with stderr.
- No password prompt anywhere; password auth is disabled in the SSH config. *(Since D-20: except the optional
  one-time key install in onboarding; regular commands stay publickey-only.)*
- The app builds and launches on an Android emulator or device and on macOS. *(Launched on the API 29
  emulator through onboarding; started on macOS without errors.)*

### Manual test checklist (owner, against the real server)

*Owner, 2026-11-07: on Android (release-key build), onboarding with the pool-password key install and printing a
PDF shared from another app work against the real server. Ticked below and in M4; the printer used wasn't recorded.*

*Owner, 2026-11-07: the release gate ([release.md, Phase 6](release.md#phase-6--test-the-candidate-release-gate))
passed on the downloaded `v1.0.0-rc.1`/`rc.2` files. Its checks are ticked or annotated below, in M2–M4.*

- [x] Fresh install → onboarding → copy the public key → add it to `~/.ssh/authorized_keys` → Test connection ✓.
- [x] The host-key fingerprint shown matches `ssh-keygen -lF login.informatik.uni-freiburg.de` from a trusted machine.
- [ ] Test connection *before* adding the key gives the "Key not authorized" message.
- [x] Share a PDF from another Android app (Files, browser, mail) → print to `tfppr1` → the paper comes out and matches the document.
      *(2026-11-07, Android; printer not recorded.)*
- [x] Print from the desktop file picker.
- [x] Copies = 2 gives two copies. *(Release gate, 2026-11-07.)*
- [ ] Wrong printer name (simulate if possible) gives a readable error with stderr.
- [ ] Airplane mode / no network gives "Can't reach the pool server".
- [ ] Off campus **with** VPN: Test connection and printing work.
- [x] Off campus **without** VPN: you get the "Connect to the university VPN" message, not a cryptic error.
- [x] Printing again after restarting the app or rebooting the device: no password or passphrase prompt.
- [ ] Large PDF (≥ 20 MB) uploads and prints; Cancel during upload stops the job.
- [x] Nothing is left in the pool home directory afterwards (`ls -la ~` unchanged).
- [x] Compare the fingerprint the app shows with `ssh-keygen -lF` (decisions.md, open question 5). The
      real server may offer other host-key or key-exchange algorithms than the test server; if Test
      connection fails with a protocol error, send the details text.
- [ ] Android: share a PDF **from another app** and check that Pool Print opens its print sheet; also try
      the in-app "Pick a PDF" picker. *(Sharing works, 2026-11-07; the picker is still open.)*
- [ ] Android: rotate the phone on the print sheet; the document stays.
- [ ] macOS: first launch creates a Keychain item "Pool Print"; no prompt on later launches.
- [x] macOS: "Pick a PDF" opens the native file dialog and prints the chosen file.
- [ ] Settings → Trusted fingerprint → Forget → confirm: the next connection asks to trust again.
- [ ] If minification is ever enabled for release builds: install the release APK and run Test
      connection (R8 broke SSH at runtime without the BouncyCastle keep rule).

---

## M2 — Options & cost

**Goal:** all standard options in the UI, a local page count and cost estimate, and remembering the
last-used printer and options.

### Tasks

- ✅ The full `PrintOptions` model and validators (copies, page ranges, duplex, number-up, orientation,
  fit-to-page, paper size), per [print-options.md](print-options.md).
- ✅ `LprCommandBuilder` with all standard options, plus injection tests for each new field.
- 🔶 `PdfPageCounter`: desktop PDFBox (unit-tested); Android `PdfRenderer` (builds, not yet run on a device).
- ✅ `CostEstimator` with both page-range semantics and both billing units (constants), plus tests.
- 🔶 Print sheet with the Advanced options section; the Print button label with pages and cost; a cost
  warning threshold. (Model and formatting are unit-tested; the screen itself has not been looked at on a
  device or in the desktop window.)
- ✅ Persistence for the last-used printer and layout options (copies and page ranges deliberately start
  fresh, D-13).

### Acceptance criteria

- Command builder tests cover every option and every combination class.
- The estimator tests from [print-options.md](print-options.md#cost-estimation) pass.
- Reopening the app pre-fills the last-used printer and options.

### Manual test checklist

- [ ] The Print button shows the page count and cost; the estimate is `?` for a damaged PDF (try a
      renamed text file) and printing still works. Android counts pages for a PDF shared from another app.
      *(Release gate, 2026-11-07: count and cost show on Android and in the packaged mac app; the damaged-PDF case is
      still open.)*
- [ ] Reopen the app: the printer, duplex, pages per sheet, orientation, fit-to-page and paper are pre-filled.
- [ ] Duplex long edge vs short edge comes out correctly in portrait and landscape.
      *(Release gate, 2026-11-07: long edge correct; short edge and landscape still open.)*
- [ ] 2-up and 4-up layouts look right. *(Release gate, 2026-11-07: 2-up correct; 4-up still open.)*
- [ ] **Page ranges with 2-up:** print `-o number-up=2 -o page-ranges=1` on a 4-page PDF. Do you get
      document pages 1–2 on one sheet (output-page semantics) or only page 1 (input-page semantics)?
      Record the answer in [decisions.md](decisions.md#d-07-page-range-semantics-with-number-up).
- [ ] Fit to page with a non-A4 PDF (e.g. Letter or slides).
- [ ] Paper size A4 is honoured.
- [ ] **Billing:** print a 2-page PDF in duplex, then check the charge on the poolcups page:
      1 page or 2? Record the answer in [decisions.md](decisions.md#d-06-billing-unit-for-duplex).
- [ ] Colour printer: is it charged 10 ct for grayscale content too?
- [ ] The estimated cost matches the actual charge for 3 different jobs.

---

## M3 — Discovery, presets, queue

**Goal:** learn the printers and their options from the server (with a silent fallback), save option
sets as presets, and see and cancel your own jobs.

### Tasks

- ✅ `LpstatParser` and `LpoptionsParser`, with fixtures. **The fixtures are synthetic** (modelled on the
  CUPS docs, `commonTest/.../core/discovery/Fixtures.kt`); real captures are still wanted (open question 1).
- 🔶 Discovery service (`DiscoveryService`): fetch, cache per printer (persisted, 24 h staleness), manual
  refresh in Settings, silent fallback. Unit-tested with `FakeSshClient`; the real server output is unseen.
- 🔶 Generated controls for discovered options; validation against the cache; collision filtering.
  (Model and validators are unit-tested; the dropdowns themselves have not been looked at.)
- 🔶 Presets: create (print sheet), rename and delete (Presets screen), apply, preset dropdown on the
  print sheet. (Logic unit-tested; screens not looked at.)
- 🔶 `LpqParser`; Queue screen; cancel own job through `lprm`, with confirmation. (Logic unit-tested;
  screen not looked at; no pull-to-refresh yet, only a Refresh button.)
- ✅ Injection tests for discovered keys and values and for job ids.

### Acceptance criteria

- ✅ The app is fully usable with discovery failing (tested with `FakeSshClient` returning errors, non-zero
  exits, empty output, localised text and garbage, in `PrintModelDiscoveryTest` and `DiscoveryServiceTest`).
- ✅ A discovered value that isn't in the cache can't reach the command (`DiscoveryCommandBuildersTest`,
  `PrintModelDiscoveryTest`).
- ✅ Only own jobs can be cancelled (`QueueServiceTest`, `QueueModelTest`, `DiscoveryCommandBuildersTest`).

### Manual test checklist

- [ ] `lpstat -p` and `lpoptions -p tfppr… -l` run on the login server. Paste the real outputs so they can become fixtures.
- [ ] Also paste `lpq -P tfppr1` with and without jobs, and say how many printers `lpstat -p` lists (the
      app keeps at most 100; the printer dropdown shows all of them).
- [ ] The discovered options for each printer look sensible, and changing one (e.g. colour mode) has the expected effect.
- [ ] The printer dropdown shows idle / printing / disabled for each printer; a printer that is not
      tfppr1–4 is labelled "type unknown, billed as colour".
- [ ] Off campus without VPN (or airplane mode): the print sheet still opens, shows "Using default printer
      settings." and printing is unaffected. *(Release gate, 2026-11-07: discovery failing (VPN off, then on) didn't
      block printing; the message itself wasn't recorded.)*
- [ ] Settings → Refresh printers and options: shows the count and a time; a failure keeps the old data.
- [ ] Queue shows your job while it's printing; Cancel (after the confirmation) removes it; other users' jobs have no Cancel.
- [ ] A preset applied from the share sheet prints in ≤ 3 taps (open the dropdown, pick the preset, Print).
- [ ] Save a preset with a long name, a name with quotes and a duplicate name; rename and delete one.
- [ ] A job printed through Pool Print appears in the queue as `(stdin)`; confirm the Rank/Owner/Job columns
      parse (the owner must equal your pool username, or nothing is marked as yours).
- [ ] Does the server print `lpstat` in English? If it is localised the printer list stays empty
      (the app then uses its built-in list).
- [ ] Look at the new screens on Android and macOS (print sheet with generated controls, Presets, Queue,
      Settings): layout, long option labels, dark mode.

## M4 — macOS polish

### Tasks

- 🔶 Drag and drop a PDF onto the window (`IncomingPdfs` and `PdfDropTarget` in `jvmMain`; the file
  filtering and the dropped-file-list parsing are unit-tested, the drop itself has not been driven).
- 🔶 "Open With → Pool Print" (`CFBundleDocumentTypes` in `Info.plist`, `Desktop.setOpenFileHandler`
  installed before the window exists, the latest file buffered). The generated `Info.plist` was inspected;
  Finder delivering a file, cold or warm, has not been tried.
- ✅ `.dmg` packaging: "Pool Print", icon, bundle id, version, only the Dmg target (D-18). Built here
  (`packageDmg`, 87 MB); the packaged app starts, so the jlink runtime has what sshj and PDFBox need
  (`suggestRuntimeModules`). Not checked: opening a real PDF and counting its pages in the packaged app.
- 🔶 Desktop layout tweaks: minimum window size, File menu with ⌘O (open) and ⌘P (print). Built, not driven.

- 🔶 **Add another device** (D-19): Settings → Add a device… appends a pasted public key to `authorized_keys`
  (`AuthorizeKeyCommand`, `PublicKeyLine`). Unit-tested with `FakeSshClient` and injection payloads; the command was
  run against sh and csh locally. Not run against the real server.
- 🔶 **One-time password key install** (D-20): onboarding → "Add it for me with my pool password" installs the
  device key with `SshClient.installKeyWithPassword`, then tests the key login. Tested with `FakeSshClient` and the
  in-process server (password and keyboard-interactive, host key before auth, password zeroed, sent once). Not run
  against the real server.

### Manual test checklist

- [x] Password key install: fresh install → key step → "Add it for me with my pool password" → trust the server
      (check the fingerprint) → type the password → Add key → Test connection ✓ with the key. `~/.ssh/authorized_keys`
      has one new line, `ls -ld ~/.ssh` is `drwx------`. Note whether the server used `password` or
      `keyboard-interactive` and whether it asked for anything besides the password.
      *(Works on Android, 2026-11-07. Release gate, same day: one new line in `authorized_keys`. Not recorded: the
      `~/.ssh` permissions and which auth method.)*
- [x] Password key install with a wrong password: "didn't accept the password", the field is empty, and nothing was
      added. After the app restarts, nothing asks for the password again. *(Release gate, 2026-11-07.)*
- [ ] Add a device: on a second device (e.g. the phone) copy its key from onboarding, paste it in Settings → Add a
      device… on the Mac → "Added". On the phone, Test connection succeeds. Check that `~/.ssh/authorized_keys`
      on the server has one new line and that `ls -ld ~/.ssh` is `drwx------`.
- [ ] Drag a PDF from Finder onto the window → a "Drop a PDF to print it" hint shows while hovering and
      the print sheet opens on drop. Dragging a non-PDF shows "Only PDF files can be printed."
      *(Release gate, 2026-11-07: printing via drag and drop works; the hint and the non-PDF message weren't recorded.)*
- [ ] Drop a PDF while a job is uploading: nothing changes. Drop one while Settings or Queue is open: the
      print sheet is waiting when you leave.
- [ ] ⌘O opens the file dialog; ⌘P prints from the print sheet and is greyed out elsewhere (home,
      Settings, while sending, with invalid page ranges). *(Release gate, 2026-11-07: printing via ⌘O works; ⌘P
      still open.)*
- [ ] The window can't be shrunk below a usable size; the print sheet is readable at the minimum.
- [ ] Right-click a PDF in Finder → Open With → Pool Print, both while the app is closed and while it's running.
- [ ] Open With on a PDF does not take over double-click: Preview stays the default viewer.
- [ ] The app shows the lime printer-slot icon in the Dock and in the `.dmg` window, and is named "Pool Print".
- [ ] Android launcher shows the lime printer-slot icon under circle and squircle masks, and with themed icons on
      (Android 13+) the monochrome glyph is tinted like the other apps.
- [x] In the installed app, print a PDF: the page count appears (PDFBox works inside the jlink runtime)
      and Test connection works (sshj does too). *(Release gate, 2026-11-07.)*
- [ ] Install from the `.dmg` on a clean user account; first launch gets past Gatekeeper (expect the "unidentified developer" prompt).
- [ ] Keychain item shows up in Keychain Access under "Pool Print" and survives an app restart.
- [ ] The existing `~/.ssh` key option works, including with a passphrase-protected key (asked once,
      then never again).
- [ ] After installing an updated `.dmg`: note whether macOS asks for Keychain access, and whether
      "Always Allow" sticks. *(rc.1 → rc.2 was installed over in the release gate, 2026-11-07; the Keychain
      behaviour wasn't reported yet.)*

---

## R1 — First sideloaded GitHub release

**Goal:** `v1.0.0` on GitHub Releases as a release-key-signed APK and an ad-hoc-signed `.dmg`, with checksums,
notes and a clear "vibe-coded, provided as is" notice. The phases, owner/agent split and release gate are in
[release.md](release.md).

- ✅ Phase 0 — decisions (2026-11-07): public repo after the Phase 3 scan, MIT, SemVer with `-rc.N` pre-releases,
  ad-hoc macOS signing, arm64 only, built on the owner's Mac, Phase 6.2 blocking list as the gate, no update
  check (D-22, D-23).
- ✅ Phase 1 — Android release signing key (2026-11-07): owner's keystore in `~/keys`, release signing from
  `local.properties` / `POOLPRINT_SIGNING_*` with a warned debug-key fallback, keystore files git-ignored,
  certificate SHA-256 `0260ccfe…a269` recorded in release.md, owner's phone switched to the release-key build.
- ✅ Phase 2 — one version number (2026-11-07): `poolprint.versionName` / `poolprint.versionCode` in
  `gradle.properties` drive the Android version, the `.dmg` version (without `-rc.N`) and the Settings → About
  label "Pool Print <version>" with the vibe-coded line; checked by `buildSrc` tests (`make test-build`).
  Manual: 👤 open Settings → About once on Android and macOS and check the label reads "Pool Print 1.0.0-rc.1".
- 🟨 Phase 3 — repository ready to go public: agent part done 2026-11-07 (history scan: no secrets, two privacy
  items for the owner; `LICENSE`, `THIRD_PARTY_NOTICES.md`, README top with notice/Download/Install, `SECURITY.md`,
  bug-report template, licence and notices rows in Settings → About). The repository is public (2026-11-07).
  Left: 👤 enable private vulnerability reporting and secret scanning.
- ✅ Phase 4 — release script (2026-11-07): `make release` (`scripts/release.sh build`) refuses a dirty tree or an
  existing tag, runs `make test-all`, builds the APK and DMG, checks the APK certificate against the release
  SHA-256 and its version, mounts the DMG and checks the bundle (identifier, arm64, signature, version), and writes
  `build/release/<version>/` with stable file names, `SHA256SUMS.txt` and a `release-notes.md` draft from
  `scripts/release-notes.template.md`; `make release-draft` creates a draft (pre-)release with `gh`, targeted at the
  built commit.
- ✅ Phase 5 — release candidate (2026-11-07): `v1.0.0-rc.1` published as a pre-release on
  `b80b3b6`, with the APK, DMG, notices and `SHA256SUMS.txt`. Next: 👤 Phase 6, test the downloaded files.
- ✅ Phase 6 — release gate (2026-11-07): the downloaded `v1.0.0-rc.1` and `rc.2` install like a user's copy, every
  blocking check passes, and rc.2 installs over rc.1. Ticked in the M1–M4 checklists above; what's still open there
  goes into the 1.0.0 known limitations. Next: 🤖 Phase 7, prepare `1.0.0`.
- ✅ Phase 7 — publish v1.0.0 (2026-11-07): [`v1.0.0`](https://github.com/yahyafati/PoolPrint/releases/tag/v1.0.0) is
  the latest release, tagged on `58f0ab2` (`versionCode 4`), with the APK, DMG, notices and `SHA256SUMS.txt`
  (checksums match GitHub's); the notes list the known limitations.
- 🟨 Phase 8 — after the release (2026-11-07): release page, downloads and linked files load without a GitHub
  login. Left: 👤 optionally tell people. Later releases follow the patch routine in release.md.
- 🟨 Phase 9 (optional) — GitHub Actions (2026-11-07, D-24): `ci.yml` runs every test suite on pushes to `main`
  and on PRs; `release.yml` builds a pushed `v*` tag (release-key APK on Linux, DMG on macOS arm64, the release
  script's checks via `scripts/release.sh ci-*`) into a **draft** release. Checked locally only. The `release`
  environment and its four secrets are set up (2026-11-07). Left: 👤 push the workflows, then a first real run
  (ideally an RC).

---

## W — Windows desktop port

**Goal:** the desktop app also runs on Windows x64 (JVM), installed from an unsigned per-user Msi that is built on a
GitHub Actions `windows-latest` run (CI on pushes and PRs, D-32) and shipped by `release.yml` or added to a normal `make release`. Decisions: D-25–D-32 in
[decisions.md](decisions.md). Linux, Windows on ARM, the Microsoft Store and auto-update are out of scope. The desktop
app runs on the JVM, so sshj, BouncyCastle, PDFBox, DataStore and the Compose UI needed no change; the macOS-only parts
were the Keychain store, the data folder, "Open With", the menu shortcuts, the picker's filename filter, the
`Dmg`-only packaging and the Mac-only `Makefile` and release script.

| Phase | Status |
|---|---|
| W0 — Decisions and doc changes | ✅ |
| W1 — Platform code (key store, data folder, OS switch) | 🔶 |
| W2 — File hand-over (picker, drop, Open With, single instance) | 🔶 |
| W3 — Packaging (Msi) | 🔶 |
| W4 — Build workflow, release tooling, release gate | 🔶 |

All five are done in the repo and unit-tested on macOS; 🔶 means the Windows behaviour is not verified yet (no
Windows machine was available to the agents): the owner's [checklist](#windows-manual-checklist-owner-on-windows) below
is the gate. The port is complete in code, build tooling and docs once that checklist has been run on a Windows PC.

### W0 — Decisions and doc changes ✅ (2026-11-07)

- ✅ Lift the target restriction (D-25: amends A-01 and D-18); Project Overview and `CLAUDE.md` list Windows.
- ✅ Key storage: Credential Manager (D-26). Data folder: `%LOCALAPPDATA%\Pool Print` (D-27; not the roaming `%APPDATA%`).
- ✅ Single instance and file hand-over (D-28, protocol in [security.md](security.md#single-instance-and-file-hand-over-windows)).
- ✅ Unsigned (D-29), Msi per-user (D-30), builds from a manual `windows-latest` workflow (D-31).
- `jna-platform` pre-registered in the dependency register; [security.md](security.md) has the Windows key-storage and
  single-instance sections. No code changed.

### W1 — Platform code 🔶 (2026-11-07)

- ✅ `DesktopOs` (`jvmMain/desktop`, `detect(osName)` / `current()`, `UnsupportedOsException`): macOS or Windows from
  `os.name`; anything else makes `main()` print the message and exit with status 1. Tested with injected `os.name` values.
- ✅ `WindowsCredentialPrivateKeyStore` (`jvmMain/keys`) implementing `PrivateKeyStore`: `load` returns `null` when nothing
  is stored, `save` overwrites, `delete` is idempotent, buffers are zeroed, the secret never touches a command line or log.
  **`jna-platform` 5.19.1 has no Credential Manager bindings**, so `CredReadW`/`CredWriteW`/`CredDeleteW`/`CredFree` and
  `CREDENTIALW` are declared in the app (D-26 note); `jna-platform` supplies `FILETIME`, `WinError` and the error text. The
  store logic (null when absent, overwrite, idempotent delete, 2560-byte limit) runs against an in-memory backend on every
  OS; the structure layout is checked on macOS (80 bytes); the real calls are untested until Windows.
- 🔶 Round-trip test against the real store, off by default like the Keychain one
  (`POOLPRINT_WINCRED_TEST=1 ./gradlew :shared:jvmTest --tests '*WindowsCredential*'`), skipped on non-Windows.
- ✅ `DesktopAppDependencies`: `desktopDataDir` / `desktopKeyStore` / `desktopHostName` take the OS (and home, env) as
  arguments and are unit-tested on macOS; host-name fallback `pc` instead of `mac` (still filtered by `safeComment`).
- 🔶 Menu shortcuts through `menuShortcut(os, key)` (⌘ on macOS, Ctrl on Windows, unit-tested). Whether the in-window menu
  bar fits at the minimum window size (420 × 560) is not checked.
- ✅ Strings: no user-facing string in `commonMain` mentions Mac, Finder, the Keychain or ⌘, so only the shortcuts needed
  to become OS-aware; [ux.md](ux.md) and [architecture.md](architecture.md) now name both OSes.

**Acceptance:** `:shared:jvmTest` passes on macOS **and** on Windows (the MINA SSHD transport tests run unchanged; on Windows the test server runs commands through Git for Windows' `sh.exe`, as the real server uses a POSIX shell); the
gated store test passes on Windows.

### W2 — File hand-over 🔶 (2026-11-07)

- 🔶 File picker: `pickerFilePattern(os)` gives `"*.pdf"` on Windows only (macOS would show it as a literal name); `pickedPdf`
  re-checks the result with `isPdfFile`. A non-PDF chosen anyway (typing `*.*` in the Windows dialog) is ignored silently:
  `rememberPdfPicker` has no rejection channel (it is a `commonMain` `expect` shared with Android).
- ✅ `startDesktop(os, args, incoming)` (`jvmMain/desktop/DesktopStartup.kt`) runs first in `main(args)`: command-line files go
  to `IncomingPdfs.offer` on every OS; on Windows it takes the `instance.lock` lock (`SingleInstance`, `FileChannel.tryLock`)
  and starts the loopback listener, or, as a second instance, sends its first PDF and returns `StartupResult.Exit`, so `main`
  exits before `application { … }` (no DataStore, key store or network). `main.kt` also raises the window when a document arrives.
- 🔶 Protocol (`HandOver.kt`): `<token> <absolute path>\n`, 256-bit hex token, 8 KiB cap, 2 s per connection, one at a time,
  constant-time compare, no replies, no logging. Endpoint file `instance.handover` (port + token), owner-only on POSIX,
  inherited user-only ACL on Windows. Details: [security.md](security.md#single-instance-and-file-hand-over-windows).
  **macOS does not use it** (LaunchServices already gives one process per app; D-28).
- 🔶 Drag and drop from Explorer: no change (`javaFileListFlavor`, like Finder).
- Tests: `HandOverTest` (27: protocol, listener, endpoint file, lock, two-instance hand-over) and `DesktopStartupTest` (9:
  arguments, Windows hand-over, picker helpers). The lock was also checked across two real JVM processes on macOS. No new library.
- Not verified without Windows: the lock between two real processes there, Explorer's Open With (cold and warm), the native
  dialog filter, and `window.toFront()` against Windows' foreground rules (it may only flash the taskbar button).

**Acceptance:** unit tests for argument handling and the hand-over protocol; drop and picker logic unchanged and still tested.

### W3 — Packaging (Msi) 🔶 (2026-11-07)

- ✅ `desktopApp/build.gradle.kts`: `targetFormats(Dmg, Msi)` and a `windows { … }` block: `PoolPrint.ico`, `menuGroup`,
  `perUserInstall = true`, `shortcut = true`, `upgradeUuid` = `fdbbb4df-e57b-487c-af3b-a52341470e7f` (**never change it**, D-30),
  `packageVersion`. On macOS the Msi tasks (`packageMsi`, `packageReleaseMsi`) are configured but skipped; `packageDmg` still builds.
- ✅ `windowsPackageVersion` in `PoolPrintVersion` (`buildSrc`): `MAJOR.MINOR.PATCH` without `-rc.N`; the build fails with
  the property name when MAJOR/MINOR > 255 or PATCH > 65535 (5 new tests; buildSrc has 11). **Consequence** (release.md, D-30):
  `1.0.0-rc.1`, `-rc.2` and `1.0.0` are the same Msi version, so they do not upgrade each other; uninstall first when going from
  an RC to the final release. Only a version bump upgrades in place.
- ✅ **PDF file association dropped.** The Compose DSL's `fileAssociation(...)` registers the extension under jpackage's own ProgId
  with no "Alternate rank" like macOS's `LSHandlerRank`, which could make Pool Print the default PDF app. The ways in on Windows are
  Explorer **Open with → Choose another app → Pool Print.exe**, drag and drop, and Ctrl+O (D-30). Unverified (no Windows JDK here), so
  the conservative choice was made.
- ✅ Icon: `desktopApp/icons/make_icon.py` also writes `PoolPrint.ico` (16–256 px, a Windows variant that fills the canvas); the
  macOS `.icns`, `PoolPrint.png` and the Android layers regenerate byte-identical.
- 🔶 jlink modules: unchanged (`java.instrument`, `java.naming`, `java.security.jgss`, `java.sql`, `jdk.unsupported`); reasoned,
  not run. The `suggestRuntimeModules` output of the Windows workflow settles it.
- ✅ ProGuard stays off (same reasons as macOS).
- Not verified without Windows: everything about the Msi (building it, per-user install without admin, shortcuts, icon,
  upgrading, uninstalling, what jpackage does on an equal version, the install folder).

**Acceptance:** `packageReleaseMsi` builds on Windows; installing, starting, upgrading over an older Msi and uninstalling all
work without admin rights.

### W4 — Build workflow, release tooling, release gate 🔶 (2026-11-07)

- ✅ `.github/workflows/windows.yml` (`workflow_dispatch` only, D-31): `windows-latest`, same JDK (Zulu 21) and the same
  SHA-pinned actions as `ci.yml`/`release.yml`, no secrets, read-only token. It validates the Gradle wrapper, runs
  `:shared:jvmTest` (the MINA SSHD tests unchanged), builds `:desktopApp:packageReleaseMsi`, checks the Msi (exactly one,
  ≥ 10 MB, OLE/CFB header), writes commit, size and SHA-256 to the run summary, runs `suggestRuntimeModules` (output in the
  log, `continue-on-error`) and uploads the Msi as the `msi` artifact (14 days). It publishes nothing.
- ✅ `Makefile`: `msi` (Windows only; elsewhere it explains the workflow and `make release WINDOWS_MSI=…`), `dmg` / `install-pc`
  / `uninstall-pc` are macOS-only with a clear message before anything builds; `make release WINDOWS_MSI=<file>`; `make help`
  updated.
- ✅ `scripts/release.sh`: `build --windows-msi <file>` (and `check-msi <file>` on its own). Checks the file (`.msi` name, ≥ 10 MiB,
  OLE/CFB magic, version in the name equals `windowsPackageVersion`) before the long build, copies it to
  `build/release/<version>/PoolPrint-<version>-windows-x64.msi`, checks the copy, adds it to `SHA256SUMS.txt`. Without the flag
  the release is Android + macOS only and the script says so (start and end). `make release-draft` attaches the Msi when present
  and re-checks it.
- ✅ Follow-up (D-32): `windows.yml` also runs on pushes to `main` and on PRs; `release.yml` has a `windows` job (JVM tests on
  Windows, `packageReleaseMsi`) and `ci-package <apk> <dmg> <msi>` / `ci-draft` ship the Msi in the tagged draft.
- ✅ `scripts/release-notes.template.md`: `{{WIN}}` / `{{NOWIN}}` lines: Windows row, **Windows install** steps (SmartScreen
  *More info → Run anyway*, per-user install, Ctrl+O / drag / Open with → Choose another app, no file association, data in
  `%LOCALAPPDATA%\Pool Print`, key in Credential Manager, update and RC → final uninstall rule) and a Windows known limitation;
  the README → Install has a Windows paragraph.
- ✅ [release.md](release.md) (Windows section: owner/agent split for getting the Msi, what the script can and cannot check, the
  Windows gate), [architecture.md](architecture.md), `CLAUDE.md`, [development.md](development.md) updated.
- Checked on macOS only: `bash -n`, the Msi checks against dummy files (good file, wrong version, too small, zip, no `.msi`
  name, missing), staging and `SHA256SUMS.txt` with and without an Msi, the generated notes both ways, `draft` with a stub
  `gh` (asset list with and without the Msi, a corrupted Msi refused), argument parsing, `make help` / `make msi`, YAML
  parsing of the workflow. **Not run:** the workflow itself (agents never trigger workflows), WiX/jpackage, any real Msi.

**Acceptance:** the owner can run the Windows workflow, download the Msi, run `make release WINDOWS_MSI=…` and get a draft
release with the Msi, checksums and Windows install notes; the Windows gate passes.

### Windows manual checklist (owner, on Windows)

Build and release path (W4):
- [ ] Run the workflow: *Actions → Windows → Run workflow* on the release commit. It finishes green: the JVM tests pass on
      Windows, the Msi step finds one file, the artifact `msi` exists, the run summary shows the commit, size and SHA-256. Note
      how long it takes, whether the Compose plugin's WiX 3.x download works there, and any Android-SDK-related message
      (`:shared` configures the Android target; `windows-latest` normally has an SDK).
- [ ] The log of *List the suggested jlink modules* (`suggestRuntimeModules`): if it lists a module beyond the five in
      `desktopApp/build.gradle.kts`, add it (union with the macOS list). (W3)
- [ ] Download the artifact, unzip it, `make release WINDOWS_MSI="…/Pool Print-<version>.msi"` on the Mac: the Msi checks pass,
      `build/release/<version>/` has `PoolPrint-<version>-windows-x64.msi`, `SHA256SUMS.txt` lists it, `release-notes.md` has the
      Windows row and install steps (fill the Windows known limitation); `make release-draft` attaches it to the draft.
- [ ] The downloaded release asset's `Get-FileHash` equals its line in `SHA256SUMS.txt`.
- [ ] Optional: `make msi` works on the Windows PC (needs `make` and a Git Bash-like shell; plain `.\gradlew.bat
      :desktopApp:packageReleaseMsi` is the fallback).

Install, start, uninstall (W3):
- [ ] `desktopApp\build\compose\binaries\main-release\msi\Pool Print-<version>.msi` (or the artifact) exists and its version is the
      numeric one (Properties → Details).
- [ ] Fresh install from the Msi **without admin rights**: no UAC prompt, a Start-menu folder "Pool Print" and a desktop
      shortcut with the lime icon (check 16/32/48 px in Explorer's views), the app starts, Settings → About shows the full version.
- [ ] SmartScreen behaviour on first run is what the release notes describe (*More info → Run anyway*).
- [ ] The installed app shows a page count for a real PDF (PDFBox in the jlinked runtime) and the key store works from the
      packaged runtime (no `NoClassDefFoundError` / module error in a console run of `Pool Print.exe`).
- [ ] Settings → Default apps → PDF is unchanged after installing and after uninstalling.
- [ ] Install a newer Msi (higher `MAJOR.MINOR.PATCH`, e.g. 1.0.1 over 1.0.0) over the old one: it replaces it in place (one
      entry in Settings → Apps), settings, presets, pinned host key and the device key survive.
- [ ] Equal Msi version (an RC and its final release, or two rcs): note what happens (refused, repaired or a second
      install). Documented workaround: uninstall first (release.md).
- [ ] Uninstall (Settings → Apps, no admin prompt) removes the app and the shortcuts; expected: `%LOCALAPPDATA%\Pool Print`
      data files and the Credential Manager entry stay (D-30). Note what actually happens, especially if the install folder is
      the same `%LOCALAPPDATA%\Pool Print` (the data file must survive).
- [ ] After uninstalling, installing the same Msi again works and the app is still onboarded.

Onboarding and printing (release gate on Windows):
- [ ] Onboarding → key generation → "Add it for me with my pool password" → trust the server (check the fingerprint) → Test
      connection ✓. Restart: nothing asks for a password.
- [ ] Credential Manager shows the Pool Print entry; Settings → regenerate key replaces it; deleting it in Control Panel makes
      the app generate a new key (and asks to add it again) without crashing.
- [ ] Print a PDF via Ctrl+O; the dialog lists PDFs only (typing `*.*` shows all; choosing a non-PDF then does nothing). (W2)
- [ ] Drag a PDF from Explorer onto the window → print sheet; a non-PDF → "Only PDF files can be printed."
- [ ] Right-click a PDF → Open with → Choose another app → Pool Print.exe (there is no file association in the Msi, D-30), with
      the app **closed** (cold start: window opens with the print sheet for that PDF) and **open**, also minimised (no second window;
      the running window comes to the front with the print sheet; if Windows only flashes the taskbar button, note it; Task Manager
      shows one Pool Print process). The default PDF viewer is unchanged, and Pool Print is not in the "Open with" list before you
      chose it. (W2)
- [ ] Open a PDF from a path with spaces and non-ASCII characters (`C:\Users\…\Übung 3.pdf`) both ways. (W2)
- [ ] Ctrl+P prints from the print sheet and is disabled elsewhere.
- [ ] Page count of a real PDF is shown.

Code checks on Windows (W1, W2):
- [ ] `./gradlew :shared:jvmTest` passes on Windows (the workflow covers this once), and
      `$env:POOLPRINT_WINCRED_TEST=1; ./gradlew :shared:jvmTest --tests '*WindowsCredential*'` passes (real Credential Manager:
      write, read, overwrite, delete; the `CredentialW` structure and the three `Cred*W` calls are only exercised here).
- [ ] `./gradlew :shared:jvmTest --tests '*HandOver*' --tests '*DesktopStartup*'` passes on Windows (real loopback sockets and the
      real file lock; also part of the full run above).
- [ ] The app starts from `./gradlew :desktopApp:run` on Windows; data lands in `%LOCALAPPDATA%\Pool Print`; the key comment is
      `pool-print@<computer name>`.
- [ ] Layout at the minimum window size with the in-window menu bar; dark mode; display scaling at 150 %.
- [ ] End the running app, start it again: no stale-lock problem (the lock dies with the process); `instance.lock` and
      `instance.handover` sit in `%LOCALAPPDATA%\Pool Print`, and `instance.handover` is gone after a normal exit.
- [ ] Double-click the Start-menu shortcut while the app runs: nothing visible happens (the second copy exits quietly).
