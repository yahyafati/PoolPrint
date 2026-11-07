# Project Overview — Pool Print

> Product name: **Pool Print**. Gradle project `PoolPrint`, package `com.yahyafati.poolprint`
> (see [decisions.md](decisions.md#d-08-product-name-vs-repository-name)).

## What it is

Pool Print is a remote-printing app for the computer pool of the Faculty of Engineering (TF) at the
University of Freiburg. It lets a student print a PDF from their Android phone, Mac or Windows PC in a few taps,
while still controlling all the printing options.

## The problem

The pool only offers remote printing over SSH. The
[official procedure](https://fachschaft.tf/en/service/pool_printing/) comes down to:

```bash
cat file.pdf | ssh <POOL_USERNAME>@login.informatik.uni-freiburg.de "lpr -P tfppr1"
```

That works on a laptop with a terminal and some CUPS knowledge. It does not work on a phone, and
nobody remembers `-o sides=two-sided-long-edge -o number-up=2`. Pool Print puts a friendly UI on
this same command and changes nothing on the server side.

## Users

- **Primary:** the author, a TF student with a pool account, printing lecture slides, exercise sheets
  and papers from an Android phone or a MacBook (a Windows PC is being added, D-25).
- **Secondary:** other TF students. Each student uses their own pool account and SSH key. There is no
  shared infrastructure.

## Environment facts

| Item | Value |
|---|---|
| SSH host | `login.informatik.uni-freiburg.de` (port 22) |
| Network | Reachable on the university network. **Off campus, only through the university VPN.** |
| Username | the student's pool (TF) login |
| Print command | `lpr -P <printer>`, reads the document from stdin |
| Queue | `lpq -P <printer>` |
| Cancel | `lprm -P <printer> <jobId>` (own jobs only) |
| Budget query | `lpquota` exists but **the Fachschaft page says it doesn't currently work**. The budget is shown at `https://poolcups.informatik.intra.uni-freiburg.de` (intranet). |
| Budget | 6 € per month |
| Price | 5 ct per b/w page, 10 ct per colour page |

### Printers

| Name | Type | Model | Speed |
|---|---|---|---|
| `tfppr1` | b/w | Ricoh MP 3554 | 35 ppm |
| `tfppr2` | colour | HP Color LaserJet Enterprise M553 | 38 ppm |
| `tfppr3` | b/w | HP LaserJet Enterprise MFP M528 | 45 ppm |
| `tfppr4` | colour | HP Color LaserJet Enterprise M653 | 56 ppm |

This table is the **hardcoded fallback** the app ships with. When dynamic discovery
(`lpstat` / `lpoptions`) works, it adds printer-specific options on top. It never replaces the
b/w vs colour classification, because that drives the cost estimate.

## Goals

1. Print a shared or picked PDF in **at most three taps after sharing it**: confirm the
   printer/preset, then Print.
2. Give full control over the standard CUPS options, plus printer-specific options found on the server.
3. Show the **page count and estimated cost before printing**, e.g. `Print · 12 pages · 0.60 €`.
4. Explain what to do when something goes wrong, instead of just showing an error.
5. Be secure by construction: key-only auth (the password is used at most once, to install the key), pinned host
   key, injection-proof command building.

## Non-goals and hard constraints

These are **not negotiable** without an explicit decision from the owner:

- **No backend, no cloud, no web version.** The device talks directly to the pool login server over SSH.
- **No password authentication for regular use.** The app never stores the pool password. Every command
  (test connection, printing, discovery, queue, "Add a device") uses SSH key authentication only; password and
  keyboard-interactive auth are never attempted for them. The one exception is optional: during onboarding the
  user may type the pool password once so the app can append the device key to `authorized_keys` (D-20). That
  login runs only the fixed key-install command, the password is kept in memory for that attempt alone, and
  everything afterwards uses the key.
- **No telemetry, analytics or crash reporting** sent anywhere.
- **No files on the remote side.** The PDF is streamed into `lpr`'s stdin. Nothing is copied to the
  home directory (no `scp`/`sftp`).
- **No filenames in the remote command.** No `-T <title>` or anything else derived from the document
  name goes into the remote command.
- **No secret prompts in regular use.** After onboarding, printing never asks for a password,
  passphrase or unlock. Secrets are stored securely and unlocked implicitly.
- **Targets: Android, macOS desktop and Windows x64 desktop (JVM) only** (Windows added 2026-11-07, D-25). Linux,
  Windows on ARM and app stores are out. The shared core still stays free of platform code, so an iOS or web target
  could be added later.

## Feature scope by milestone

The full breakdown, with acceptance criteria and manual test checklists, is in [roadmap.md](roadmap.md).

| Milestone | Theme | Highlights |
|---|---|---|
| **M1** | MVP | Onboarding and Ed25519 key generation, test connection, pick/share PDF → printer → print, readable success/error |
| **M2** | Options & cost | All standard options in the UI, local page count and cost estimate, remember last used printer and options |
| **M3** | Power features | `lpoptions` discovery, saved presets, queue view and cancel own jobs |
| **M4** | macOS polish | Drag and drop, "Open With", `.dmg` packaging |
| **W0–W4** | Windows port | Credential Manager key store, single instance and file hand-over, unsigned per-user Msi, Windows build workflow (CI and releases) and release tooling; see [roadmap.md](roadmap.md#w--windows-desktop-port) |

## Documentation map

| Document | Read it for |
|---|---|
| [README.md](../README.md) | Setup, key onboarding, building, how it works (start here) |
| [architecture.md](architecture.md) | Modules, source sets, components, data flow, key interfaces |
| [print-options.md](print-options.md) | CUPS option mapping, validation rules, discovery parsing, cost formula |
| [security.md](security.md) | Threat model, key storage, host-key pinning, command construction rules |
| [ux.md](ux.md) | Screens, main flow, error message catalogue |
| [roadmap.md](roadmap.md) | Milestones, tasks, acceptance criteria, manual test checklists, status |
| [decisions.md](decisions.md) | Decisions taken and open questions for the owner |
| [development.md](development.md) | How to work on this repo: rules for agents and humans, testing, dependency policy |
