{{NOWIN}}Pool Print {{VERSION}}. Print PDFs on the Uni Freiburg TF pool printers from Android or macOS.
{{WIN}}Pool Print {{VERSION}}. Print PDFs on the Uni Freiburg TF pool printers from Android, macOS or Windows.
{{PRERELEASE_NOTE}}

> ⚠️ **Vibe-coded, use at your own risk.** This app was written largely by AI coding agents and has not been
> audited. It is shared **as is, without any warranty or guarantee**: it may contain bugs, misprint, waste
> print budget, or mishandle your SSH key. You alone are responsible for using it on your pool account.
> Unofficial; not affiliated with the University of Freiburg, the TF pool administration or the Fachschaft.

## Downloads
| File | For |
|---|---|
| `{{APK}}` | Android 10+ |
| `{{DMG}}` | macOS 12+ on Apple Silicon |
{{WIN}}| `{{MSI}}` | Windows 10/11, 64-bit (x64) |
| `THIRD_PARTY_NOTICES.md` | Licences of the bundled libraries |

Install steps: see the README → Install. Off campus you need the university VPN.

{{NOWIN}}**This release has no Windows download.**
{{WIN}}## Windows install
{{WIN}}1. Download `{{MSI}}` and, if you like, compare it with `SHA256SUMS.txt` (PowerShell: `Get-FileHash .\{{MSI}}`).
{{WIN}}2. Double-click it. The installer is **not signed**, so Windows SmartScreen shows *"Windows protected your PC"*:
{{WIN}}   click **More info → Run anyway**. This is expected for this app.
{{WIN}}3. It installs for your user only (no administrator password) and adds a **Pool Print** entry to the Start menu and
{{WIN}}   a desktop shortcut.
{{WIN}}4. Open a PDF with **Ctrl+O**, by dragging it onto the window, or in Explorer: right-click the PDF → **Open with →
{{WIN}}   Choose another app → Pool Print**. The installer does *not* make Pool Print your default PDF app.
{{WIN}}5. Updating: run the newer `.msi`; it replaces the old version in place. A release candidate and its final release
{{WIN}}   share one installer version, so uninstall the old one first (Settings → Apps) when going from `-rc.N` to the final
{{WIN}}   release. Settings and your key stay.
{{WIN}}6. Where things are: settings, presets and the pinned server key in `%LOCALAPPDATA%\Pool Print`; the SSH key in
{{WIN}}   Windows **Credential Manager** (Windows Credentials → *Pool Print*). Uninstalling leaves both; delete them yourself
{{WIN}}   to remove everything (you would then onboard again).

## What's new
<!-- Edit: commits since {{SINCE}}, pre-filled by `make release`. Keep what a user would notice. -->
{{CHANGES}}

## Known limitations
- Not yet checked against the real server: …
- macOS: unsigned (ad-hoc) build; use *Open Anyway* on first launch. Updates may ask for Keychain access once.
{{WIN}}- Windows: unsigned installer (SmartScreen warning, see above). The Windows build is new and has had less testing than Android and macOS: …
- No automatic update check (on purpose: the app only talks to the pool server). Watch this repo's releases.

## Verify
- SHA-256 checksums: `SHA256SUMS.txt`
- Android signing certificate SHA-256: `{{CERT_SHA256}}`
