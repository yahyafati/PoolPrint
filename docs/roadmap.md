# Roadmap

Work goes **milestone by milestone**. At the end of each milestone, stop and report: a short summary,
the manual test checklist, and anything that's uncertain (see [development.md](development.md#milestone-reports)).

Status legend: ⬜ not started · 🟨 in progress · ✅ done (unit-tested) · 🔶 done, needs manual check against the real server

| Milestone | Status |
|---|---|
| M1 — MVP | 🔶 |
| M2 — Options & cost | 🔶 |
| M3 — Discovery, presets, queue | ⬜ |
| M4 — macOS polish | ⬜ |

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
- No password prompt anywhere; password auth is disabled in the SSH config.
- The app builds and launches on an Android emulator or device and on macOS. *(Launched on the API 29
  emulator through onboarding; started on macOS without errors.)*

### Manual test checklist (owner, against the real server)

- [ ] Fresh install → onboarding → copy the public key → add it to `~/.ssh/authorized_keys` → Test connection ✓.
- [ ] The host-key fingerprint shown matches `ssh-keygen -lF login.informatik.uni-freiburg.de` from a trusted machine.
- [ ] Test connection *before* adding the key gives the "Key not authorized" message.
- [ ] Share a PDF from another Android app (Files, browser, mail) → print to `tfppr1` → the paper comes out and matches the document.
- [ ] Print from the desktop file picker.
- [ ] Copies = 2 gives two copies.
- [ ] Wrong printer name (simulate if possible) gives a readable error with stderr.
- [ ] Airplane mode / no network gives "Can't reach the pool server".
- [ ] Off campus **with** VPN: Test connection and printing work.
- [ ] Off campus **without** VPN: you get the "Connect to the university VPN" message, not a cryptic error.
- [ ] Printing again after restarting the app or rebooting the device: no password or passphrase prompt.
- [ ] Large PDF (≥ 20 MB) uploads and prints; Cancel during upload stops the job.
- [ ] Nothing is left in the pool home directory afterwards (`ls -la ~` unchanged).
- [ ] Compare the fingerprint the app shows with `ssh-keygen -lF` (decisions.md, open question 5). The
      real server may offer other host-key or key-exchange algorithms than the test server; if Test
      connection fails with a protocol error, send the details text.
- [ ] Android: share a PDF **from another app** and check that Pool Print opens its print sheet; also try
      the in-app "Choose a PDF…" picker.
- [ ] Android: rotate the phone on the print sheet; the document stays.
- [ ] macOS: first launch creates a Keychain item "Pool Print"; no prompt on later launches.
- [ ] macOS: "Choose a PDF…" opens the native file dialog and prints the chosen file.
- [ ] Settings → Forget pinned key → confirm: the next connection asks to trust again.
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
- [ ] Reopen the app: the printer, duplex, pages per sheet, orientation, fit-to-page and paper are pre-filled.
- [ ] Duplex long edge vs short edge comes out correctly in portrait and landscape.
- [ ] 2-up and 4-up layouts look right.
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

### Tasks

- ⬜ `LpstatParser` and `LpoptionsParser`, with fixtures (real captures preferred).
- ⬜ Discovery service: fetch, cache per printer, manual refresh, silent fallback.
- ⬜ Generated controls for discovered options; validation against the cache; collision filtering.
- ⬜ Presets: create, rename, delete, apply; preset dropdown on the print sheet.
- ⬜ `LpqParser`; Queue screen; cancel own job through `lprm`, with confirmation.
- ⬜ Injection tests for discovered keys and values and for job ids.

### Acceptance criteria

- The app is fully usable with discovery failing (tested with `FakeSshClient` returning errors or garbage).
- A discovered value that isn't in the cache can't reach the command (tested).
- Only own jobs can be cancelled (tested).

### Manual test checklist

- [ ] `lpstat -p` and `lpoptions -p tfppr… -l` run on the login server. Paste the real outputs so they can become fixtures.
- [ ] The discovered options for each printer look sensible, and changing one (e.g. colour mode) has the expected effect.
- [ ] Queue shows your job while it's printing; Cancel removes it; other users' jobs can't be cancelled.
- [ ] A preset applied from the share sheet prints in ≤ 3 taps.

---

## M4 — macOS polish

### Tasks

- ⬜ Drag and drop a PDF onto the window.
- ⬜ "Open With → Pool Print" (`CFBundleDocumentTypes` and `Desktop.setOpenFileHandler`, including a cold start).
- ⬜ `.dmg` packaging: app name, icon, bundle id, version; drop the Msi/Deb targets unless wanted.
- ⬜ Desktop layout tweaks (window size, keyboard shortcuts: ⌘O open, ⌘P print).

### Manual test checklist

- [ ] Drag a PDF from Finder onto the window → the print sheet opens.
- [ ] Right-click a PDF in Finder → Open With → Pool Print, both while the app is closed and while it's running.
- [ ] Install from the `.dmg` on a clean user account; first launch gets past Gatekeeper (expect the "unidentified developer" prompt).
- [ ] Keychain item shows up in Keychain Access under "Pool Print" and survives an app restart.
- [ ] The existing `~/.ssh` key option works, including with a passphrase-protected key (asked once,
      then never again).
- [ ] After installing an updated `.dmg`: note whether macOS asks for Keychain access, and whether
      "Always Allow" sticks.
