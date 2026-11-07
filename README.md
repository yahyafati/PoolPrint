# Pool Print

> ⚠️ **Vibe-coded, use at your own risk.** This app was written largely by AI coding agents and has not been
> audited. It is shared **as is, without any warranty or guarantee**: it may contain bugs, misprint, waste
> print budget, or mishandle your SSH key. You alone are responsible for using it on your pool account.
> Unofficial; not affiliated with the University of Freiburg, the TF pool administration or the Fachschaft.

Print PDFs on the **Uni Freiburg TF computer-pool printers** from your Android phone or Mac in a few
taps, without opening a terminal.

> **Status: preparing the first release (1.0.0).** All planned features (milestones 1–4) are built and
> unit-tested. Parts of them have not been checked against the real pool server yet; each release's notes list
> what is known not to work or not yet verified. Progress: [docs/roadmap.md](docs/roadmap.md).

## Download

**[Latest release](https://github.com/yahyafati/PoolPrint/releases/latest)**: an APK for Android and a `.dmg`
for macOS on Apple Silicon, with SHA-256 checksums. There is no app store version and no automatic update
check; to hear about new versions, use *Watch → Custom → Releases* on this repository.

## Install

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

Then set up your key once: see [Setup](#setup-key-onboarding). Off campus you need the university VPN.

## What it does

Pool Print wraps the pool's official SSH printing procedure:

```bash
cat file.pdf | ssh <POOL_USERNAME>@login.informatik.uni-freiburg.de "lpr -P tfppr1"
```

and adds a friendly UI, full control over the printing options, and a cost estimate before you print.

- 📲 **Share to print:** share a PDF from any Android app, or drop it onto the Mac window.
- 🔑 **Key-only login:** the app generates an SSH key per device. Your pool password is never stored; it's
  only asked for if you let the app add its key for you during setup, and used once for that.
- 🖨️ **All the options:** printer, copies, page ranges, duplex, pages per sheet, orientation, fit to page,
  paper size, plus printer-specific options read from the server.
- 💶 **Know the cost up front:** `Print · 12 pages · 0.60 €` (5 ct b/w, 10 ct colour, from your 6 € monthly budget).
- 🚫 **No server, no cloud, no tracking:** your device talks only to the pool login server.

## Printers

| Name | Type | Model |
|---|---|---|
| `tfppr1` | b/w | Ricoh MP 3554 |
| `tfppr2` | colour | HP Color LaserJet Enterprise M553 |
| `tfppr3` | b/w | HP LaserJet Enterprise MFP M528 |
| `tfppr4` | colour | HP Color LaserJet Enterprise M653 |

## How it works

```mermaid
sequenceDiagram
    participant U as You
    participant A as Pool Print
    participant S as login.informatik.uni-freiburg.de
    U->>A: Share / pick / drop a PDF
    A->>A: Count pages, apply options, estimate cost
    U->>A: Tap "Print · 12 pages · 0.60 €"
    A->>S: SSH (Ed25519 key, pinned host key)<br/>exec: lpr -P tfppr1 -o sides=… …
    A->>S: Stream PDF bytes into lpr's stdin
    S-->>A: exit status + stderr
    A-->>U: "Sent to tfppr1 · job 1234" or a readable error
```

1. The PDF never gets copied to your pool home directory. It's streamed straight into `lpr`.
2. The remote command is built only from validated values (enums, numbers, known printer and option
   names), and every argument is shell-quoted. Filenames never go into the command.
3. On the first connection, the server's host key fingerprint is shown and pinned. If it ever changes,
   the app refuses to connect and warns you.
4. Optionally (milestone 3), the app runs `lpstat -p` and `lpoptions -p <printer> -l` to discover
   printer-specific options. If that fails, it uses the built-in printer list and standard options.

More detail: [docs/architecture.md](docs/architecture.md) · [docs/security.md](docs/security.md) ·
[docs/print-options.md](docs/print-options.md).

## Setup: key onboarding

You need a TF pool account and a way to log in to it **once** to install the key: a pool computer, or
`ssh` with your password from a laptop.

1. Open Pool Print and enter your pool username.
2. The app generates an **Ed25519 key pair** and shows the public key. Tap **Copy** (or Share it to yourself).
3. Log in to your pool account and add the key:
   ```bash
   mkdir -p ~/.ssh && chmod 700 ~/.ssh
   echo 'ssh-ed25519 AAAA… pool-print@your-device' >> ~/.ssh/authorized_keys
   chmod 600 ~/.ssh/authorized_keys
   ```
   Or let the app do it: **Add it for me with my pool password** logs in once with your password, appends
   the key and forgets the password (it is not saved). Already printing from another device? Paste the new
   key there under **Settings → Add a device** instead.
4. Back in the app, check the server fingerprint and accept it. To compare, on a machine that already
   trusts the server, run:
   ```bash
   ssh-keygen -lF login.informatik.uni-freiburg.de
   ```
5. Tap **Test connection**. It runs `echo ok` on the server. ✓ means you're ready to print.

> **Off campus?** The login server is only reachable from the university network. Connect to the
> university VPN first, otherwise you'll get "Can't reach the pool server".

After onboarding, the app never asks for a password or passphrase again.

Where the private key is kept:
- **Android:** encrypted with a key held in the Android Keystore, and excluded from backups.
- **macOS:** in the login Keychain (item "Pool Print"). Unsigned builds may ask once after an update to
  allow Keychain access: choose **Always Allow**.

To revoke access, delete the key's line from `~/.ssh/authorized_keys` on the pool account.

## Building

Requirements: a JDK (the Gradle daemon is pinned to JDK 21, which the toolchain resolver can download); the Android SDK for the
Android app; macOS for building the `.dmg`.

### Android

```bash
./gradlew :androidApp:assembleDebug
```

The APK ends up in `androidApp/build/outputs/apk/debug/`. To install it on a connected device or emulator:

```bash
./gradlew :androidApp:installDebug
```

### Desktop (macOS)

Run:

```bash
./gradlew :desktopApp:run
```

Run with hot reload:

```bash
./gradlew :desktopApp:hotRun --auto
```

Build the `.dmg` installer:

```bash
./gradlew :desktopApp:packageDmg
```

The output goes to `desktopApp/build/compose/binaries/main/dmg/`. The app is only ad-hoc signed, so a
downloaded copy needs **Open Anyway** on first launch (see [Install](#install)). On the Mac you can drag a PDF onto the
window, use Finder's Open With → Pool Print, or press ⌘O; ⌘P prints from the print sheet.

### Tests

```bash
./gradlew :shared:jvmTest
```

```bash
./gradlew :shared:testAndroidHostTest
```

The tests use a fake SSH client and an in-process SSH server on loopback; they never contact the real pool
server. One extra test touches the macOS login keychain and only runs with `POOLPRINT_KEYCHAIN_TEST=1`.

## Project structure

```
shared/        all app logic and Compose UI (Kotlin Multiplatform)
  commonMain     models, validation, command builder, cost estimator, parsers, UI
  jvmCommonMain  SSH client (sshj), Ed25519 key generation (shared by Android and desktop)
  androidMain    Android Keystore, PDF page count, intents
  jvmMain        macOS Keychain, PDF page count, file handling
androidApp/    thin Android shell (manifest, MainActivity, share target)
desktopApp/    thin Compose Desktop shell (window, macOS packaging)
docs/          design documentation
```

## Documentation

| Document | Contents |
|---|---|
| [Project Overview](docs/Project%20Overview.md) | Product description, goals, constraints, environment facts |
| [Architecture](docs/architecture.md) | Modules, source sets, interfaces, data flow, platform layer |
| [Print options](docs/print-options.md) | CUPS option mapping, validation, discovery parsing, cost formula |
| [Security](docs/security.md) | Threat model, key storage, host-key pinning, safe command construction |
| [UI / UX](docs/ux.md) | Screens, main flow, error messages |
| [Roadmap](docs/roadmap.md) | Milestones, acceptance criteria, manual test checklists |
| [Decisions](docs/decisions.md) | Decision log and open questions |
| [Development guide](docs/development.md) | Contribution rules, testing strategy, milestone reports |
| [Release guide](docs/release.md) | How a sideloaded GitHub release is built, tested and published |

## Privacy

Pool Print has no analytics, telemetry or crash reporting, and it only talks to
`login.informatik.uni-freiburg.de`. Your SSH key stays on your device.

## Licence

[MIT](LICENSE) © Yahya Fati. The apps also contain third-party libraries under their own licences:
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Security

Found a vulnerability? Report it privately, see [SECURITY.md](SECURITY.md). Never post keys, pool usernames or
fingerprints in public issues.

## Disclaimer

Pool Print is **vibe-coded**: written largely by AI coding agents, and not audited by anyone. It is provided
**as is, without any warranty or guarantee** (see the [licence](LICENSE)). It may contain bugs, misprint, waste
print budget, or mishandle your SSH key; you alone are responsible for using it on your pool account. Nobody
promises support, fixes or timelines.

This is an unofficial student project. It isn't affiliated with the University of Freiburg, the TF
pool administration or the Fachschaft. The printing procedure it uses is the one described at
<https://fachschaft.tf/en/service/pool_printing/>.
