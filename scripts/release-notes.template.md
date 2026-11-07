Pool Print {{VERSION}}. Print PDFs on the Uni Freiburg TF pool printers from Android or macOS.
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
| `THIRD_PARTY_NOTICES.md` | Licences of the bundled libraries |

Install steps: see the README → Install. Off campus you need the university VPN.

## What's new
<!-- Edit: commits since {{SINCE}}, pre-filled by `make release`. Keep what a user would notice. -->
{{CHANGES}}

## Known limitations
- Not yet checked against the real server: …
- macOS: unsigned (ad-hoc) build; use *Open Anyway* on first launch. Updates may ask for Keychain access once.
- No automatic update check (on purpose: the app only talks to the pool server). Watch this repo's releases.

## Verify
- SHA-256 checksums: `SHA256SUMS.txt`
- Android signing certificate SHA-256: `{{CERT_SHA256}}`
