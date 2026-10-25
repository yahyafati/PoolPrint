# Decisions and open questions

A lightweight decision log. Each entry has a status: **Accepted** (decided), **Proposed** (a
recommendation waiting for the owner or a spike), or **Open** (needs information).

When a decision changes, update the entry and add a dated note. Don't delete history.

---

## Accepted (from the project brief)

| ID | Decision |
|---|---|
| A-01 | Kotlin Multiplatform + Compose Multiplatform; targets Android and macOS desktop (JVM). No web, no iOS for now. |
| A-02 | No backend, cloud or telemetry. The device talks directly to `login.informatik.uni-freiburg.de` over SSH. |
| A-03 | SSH key auth only (Ed25519, generated on the device). The pool password is never asked for or stored. |
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
- Files go in app-private storage: Android `filesDir`, macOS `~/Library/Application Support/Pool Print/`.
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
- **Name and version:** `packageName = "Pool Print"` (so the bundle is `Pool Print.app`), version `1.0.0`
  (macOS needs a major version of at least 1), bundle id unchanged. The icon is a drawn printer
  (`desktopApp/icons/make_icon.py` regenerates `PoolPrint.icns`); the Android launcher icon is still the template one.
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
| Apache MINA SSHD `sshd-core` 2.20.0 (+ `bcpkix`, `slf4j-simple`) | `jvmTest` only | In-process SSH server for testing the real `SshClient`; also run standalone for the emulator | Added (M1) |
| Robolectric | Android host tests | Keystore and PdfRenderer tests, if needed | Optional |
