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
| R1 — First sideloaded GitHub release (v1.0.0) | 🟨 |

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
      the in-app "Pick a PDF" picker.
- [ ] Android: rotate the phone on the print sheet; the document stays.
- [ ] macOS: first launch creates a Keychain item "Pool Print"; no prompt on later launches.
- [ ] macOS: "Pick a PDF" opens the native file dialog and prints the chosen file.
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
      settings." and printing is unaffected.
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

- [ ] Password key install: fresh install → key step → "Add it for me with my pool password" → trust the server
      (check the fingerprint) → type the password → Add key → Test connection ✓ with the key. `~/.ssh/authorized_keys`
      has one new line, `ls -ld ~/.ssh` is `drwx------`. Note whether the server used `password` or
      `keyboard-interactive` and whether it asked for anything besides the password.
- [ ] Password key install with a wrong password: "didn't accept the password", the field is empty, and nothing was
      added. After the app restarts, nothing asks for the password again.
- [ ] Add a device: on a second device (e.g. the phone) copy its key from onboarding, paste it in Settings → Add a
      device… on the Mac → "Added". On the phone, Test connection succeeds. Check that `~/.ssh/authorized_keys`
      on the server has one new line and that `ls -ld ~/.ssh` is `drwx------`.
- [ ] Drag a PDF from Finder onto the window → a "Drop a PDF to print it" hint shows while hovering and
      the print sheet opens on drop. Dragging a non-PDF shows "Only PDF files can be printed."
- [ ] Drop a PDF while a job is uploading: nothing changes. Drop one while Settings or Queue is open: the
      print sheet is waiting when you leave.
- [ ] ⌘O opens the file dialog; ⌘P prints from the print sheet and is greyed out elsewhere (home,
      Settings, while sending, with invalid page ranges).
- [ ] The window can't be shrunk below a usable size; the print sheet is readable at the minimum.
- [ ] Right-click a PDF in Finder → Open With → Pool Print, both while the app is closed and while it's running.
- [ ] Open With on a PDF does not take over double-click: Preview stays the default viewer.
- [ ] The app shows the lime printer-slot icon in the Dock and in the `.dmg` window, and is named "Pool Print".
- [ ] Android launcher shows the lime printer-slot icon under circle and squircle masks, and with themed icons on
      (Android 13+) the monochrome glyph is tinted like the other apps.
- [ ] In the installed app, print a PDF: the page count appears (PDFBox works inside the jlink runtime)
      and Test connection works (sshj does too).
- [ ] Install from the `.dmg` on a clean user account; first launch gets past Gatekeeper (expect the "unidentified developer" prompt).
- [ ] Keychain item shows up in Keychain Access under "Pool Print" and survives an app restart.
- [ ] The existing `~/.ssh` key option works, including with a passphrase-protected key (asked once,
      then never again).
- [ ] After installing an updated `.dmg`: note whether macOS asks for Keychain access, and whether
      "Always Allow" sticks.

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
- ⬜ Phase 2 — one version number (next).
