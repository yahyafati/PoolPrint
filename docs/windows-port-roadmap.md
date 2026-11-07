# Windows port — temporary roadmap

> **Temporary planning file.** Once the decisions in W0 are made, fold this into [roadmap.md](roadmap.md) as a
> milestone (e.g. `W1 — Windows desktop`), record the decisions in [decisions.md](decisions.md), and delete this file.

Same working rules as every milestone ([development.md](development.md)): one phase at a time, stop and report,
register every new library, keep the docs in sync. Linux, Windows on ARM, the Microsoft Store and auto-update are
out of scope.

Status legend: ⬜ not started · 🟨 in progress · ✅ done (unit-tested) · 🔶 done, needs manual check on Windows

| Phase | Status |
|---|---|
| W0 — Decisions and doc changes | ⬜ |
| W1 — Platform code (key store, data folder, OS switch) | ⬜ |
| W2 — File hand-over (picker, drop, Open With, single instance) | ⬜ |
| W3 — Packaging (Msi) | ⬜ |
| W4 — Build machine, release tooling, release gate | ⬜ |

**Estimate:** 2–4 days of work. The code is small; building and testing on a real Windows machine is what takes
the time.

## Starting point

The desktop app runs on the JVM, so sshj, BouncyCastle, PDFBox, DataStore and the Compose UI need no changes.
The parts tied to macOS:

| Where | What is macOS-only |
|---|---|
| `jvmMain/.../keys/MacKeychainPrivateKeyStore.kt` | Key storage via the Security framework (JNA). |
| `jvmMain/.../app/DesktopAppDependencies.kt` | `~/Library/Application Support/Pool Print`; always picks the Keychain store; hostname fallback `"mac"`. |
| `jvmMain/.../desktop/IncomingPdfs.kt` | "Open With" through `Desktop.setOpenFileHandler`, which Windows doesn't support (it already returns `false` there). |
| `jvmMain/.../desktop/PoolPrintMenuBar.kt` | `KeyShortcut(..., meta = true)`: ⌘O / ⌘P. |
| `jvmMain/.../pdf/PdfPicker.jvm.kt` | `FileDialog.setFilenameFilter` — **ignored on Windows**, so the dialog shows every file. |
| `desktopApp/build.gradle.kts` | `TargetFormat.Dmg` only (D-18), `macOS { … }` block, `.icns` icon. |
| `Makefile`, `scripts/release.sh` | `/Applications`, `hdiutil`, `codesign`, DMG checks. |

## W0 — Decisions and doc changes

Decide these first; each becomes a D-entry in [decisions.md](decisions.md).

- ⬜ **Lift the target restriction:** amend A-01 ("Android and macOS desktop") and D-18 ("Dmg only") to include
  Windows. Update [Project Overview.md](Project%20Overview.md) (targets) and `CLAUDE.md` (project summary, commands).
- ⬜ **Windows key storage.** Both options open without a prompt (A-11) and are bound to the Windows user:
  - **Credential Manager** (`CredWriteW`/`CredReadW`/`CredDeleteW`, `CRED_TYPE_GENERIC`,
    `CRED_PERSIST_LOCAL_MACHINE` so it doesn't roam). The closest analogue to the Keychain item; the user can see
    and delete it in Control Panel. Blob limit 2560 bytes; the OpenSSH Ed25519 key is ~400. **Recommended.**
  - **DPAPI** (`CryptProtectData`, current-user scope) with the ciphertext in a file under the data folder.
  Either way via `jna-platform` (same version as `jna`) → register it in the dependency register.
- ⬜ **Data folder:** `%LOCALAPPDATA%\Pool Print` (not `%APPDATA%`, which roams: settings and pinned host keys
  would follow the user to another PC while the non-roaming private key wouldn't).
- ⬜ **Single instance.** Explorer's "Open with" starts a *new process* for every file. Two processes on the same
  DataStore file can corrupt it, so a second instance must hand its file to the first and exit, never open the
  store. Proposal: an exclusive `FileChannel` lock on `%LOCALAPPDATA%\Pool Print\instance.lock`; the running
  instance listens on a loopback port with a random token, both written to a file only the user can read; the second
  instance sends the path and exits. Only a path is accepted, and it goes through `isPdfFile` and then the normal
  print sheet (the user still has to press Print). Document it in [security.md](security.md).
- ⬜ **Code signing.** Unsigned installers get SmartScreen's "Windows protected your PC → More info → Run anyway".
  Options: stay unsigned (fine for a personal app, documented in the release notes), Azure Trusted Signing
  (monthly fee), or an OV certificate. Recommendation: unsigned for now.
- ⬜ **Installer format:** Msi (upgrades in place via a fixed `upgradeUuid`) vs Exe. Recommendation: Msi,
  per-user install (no admin prompt).
- ⬜ **Where Windows builds come from:** jpackage can't build a Windows installer on macOS. Options: a Windows PC,
  a Windows VM, or a GitHub Actions `windows-latest` workflow (the owner triggers it; agents never push).

## W1 — Platform code

- ⬜ `DesktopOs` (or similar) in `jvmMain`: detects macOS / Windows from `os.name`; anything else fails clearly
  at startup ("unsupported OS") instead of half-working.
- ⬜ `WindowsCredentialPrivateKeyStore` (or the DPAPI variant) implementing `PrivateKeyStore`: `load` returns
  `null` when nothing is stored, `save` overwrites, `delete` is idempotent, buffers are zeroed after use, the secret
  never touches a command line or a log.
- ⬜ Round-trip test against the real store, off by default like the Keychain one
  (`POOLPRINT_WINCRED_TEST=1 ./gradlew :shared:jvmTest --tests '*WindowsCredential*'`), skipped on non-Windows.
- ⬜ `DesktopAppDependencies`: data folder and key store chosen per OS; hostname fallback `"pc"` instead of `"mac"`
  (it ends up in the key comment `pool-print@<host>`, still filtered by `safeComment`).
- ⬜ Menu shortcuts: ⌘ on macOS, Ctrl on Windows. The Compose menu bar renders inside the window on Windows;
  check the layout still fits at the minimum window size.
- ⬜ Strings/UI that say "Mac", "Finder", "Keychain" or "⌘": make them OS-aware (Settings, onboarding, error
  catalogue in [ux.md](ux.md)).

**Acceptance:** `:shared:jvmTest` passes on macOS **and** on Windows (the MINA SSHD transport tests run unchanged);
the gated store test passes on Windows.

## W2 — File hand-over

- ⬜ File picker: on Windows set `dialog.file = "*.pdf"` so the native dialog filters, and re-check the result
  with `isPdfFile` (the filter is only a hint). Fix the "native macOS open panel" comment.
- ⬜ `main(args)`: files passed on the command line go to `IncomingPdfs.offer(...)` before the window exists
  (what `setOpenFileHandler` does on macOS).
- ⬜ Single-instance lock and hand-over as decided in W0, with unit tests for the lock and the message parsing
  (wrong token, garbage, non-PDF path, path that doesn't exist).
- ⬜ Drag and drop from Explorer: `javaFileListFlavor` should already work; no code change expected.

**Acceptance:** unit tests for argument handling and the hand-over protocol; drop and picker logic unchanged and
still tested.

## W3 — Packaging

- ⬜ `desktopApp/build.gradle.kts`: add `TargetFormat.Msi` next to Dmg and a `windows { … }` block:
  `iconFile` (`PoolPrint.ico`), `menuGroup`, `perUserInstall = true`, `shortcut = true`, a fixed `upgradeUuid`
  (**generate once, never change** — it is what lets a new Msi replace the old one), `packageVersion`.
- ⬜ Version: Msi needs numeric `MAJOR.MINOR.BUILD` (MAJOR, MINOR ≤ 255; BUILD ≤ 65535). Add a
  `windowsPackageVersion` to `PoolPrintVersion` in `buildSrc` (like `macPackageVersion`), with `:buildSrc:test` cases
  (`1.0.0-rc.2` → `1.0.0`).
- ⬜ PDF file association (`fileAssociation(...)` in the Compose DSL — check the exact API in Compose 1.12).
  It must only add Pool Print to "Open with", **not** become the default PDF app. If jpackage's association claims
  the default, drop it and rely on "Open with → Choose another app" plus drag and drop.
- ⬜ Icon: extend `desktopApp/icons/make_icon.py` to also write `PoolPrint.ico` (16–256 px) from the same glyph.
- ⬜ jlink modules: run `suggestRuntimeModules` on Windows and keep the union with the macOS list.
- ⬜ ProGuard stays off (same reasons as macOS).

**Acceptance:** `packageReleaseMsi` builds on Windows; installing, starting, upgrading over an older Msi and
uninstalling all work without admin rights.

## W4 — Build machine, release tooling, release gate

- ⬜ Windows build path as decided in W0. If CI: a workflow on `windows-latest` that runs `:shared:jvmTest` and
  `:desktopApp:packageReleaseMsi` and uploads the Msi as an artifact (manual trigger only, no publishing).
- ⬜ `Makefile`: `msi` target; make `install-pc` / `uninstall-pc` OS-aware (or Mac-only with a clear message).
- ⬜ `scripts/release.sh`: accept a Windows Msi built elsewhere (`--windows-msi <path>`), copy it to
  `build/release/<version>/PoolPrint-<version>-windows-x64.msi`, include it in `SHA256SUMS.txt`. Check what can be
  checked from macOS (file exists, name, size); the rest is on the release gate.
- ⬜ `scripts/release-notes.template.md`: Windows install steps, including the SmartScreen warning if unsigned.
- ⬜ [release.md](release.md): Windows rows in the release gate; [architecture.md](architecture.md): desktop
  platform section and packaging.

## Manual test checklist (owner, on Windows)

- [ ] Fresh install from the Msi without admin rights; Start-menu shortcut; the app starts.
- [ ] SmartScreen behaviour on first run is what the release notes describe.
- [ ] Onboarding → key generation → "Add it for me with my pool password" → trust the server (check the
      fingerprint) → Test connection ✓. Restart: nothing asks for a password.
- [ ] Credential Manager shows the Pool Print entry; Settings → regenerate key replaces it; deleting it in Control
      Panel makes the app generate a new key (and asks to add it again) without crashing.
- [ ] Print a PDF via Ctrl+O; the dialog only shows PDFs (or rejects others clearly).
- [ ] Drag a PDF from Explorer onto the window → print sheet; a non-PDF → "Only PDF files can be printed."
- [ ] Right-click a PDF → Open with → Pool Print, with the app **closed** (cold start) and **open** (goes to the
      running window, no second window). The default PDF viewer is unchanged.
- [ ] Ctrl+P prints from the print sheet and is disabled elsewhere.
- [ ] Page count of a real PDF is shown (PDFBox in the packaged runtime).
- [ ] Install a newer Msi over the old one: settings, presets, pinned host key and the device key survive.
- [ ] Uninstall removes the app; decide and note whether `%LOCALAPPDATA%\Pool Print` and the credential stay.
- [ ] Layout at the minimum window size with the in-window menu bar; dark mode; display scaling at 150 %.
