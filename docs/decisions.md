# Decisions and open questions

A lightweight decision log. Each entry has a status: **Accepted** (decided), **Proposed** (a
recommendation waiting for the owner or a spike), or **Open** (needs information).

When a decision changes, update the entry and add a dated note. Don't delete history.

---

## Accepted (from the project brief)

| ID | Decision |
|---|---|
| A-01 | Kotlin Multiplatform + Compose Multiplatform; targets Android and macOS desktop (JVM). No web, no iOS for now. *Amended 2026-11-07 (owner):* Windows x64 desktop (JVM) is a third target; see D-25. |
| A-02 | No backend, cloud or telemetry. The device talks directly to `login.informatik.uni-freiburg.de` over SSH. |
| A-03 | SSH key auth only (Ed25519, generated on the device). The pool password is never stored. *Amended 2026-10-25 (owner):* onboarding may ask for it once, optionally, to install the device key; see D-20. Everything else stays key-only. |
| A-04 | The PDF is streamed into `lpr`'s stdin over an exec channel. No remote file copies, no filenames in commands. |
| A-05 | Host key trust-on-first-use with a pinned fingerprint; a change blocks the connection. |
| A-06 | Remote commands are built from typed, allow-listed values with per-token shell quoting. |
| A-07 | The shared core (`commonMain`) stays free of platform code. SSH lives in a `jvmCommonMain` source set shared by Android and desktop. |
| A-08 | Dynamic discovery is optional. The hardcoded printer table and standard options always work. |
| A-09 | Cost is estimated from the printer type: 5 ct b/w, 10 ct colour. |
| A-10 | Libraries may be added as needed (owner, 2026-10-25). Every runtime or test dependency is listed in the [dependency register](#dependency-register) with its purpose. Prefer well-maintained libraries; anything touching crypto or credentials gets extra scrutiny. |
| A-11 | **No secret prompts in regular use** (owner, 2026-10-25). After onboarding, printing must not ask for a password, key passphrase or unlock. Secrets are stored securely but unlocked implicitly (device unlock / macOS login keychain). |
| A-12 | Off campus, `login.informatik.uni-freiburg.de` is reachable **only through the university VPN** (owner, 2026-10-25). The app can't fix this and must say so clearly when the connection fails. |

---

## D-01 SSH library

**Status:** Accepted (2026-10-25): **sshj**. The spike ran on desktop JVM and the API 29 and API 35 emulators. The one thing still unverified is the real pool server's algorithms (M1 manual checklist).

Candidates:
- **`com.github.mwiede:jsch`:** an actively maintained JSch fork with a small dependency footprint.
  Ed25519 needs either Java 15+ EdDSA (not available on Android) or BouncyCastle.
- **`com.hierynomus:sshj`:** a cleaner API with stream-friendly exec. It depends on BouncyCastle and an
  EdDSA library, and Android's built-in, stripped "BC" provider is a known source of conflicts.

Spike criteria: generates and loads Ed25519 keys on Android API 29 and newer; exec with stdin
streaming works; host-key callback can be customised; password and keyboard-interactive auth can be
disabled; reasonable APK size; no reflection problems under R8.

### Spike results

Versions: sshj 0.41.2, JSch fork 2.28.8, BouncyCastle 1.86, test server Apache MINA SSHD 2.20.0.
Both libraries were run through the same scenarios against a local MINA SSHD (Ed25519 host key):
`echo ok`, non-zero exit with stderr, a 20 MB random stdin whose SHA-256 is checked on the server side,
host-key rejection before authentication, and rejected-key-without-fallback.

| Criterion | sshj | JSch fork |
|---|---|---|
| Desktop JVM, all 6 scenarios (`SshSpikeJvmTest`) | ✅ | ✅ |
| Android API 29 and API 35 emulators, exec / stderr+exit / 20 MB stdin / host-key reject | ✅ (`DefaultConfig` and `AndroidConfig` both work) | ✅ |
| Same, minified with R8 | ✅ with keep rules | ✅ with keep rules |
| Ed25519 key generation | ✅ BouncyCastle lightweight API, no JCA provider (shared helper) | ✅ same key file loads |
| Only `publickey` is ever tried | ✅ `authPublickey`; server saw no password or keyboard-interactive attempt, even after a rejection | ✅ `PreferredAuthentications=publickey`; same result |
| Host-key callback | `HostKeyVerifier.verify(host, port, PublicKey)`; fingerprint computed from the key blob matches the server's `SHA256:…` | `HostKeyRepository.check(host, keyBytes)`; same |
| Needs extra setup on Android | Replace the platform "BC" provider with the bundled one (process-wide) | Static `JSch.setConfig` routing Ed25519, XDH and ChaCha20 to the `com.jcraft.jsch.bc.*` classes (process-wide) |
| Required R8 rules | `-keep class org.bouncycastle.** { *; }` | the same plus `-keep class com.jcraft.jsch.** { *; }` |
| Jar size | 0.6 MB (+ BouncyCastle) | 0.7 MB (+ BouncyCastle) |

Findings that matter for the real implementation:

1. **Both need the bundled BouncyCastle on Android.** Android's own BC is repackaged as
   `com.android.org.bouncycastle` (v1.68 on API 35, v1.61 on API 29; no JCA `Ed25519` generator on API 29) and has no Ed25519, so it doesn't clash with the
   bundled `org.bouncycastle`: classes are loaded from the APK. The size cost is dominated by BC
   (`bcprov` 7.2 MB, `bcpkix` 1.3 MB, `bcutil` 0.8 MB as jars), not by the SSH library. The blanket
   `-keep` rule is the safe starting point; narrowing it is a later optimisation.
2. **Pin the BC artifacts to one version.** sshj pulls BC 1.84 transitively. With only `bcprov` raised
   to 1.86, the Android build fails on duplicate classes (`IANAObjectIdentifiers` in `bcprov` and
   `bcutil`). Declaring `bcprov` and `bcpkix` at the same version fixes it.
3. **Android packaging:** exclude `META-INF/LICENSE.md` (three BC jars ship it), and add the `INTERNET`
   permission to the manifest.
4. **R8 breaks both libraries without keep rules** (BC's provider tables and JSch's `Class.forName`
   algorithm names are name-based). The failures were runtime errors, not build errors, so a release
   build has to be tested on a device. Keep an R8-minified smoke test on the checklist.
5. **Error classification:** neither library raises a typed exception for "host key rejected". The spike
   matched message strings, which is fragile. The real client should record the verdict in the
   verifier/repository callback (a flag) and map the exception from that. Same for `AuthRejected`.
6. **Key generation** uses `Ed25519KeyPairGenerator` plus `OpenSSHPrivateKeyUtil` /
   `OpenSSHPublicKeyUtil` from `bcprov`: no JCA provider is involved, and the OpenSSH-format output
   loads in both libraries. The comment can't be embedded in the private key by these helpers; it
   only goes on the public line.
7. **Streaming:** a 20 MB upload took about 1 s on the API 29 emulator and 3.5 to 4 s on API 35 with both libraries (emulator timings, not representative). Closing
   the exec channel's output stream sends EOF in sshj; JSch sends it when the input stream ends and
   exposes completion only through `isClosed` polling.
8. Clients may probe a public key before signing, so a server sees two `publickey` attempts per
   connection. Tests must not assert "exactly one".

Why sshj: the same result with less glue (no static config, explicit `authPublickey`, a callback that
receives a `PublicKey`, streams and `join(timeout)` on the command), and it is actively maintained.
Its extra dependency is `slf4j-api`, which prints nothing without a binding. JSch's advantages are
only its smaller dependency list (BC is needed either way on Android) and a familiar API.

**Not verified:**
- **The real pool server.** Nobody here can reach it. Its key-exchange and host-key algorithms are
  unknown; a server that only offers legacy algorithms would need `Config` changes. It stays on the
  M1 manual checklist ("Test connection ✓").
- A physical device. Also only the arm64 `google_apis` images were tried (API 29: `Pixel_6_API_29`).

The throwaway spike code, the JSch dependency and the `r8spike` build type were removed after the
decision. Kept: `TestSshServer` (jvmTest) and the BouncyCastle key generator and R8 keep rule. The real
client is `SshjClient` (jvmCommonMain); its tests (`SshjClientTest`) run against `TestSshServer` and
cover host-key unknown/changed, auth rejection without fallback, 20 MB streaming, timeout and cancel.
For manual runs on the emulator, `./gradlew :shared:runTestSshServer` starts the same server on port
2222 (reach it at `10.0.2.2`); the app's server address is fixed, so this needs a temporary code change.

Implementation notes (M1):
- Cancel and timeout: sshj's blocking I/O ignores coroutine cancellation, and a writer blocked on a
  window the server never opens can't even be disconnected cleanly. `SshjClient` runs the upload on a
  dedicated, interruptible thread and a watcher coroutine closes the socket on cancel or deadline.
- sshj's `loadKeys(String, ...)` copies the private key into a `String`, which can't be zeroed. The
  byte array is zeroed; the String copy lives until garbage collection.

## D-02 PDF page counting

**Status:** Accepted and implemented (2026-10-25).
- Android: `android.graphics.pdf.PdfRenderer` (platform API, no dependency). It needs a seekable file
  descriptor: a content URI usually provides one; otherwise the PDF is copied to the cache directory and
  the copy is deleted right after counting.
- Desktop: Apache PDFBox 3.0.8 (`Loader.loadPDF`, a picked file is read in place).
- Both sit behind `PdfPageCounter` in `commonMain`. A file that can't be read (corrupt, encrypted, not a
  PDF) gives `null`: the button says "Print · ? pages" and printing stays possible.
- Not verified: PDFBox inside the packaged `.dmg` (M4 added the runtime modules and the packaged app starts,
  but a page count in it has not been seen; D-18), and `PdfRenderer` on a real device.

## D-03 Persistence for settings, presets and caches

**Status:** Accepted (2026-10-25).
- **AndroidX DataStore** (multiplatform, `datastore-preferences-core` or a typed `DataStore<T>`) for
  non-secret state: username, last-used options, presets, discovery cache, pinned host keys.
- **`kotlinx-serialization-json`** for structured values such as presets, the discovery cache and
  host-key pins.
- Files go in app-private storage: Android `filesDir`, macOS `~/Library/Application Support/Pool Print/`, Windows
  `%LOCALAPPDATA%\Pool Print` (D-27).
- Secrets (the private key, an optional `~/.ssh` key passphrase) **never** go in DataStore. They go in
  the platform secure store (see D-04 and [security.md](security.md#key-handling)).

## D-04 macOS Keychain access

**Status:** Accepted (2026-10-25).
**JNA** bindings to the macOS Security framework (`SecItemAdd`, `SecItemCopyMatching`,
`SecItemUpdate`, `SecItemDelete`) behind a small `MacKeychain` class in `jvmMain`.

Rejected alternatives: the `security` CLI (passing the secret safely through it is awkward, and
`argv` would leak it) and `java-keyring` (a thin wrapper around the same thing with less control
over item attributes).

Item attributes: generic password, service `Pool Print`, account
`ssh-ed25519-private-key@login.informatik.uni-freiburg.de` (the key belongs to the device, not to a
username, so it doesn't change when the username does), not synchronisable (no iCloud Keychain).

**Prompts (A-11):** the login keychain is unlocked when the user logs in, and an item created by the
app can be read by the same app without a prompt. Known caveat: the item's access list is tied to the
app's code signature. With unsigned or ad-hoc-signed builds, macOS may ask **once after each app
update** to allow access ("Always Allow" fixes it). Signing the `.dmg` consistently (M4) removes this.
Note it in the README.

**Implemented and checked (2026-10-25):** `MacKeychainPrivateKeyStore` (jvmMain). Its round-trip test
(save, load, overwrite, delete) runs only with `POOLPRINT_KEYCHAIN_TEST=1` because it touches the real
login keychain (service `Pool Print (test)`, deleted afterwards). It passed once on the owner's Mac
without any prompt when run from Gradle's JVM. Whether the packaged app prompts after an update is
still on the M4 checklist.

## D-05 Kotlin / Compose / Gradle versions

**Status:** Accepted and done (2026-10-25). Checked against the latest stable releases:
Kotlin 2.4.20 → **2.4.21**, Compose Multiplatform 1.12.1 → **1.12.2**, Gradle 9.6.0 → **9.8.2**
(the wrapper's `distributionSha256Sum` was updated from the checksum published at services.gradle.org),
AGP 9.4.1 (already the latest). Coroutines 1.11.0 is the latest. `org.jetbrains.compose.material3` stays at
**1.12.0-alpha03**: the newest stable release (1.9.0) is older than Compose 1.12 and wasn't tried
against it; revisit when a 1.12+ stable ships.

## D-06 Billing unit for duplex

**Status:** Open. Needs a manual test (M2 checklist).
Does the pool charge per printed side or per sheet? The estimator defaults to **per side**
(`BILLING_UNIT = PER_SIDE`), so duplex doesn't lower the cost. Flip the constant once this is verified.
Related: is a colour printer charged 10 ct for grayscale output?

## D-07 Page-range semantics with number-up

**Status:** Open. Needs a manual test (M2 checklist).
The CUPS docs say `page-ranges` selects **output** pages (after `number-up`). The estimator defaults to
`OUTPUT_PAGES`. Verify on the pool printers and update the default and the UI hint.

## D-08 Product name vs repository name

**Status:** Accepted and done (2026-10-25). Everything is renamed:
- Gradle `rootProject.name = "PoolPrint"`, so the generated resources package is
  `poolprint.shared.generated.resources`.
- Package and Android namespace / `applicationId`: `com.yahyafati.poolprint` (`…poolprint.shared` for
  the library).
- Android `app_name` and the desktop window title: "Pool Print". Desktop `packageName = "PoolPrint"`
  (no space, for installer compatibility); macOS `bundleID = com.yahyafati.poolprint`,
  `dockName = "Pool Print"`.
- Still to do by the owner: rename the checkout folder (`~/StudioProjects/EasyPrinter`) and any
  remote repository.

## D-09 Persistent SSH connection

**Status:** Proposed. Use one connection per operation in M1 and revisit if latency bothers users.

## D-10 Transport errors are exceptions

**Status:** Accepted (2026-10-25). `SshClient.exec` returns `SshResult` for anything the remote command
did (including a non-zero exit) and throws `SshException(SshError)` for transport failures. The
services (`PoolServices`) catch it once and return typed results (`TestConnectionResult`,
`PrintResult`) with a `UserFailure` that maps to the error catalogue. `SshError.HostKeyUnknown` also
carries the key type.

## D-11 Android storage and backup

**Status:** Accepted (2026-10-25). The encrypted key and the DataStore file live in `noBackupFilesDir`,
and the manifest sets `allowBackup="false"`. After a restore the app starts clean instead of holding a
key blob whose Keystore key is gone. If the Keystore key is lost anyway, loading returns null and
`KeyManager.ensureKey` generates a new key.

## D-12 Scope cuts in M1

**Status:** Accepted (2026-10-25).
- macOS "use an existing `~/.ssh` key" is not implemented in M1 (the roadmap says "Keychain or an
  `~/.ssh` path"); it stays with the M4 checklist.
- Only `content://` URIs are accepted from the Android share intent, never `file://`, so another app
  can't make Pool Print read its private files.
- No page counting or cost in M1 (done in M2): the Print button just said "Print".
- Android handles configuration changes itself (`configChanges` in the manifest), so the picked
  document survives rotation; process death drops it.

---

## D-13 What "last used" remembers

**Status:** Accepted (2026-10-25), please confirm.
M2 remembers the printer, duplex, pages per sheet, orientation, fit-to-page and paper size, saved when
Print is tapped. **Copies and page ranges are not remembered**: they belong to one job, and a leftover
"5 copies" or "pages 3-9" on the next document would waste budget quietly. Presets (M3) do not store
page ranges either (ux.md). Saved values are re-validated on load; anything unknown falls back to the default.

## D-14 Cost-estimator defaults

**Status:** Accepted (2026-10-25), pending D-06 and D-07.
`CostPolicy()` defaults to `PER_SIDE` and `OUTPUT_PAGES` (the documented defaults) and is passed into
`PrintModel`; flipping a default is a one-line change plus the tests that name it. The page-range
validator uses the same policy to find the highest valid page (with output semantics and 2-up, an
8-page document accepts ranges up to 4). `WARNING_THRESHOLD_CENTS` is 200 (warning above 2 €, not at exactly 2 €).

## D-15 What a preset stores

**Status:** Accepted (2026-10-25), please confirm.
A preset stores the printer, duplex, pages per sheet, orientation, fit-to-page, paper size and the
**discovered** options changed from their defaults. It does **not** store copies or page ranges: a
preset applied to the next document must not silently print five copies (the same reasoning as D-13).
ux.md said "all options but no page ranges"; this narrows it. The preset name is local display text
(1 to 40 characters, no control characters, unique ignoring case, at most 30 presets) and never reaches a
command. When a preset is applied, its discovered choices are re-validated against the **cached** options
of its printer; a choice that is no longer in the cache is dropped. The print sheet shows a preset as
selected only while the current options equal it exactly. "Last used" (D-13) does not remember discovered
options either; use a preset for those.

## D-16 Discovery: when it runs, what is cached

**Status:** Accepted (2026-10-25).
- Opening a model reads **only the cache**; the print screen calls `refreshDiscovery()` when it opens and
  when the printer changes. Nothing else in the app starts a background fetch.
- Cache entries older than 24 hours (or missing) are refreshed then; Settings → "Refresh printers and options"
  forces it. Failure of any kind (unreachable, auth, non-zero exit, empty or localised output, an unknown host
  key) is silent: the old cache or the hardcoded printers stay, and the sheet shows "Using default printer
  settings." Discovery never pins a host key; trust is only asked in the print and queue flows.
- Limits: at most 100 printers, 60 options per printer, 40 values per option. Data read back from storage is
  run through the parser's rules again (`DiscoverySnapshot.sanitized`).
- Discovered printers that aren't in the hardcoded table get `PrinterType.UNKNOWN` and are billed as
  colour (10 ct) by the estimator.
- Options with a single value get no control. Option labels are display text only.
- `PoolServices.run(RemoteCommand)` is the one entry point for non-`lpr` commands; a `RemoteCommand` can only
  come from a `core.command` builder.

## D-17 Queue and cancelling

**Status:** Accepted (2026-10-25).
`QueueService` keeps the latest `lpq` result per printer in memory. `lprm` is only built for an `OwnedJobId`:
digits only, present in that latest result, and owned by the pool username (string equality with the `lpq`
owner column). The Cancel button only appears for own rows, asks for confirmation, and the queue is refreshed
afterwards. Pull-to-refresh was not built; there is a Refresh button on both platforms. The `lpq` parser
requires the `active`/`1st`/`2nd` rank column, an owner, a numeric job id and a trailing `N bytes`; other rows
are skipped, and text with no header, status or rows is treated as unexpected output.

## D-18 macOS packaging and file hand-over

**Status:** Accepted (2026-10-25).
- **Dmg only.** The Msi and Deb targets are gone from `desktopApp/build.gradle.kts`: nothing here can build or test
  them, and the app's key storage is macOS-only anyway. Re-add them if a Windows or Linux build is ever wanted.
  *Amended 2026-11-07:* the Windows port re-adds `Msi` next to `Dmg` (D-25, D-30); Deb stays out.
- **Name and version:** `packageName = "Pool Print"` (so the bundle is `Pool Print.app`), version `1.0.0`
  (macOS needs a major version of at least 1), bundle id unchanged. The icon is a printer slot and the sheet
  coming out of it, ink (`#141F00`) on lime (`#C8F244`), from the Claude Design sheet. `desktopApp/icons/make_icon.py`
  draws it from one glyph definition on the 108dp grid and writes `PoolPrint.icns` (824 px body on the 1024 canvas,
  with a shadow) and the Android adaptive icon layers (vector foreground, also used as the themed monochrome layer;
  the template PNG mipmaps are gone since minSdk 29 always uses the adaptive icon).
- **Runtime modules:** `java.instrument`, `java.naming`, `java.security.jgss`, `java.sql`, `jdk.unsupported`, as
  suggested by `suggestRuntimeModules`. The packaged app starts with them; PDFBox and sshj inside it are on the
  manual checklist.
- **Open With:** `CFBundleDocumentTypes` for `com.adobe.pdf` with `LSHandlerRank` = `Alternate` (listed in Open
  With, but Preview stays the default). I wrote the plist by hand instead of using `fileAssociation(...)` so the
  rank and content type are explicit.
- **One document at a time:** of several dropped or opened files the first PDF is used; the rest are ignored
  silently. A document that arrives while a job is uploading (or waiting for host-key trust) is ignored, so a
  running upload never loses its sheet. Anything else (home, Settings, an idle print sheet) is replaced by the
  new document, and any unsent options on the old sheet are lost.
- **Not a PDF check:** files are accepted by `.pdf` extension only; a broken one still prints as "? pages", as
  with the picker. Drag and drop of a file list is how Finder drags work; a drag from a browser (a URL) is not
  supported.
- **Signing:** the `.dmg` is ad-hoc signed by jpackage, not notarised. The Keychain prompt after updates (D-04)
  is therefore still possible; "Always Allow" fixes it. Not tested, since it needs two installed versions.
- Still not done: the `~/.ssh` key option (D-12). It is an M4 checklist line in the roadmap but was never a task;
  say if you want it built.

## D-19 One key per device; adding a device from an authorized one

**Status:** Accepted (2026-10-25).
- **One key per device** stays: the private key never leaves the device's secure store (Keychain / Keystore). A lost
  device is revoked by deleting its line from `authorized_keys`.
- **Add device** (Settings) is the shortcut for the manual `echo … >> authorized_keys` step: paste the new device's
  public key on a device that already works and the app appends it over its existing key login. Rejected
  alternatives: exporting/importing the private key (breaks non-exportable storage) and a helper using the pool
  password (breaks "no password auth ever"). *Note 2026-10-25:* the owner later allowed the password once, in
  onboarding, for the first-device step only (D-20). "Add a device" itself is unchanged and still key-only.
- **How:** a fixed command, `umask 077; mkdir -p ~/.ssh; chmod 700 ~/.ssh; cat >> ~/.ssh/authorized_keys;
  chmod 600 ~/.ssh/authorized_keys`, with the key on stdin as a validated `PublicKeyLine` (see security.md). It
  works in sh and csh login shells (checked locally).
- **Trade-off:** a device that is logged in can now add keys, not only print. Anyone with a working device could
  already run arbitrary commands over SSH by extracting its key, so this doesn't widen what a compromised
  device can do; it only makes it possible from inside the app.
- **Not deduplicated:** adding the same key twice appends a second identical line (harmless). The first device
  still needs the manual step (or a pool computer).
- **Not tested against the real server** (manual checklist in the roadmap).

## D-20 One-time password key install in onboarding

**Status:** Accepted (owner, 2026-10-25). Amends A-03; A-11 is unchanged.
- **What:** the key step of onboarding has an optional "Add it for me with my pool password". The app logs in once
  with the password and runs the D-19 `AuthorizeKeyCommand` with this device's public key on stdin, then tests the
  key login as usual. This replaces the manual `echo … >> authorized_keys` step for the first device.
- **Never stored:** the password is typed into a secure text field (no copy/cut), handed over as a `CharArray`,
  and the field is cleared at once. The model keeps a copy only while the user is asked to trust the server, so the
  install can resume after "Trust". Every copy is zeroed when the attempt ends, on Back and on Cancel. It is never
  written to settings, the Keychain/Keystore, logs or the remote command. Compose's text field holds the typed
  characters as a `String` until it is cleared, which can't be zeroed (same caveat as the PEM `String` in D-01).
- **Narrow API:** `SshClient.installKeyWithPassword(password, key)` is a separate method that can run nothing but
  `AuthorizeKeyCommand`; `exec` stays publickey-only, so printing can never fall back to the password.
- **Host key first:** the usual TOFU check runs before authentication, so the password is never sent to an
  unpinned or changed server key (tested: the test server sees no auth attempt at all).
- **Methods:** `password` first. Only if the server doesn't offer that method, `keyboard-interactive`, answering
  only a hidden prompt that looks like "Password:" (sshj `PasswordResponseProvider`). A rejected password is never
  sent a second time. A server that asks for a second factor (an OTP prompt) fails with "the server didn't accept
  the password", and the user falls back to the manual commands.
- **Rejected alternatives:** storing the password for regular use (makes the app hold the university account
  password, which unlocks much more than printing); a password option in Settings (the key step is the only place
  that needs it).
- **Not tested against the real server:** whether the pool server allows password or keyboard-interactive logins
  over SSH, and what its prompt looks like (manual checklist in the roadmap).

## D-21 Visual design: the Claude Design "Pool Print" phone screens

**Status:** Accepted (owner, 2026-10-25).
- **What:** the UI follows the "Pool Print Phone" screens of the owner's Claude Design project (Modernist design
  system): ink on a warm ground, one lime accent for the primary action, flat surfaces, zero corner radius, strong
  2dp section rules and 1dp row dividers, labels flush left (also inside buttons), Lucide icons. Light and dark
  follow the system. Material 3 stays as the component base for menus and the password field, recoloured and
  squared.
- **Where:** the tokens and type scale are in `ui/Theme.kt` (`PoolColors`, `PoolTypography`), the building blocks
  in `ui/Components.kt`, the icons in `ui/Icons.kt`. Screens use those, not raw colours or Material defaults.
  Desktop gets the same screens as a centred, phone-width column; the separate macOS design is not implemented yet.
- **Fonts:** the design uses Archivo and JetBrains Mono. They are not bundled yet (pending the owner's OK to add the
  font files, both SIL OFL); the app uses the platform sans and monospace meanwhile. Swapping is one change in
  `PoolPrintTheme`.
- **Where the app departs from the mock-ups,** because the data or feature doesn't exist: no printer location or
  room, no monthly-budget figure in the cost warning, no live "Connected" status (home says "Ready"), no "Auto"
  orientation, no "I'll do it later" in onboarding, no undo after deleting a preset (a confirmation instead), no
  "Edit" for the username and no default-printer setting (the last-used printer is remembered, D-13). The test
  connection screen shows one progress bar, not three sub-steps, since the test is a single call. The key snippet
  keeps its `chmod` lines, and the optional password install (D-20) stays as a link on the key step.
- **Behaviour that came with it:** a running connection test can be cancelled; the print sheet's model outlives
  Queue and Settings opened from it; the trust prompt and the changed-host-key warning are full screens (the latter
  still has no way to continue, see security.md).
- **No new dependencies.**

## D-22 Distribution and signing (release R1)

**Status:** Accepted (owner, 2026-11-07). Answers to [release.md](release.md) Phase 0.1, 0.2, 0.4–0.9.
- **Channel:** sideloaded **GitHub Releases** of `yahyafati/PoolPrint` (APK + `.dmg`, checksums, notes). No app
  store, no update server.
- **Visibility (0.1):** the repo goes **public**, but only after the owner has approved the Phase 3 secret and
  privacy scan of the whole history. Until then it stays private.
- **Licence (0.2):** **MIT**. Its "as is, without warranty" clause is the legal backing for the plain-language
  vibe-coded notice (README, release notes, Settings → About). All current runtime dependencies are compatible.
- **Android signing:** a dedicated release key (`~/keys/poolprint-release.jks`, PKCS12, alias `poolprint`), created
  and backed up by the owner, read from `local.properties` (Phase 1). Debug-key fallback for contributors only; the
  release script refuses debug-signed APKs.
- **macOS signing (0.4):** **ad-hoc** (jpackage) for v1, no Developer ID, no notarisation. Users click *Open Anyway*
  once; the Keychain prompt after updates (D-04) stays possible. Revisit if users complain.
- **Mac architectures (0.5):** **arm64 only**. No Intel build (jpackage can't cross-build; would need an Intel
  machine or runner).
- **Where releases are built (0.6):** on the **owner's Mac** with `make release`. The signing key never leaves it.
  GitHub Actions (Phase 9) stays optional; the workflows exist since D-24.
- **Release gate (0.7):** the **blocking list** in release.md Phase 6.2 must pass on the RC built from the release
  commit. Every other open real-server check ships as a "known limitation" in the notes.
- **Update notifications (0.8):** **none**. The app talks only to the pool server; users watch the repo's releases.
- **Pool admins (0.9):** undecided. A heads-up to the Fachschaft/pool admins stays an optional courtesy with no
  deadline; it doesn't block the release.

## D-23 Versioning (release R1)

**Status:** Accepted (owner, 2026-11-07). Answer to [release.md](release.md) Phase 0.3.
- **SemVer** `MAJOR.MINOR.PATCH`; the first public release is `1.0.0`.
- **Release candidates** are `1.0.0-rc.1`, `-rc.2`, … with tags `v1.0.0-rc.N`, published as GitHub pre-releases.
- **Android `versionCode`** is a plain counter, **+1 for every build that leaves the owner's machine** (RCs
  included), never reused. `1` is taken by the existing debug builds, so the first RC is `2`.
- **macOS `packageVersion`** is the `versionName` without the `-rc.N` suffix (macOS accepts only numbers, major
  ≥ 1, D-18). The full name is shown in Settings → About.
- **One source** for both apps: `poolprint.versionName` and `poolprint.versionCode` in `gradle.properties`
  (Phase 2), parsed and checked by `buildSrc/PoolPrintVersion.kt`; a malformed value fails the build.

## D-24 GitHub Actions (release R1, Phase 9)

**Status:** Accepted (owner asked for Phase 9, 2026-11-07). Extends D-22 (0.6) without replacing it.
- **CI:** `.github/workflows/ci.yml` runs every test suite (`make test-all`'s three) on `ubuntu-latest` for pushes
  to `main` and all pull requests, after Gradle wrapper validation. No secrets.
- **Release builds:** `.github/workflows/release.yml` builds a pushed `v*` tag: release-key APK on Linux, DMG on a
  macOS arm64 runner, then a **draft** release. Publishing stays manual. It reuses `scripts/release.sh`'s checks
  (`ci-*` commands), so CI and `make release` refuse the same things. The tag must equal
  `v<poolprint.versionName>` and be on `main`. A tag created by publishing a `make release-draft` draft is
  recognised and skipped.
- **Both ways stay:** `make release` on the owner's Mac remains the default (D-22). The workflow is used only once
  the owner has set up the `release` environment; one way per version.
- **Signing key in GitHub:** only as **environment secrets** of `release` (required owner approval, `v*` tags only),
  read by one job and one step; the keystore is decoded to the runner's temp dir and deleted after the build, the
  configuration cache is off for that build, and `release.yml` restores and saves no Gradle cache. Accepted
  trade-off: the key then also lives in GitHub.
- **Actions:** only `actions/*` and `gradle/actions/wrapper-validation`, each pinned to a full commit SHA (see the
  dependency register). Update them by SHA, never by moving tag.
- **Intel:** still none (D-22, 0.5); an Intel DMG would be a second macOS job on an Intel runner.

## D-25 Windows x64 desktop as a third target (Windows port)

**Status:** Accepted (owner, 2026-11-07). Amends A-01 and D-18; the work is tracked in
the "W — Windows desktop port" milestone in [roadmap.md](roadmap.md#w--windows-desktop-port) (phases W0–W4).
- **Targets** are now Android, macOS desktop (arm64) and **Windows x64 desktop**, all on the JVM. The shared core
  still stays free of platform code (A-07); everything Windows-specific lives in `jvmMain` (key store, data folder,
  OS detection) and in `desktopApp` (packaging).
- **Out of scope:** Linux, Windows on ARM, the Microsoft Store and auto-update. An unsupported OS fails clearly at
  startup (W1) instead of half-working.
- **No new hard-constraint exceptions:** key-only auth, no backend, no telemetry, no secret prompts in regular use
  (A-11) and the command rules apply on Windows unchanged.
- **D-18** ("Dmg only") is amended: `Msi` is added for Windows (D-30); Deb is still out.

## D-26 Windows key storage: Credential Manager

**Status:** Accepted (owner, 2026-11-07). The Windows counterpart of D-04.
- **What:** the private key lives in **Windows Credential Manager** as a generic credential, written, read and
  deleted with `CredWriteW` / `CredReadW` / `CredDeleteW` (`CRED_TYPE_GENERIC`) through JNA, behind a
  `PrivateKeyStore` implementation in `jvmMain` (W1). `CRED_PERSIST_LOCAL_MACHINE`, so the credential stays on this
  PC and does not roam with a domain profile. Target name `Pool Print`-based, mirroring the Keychain item (D-04):
  the key belongs to the device, not to a username.
- **Why:** it is the closest analogue of the Keychain item. It is bound to the Windows user, opens without a prompt
  (A-11), and the user can see and delete it under Control Panel → Credential Manager. The blob limit is 2560 bytes;
  the OpenSSH Ed25519 private key is about 400.
- **Rejected:** DPAPI (`CryptProtectData`, current-user scope) with the ciphertext in a file under the data folder.
  Equally prompt-free and user-bound, but it adds a file whose lifecycle the app has to manage, and the user has no
  place to see or remove it.
- **Same rules as the Keychain store:** `load` returns `null` when nothing is stored, `save` overwrites, `delete` is
  idempotent, buffers are zeroed after use, and the secret never touches a command line or a log. The round-trip
  test touches the real store, so it is off by default (`POOLPRINT_WINCRED_TEST=1`) and skipped on other OSes.
- **Dependency:** `jna-platform` (same version as `jna`) for the `Advapi32` bindings; registered below, added to the
  version catalog in W1.
- **W1 finding (2026-11-07):** `jna-platform` 5.19.1 has no Credential Manager bindings (its `Advapi32` has no
  `CredReadW`/`CredWriteW`/`CredDeleteW`, and there is no `Wincred` class). `WindowsCredentialPrivateKeyStore` therefore declares
  those three functions, `CredFree` and the `CREDENTIALW` structure itself (`jvmMain/keys`) and uses `jna-platform` only for
  `FILETIME`, `WinError.ERROR_NOT_FOUND` and `Kernel32Util.formatMessage`. The credential is keyed by target name `Pool Print`
  (what Control Panel lists); the account string `ssh-ed25519-private-key@…` is stored as its user name. A key over the
  2560-byte blob limit is refused with `CredentialTooLargeException`. The structure layout (80 bytes on x64) is unit-tested
  on macOS; the real calls are only exercised by the gated test on Windows.
- **Not verified here:** nothing in this environment runs Windows. Behaviour in the packaged app (credential
  survives an Msi upgrade, uninstall leaves it, deleting it in Control Panel makes the app generate a new key) is on
  the Windows manual checklist.

## D-27 Windows data folder: `%LOCALAPPDATA%\Pool Print`

**Status:** Accepted (owner, 2026-11-07). The Windows counterpart of the macOS folder in D-03.
- **Where:** `%LOCALAPPDATA%\Pool Print` (non-roaming). It holds the DataStore file (settings, presets, discovery
  cache, pinned host keys) and the single-instance files (D-28).
- **Why not `%APPDATA%`:** that folder roams with a domain or enterprise profile. The settings and the pinned host
  keys would follow the user to another PC, while the private key (D-26, `CRED_PERSIST_LOCAL_MACHINE`) would not:
  the second PC would have a username and a pin but no key. Keeping everything non-roaming means a PC either has the
  full set or none of it.
- **Uninstall:** whether the Msi removes this folder and the credential, or leaves them for a reinstall, is decided
  with the Msi (W3) and noted on the checklist.

## D-28 Windows single instance and file hand-over

**Status:** Accepted (owner, 2026-11-07). Protocol in [security.md](security.md#single-instance-and-file-hand-over-windows).
- **Problem:** Explorer's "Open with" starts a *new process* for every file, and Windows has no equivalent of the
  macOS open-file event. Two processes on one DataStore file can corrupt it, so a second instance must hand its file
  to the first and exit without ever opening the store.
- **Decision:** an exclusive `FileChannel` lock on `%LOCALAPPDATA%\Pool Print\instance.lock` decides who is the
  running instance. The running instance listens on a loopback-only port and writes the port plus a random token to a
  file only the user can read. A second instance reads it, sends the file path with the token, and exits. Only a
  path is accepted; it goes through `isPdfFile` and then the normal print sheet, so the user still presses Print.
- **Rejected:** a named pipe or a Windows message hook (more JNA, harder to test), and allowing several instances
  (store corruption).
- **Scope:** Windows only. macOS already has one process per app and delivers files through the open-file handler.
- **Implemented in W2 (2026-11-07):** `HandOver.kt` / `DesktopStartup.kt`, no new library. A second launch that carries no PDF exits
  quietly (the protocol has no "focus" message, by design); if the first instance can't be reached within 5 s the second exits
  with status 1 and a stderr message, still without opening the store.

## D-29 Windows code signing: unsigned for now

**Status:** Accepted (owner, 2026-11-07). The Windows counterpart of the macOS ad-hoc decision in D-22.
- The Msi is **not Authenticode-signed**. SmartScreen shows "Windows protected your PC"; the user clicks *More info*
  → *Run anyway*. The release notes say so, with the steps (W4: `scripts/release-notes.template.md`).
- **Why:** a personal, sideloaded app (D-22) doesn't justify a certificate. Azure Trusted Signing (monthly fee) and
  an OV certificate (yearly cost, and SmartScreen reputation still has to build up) were considered. Revisit if
  users complain.
- Because the Msi is unsigned, its integrity rests on `SHA256SUMS.txt` and the GitHub release, as for the DMG.

## D-30 Windows installer: Msi, per-user, fixed upgrade UUID

**Status:** Accepted (owner, 2026-11-07).
- **Format:** `TargetFormat.Msi` (jpackage), **per-user install**: no administrator prompt, installs under the user's
  profile. A fixed `upgradeUuid` (generated once, never changed) lets a newer Msi replace an older one in place.
- **Rejected:** an Exe installer (no in-place upgrade story as simple as Msi's) and a machine-wide install (needs
  admin, which a student's lab or shared PC may not grant).
- **Version:** Msi needs a numeric `MAJOR.MINOR.BUILD`; the `-rc.N` suffix is dropped as for macOS (D-23, W3; see the W3 details below).
- File association and the icon are W3 details; the association must only add Pool Print to "Open with", not take
  over as the default PDF app (as `LSHandlerRank` = `Alternate` on macOS, D-18).
- **W3 details (2026-11-07):**
  - **`upgradeUuid` = `fdbbb4df-e57b-487c-af3b-a52341470e7f`**, hard-coded in `desktopApp/build.gradle.kts`.
    **Never change it:** a different UUID makes Windows treat the next Msi as another product, and the old install
    would no longer be replaced. The rest: `perUserInstall = true`, desktop shortcut, Start-menu group `Pool Print`,
    icon `desktopApp/icons/PoolPrint.ico` (16–256 px, from the same glyph as the other icons), `packageName`
    `Pool Print`, `modules(...)` shared with macOS, ProGuard off as on macOS.
  - **Msi version** = `windowsPackageVersion` in `PoolPrintVersion` (`buildSrc`): `MAJOR.MINOR.PATCH` without `-rc.N`,
    MAJOR/MINOR ≤ 255, PATCH ≤ 65535, otherwise the build fails with the property name. **Consequence:**
    `1.0.0-rc.1`, `1.0.0-rc.2` and `1.0.0` are all Msi `1.0.0`. Windows Installer ignores the suffix and a
    major upgrade needs a *higher* version, so those builds are not upgrades of each other: uninstall the old one
    first (the settings and the credential survive, see below). Only a real version bump (`1.0.1`, `1.1.0`) upgrades
    in place. Whether jpackage's Msi refuses, repairs or double-installs on an equal version is not verified
    (no Windows here; on the checklist).
  - **No PDF file association in the Msi.** The Compose DSL has `windows { fileAssociation("application/pdf", "pdf",
    …) }`, which hands jpackage a `--file-associations` file with only mime type, extension, description and icon.
    jpackage then registers the extension with a ProgId of its own, and there is no equivalent of macOS's
    `LSHandlerRank` = `Alternate` in it, so it could take `.pdf` away from the user's viewer (and touch it again on
    uninstall). The JDK's Windows jpackage classes are not in the macOS JDK here, so this could not be read or tried;
    the downside (a changed default PDF viewer) is worse than the gain, so the association is **dropped**. The ways in on Windows are Explorer's
    **Open with → Choose another app → Pool Print** (pick `Pool Print.exe`, without "Always" unless wanted), drag and
    drop onto the window, and Ctrl+O. (W2's command-line and hand-over code works for any of them.) Revisit only with a Windows PC to inspect
    the registry result.
  - **Uninstall:** the Msi removes only what it installed (the app files in the per-user install folder, which jpackage puts under
    `%LOCALAPPDATA%` (probably `%LOCALAPPDATA%\Pool Print`, i.e. the same folder as the data; not verified, on the checklist,
    and a reason to watch that the data file survives), and the shortcuts). The data files (D-27) and the Credential Manager
    entry (D-26) are the app's own data, not part of the package, so they stay, as the macOS app leaves its Keychain item. Expected, not verified; checklist.
  - **jlink modules:** unchanged (`java.instrument`, `java.naming`, `java.security.jgss`, `java.sql`,
    `jdk.unsupported`). JNA brings its own `jnidispatch.dll` in the jar, and the Credential Manager, the loopback
    sockets, the file lock and the owner-only ACL are all `java.base`/`java.desktop` (already in); `jdk.crypto.mscapi`
    (the Windows certificate store provider) is not used. `suggestRuntimeModules` only runs for the build OS, so the
    Windows run (and a union with the macOS list) is on the checklist.

## D-31 Where Windows builds come from: GitHub Actions, manual trigger

**Status:** Accepted (owner, 2026-11-07). Extends D-24 and D-22 (0.6).
- jpackage cannot build a Windows installer on macOS. Windows builds come from a **GitHub Actions `windows-latest`
  workflow started by hand (`workflow_dispatch`) only**, which runs the JVM tests and builds the Msi and uploads it
  as a workflow **artifact**. It does not publish, tag or touch a release, and it needs no secrets (the Msi is
  unsigned, D-29). Created in W4; the same pinning rules as the other workflows apply (D-24).
- The owner downloads the Msi from the run and passes it to the release script (`--windows-msi`, W4), so a Windows
  release still goes out through `make release` on the owner's Mac (D-22).
- A local Windows PC or VM remains possible for manual testing. Agents still never push, tag or publish.
- **W4 details (2026-11-07):** `.github/workflows/windows.yml` (`windows-latest`, Zulu 21, the same SHA-pinned actions as
  the other workflows, `contents: read`, no secrets) runs `:shared:jvmTest`, builds `:desktopApp:packageReleaseMsi`, checks
  the file (one `.msi`, ≥ 10 MB, OLE/CFB header), writes commit/size/SHA-256 to the run summary, prints
  `suggestRuntimeModules` (not a gate) and uploads the Msi as the `msi` artifact for 14 days. `release.yml` stays Android +
  macOS: a release with Windows goes through `make release WINDOWS_MSI=<file>` (`scripts/release.sh build --windows-msi`),
  which checks the file, copies it as `PoolPrint-<version>-windows-x64.msi`, adds it to `SHA256SUMS.txt` and the notes, and
  `make release-draft` attaches it. Without an Msi the release is Android + macOS only and says so. The release gate for
  Windows is the owner's install on a Windows PC (release.md, "The Windows gate"); what the script can't see (that the Msi
  installs, or that it was built from the release commit) stays on the checklist.
- **Not verified here:** nothing in this environment can run the workflow or the Msi; both are on the manual
  checklist.
- **Amended by D-32 (2026-11-07):** the workflow also runs on pushes to `main` and on pull requests, and `release.yml`
  builds the Msi for tagged releases.

---

## D-32 Windows build in CI and in the release workflow

**Status:** Accepted (owner, 2026-11-07). Amends D-31; extends D-24.
- `.github/workflows/windows.yml` runs on **pushes to `main`, pull requests and by hand** (was `workflow_dispatch` only),
  so a change that breaks the Windows tests or the Msi build shows on the PR. Same job as before: `:shared:jvmTest` on
  `windows-latest`, `packageReleaseMsi`, file checks, `suggestRuntimeModules`, the Msi as an artifact. Still no secrets
  (fork PRs run it) and no publishing; a newer push to the same PR cancels the older run.
- `release.yml` gets a **windows** job (`windows-latest`, after `prepare`, no secrets, unsigned per D-29): `:shared:jvmTest`
  on Windows, `packageReleaseMsi`, upload. The draft job waits for it and passes the Msi to `scripts/release.sh ci-package
  <apk> <dmg> <msi>`, which runs the same `check_msi` as `make release WINDOWS_MSI=…` (name, size, OLE/CFB header, version
  in the name), copies it as `PoolPrint-<version>-windows-x64.msi` and lists it in `SHA256SUMS.txt`; `ci-draft` attaches
  it. A tagged release built by the workflow therefore always includes Windows; a failed Windows job stops the draft.
- `make release` on the Mac is unchanged: the Msi stays optional there (`WINDOWS_MSI=<file>` from a `windows.yml` run).
- The Windows gate (release.md) is still the owner's install on a Windows PC before publishing the draft.
- **Not verified here:** neither workflow has run on GitHub from this environment; the first PR/tag run shows it.

---

## Open questions

Questions for the owner that block or shape later work:

1. **Real command output** (pending, still needed in M3: owner to capture when on campus or on VPN): the output of
   `lpstat -p`, `lpoptions -p tfppr1 -l` (and tfppr2–4), and `lpq -P tfppr1` (with and without jobs)
   from the login server. They'll become test fixtures. Until then the fixtures are synthetic.
2. **Pool username format:** confirm the allowed characters so the validator isn't too strict.
3. **Host-key fingerprint:** do you want to ship a known fingerprint as a pre-pin, in addition to TOFU?
4. **Budget:** `lpquota` reportedly doesn't work and the budget page is intranet-only. Should the app try
   `lpquota` and show the result when it works, or leave budget out completely?

5. **Host key seen from the emulator** (not verified, do not treat as authentic): during M1 testing,
   `login.informatik.uni-freiburg.de` answered from this Mac's network with `ssh-ed25519`
   `SHA256:N0jIYeTuFtSpa91Wy71QcDY//4HvPgh6XFfSNfb08f0`. Compare it with `ssh-keygen -lF` from a trusted
   machine. This also shows the server offers an Ed25519 host key, which the client prefers. The app
   did **not** pin it (trust was declined) and sent no credentials.
   **Answered 2026-11-07** (release gate, [release.md Phase 6](release.md#phase-6--test-the-candidate-release-gate)):
   on real devices, the fingerprint the app shows on first connect matches `ssh-keygen -lF
   login.informatik.uni-freiburg.de` from a trusted machine.

6. **Windows port, unverified without a Windows PC** (2026-11-07, W1–W4; checklist in
   [roadmap.md](roadmap.md#windows-manual-checklist-owner-on-windows)): the Credential Manager calls, the file lock and
   hand-over between real processes, Explorer's Open With, the native dialog filter, whether `toFront()` is honoured, the Msi
   itself (WiX download, per-user install, shortcuts, upgrade, equal-version behaviour, uninstall, install folder), the
   `suggestRuntimeModules` list, and the Windows workflow (never run). Nothing about Windows is claimed to work until the
   owner has run that checklist.

Answered on 2026-10-25: off-campus access needs VPN (A-12); libraries are allowed as needed (A-10);
persistence (D-03) and Keychain (D-04) were decided by the implementer; rename everything (D-08).

---

## Dependency register

All non-template dependencies, planned or added. Update this table whenever `libs.versions.toml` changes.

| Library | Scope | Purpose | Status |
|---|---|---|---|
| `com.hierynomus:sshj` 0.41.2 (D-01) | `jvmCommonMain` | SSH exec | Added (M1) |
| BouncyCastle `bcprov` + `bcpkix` 1.86 (keep versions equal) | `jvmCommonMain` | Ed25519 key generation (lightweight API) and signatures on Android and desktop | Added (M1) |
| `kotlinx-coroutines-core` / `-test` 1.11.0 | common / tests | Async SSH calls, flow tests | Added (M1) |
| AndroidX DataStore `datastore-preferences-core` 1.3.0 | `commonMain` | Username, public key, host-key pins (later: options, presets, caches) | Added (M1) |
| `kotlinx-serialization-json` 1.12.0 (+ plugin) | `commonMain` | Structured persisted values (host-key pins) | Added (M1) |
| `androidx.activity:activity-compose` (already in the template catalog) | `androidMain` | `OpenDocument` picker | Added (M1) |
| JNA 5.19.1 | `jvmMain` | macOS Keychain access | Added (M1) |
| Apache PDFBox `pdfbox` 3.0.8 | `jvmMain` | Page counting on desktop (D-02) | Added (M2) |
| JNA Platform `jna-platform` (same version as JNA, 5.19.1) (D-26) | `jvmMain` | Windows helpers for the Credential Manager store: `WinBase.FILETIME`, `WinError`, `Kernel32Util.formatMessage`. It has **no** `CredRead`/`CredWrite` bindings, so those three calls are declared in the app (D-26 note) | Added (W1) |
| Apache MINA SSHD `sshd-core` 2.20.0 (+ `bcpkix`, `slf4j-simple`) | `jvmTest` only | In-process SSH server for testing the real `SshClient`; also run standalone for the emulator | Added (M1) |
| `kotlin-test` (Gradle's embedded Kotlin version) | `buildSrc` tests only | Tests for the version parsing (release Phase 2); not shipped | Added (R1) |
| `actions/checkout` v7.0.1, `actions/setup-java` v6.2.0, `actions/upload-artifact` v7.0.2, `actions/download-artifact` v8.0.2 (pinned by SHA, D-24) | GitHub Actions only | CI and release workflows; not shipped | Added (R1, Phase 9) |
| `gradle/actions/wrapper-validation` v6.4.1 (pinned by SHA, D-24) | GitHub Actions only | Checks `gradle-wrapper.jar` against Gradle's published checksums; not shipped | Added (R1, Phase 9) |
| Robolectric | Android host tests | Keystore and PdfRenderer tests, if needed | Optional |
