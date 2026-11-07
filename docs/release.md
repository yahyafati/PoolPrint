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
| [3](#phase-3--repository-ready-to-go-public) | Secret scan, licence, third-party notices, README install guide | 🤖 prepare · 👤 approve |
| [4](#phase-4--release-script) | `make release`: tests, signed APK, DMG, checks, checksums, notes draft | 🤖 |
| [5](#phase-5--release-candidate) | Build `v1.0.0-rc.1`, put it on a draft/pre-release | 👤🤖 |
| [6](#phase-6--test-the-candidate-release-gate) | Install the downloaded RC on real devices; run the release gate | 👤 |
| [7](#phase-7--publish-v100) | Tag and publish `v1.0.0` | 👤🤖 |
| [8](#phase-8--after-the-release) | Verify, update docs, patch-release routine | 👤🤖 |
| [9](#phase-9-optional--automate-with-github-actions) | *(optional)* Build releases in GitHub Actions | 🤖 workflow · 👤 secrets |

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
poolprint.signing.storeFile=/Users/yahyahaji/keys/poolprint-release.jks
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
anything else fails the build with the property name. `macPackageVersion` drops the `-rc.N`. Its tests are
`./gradlew :buildSrc:test`, part of `make test-all`. Android passes `BuildConfig.VERSION_NAME` into `App()`; the desktop
build generates `BuildInfo.VERSION_NAME` (`:desktopApp:generateBuildInfo`), so the label is right under `run` and
`hotRun` too. Settings → About shows "Pool Print <version>" with the vibe-coded line and the privacy line under it.

Checked 2026-11-07: with the defaults the APK is `versionCode 2` / `versionName 1.0.0-rc.1` and the bundle and DMG
are `1.0.0` (`Pool Print-1.0.0.dmg`); with `-Ppoolprint.versionName=1.2.3-rc.4 -Ppoolprint.versionCode=7` they become
`7` / `1.2.3-rc.4`, `Pool Print-1.2.3.dmg` and `BuildInfo.VERSION_NAME = "1.2.3-rc.4"`; `0.9.0` fails the build.
Not yet looked at on screen: the About row itself (👤 open Settings once on each platform).

---

## Phase 3 — Repository ready to go public

🤖 **Agent prepares**, 👤 **owner reviews and approves.**

- [ ] **Secret and privacy scan of the whole history**, not just `HEAD`: private-key headers
      (`BEGIN … PRIVATE KEY`, `openssh-key-v1`), `.jks`/`.keystore` files, `local.properties`, your real pool username,
      real `lpq` owners, IP addresses. If the agent may install a tool, `gitleaks detect --log-opts="--all"` is the
      quick way; otherwise `git log -p --all | grep -nE '…'`. The agent reports findings; **you** decide whether
      anything needs a history rewrite (that would be done before going public).
      *(The server host-key fingerprint in `decisions.md`, open question 5, is public server information and fine.)*
- [ ] **`LICENSE`**: the MIT licence (0.2, D-22), copyright holder Yahya Fati, and a licence line in the README.
- [ ] **`THIRD_PARTY_NOTICES.md`**: the libraries that ship inside the APK/DMG and their licences (sshj, BouncyCastle,
      PDFBox, JNA, kotlinx, AndroidX/Compose, and what the release build actually bundles; test-only MINA SSHD is not
      shipped). Apache-2.0 requires passing on their licence and NOTICE texts with binary redistributions. Attach it
      to every release.
- [ ] **"Vibe-coded, as is" notice**, worded like the box in the [release notes template](#release-notes-template),
      in three places: right under the README title (before any feature list or download link), at the top of
      every release's notes, and in the app's **Settings → About** next to the version (Phase 2), with a link to the
      licence. The MIT licence's own no-warranty clause is the legal part; this notice is the
      plain-language one people actually read. Extend the README's existing *Disclaimer* section to match.
- [ ] **README rewrite of the top**: real status, a **Download** link to
      `https://github.com/yahyafati/PoolPrint/releases/latest`, and an **Install** section (text below). Building
      from source moves further down.
- [ ] **`SECURITY.md`** (short): how to report a vulnerability privately (GitHub's *Report a vulnerability*), and a
      reminder never to post keys, usernames or fingerprints in issues.
- [ ] **Bug-report issue template** asking for app version, platform/OS version, what happened, and the error's
      *details* text, with the same privacy reminder.
- [ ] 👤 After reviewing the scan: GitHub → *Settings → General → Danger Zone → Change visibility → Public*. Then
      *Settings → Code security*: enable **private vulnerability reporting** and **secret scanning**.

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

- [ ] `make release` (in the `Makefile`, the logic in `scripts/release.sh` if it gets long). It must:
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
- [ ] `make release-draft`: runs `gh release create v<version> --draft [--prerelease] --title … --notes-file …`
      with the files above. Draft releases are visible only to you, so this is safe to run; **publishing** stays a
      separate, manual click or command.
- [ ] Document both targets in `make help`, `CLAUDE.md` and `development.md`.

The agent can test everything up to the APK certificate check without your key (it will fail the check, as
intended, on a debug-signed build).

**Done when:** on your Mac, `make release` produces the folder with two binaries, notices, checksums and notes.

---

## Phase 5 — Release candidate

- [ ] 🤖 Version is `1.0.0-rc.1` / `versionCode 2` (Phase 2); notes and README are current; commit on `main`
      (or a `release/1.0.0` branch you merge).
- [ ] 👤 Run on your Mac:
      ```bash
      make release
      ```
- [ ] 👤 Read `build/release/1.0.0-rc.1/release-notes.md`, edit it, then:
      ```bash
      make release-draft
      ```
- [ ] 👤 On GitHub, open the draft, check the files and the text, tick **Set as a pre-release**, and **Publish**.
      Publishing creates the tag `v1.0.0-rc.1` on the commit you built. (If you prefer to keep the RC private, leave it
      as a draft and download the files while logged in to GitHub.)

**Done when:** `v1.0.0-rc.1` exists as a pre-release with both binaries and `SHA256SUMS.txt`.

---

## Phase 6 — Test the candidate (release gate)

👤 **Owner.** Test **the downloaded files**, not your build folder: only a downloaded file goes through Gatekeeper,
quarantine and Android's install-from-unknown-sources path, which is what users will see.

### 6.1 Install like a user

- [ ] Android: download the APK in the phone's browser → install (allow the source, Play Protect prompt) → app opens.
- [ ] Android: `apksigner` fingerprint of the downloaded APK matches the release notes (or check on the Mac).
- [ ] macOS: download the DMG in Safari → `shasum -a 256 -c SHA256SUMS.txt` (in Downloads, ignoring the missing APK
      line) → drag to Applications → *Open Anyway* flow works → app opens. Ideally also on a second user account or Mac.

### 6.2 Blocking checks (must pass for 1.0.0)

From the [roadmap](roadmap.md) checklists, the ones a new user hits on day one:

- [ ] Onboarding with **copy the key** → add it on the pool account → Test connection ✓ (M1).
- [ ] Onboarding with **"Add it for me with my pool password"** → Test connection ✓; one new line in
      `authorized_keys`; wrong password gives the right message and adds nothing (M4).
- [ ] The fingerprint shown on first connect matches `ssh-keygen -lF login.informatik.uni-freiburg.de` (M1).
- [ ] Android: **share a PDF from another app** → print to a b/w printer → correct paper output (M1).
- [ ] macOS: print via **⌘O / Pick a PDF** and via **drag and drop** (M1, M4).
- [ ] Copies, duplex (long edge), 2-up: output correct (M2).
- [ ] Page count and cost show in the packaged mac app and on Android (M2, M4).
- [ ] Restart the app / reboot the phone → print again → **no password or passphrase prompt** (M1).
- [ ] Off campus without VPN → "Connect to the university VPN" message, not a crash or cryptic error (M1).
- [ ] Nothing left in the pool home directory after printing (`ls -la ~` unchanged) (M1).
- [ ] Discovery failing (e.g. VPN off then on) doesn't block printing (M3).

### 6.3 Update path (needs a second build)

- [ ] 🤖 Bump to `1.0.0-rc.2` / `versionCode 3` (even with no code change) and 👤 build and pre-release it.
- [ ] Android: install rc.2 **over** rc.1 → still onboarded, settings kept.
- [ ] macOS: replace the app in Applications → note whether a Keychain prompt appears and whether *Always Allow*
      sticks (D-04, M4 checklist).

### 6.4 Report

Send the agent the results (ticked list, failures with the error *details* text, the real `lpstat`/`lpoptions`/`lpq`
output if you captured it). 🤖 The agent ticks the roadmap checklists, records answers in `decisions.md` (D-06,
D-07, open questions), fixes blocking bugs (→ another RC), and writes the **known limitations** for the notes:
every non-blocking check that's still open.

**Done when:** every blocking check passes on an RC built from the commit you're about to release.

---

## Phase 7 — Publish v1.0.0

- [ ] 🤖 Set `poolprint.versionName=1.0.0` and bump `versionCode`; finalise the notes (changes, known limitations,
      certificate fingerprint); update the README status. Commit.
- [ ] 👤 Build and draft:
      ```bash
      make release
      ```
      ```bash
      make release-draft
      ```
- [ ] 👤 On GitHub: review the draft, **untick** pre-release, tick **Set as the latest release**, Publish.
- [ ] 👤 Pull the tag GitHub created, so your checkout knows it:
      ```bash
      git pull --tags
      ```

The binaries in `1.0.0` must come from this build, not from an RC: the `versionCode` and version label differ.

### Release notes template

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

Install steps: see the README → Install. Off campus you need the university VPN.

## What's new
- …

## Known limitations
- Not yet checked against the real server: …
- macOS: unsigned (ad-hoc) build; use *Open Anyway* on first launch. Updates may ask for Keychain access once.
- No automatic update check (on purpose: the app only talks to the pool server). Watch this repo's releases.

## Verify
- SHA-256 checksums: `SHA256SUMS.txt`
- Android signing certificate SHA-256: `…`
```

**Done when:** `https://github.com/yahyafati/PoolPrint/releases/latest` shows 1.0.0 with all files.

---

## Phase 8 — After the release

- [ ] 👤 From a logged-out browser (if public): the release page, downloads and README links work.
- [ ] 🤖 Roadmap: R1 → ✅ (or 🔶 with what's left); release certificate fingerprint filled in above; the
      `poolprint.versionName` on `main` bumped to the next development version if you like (e.g. `1.0.1`).
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
3. 👤 `make release` → `make release-draft` → review → Publish. Use an RC first when the change touches SSH,
   key storage, onboarding or command building (things only the real server can confirm).
4. 👤 Smoke test the downloaded files (install over the previous version, print one page).

---

## Phase 9 (optional) — Automate with GitHub Actions

Worth it once releases become routine or you want an Intel build. Trade-off: your signing key then lives in GitHub
secrets.

- [ ] 🤖 `.github/workflows/ci.yml`: on push/PR, `./gradlew :shared:jvmTest :shared:testAndroidHostTest` on
      `ubuntu-latest`.
- [ ] 🤖 `.github/workflows/release.yml`: on a pushed tag `v*`:
  - Android job (`ubuntu-latest`): decode the keystore from a secret into the runner's temp dir, `assembleRelease`
    with `POOLPRINT_SIGNING_*` env vars, verify the certificate, upload the APK.
  - macOS job (`macos-latest`, arm64; add an Intel runner job if 0.5 says so): `packageReleaseDmg`, upload.
  - Release job: checksums, notices, **draft** release with all files. You still publish by hand.
- [ ] 👤 Add repository secrets: `POOLPRINT_KEYSTORE_BASE64` (`base64 -i ~/keys/poolprint-release.jks | pbcopy`),
      `POOLPRINT_SIGNING_STORE_PASSWORD`, `POOLPRINT_SIGNING_KEY_ALIAS`, `POOLPRINT_SIGNING_KEY_PASSWORD`. Protect them
      with an environment (`release`) that requires your approval.
- [ ] Note: on a **private** repo, macOS runner minutes count ×10 against the free quota; on a public repo standard
      runners are free.

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
