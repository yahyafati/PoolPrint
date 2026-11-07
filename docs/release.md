# Release guide: sideloaded builds on GitHub

How to ship Pool Print as a **GitHub Release** that people download and install themselves: an Android APK
and a macOS `.dmg`, with checksums and release notes. No app store, no backend, no update server.

Every release is presented honestly as what it is: a **vibe-coded** app (written largely by AI coding agents,
not audited), shared **as is, without any warranty or guarantee**. That notice goes in the README, the release
notes and the app itself (Phase 3).

This is milestone **R1** in [roadmap.md](roadmap.md). Work through it phase by phase, like the other milestones
([development.md](development.md#milestone-reports)): at the end of each phase, report and wait for the owner.

**Who does what**

| Mark | Meaning |
|---|---|
| 👤 **Owner** | Only you can do it: decisions, secrets, the real pool server, real devices, publishing. |
| 🤖 **Agent** | A coding agent can do it in the repo (code, scripts, docs, local builds and tests). |
| 👤🤖 | The agent prepares, you review and approve or run it. |

What an agent will **not** do, by design: create or read signing passwords, reach the pool server, install on your
phone, change the repository's visibility, or push tags and publish a release without your explicit OK in chat.

## Where things stand today (2026-11-07)

Facts from the repo that shape this plan:

| Area | Current state | Problem for a public release |
|---|---|---|
| GitHub repo | `yahyafati/PoolPrint`, **private**, no licence file | Releases of a private repo are visible only to collaborators. Without a licence, nobody may legally redistribute or modify the code. |
| Android signing | `release` is signed with the **debug keystore** (`~/.android/debug.keystore`) | That key is machine-local and throwaway. Every future update must be signed with the *same* key, so it has to be a real, backed-up key from the first public build. |
| Android version | `versionCode = 1`, `versionName = "1.0"`, hardcoded in `androidApp/build.gradle.kts` | Two version sources (Android, desktop) that can drift; Android refuses an update whose `versionCode` isn't higher. |
| macOS version | `packageVersion = "1.0.0"`, hardcoded in `desktopApp/build.gradle.kts` | Same drift. macOS needs a numeric `MAJOR.MINOR.PATCH` with `MAJOR ≥ 1` (D-18). |
| macOS signing | jpackage **ad-hoc** signature, hardened runtime, **arm64 only** (built on Apple Silicon) | Downloaded copies get Gatekeeper's "could not verify" block; Intel Macs can't run it. |
| In-app version | Not shown anywhere | Bug reports can't say which build they're on. |
| Manual checks | Most M1–M4 real-server checks are unticked (🔶) | Shipping untested remote behaviour to other students. |
| README | Says "Milestone 1 … in development"; macOS tip says "right-click → Open" | Outdated; on macOS 15+ the right-click bypass is gone (System Settings → Privacy & Security → **Open Anyway** instead). |

## The plan at a glance

| Phase | What | Who |
|---|---|---|
| [0](#phase-0--decisions) | ✅ Decide visibility, licence, versioning, macOS signing, architectures, release gate | 👤 |
| [1](#phase-1--android-release-signing-key) | ✅ Create and back up the Android release key; wire it into Gradle | 👤 key · 🤖 Gradle |
| [2](#phase-2--one-version-number) | ✅ One version source for both apps; show the version in Settings | 🤖 |
| [3](#phase-3--repository-ready-to-go-public) | 🟨 Secret scan, licence, third-party notices, README install guide (repo public; 👤 code-security settings pending) | 🤖 prepare · 👤 approve |
| [4](#phase-4--release-script) | ✅ `make release`: tests, signed APK, DMG, checks, checksums, notes draft | 🤖 |
| [5](#phase-5--release-candidate) | ✅ Build `v1.0.0-rc.1`, put it on a draft/pre-release | 👤🤖 |
| [6](#phase-6--test-the-candidate-release-gate) | ✅ Install the downloaded RC on real devices; run the release gate | 👤 |
| [7](#phase-7--publish-v100) | ✅ Tag and publish `v1.0.0` | 👤🤖 |
| [8](#phase-8--after-the-release) | 🟨 Verify, update docs, patch-release routine (🤖 done; 👤 optional announcement) | 👤🤖 |
| [9](#phase-9-optional--automate-with-github-actions) | 🟨 *(optional)* Build releases in GitHub Actions (workflows, `release` environment and secrets done; first real run pending) | 🤖 workflow · 👤 secrets |

---

## Phase 0 — Decisions

👤 **Owner.** Answer these in chat (or edit this table). The agent records them in [decisions.md](decisions.md) as
D-22 (distribution and signing) and D-23 (versioning), and adjusts the later phases.

| # | Question | Options | Recommendation |
|---|---|---|---|
| 0.1 | **Repo visibility** | Make `PoolPrint` public · keep private and invite each user as a collaborator | **Public.** Other students can't download from a private repo without a GitHub invite. Do it only after Phase 3's secret scan. |
| 0.2 | **Licence** | MIT · Apache-2.0 · GPL-3.0 · none (all rights reserved) | **MIT** if you just want people to use and fork it; **GPL-3.0** if forks must stay open. Both carry the "provided as is, without warranty" clause that backs the vibe-coded notice (Phase 3). All current runtime dependencies are compatible with either. |
| 0.3 | **Version scheme** | SemVer `MAJOR.MINOR.PATCH`, release candidates as `-rc.N` | **SemVer.** First release `1.0.0`; candidates `v1.0.0-rc.1`, `-rc.2`… as GitHub pre-releases. Android `versionCode` is a plain counter, +1 for every build that leaves your machine (RCs included). |
| 0.4 | **macOS signing** | Ad-hoc (free, today's state) · Apple Developer ID + notarisation (99 €/year) | **Ad-hoc for v1.** Users click *Open Anyway* once. Notarisation removes that and the Keychain prompt after updates (D-04), but costs money and needs signing secrets; revisit if users complain. |
| 0.5 | **Mac architectures** | arm64 only · arm64 + Intel (x86_64) | **arm64 only** unless you know Intel users. An Intel build needs an Intel Mac or an Intel CI runner (Phase 9); jpackage can't cross-build. |
| 0.6 | **Where releases are built** | On your Mac (`make release`) · GitHub Actions | **Your Mac for v1.0.0.** The signing key never leaves it. CI is Phase 9, optional. |
| 0.7 | **Release gate** | Which real-server checks must pass before `1.0.0` | The **blocking list** in [Phase 6](#phase-6--test-the-candidate-release-gate). Everything else ships as "known limitations". |
| 0.8 | **Update notifications** | None · in-app "new version" check against the GitHub API | **None.** An update check would make the app talk to a host other than the pool server, which breaks the privacy promise in the README. Users click *Watch → Custom → Releases* on GitHub. |
| 0.9 | **Telling the pool admins** | Yes · no | Optional courtesy: the app only automates the documented `ssh … lpr` procedure, but a heads-up to the Fachschaft/pool admins avoids surprises. |

**Done when:** every row has an answer and D-22/D-23 are in `decisions.md`.

✅ **Answered 2026-11-07** (recorded as [D-22](decisions.md#d-22-distribution-and-signing-release-r1) and
[D-23](decisions.md#d-23-versioning-release-r1)): every recommendation above was taken.

| # | Answer |
|---|---|
| 0.1 | **Public**, flipped only after the owner approves the Phase 3 scan. |
| 0.2 | **MIT**. |
| 0.3 | **SemVer**, RCs as `v1.0.0-rc.N` pre-releases, `versionCode` +1 per build that leaves the Mac (first RC: 2). |
| 0.4 | **Ad-hoc** for v1. |
| 0.5 | **arm64 only**. |
| 0.6 | **The owner's Mac** (`make release`); Phase 9 stays optional. |
| 0.7 | The **Phase 6.2 blocking list**; everything else goes into known limitations. |
| 0.8 | **None**. |
| 0.9 | Undecided: optional courtesy, doesn't block the release. |

---

## Phase 1 — Android release signing key

Android installs an update only if it's signed with **the same key** as the installed app. Lose the key and every
user has to uninstall and reinstall, which on Pool Print means a **new device key and onboarding again** (the
Keystore-protected key can't be backed up, D-11). So: one key, created once, backed up twice.

### 1.1 👤 Create the keystore (outside the repo)

It goes in `~/keys`, next to the keys of your other apps. That folder should be readable by you only; `ls -ld ~/keys`
should start with `drwx------` (if not: `chmod 700 ~/keys`).

```bash
keytool -genkeypair -v -keystore ~/keys/poolprint-release.jks -storetype PKCS12 -alias poolprint -keyalg RSA -keysize 4096 -validity 10000 -dname "CN=Yahya Fati, O=Pool Print"
```

`keytool` asks for the password interactively, so it doesn't land in your shell history. With PKCS12 the key
password is the same as the store password.

### 1.2 👤 Back it up

- Put `poolprint-release.jks` **and** its password in your password manager (as an attachment), and on one offline
  medium. Never commit it, never send it to an agent or paste it in chat.

### 1.3 👤 Tell Gradle where it is

Add to the repo's **`local.properties`** (the file next to `settings.gradle.kts` that already holds `sdk.dir`). It is
in `.gitignore`, so it never gets committed. Not `gradle.properties`: that one is checked in.

```properties
poolprint.signing.storeFile=/Users/<you>/keys/poolprint-release.jks
poolprint.signing.storePassword=<password>
poolprint.signing.keyAlias=poolprint
poolprint.signing.keyPassword=<password>
```

```bash
chmod 600 local.properties
```

Two things to know about `local.properties`:
- It belongs to this one checkout. A fresh clone or a git worktree (as agent sessions sometimes use) doesn't have
  it, so its release builds fall back to debug signing, and the release script refuses them. Build releases from
  your main checkout.
- Android Studio rewrites `sdk.dir` in it now and then and normally keeps the other lines. Your password manager
  copy (1.2) is the backup, not this file.

### 1.4 🤖 Wire it into the build

Agent tasks (✅ done 2026-11-07; a partial setup or a missing keystore file fails the build with the key
names, never the values):

- [x] `androidApp/build.gradle.kts`: add a `release` signing config that reads the four `poolprint.signing.*` keys
      from the root project's `local.properties`, falling back to the matching `POOLPRINT_SIGNING_*` environment
      variables (for CI). Gradle doesn't load `local.properties` on its own, so `providers.gradleProperty(...)` won't
      see these keys. Read the file through `providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))`
      and `java.util.Properties`, so the configuration cache tracks it as an input; read the environment variables
      with `providers.environmentVariable(...)`.
- [x] Never log, print or echo the password values, and don't read them back while checking the change. Test the
      "missing" path by building without the keys, e.g. in a worktree.
- [x] If the properties are missing, keep signing `release` with the debug key so `make install-phone` still works
      for contributors, and print a Gradle warning ("release APK is debug-signed, not for distribution").
      The release script (Phase 4) refuses to package a debug-signed APK.
- [x] `.gitignore`: add `*.jks`, `*.keystore`, `keystore.properties`.
- [x] Update the comment and docs that say release is signed with the debug keystore (`CLAUDE.md`, `Makefile`
      header, `development.md`).

### 1.5 👤 Record the certificate fingerprint

After the agent's change, build once and read the certificate:

```bash
make android
```

```bash
SDK=$(grep '^sdk.dir=' local.properties | cut -d= -f2-); "$SDK"/build-tools/$(ls "$SDK"/build-tools | sort -V | tail -1)/apksigner verify --print-certs androidApp/build/outputs/apk/release/androidApp-release.apk
```

(The SDK path comes from `sdk.dir` in `local.properties`, so this works without `ANDROID_HOME` set.)

Send the `SHA-256 digest` line to the agent. It is **public** (it's in every APK) and goes into this doc, the release
notes and the release script, so users and the script can check that an APK really is yours.

> Release certificate SHA-256: `0260ccfe30cadbf9d5f65bd5a4eb160704d5557f3d978a607d0e86ff7532a269`

### 1.6 👤 Switch your own installs over

Your phone currently runs a debug-key build, so the first release-key build can't update it. Once, on each of your
Android devices: remove the old key's line from `~/.ssh/authorized_keys` on the pool account (Settings shows which
key the device uses), uninstall Pool Print, install the release-key build, onboard again. The Mac is unaffected.

**Done when:** `make android` produces an APK whose certificate matches the recorded fingerprint, and nothing
secret is in git (`git status` clean, `git check-ignore local.properties` prints the path,
`git log -p --all | grep -i 'jks\|poolprint.signing'` finds only `.gitignore`, the build script and docs).

✅ **Done 2026-11-07.** The release APK is signed by `CN=Yahya Fati, O=Pool Print` with the SHA-256 above; the
owner's phone runs the release-key build and is onboarded again. History check: no keystore or `local.properties`
was ever committed, and the only `poolprint.signing.*` lines are this guide's examples (passwords as
`<password>`), the build script and docs.

---

## Phase 2 — One version number

🤖 **Agent.** ✅ Done 2026-11-07.

- [x] Put the version in **one place**, the repo's `gradle.properties`:
      ```properties
      poolprint.versionName=1.0.0-rc.1
      poolprint.versionCode=2
      ```
      (`versionCode` 1 is already taken by the debug builds on your phone.)
- [x] `androidApp`: `versionName` and `versionCode` from those properties.
- [x] `desktopApp`: `packageVersion` = `versionName` without the `-rc.N` suffix (macOS accepts only numbers). The full
      name (with `-rc.N`) is still shown in the app (next point).
- [x] Show the version in **Settings → About** ("Pool Print 1.0.0-rc.1"), on both platforms, with the short
      "vibe-coded, provided as is, no warranty" line from Phase 3 under it. Pass the version from the app
      modules into `App()` (or a small `expect`/`actual`) so `commonMain` stays platform-free; the label goes in
      Compose resources.
- [x] A unit test that the desktop version strips the suffix correctly.
- [x] Document the "how to bump the version" rule in this file's [patch routine](#patch-release-routine).

**Done when:** changing the two properties changes the Android version, the `.dmg` version and the Settings label.

**How it's built.** `buildSrc/src/main/kotlin/PoolPrintVersion.kt` reads and checks both properties
(`MAJOR.MINOR.PATCH` or `MAJOR.MINOR.PATCH-rc.N`, `MAJOR ≥ 1`, no leading zeros; `versionCode` a whole number from 1);
anything else fails the build with the property name. `macPackageVersion` and `windowsPackageVersion` (also range-checked for the Msi) drop the `-rc.N`. Its tests are
`./gradlew :buildSrc:test`, part of `make test-all`. Android passes `BuildConfig.VERSION_NAME` into `App()`; the desktop
build generates `BuildInfo.VERSION_NAME` (`:desktopApp:generateBuildInfo`), so the label is right under `run` and
`hotRun` too. Settings → About shows "Pool Print <version>" with the vibe-coded line and the privacy line under it.

Checked 2026-11-07: with the defaults the APK is `versionCode 2` / `versionName 1.0.0-rc.1` and the bundle and DMG
are `1.0.0` (`Pool Print-1.0.0.dmg`); with `-Ppoolprint.versionName=1.2.3-rc.4 -Ppoolprint.versionCode=7` they become
`7` / `1.2.3-rc.4`, `Pool Print-1.2.3.dmg` and `BuildInfo.VERSION_NAME = "1.2.3-rc.4"`; `0.9.0` fails the build.
Not yet looked at on screen: the About row itself (👤 open Settings once on each platform).

---

## Phase 3 — Repository ready to go public

🤖 **Agent prepares**, 👤 **owner reviews and approves.** 🤖 part done 2026-11-07 ([scan results](#secret-scan-results-2026-11-07));
the owner made the repository public the same day. Still off: private vulnerability reporting and secret scanning.

- [x] **Secret and privacy scan of the whole history**, not just `HEAD`: private-key headers
      (`BEGIN … PRIVATE KEY`, `openssh-key-v1`), `.jks`/`.keystore` files, `local.properties`, your real pool username,
      real `lpq` owners, IP addresses. If the agent may install a tool, `gitleaks detect --log-opts="--all"` is the
      quick way; otherwise `git log -p --all | grep -nE '…'`. The agent reports findings; **you** decide whether
      anything needs a history rewrite (that would be done before going public).
      *(The server host-key fingerprint in `decisions.md`, open question 5, is public server information and fine.)*
- [x] **`LICENSE`**: the MIT licence (0.2, D-22), copyright holder Yahya Fati, and a licence line in the README.
- [x] **`THIRD_PARTY_NOTICES.md`**: the libraries that ship inside the APK/DMG and their licences (sshj, BouncyCastle,
      PDFBox, JNA, kotlinx, AndroidX/Compose, and what the release build actually bundles; test-only MINA SSHD is not
      shipped). Apache-2.0 requires passing on their licence and NOTICE texts with binary redistributions. Attach it
      to every release.
- [x] **"Vibe-coded, as is" notice**, worded like the box in the [release notes template](#release-notes-template),
      in three places: right under the README title (before any feature list or download link), at the top of
      every release's notes, and in the app's **Settings → About** next to the version (Phase 2), with a link to the
      licence. The MIT licence's own no-warranty clause is the legal part; this notice is the
      plain-language one people actually read. Extend the README's existing *Disclaimer* section to match.
- [x] **README rewrite of the top**: real status, a **Download** link to
      `https://github.com/yahyafati/PoolPrint/releases/latest`, and an **Install** section (text below). Building
      from source moves further down.
- [x] **`SECURITY.md`** (short): how to report a vulnerability privately (GitHub's *Report a vulnerability*), and a
      reminder never to post keys, usernames or fingerprints in issues.
- [x] **Bug-report issue template** asking for app version, platform/OS version, what happened, and the error's
      *details* text, with the same privacy reminder.
- [x] 👤 After reviewing the scan: GitHub → *Settings → General → Danger Zone → Change visibility → Public*.
      *(Public by 2026-11-07.)*
- [ ] 👤 Then *Settings → Code security*: enable **private vulnerability reporting** and **secret scanning**.
      *(Both still disabled on 2026-11-07, checked with the GitHub API. Until reporting is on, the private
      route in `SECURITY.md` and the issue template's contact link doesn't work.)*

**What landed (2026-11-07).** `LICENSE` (MIT, © 2026 Yahya Fati). `THIRD_PARTY_NOTICES.md`, built from the release
runtime classpaths of both apps: component table per app, the NOTICE texts of sshj, PDFBox, FontBox, PDFBox io and
Commons Logging, and the MIT, BSD-3-Clause and Apache-2.0 texts; JNA is used under Apache-2.0, and the bundled
OpenJDK 21 runtime keeps its own `legal/` folder inside the app. **Re-check it whenever a dependency changes** (the
commands are at its top). The vibe-coded box is under the README title, and in Settings → About the line now reads
like the release-notes box, with **MIT licence** and **Third-party notices** rows that open the files on GitHub in
the browser. README: notice, real status, Download, Install (the draft below), What it does; the Setup section now
mentions the password key install (D-20) and *Settings → Add a device*; new Licence and Security sections; the
Disclaimer extended. `SECURITY.md`, `.github/ISSUE_TEMPLATE/bug_report.yml` (version, platform, OS, what
happened, error *Details*, a required privacy checkbox) and `config.yml` (links to private reporting).
Not looked at on screen yet: the two new About rows (👤 tap each once on Android and macOS; they need the repo to be
public to show the files).

### Secret-scan results (2026-11-07)

Scope: every commit reachable from any ref (29 commits: `main`, the three merged feature branches, `origin/main`;
no tags, no stashes), including the 8 `main` commits not pushed yet. gitleaks isn't installed, so this was
`git log -p --all` searched with patterns, plus the list of every file name ever committed.

Nothing that needs a history rewrite for **secrets**:

| Looked for | Result |
|---|---|
| Private keys (`BEGIN … PRIVATE KEY`, `openssh-key-v1` and its base64 form) | Only this file's pattern list and the key-encoding code that writes the header at runtime. |
| `.jks` / `.keystore` / `.p12` / `.pem` / `id_*` / `known_hosts` / `local.properties` files | Never committed. `.gitignore` covers `local.properties`, `*.jks`, `*.keystore`, `keystore.properties`. |
| Passwords, tokens, API keys | Signing values only as `<password>` placeholders; test-server passwords (`secret`, `correct horse`, `p4ss`) are fixtures. No GitHub/AWS/Google token formats. |
| Pool usernames, `lpq` owners | Only `jdoe`, `doej`, `muster`; public keys only as the `ssh-ed25519 AAAA…` placeholder. |
| IP addresses | `10.0.2.2` (emulator) and `127.0.0.1` only. |
| Fingerprints | Server host key `SHA256:N0jI…` (public, fine), two synthetic test values, and the release certificate SHA-256 (published on purpose). |
| Image files | No embedded metadata (paths, author, e-mail). |

👤 **For you to decide** (privacy, not secrets):

1. **Your e-mail address is the author address of all 29 commits** and becomes public with the repo. Keep it, or
   rewrite the authors to your GitHub `noreply` address before going public (changes every commit hash). Either
   way, consider committing with the noreply address from now on and turning on GitHub's *Block command line
   pushes that expose my email*.
2. **Your macOS account name** appears in old versions of this file, as the keystore path
   (`/Users/<account>/Keystores/…` and `/Users/<account>/keys/…`). `HEAD` now says `/Users/<you>/…`; history keeps
   it. Low sensitivity: a path, not the key.
3. Your real name is in the keystore's certificate (`CN=Yahya Fati`), the macOS bundle vendor and `LICENSE`. On
   purpose; listed for completeness.
4. **Not checkable by an agent:** your actual pool username isn't known here, so it was only searched indirectly
   (no unknown username-like strings turned up). Check it yourself; `0` means it's not in the history:
   ```bash
   git log -p --all | grep -ci 'YOUR_POOL_USERNAME'
   ```

### Install text for the README (draft)

> ⚠️ Pool Print is vibe-coded (written largely by AI agents), unaudited, and provided **as is, without any warranty**.
> Install it only if you accept that risk for your pool account and print budget.

**Android (10 or newer)**
1. On the phone, open the latest release and download `PoolPrint-<version>-android.apk`.
2. Open it. Android asks to allow installing from this source (browser or Files): allow it for that app.
3. If Play Protect says it doesn't recognise the developer, choose *More details → Install anyway*.
4. Optional: compare the certificate fingerprint in the release notes.

**macOS (12 or newer, Apple Silicon)**
1. Download `PoolPrint-<version>-macos-arm64.dmg`, open it, drag **Pool Print** to **Applications**.
2. Open it once. macOS says it can't verify the app: click *Done*, then *System Settings → Privacy & Security →*
   **Open Anyway** next to "Pool Print", and confirm. (macOS 14 and older: right-click the app → *Open*.)
3. After an update, macOS may ask whether Pool Print may use its Keychain item: choose **Always Allow**.

**Updating:** download the newer file and install over the old one. Your key and settings stay. Don't uninstall
first on Android: that deletes the device key and you'd have to onboard again.

**Done when:** you've approved the scan result, the licence and the README, and (if chosen) the repo is public.

---

## Phase 4 — Release script

🤖 **Agent.** One command that turns a clean checkout into release files, so every release is built the same way.
✅ Done 2026-11-07 (see [how it works](#how-the-release-script-works)).

- [x] `make release` (in the `Makefile`, the logic in `scripts/release.sh` if it gets long). It must:
  1. refuse to run unless the working tree is clean and the version's tag doesn't exist yet;
  2. run `make test-all`;
  3. build `:androidApp:assembleRelease` and `:desktopApp:packageReleaseDmg`;
  4. **check the APK**: `apksigner verify --print-certs` must show the release certificate from Phase 1.5,
     otherwise stop ("APK is not signed with the release key");
  5. **check the app bundle**: `codesign -dv` shows identifier `com.yahyafati.poolprint` and the expected
     architecture; the DMG mounts and contains `Pool Print.app`;
  6. copy the files into `build/release/<version>/` with stable names:
     `PoolPrint-<version>-android.apk`, `PoolPrint-<version>-macos-arm64.dmg`, `THIRD_PARTY_NOTICES.md`;
  7. write `SHA256SUMS.txt` (`shasum -a 256`);
  8. write `release-notes.md` from the [template](#release-notes-template), with the commits since the last tag
     pre-filled for you to edit.
- [x] `make release-draft`: runs `gh release create v<version> --draft [--prerelease] --title … --notes-file …`
      with the files above. Draft releases are visible only to you, so this is safe to run; **publishing** stays a
      separate, manual click or command.
- [x] Document both targets in `make help`, `CLAUDE.md` and `development.md`.

The agent can test everything up to the APK certificate check without your key (it will fail the check, as
intended, on a debug-signed build).

**Done when:** on your Mac, `make release` produces the folder with two binaries, notices, checksums and notes.

### How the release script works

`make release` runs `scripts/release.sh build`, `make release-draft` runs `scripts/release.sh draft`. Both always
build `release` (they ignore `BUILD=`), take the version from `gradle.properties`, and stop at the first failed
check with a message saying what to do.

`make release`, in order:

1. **Checkout**: macOS on arm64 (D-22), `git status --porcelain` empty (untracked files count), tags fetched from
   `origin` (best effort, since GitHub creates the tag only on publish), and no tag `v<version>` yet.
2. `make test-all`.
3. `./gradlew :androidApp:assembleRelease :desktopApp:packageReleaseDmg`.
4. **APK**: `apksigner verify --print-certs` must show exactly one signer with the release certificate SHA-256
   (Phase 1.5, a constant at the top of the script); otherwise "APK is not signed with the release key". `aapt2 dump
   badging` must show `com.yahyafati.poolprint` and the `versionCode`/`versionName` from `gradle.properties`.
   The SDK comes from `sdk.dir` in `local.properties`, else `ANDROID_HOME`/`ANDROID_SDK_ROOT`.
5. **DMG**: mounted read-only (`hdiutil attach -nobrowse`), must contain `Pool Print.app`; that bundle (the one users
   get) must show `Identifier=com.yahyafati.poolprint` and `Mach-O thin (arm64)` in `codesign -dv`, pass
   `codesign --verify --deep --strict`, and have the numeric version in `Info.plist`.
6. Copies into `build/release/<version>/` (git-ignored): `PoolPrint-<version>-android.apk`,
   `PoolPrint-<version>-macos-arm64.dmg`, `THIRD_PARTY_NOTICES.md`, plus a hidden `.release-commit` with the commit
   the files were built from (not uploaded). The copied APK is checked again.
7. `SHA256SUMS.txt` over the APK, the DMG and the notices (`shasum -a 256`).
8. `release-notes.md` from [`scripts/release-notes.template.md`](../scripts/release-notes.template.md): version,
   file names, certificate SHA-256, a release-candidate line for `-rc.N`, and under *What's new* every non-merge
   commit since the last **final** release tag (RC tags are skipped, so `1.0.0` lists everything since the previous
   final release, not just since its last RC). If `release-notes.md` already exists for this version (a rerun after
   you edited it), it is kept; delete it to regenerate.

`make release-draft`:

- needs `gh` logged in, all files present, `shasum -c` passing, and the `<!-- Edit: … -->` line deleted from
  `release-notes.md` (that's how it knows you've reviewed the notes; it only warns about leftover `…`);
- fetches from `origin` and refuses if the tag already exists, if a release `v<version>` (e.g. an earlier draft)
  already exists, or if the built commit isn't on any remote branch yet (push it first; the script never pushes);
- runs `gh release create v<version> --draft [--prerelease] --target <built commit> --title "Pool Print <version>"
  --notes-file release-notes.md` with the APK, DMG, notices and `SHA256SUMS.txt`. `--prerelease` is set for
  `-rc.N`. `--target` makes GitHub create the tag on the commit the files came from when you publish.

**Checked 2026-11-07** (by the agent, without the release key in play): on the existing release-key APK and DMG the
APK, DMG and bundle checks pass and the mount is cleaned up; the notes draft fills every placeholder; `make release`
refuses a dirty tree, an existing tag, and bad `versionName`/`versionCode` values; `make release-draft` refuses a
missing build folder. End to end in a scratch clone without the `poolprint.signing.*` keys, it ran the tests, built
both binaries and stopped at step 4 with "APK is not signed with the release key", as intended. With the expected
digest switched to the debug certificate (in that scratch copy only), it ran through step 8: the folder with both
binaries, notices, checksums and notes; a rerun kept the edited notes; `make release-draft` refused while the
`<!-- Edit` line was there and then because the commit wasn't pushed. Not run: a full `make release` with the release
key, and `make release-draft` against GitHub (👤 Phase 5).

---

## Phase 5 — Release candidate

- [x] 🤖 Version is `1.0.0-rc.1` / `versionCode 2` (Phase 2); notes and README are current; commit on `main`
      (or a `release/1.0.0` branch you merge).
- [x] 👤 Run on your Mac:
      ```bash
      make release
      ```
- [x] 👤 Read `build/release/1.0.0-rc.1/release-notes.md`, edit it, then:
      ```bash
      make release-draft
      ```
- [x] 👤 On GitHub, open the draft, check the files and the text, tick **Set as a pre-release**, and **Publish**.
      Publishing creates the tag `v1.0.0-rc.1` on the commit you built. (If you prefer to keep the RC private, leave it
      as a draft and download the files while logged in to GitHub.)

**Done when:** `v1.0.0-rc.1` exists as a pre-release with both binaries and `SHA256SUMS.txt`.

✅ **Done 2026-11-07.** [`v1.0.0-rc.1`](https://github.com/yahyafati/PoolPrint/releases/tag/v1.0.0-rc.1) is a
published pre-release, tagged on `b80b3b6`, the commit the files were built from. It has the APK, the DMG,
`THIRD_PARTY_NOTICES.md` and `SHA256SUMS.txt`; GitHub's SHA-256 for each binary matches `SHA256SUMS.txt`. The notes
are the edited ones (What's new as a feature list; Known limitations from the roadmap and the owner's Android test).
The repository was still **private** then, so only collaborators could see the release; it went public before 1.0.0
(Phase 3).

---

## Phase 6 — Test the candidate (release gate)

👤 **Owner.** Test **the downloaded files**, not your build folder: only a downloaded file goes through Gatekeeper,
quarantine and Android's install-from-unknown-sources path, which is what users will see.

### 6.1 Install like a user

- [x] Android: download the APK in the phone's browser → install (allow the source, Play Protect prompt) → app opens.
- [x] Android: `apksigner` fingerprint of the downloaded APK matches the release notes (or check on the Mac).
- [x] macOS: download the DMG and `SHA256SUMS.txt` in Safari → `shasum -a 256 -c --ignore-missing SHA256SUMS.txt`
      (in Downloads) → drag to Applications → *Open Anyway* flow works → app opens. Ideally also on a second user account or Mac.

### 6.2 Blocking checks (must pass for 1.0.0)

From the [roadmap](roadmap.md) checklists, the ones a new user hits on day one:

- [x] Onboarding with **copy the key** → add it on the pool account → Test connection ✓ (M1).
- [x] Onboarding with **"Add it for me with my pool password"** → Test connection ✓; one new line in
      `authorized_keys`; wrong password gives the right message and adds nothing (M4).
- [x] The fingerprint shown on first connect matches `ssh-keygen -lF login.informatik.uni-freiburg.de` (M1).
- [x] Android: **share a PDF from another app** → print to a b/w printer → correct paper output (M1).
- [x] macOS: print via **⌘O / Pick a PDF** and via **drag and drop** (M1, M4).
- [x] Copies, duplex (long edge), 2-up: output correct (M2).
- [x] Page count and cost show in the packaged mac app and on Android (M2, M4).
- [x] Restart the app / reboot the phone → print again → **no password or passphrase prompt** (M1).
- [x] Off campus without VPN → "Connect to the university VPN" message, not a crash or cryptic error (M1).
- [x] Nothing left in the pool home directory after printing (`ls -la ~` unchanged) (M1).
- [x] Discovery failing (e.g. VPN off then on) doesn't block printing (M3).

### 6.3 Update path (needs a second build)

- [x] 🤖 Bump to `1.0.0-rc.2` / `versionCode 3` (even with no code change) and 👤 build and pre-release it.
      🤖 Bumped 2026-11-07 (`gradle.properties`, and the version line of `THIRD_PARTY_NOTICES.md`; no dependency
      changed since rc.1); 👤 published as a pre-release the same day.
- [x] Android: install rc.2 **over** rc.1 → still onboarded, settings kept.
- [x] macOS: replace the app in Applications → note whether a Keychain prompt appears and whether *Always Allow*
      sticks (D-04, M4 checklist).

### 6.4 Report

Send the agent the results (ticked list, failures with the error *details* text, the real `lpstat`/`lpoptions`/`lpq`
output if you captured it). 🤖 The agent ticks the roadmap checklists, records answers in `decisions.md` (D-06,
D-07, open questions), fixes blocking bugs (→ another RC), and writes the **known limitations** for the notes:
every non-blocking check that's still open.

**Done when:** every blocking check passes on an RC built from the commit you're about to release.

✅ **Done 2026-11-07** (owner): the downloaded rc.1 and rc.2 install as described, every blocking check passes, and
rc.2 installs over rc.1 on Android with the device still onboarded. The roadmap's M1–M4 checklists are ticked or
annotated to match. Not reported, so not recorded: the macOS Keychain behaviour after replacing the app (D-04), the
optional second-account/second-Mac install, and the real `lpstat`/`lpoptions`/`lpq` output (D-06, D-07 and open
question 1 stay open). Every non-blocking check still open in the roadmap goes into the 1.0.0 known limitations
(Phase 7). The binaries for 1.0.0 come from a new build; only docs change between rc.2 and it.

---

## Phase 7 — Publish v1.0.0

- [x] 🤖 Set `poolprint.versionName=1.0.0` and bump `versionCode`; finalise the notes (changes, known limitations,
      certificate fingerprint); update the README status. Commit.
      *(2026-11-07: `1.0.0` / `versionCode 4`; README status and the notices' version line updated. The notes are
      pre-written in `build/release/1.0.0/release-notes.md`, which `make release` keeps: known limitations are every
      non-blocking roadmap check still open after Phase 6. 👤 Review them and delete their `<!-- Edit:` line before
      `make release-draft`.)*
- [x] 👤 Build and draft:
      ```bash
      make release
      ```
      ```bash
      make release-draft
      ```
- [x] 👤 On GitHub: review the draft, **untick** pre-release, tick **Set as the latest release**, Publish.
- [x] 👤 Pull the tag GitHub created, so your checkout knows it:
      ```bash
      git pull --tags
      ```

The binaries in `1.0.0` must come from this build, not from an RC: the `versionCode` and version label differ.

### Release notes template

The script fills [`scripts/release-notes.template.md`](../scripts/release-notes.template.md) (Phase 4); edit that
file to change the notes of every release. Filled in for 1.0.0 it reads:

```markdown
Pool Print 1.0.0. Print PDFs on the Uni Freiburg TF pool printers from Android or macOS.

> ⚠️ **Vibe-coded, use at your own risk.** This app was written largely by AI coding agents and has not been
> audited. It is shared **as is, without any warranty or guarantee**: it may contain bugs, misprint, waste
> print budget, or mishandle your SSH key. You alone are responsible for using it on your pool account.
> Unofficial; not affiliated with the University of Freiburg, the TF pool administration or the Fachschaft.

## Downloads
| File | For |
|---|---|
| `PoolPrint-1.0.0-android.apk` | Android 10+ |
| `PoolPrint-1.0.0-macos-arm64.dmg` | macOS 12+ on Apple Silicon |
| `THIRD_PARTY_NOTICES.md` | Licences of the bundled libraries |

Install steps: see the README → Install. Off campus you need the university VPN.

## What's new
<!-- Edit: commits since v…, pre-filled by `make release`. Keep what a user would notice. -->
- …

## Known limitations
- Not yet checked against the real server: …
- macOS: unsigned (ad-hoc) build; use *Open Anyway* on first launch. Updates may ask for Keychain access once.
- No automatic update check (on purpose: the app only talks to the pool server). Watch this repo's releases.

## Verify
- SHA-256 checksums: `SHA256SUMS.txt`
- Android signing certificate SHA-256: `0260ccfe30cadbf9d5f65bd5a4eb160704d5557f3d978a607d0e86ff7532a269`
```

**Done when:** `https://github.com/yahyafati/PoolPrint/releases/latest` shows 1.0.0 with all files.

✅ **Done 2026-11-07.** [`v1.0.0`](https://github.com/yahyafati/PoolPrint/releases/tag/v1.0.0) is published as the
latest release (not a pre-release), tagged on `58f0ab2`, the commit the files were built from (`versionCode 4`). It
has the APK, the DMG, `THIRD_PARTY_NOTICES.md` and `SHA256SUMS.txt`; GitHub's SHA-256 for each file matches
`SHA256SUMS.txt`. The notes are the prepared ones (feature list, known limitations from the roadmap).

---

## Phase 8 — After the release

- [x] 👤 From a logged-out browser (if public): the release page, downloads and README links work.
      *(2026-11-07, checked by the agent without a GitHub login: `releases/latest`, the APK download, and the
      LICENSE, notices and advisory links used by the README, `SECURITY.md` and Settings → About all answer.)*
- [x] 🤖 Roadmap: R1 → ✅ (or 🔶 with what's left); release certificate fingerprint filled in above; the
      `poolprint.versionName` on `main` bumped to the next development version if you like (e.g. `1.0.1`).
      *(2026-11-07: R1 → 🔶, left: the two code-security settings. The version stays `1.0.0`; the next release
      bumps it per the [patch routine](#patch-release-routine), and `make release` refuses to rebuild `1.0.0`.)*
- [ ] 👤 Optional: tell people (Fachschaft channel, friends). Ask them to report problems as GitHub issues, with
      the error *details* text and without usernames.

### Patch release routine

For every later release (`1.0.1`, `1.1.0`, …):

1. 🤖 Fix on a branch, tests green, merge to `main`.
2. 🤖 Bump the version in `gradle.properties`, nowhere else (Phase 2):
   - `poolprint.versionCode`: **+1, always**, for every build that leaves your Mac, RCs and rebuilds of the same
     version included. Android refuses an equal or lower code, so a code is never reused or lowered.
   - `poolprint.versionName`: `PATCH` +1 for fixes (`1.0.1`), `MINOR` +1 and `PATCH` 0 for new features (`1.1.0`),
     `MAJOR` +1 for breaking changes (e.g. a new onboarding). For a candidate append `-rc.1`, then `-rc.2`, …; the
     final release drops the suffix (`1.1.0-rc.2` → `1.1.0`, with a new `versionCode`).
   - macOS sees only `MAJOR.MINOR.PATCH`, so an RC and its final release share the `.dmg` version; the app's
     Settings → About shows the full name.
   - **Windows Msi versions (D-30):** the Msi version is `windowsPackageVersion` (`MAJOR.MINOR.PATCH` without the
     suffix; MAJOR/MINOR ≤ 255, PATCH ≤ 65535, else the build fails). An RC and its final release therefore share
     one Msi version (`1.0.0-rc.2` and `1.0.0` are both `1.0.0`), and Windows Installer does not upgrade an Msi
     with an equal version: **uninstall the old Msi first** when going from an RC to the final release (settings and
     key survive an uninstall). Only a higher `MAJOR.MINOR.PATCH` upgrades in place. The Msi's `upgradeUuid` is fixed
     forever (decisions.md, D-30).
3. 👤 `make release` → `make release-draft` → review → Publish. Use an RC first when the change touches SSH,
   key storage, onboarding or command building (things only the real server can confirm).
4. 👤 Smoke test the downloaded files (install over the previous version, print one page).

---

## Phase 9 (optional) — Automate with GitHub Actions

Worth it once releases become routine or you want an Intel build. Trade-off: your signing key then lives in GitHub
secrets. Recorded as [D-24](decisions.md#d-24-github-actions-release-r1-phase-9).

🤖 part ✅ done 2026-11-07; 👤 the `release` environment and its secrets were set up the same day. Left: commit and
push the workflows to `main`, then a first real run (ideally an RC). Until that run passes, `make release` stays the
way to release.

- [x] 🤖 `.github/workflows/ci.yml`: on push to `main` and on every PR, `./gradlew :shared:jvmTest
      :shared:testAndroidHostTest :buildSrc:test` (all of `make test-all`) on `ubuntu-latest`, after Gradle's
      wrapper-JAR validation. Test reports are uploaded when it fails. No secrets.
- [x] 🤖 `.github/workflows/release.yml`: on a pushed tag `v*`:
  - Android job (`ubuntu-latest`): decode the keystore from a secret into the runner's temp dir, `assembleRelease`
    with `POOLPRINT_SIGNING_*` env vars, verify the certificate, upload the APK.
  - macOS job (`macos-latest`, arm64; add an Intel runner job if 0.5 says so): `packageReleaseDmg`, upload.
  - Release job: checksums, notices, **draft** release with all files. You still publish by hand.
- [x] 👤 Create the `release` environment and add its secrets ([setup below](#setting-up-the-release-environment)):
      `POOLPRINT_KEYSTORE_BASE64` (`base64 -i ~/keys/poolprint-release.jks | pbcopy`),
      `POOLPRINT_SIGNING_STORE_PASSWORD`, `POOLPRINT_SIGNING_KEY_ALIAS`, `POOLPRINT_SIGNING_KEY_PASSWORD`. Protect them
      with the environment's required approval.
      *(2026-11-07, checked with the GitHub API: environment `release` with `yahyafati` as required reviewer,
      deployments limited to tags `v*`, the four secrets as environment secrets and none at repository level,
      default workflow permissions read-only. Whether the values are right only shows in the first run.)*
- [x] Note: on a **private** repo, macOS runner minutes count ×10 against the free quota; on a public repo standard
      runners are free. *(The repo is public.)*

### How the workflows work

Both pin every action to a full commit SHA (version in a comment), use Zulu 21 like
`gradle/gradle-daemon-jvm.properties`, and only read the repo by default (`contents: read`).

**`ci.yml`** runs on pushes to `main` and on pull requests, forks included. It has no secrets. The wrapper
validation matters because a changed `gradle-wrapper.jar` would later also run in the release workflow, next to the
signing key. The Gradle cache comes from `actions/setup-java` (`cache: gradle`).

**`release.yml`** runs on a pushed tag `v*` and reuses the checks of `make release` through `scripts/release.sh`
`ci-*` commands, so both ways check the same things:

1. **prepare** (`ubuntu-latest`): if a release for the tag already exists, everything else is skipped. That's the
   case when you published a draft made by `make release-draft`: publishing creates the tag, which starts this
   workflow. Otherwise `ci-check-tag` requires the tag to be `v<poolprint.versionName>` from `gradle.properties` and
   its commit to be on `main`; then wrapper validation and every test suite.
2. **android** (`ubuntu-latest`, environment `release`, waits for your approval): decodes the keystore into
   `$RUNNER_TEMP` (mode 600), builds `:androidApp:assembleRelease` with the four `POOLPRINT_SIGNING_*` variables
   (only that step sees the passwords; no configuration cache, so they aren't written to disk), deletes the keystore,
   then `ci-check-apk`: release certificate, one signer, package name, `versionCode` and `versionName`. Uploads the
   APK as a workflow artifact (kept 7 days).
3. **macos** (`macos-latest`, Apple Silicon): `:desktopApp:packageReleaseDmg`, then `ci-check-dmg`: refuses a
   non-arm64 runner, mounts the DMG and checks the bundle exactly like `make release` step 5. Uploads the DMG.
4. **release** (`ubuntu-latest`, the only job that may write to the repo): `ci-package` writes
   `build/release/<version>/` like `make release` steps 6–8 (stable names, the APK checked again, notices,
   `SHA256SUMS.txt`, `release-notes.md` from the template with the commits since the last final release, skipping
   the tag being released), then `ci-draft` refuses if any release for the tag exists (drafts included) and runs
   `gh release create v<version> --draft [--prerelease] --verify-tag` with the same four files as `make release-draft`.

No Gradle cache is restored or saved in `release.yml`: a release build shouldn't depend on, or leave anything in, a
cache that other workflow runs share.

### Setting up the release environment

👤 Once, on GitHub (*Settings* of `yahyafati/PoolPrint`):

1. *Environments → New environment* → name it `release`.
2. *Required reviewers*: add yourself, and leave *Prevent self-review* unticked (you are the only reviewer).
3. *Deployment branches and tags → Selected branches and tags → Add rule* → type **Tag**, pattern `v*`.
4. *Environment secrets → Add secret*, four times (environment secrets, not repository secrets, so only the approved
   Android job can read them):

   | Secret | Value |
   |---|---|
   | `POOLPRINT_KEYSTORE_BASE64` | `base64 -i ~/keys/poolprint-release.jks \| pbcopy`, then paste |
   | `POOLPRINT_SIGNING_STORE_PASSWORD` | the keystore password |
   | `POOLPRINT_SIGNING_KEY_ALIAS` | `poolprint` |
   | `POOLPRINT_SIGNING_KEY_PASSWORD` | the key password (with PKCS12 the same as the store password) |

   Afterwards clear the clipboard (e.g. copy something else). Never paste these anywhere else.
5. Optional: *Settings → Actions → General → Workflow permissions* can stay at *Read repository contents*; the
   release job asks for `contents: write` itself.

### Releasing with the workflow

Instead of step 3 of the [patch routine](#patch-release-routine) (use one way per version, not both):

1. 🤖 Bump the version in `gradle.properties`, commit; 👤 push to `main` and wait for CI to pass.
2. 👤 Tag that commit and push the tag (this starts `release.yml`):
   ```bash
   git tag v1.0.1 && git push origin v1.0.1
   ```
3. 👤 *Actions → Release → the run → Review deployments → release → Approve*.
4. 👤 When it's green, open the draft under *Releases*: edit the notes (*What's new*, *Known limitations*; delete
   the `<!-- Edit: … -->` line), check the files, for an RC keep *Set as a pre-release*, then **Publish**.

If a run fails after the tag was pushed, fix on `main` and release the next version (new tag, `versionCode` +1);
don't move a pushed tag. A failed run that only needs repeating (runner trouble) can be re-run from the Actions page;
`ci-draft` refuses to make a second draft if the first run got that far.

**Checked 2026-11-07** (by the agent, locally; nothing ran on GitHub, since agents don't push or tag): both workflow
files parse as YAML (actionlint couldn't be installed here); the combined test command passes; in a scratch clone
`ci-check-tag` accepts a matching tag on `main` and refuses a wrong tag, an empty one, and a commit not on `main`;
`ci-check-apk` passes the published 1.0.0 APK and refuses the rc.2 APK (wrong `versionCode`); `ci-check-dmg` passes
the 1.0.0 DMG; `ci-package` on the 1.0.0 files reproduces the published `SHA256SUMS.txt` exactly, and the notes skip
the tag on `HEAD` (a `1.0.1-rc.1` tag lists the commits since `v1.0.0`); `ci-draft` (with a stub `gh` that printed
instead of creating) refused `v1.0.0` because the release exists and built the expected `gh release create … --draft
--prerelease --verify-tag` call for `v1.0.1-rc.1`; the DMG build configures without an Android SDK. **Not run:** the
workflows themselves, the keystore decoding, the environment approval and a draft created by the workflow
(👤 first real run, ideally with an RC).

---

## Risks and things to keep an eye on

- **Losing the release keystore** = no more updates for existing Android users without uninstall and re-onboarding.
  Two backups (Phase 1.2).
- **Android developer verification.** Google is rolling out a requirement that apps installed on certified Android
  devices come from a registered developer, including sideloaded ones, country by country. Before each release,
  check whether it applies to Germany yet and what it asks of a hobby developer (registration, package name, signing
  key). It would not change the APK, only add a registration step for you.
- **Gatekeeper and Keychain on macOS** stay slightly awkward while the app is ad-hoc signed (0.4).
- **Untested remote behaviour.** Anything not on the blocking list goes into *known limitations*, plainly worded.
- **Expectations.** The app is vibe-coded and shared as is. Keep the notice prominent and don't promise support,
  fixes or timelines anywhere (README, notes, issue replies).
- **Users posting secrets.** Issue template and `SECURITY.md` remind them not to post keys, usernames or fingerprints.

## Prompts for the agent

Copy into a new agent session when you get to a phase:

- *"Read docs/release.md. Record my Phase 0 answers in decisions.md as D-22/D-23: …"*
- *"Do the agent tasks of Phase 1.4 in docs/release.md. The signing keys are in local.properties; don't print their values."*
- *"Do Phase 2 of docs/release.md."*
- *"Do Phase 3 of docs/release.md up to the owner review; report the secret-scan findings, don't rewrite history."*
- *"Do Phase 4 of docs/release.md. The release certificate SHA-256 is …"*
- *"Here are my Phase 6 results: … Update the roadmap and decisions, fix what's blocking, prepare the next RC."*
- *"Prepare v1.0.0 per Phase 7 of docs/release.md."*
- *"Do Phase 9 of docs/release.md."* · *"Bump to 1.0.1 for a release through the workflow (Phase 9)."*
