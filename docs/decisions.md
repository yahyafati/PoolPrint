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

**Status:** Proposed. Decide after the M1 Android spike.

Candidates:
- **`com.github.mwiede:jsch`:** an actively maintained JSch fork with a small dependency footprint.
  Ed25519 needs either Java 15+ EdDSA (not available on Android) or BouncyCastle.
- **`com.hierynomus:sshj`:** a cleaner API with stream-friendly exec. It depends on BouncyCastle and an
  EdDSA library, and Android's built-in, stripped "BC" provider is a known source of conflicts.

Spike criteria: generates and loads Ed25519 keys on Android API 29 and newer; exec with stdin
streaming works; host-key callback can be customised; password and keyboard-interactive auth can be
disabled; reasonable APK size; no reflection problems under R8.

## D-02 PDF page counting

**Status:** Proposed.
- Android: `android.graphics.pdf.PdfRenderer` (platform API, no dependency).
- Desktop: Apache PDFBox (counts as the allowed "PDF page-count library").
- Both sit behind `PdfPageCounter` in `commonMain`.

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
`<user>@login.informatik.uni-freiburg.de`, not synchronisable (no iCloud Keychain).

**Prompts (A-11):** the login keychain is unlocked when the user logs in, and an item created by the
app can be read by the same app without a prompt. Known caveat: the item's access list is tied to the
app's code signature. With unsigned or ad-hoc-signed builds, macOS may ask **once after each app
update** to allow access ("Always Allow" fixes it). Signing the `.dmg` consistently (M4) removes this.
Note it in the README.

## D-05 Kotlin / Compose / Gradle versions

**Status:** Open. Check at the start of M1.
The template currently has Kotlin 2.4.20, Compose Multiplatform 1.12.1, AGP 9.4.1, Gradle 9.6.0 and
Material3 `1.12.0-alpha03`. Check these against the latest **stable** releases. Material3 is an alpha,
so a stable version is preferred if one is compatible.

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

---

## Open questions

Questions for the owner that block or shape later work:

1. **Real command output** (pending: owner to capture when on campus or on VPN): the output of
   `lpstat -p`, `lpoptions -p tfppr1 -l` (and tfppr2–4), and `lpq -P tfppr1` (with and without jobs)
   from the login server. They'll become test fixtures. Until then the fixtures are synthetic.
2. **Pool username format:** confirm the allowed characters so the validator isn't too strict.
3. **Host-key fingerprint:** do you want to ship a known fingerprint as a pre-pin, in addition to TOFU?
4. **Budget:** `lpquota` reportedly doesn't work and the budget page is intranet-only. Should the app try
   `lpquota` and show the result when it works, or leave budget out completely?

Answered on 2026-10-25: off-campus access needs VPN (A-12); libraries are allowed as needed (A-10);
persistence (D-03) and Keychain (D-04) were decided by the implementer; rename everything (D-08).

---

## Dependency register

All non-template dependencies, planned or added. Update this table whenever `libs.versions.toml` changes.

| Library | Scope | Purpose | Status |
|---|---|---|---|
| SSH library (JSch fork or sshj, see D-01) | `jvmCommonMain` | SSH exec, Ed25519 keys | Planned (M1) |
| BouncyCastle (`bcprov`), if the SSH library needs it | `jvmCommonMain` | Ed25519 on Android | Planned (M1, decided by the spike) |
| `kotlinx-coroutines-core` / `-test` | common / tests | Async SSH calls, flow tests | Planned (M1) |
| AndroidX DataStore (KMP) | `commonMain` | Settings, presets, caches, host-key pins | Planned (M1) |
| `kotlinx-serialization-json` (+ plugin) | `commonMain` | Structured persisted values | Planned (M1) |
| JNA | `jvmMain` | macOS Keychain access | Planned (M1) |
| Apache PDFBox | `jvmMain` | Page counting on desktop | Planned (M2) |
| Apache MINA SSHD | tests only | In-process SSH server for testing the real `SshClient` | Planned (M1) |
| Robolectric | Android host tests | Keystore and PdfRenderer tests, if needed | Optional |
