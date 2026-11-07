# UI / UX

Compose Multiplatform, light and dark mode (follows the system). The same screens run on Android and macOS;
desktop shows them as a centred, phone-width column, not a different flow. The look follows the owner's Claude
Design project "Pool Print" (phone screens P01–P22, D-21).

## Visual system

The Modernist design system: flat and architectural, mostly ink on a warm ground.

- **Colour:** one lime accent for the primary action, a deeper green for links and selected states, red only for
  errors and destructive actions, a teal note for the cost warning. Tokens are in `ui/Theme.kt` (`PoolColors`, one
  set for light and one for dark).
- **Shape:** zero corner radius everywhere. Structure comes from rules: a strong 2dp rule between sections, a 1dp
  divider between rows.
- **Type:** heavy (800) headlines, 16–17 sp body, uppercase section labels, monospace for keys, fingerprints,
  usernames and page ranges. The design's fonts (Archivo, JetBrains Mono) are not bundled yet; the platform fonts
  stand in (D-21).
- **Buttons:** full-width blocks with the label flush left and an arrow (→) or retry glyph (↻) at the right edge.
  Primary is lime, secondary is a 2dp outline, destructive is red, the blocking "Close" is ink.
- **Errors:** a 3dp red rule, the warning icon and a headline, the next step under it, inline actions in the accent
  colour, and the raw stderr behind **Details ▾**.
- **Confirmations:** a panel over a scrim with a 4dp top rule (red for destructive), the question, a red action and
  an outlined "keep" button. Tapping the scrim cancels.
- Icons are Lucide (copy, share, refresh, warning, check).

## Principles

- **Three taps after sharing:** share PDF → (sheet opens with the last-used or preset options) →
  confirm the printer/preset if needed → **Print**. The common case is a single tap on Print.
- **Cost before commitment:** the Print button always shows the page count and cost:
  `Print · 12 pages · 0.60 €`, with the rate ("B/W · 5 ct per page") above it.
- **Simple by default:** preset, printer, copies and two-sided are visible. Everything else sits under
  an expandable **Advanced options** row.
- **Errors say what to do next**, in plain language, with the raw stderr behind "Details".

## Screens

### Onboarding (first run)

A four-segment progress bar with "← Back" and "Step n of 4" runs across the top.

1. **Welcome (P01):** the app mark, "Print PDFs straight to the pool printers.", and three numbered points: the
   password is never stored (the app makes its own key), the key is added to the pool account once, off campus you
   need the VPN. **Get started**.
2. **Pool username (P02):** "What's your pool username?", a monospace field with the hint "Lowercase, e.g. ab1234.
   Not your email.", validated inline. **Continue**.
3. **Your key (P03):** the app generates an Ed25519 key. Three numbered steps:
   1. **Copy this snippet**, a monospace box with the public key filled in, plus **Copy snippet** and
      **Send to…** (the share sheet; Android only):
      ```
      mkdir -p ~/.ssh && chmod 700 ~/.ssh
      echo '<public key>' >> ~/.ssh/authorized_keys
      chmod 600 ~/.ssh/authorized_keys
      ```
   2. At a pool computer, open Terminal, paste, press Enter (or over SSH with the password from a laptop).
   3. Come back and test the connection.

   **Show only the public key ▾** reveals the bare key with Copy / Share, and the hint to paste it on another
   device under Settings → Add a device. **I've added it · Test** goes to step 4 and starts the test.
   **Add it for me with my pool password** opens 3a.

   3a. **Add the key with your password (optional, D-20):** explains that the app logs in once, adds this device's
   key to `~/.ssh/authorized_keys` and forgets the password. One secure password field (hidden, no copy), the VPN
   note and **Add key**. On the first connection the trust screen appears first; the password is only sent after
   **It matches · Trust**. On success it moves to step 4 and runs the test with the key. A wrong password shows the
   "didn't accept the password" error and the field is empty again.
4. **Test connection (P04–P07):** runs `echo ok`. While it runs: "Testing connection…", the VPN note, a progress
   bar and **Cancel**.
   - First connection: the full-screen trust prompt (P05, see below), then the test runs again.
   - Success (P06): a check mark, "You're set.", "Connected as ab1234.", a note that next time a PDF can simply be
     shared or opened with Pool Print, and **Start printing**.
   - Failure (P07): "Almost there" and the catalogue error. For an unauthorized key the actions are **Show
     snippet** (back to step 3) and **Test again**.

After onboarding, nothing asks for a password or passphrase again (A-11). The password in 3a is never stored.

### Trust the server (P05)

Shown full-screen on the first connection, from onboarding, printing or the queue. "Is this the pool server?",
how to compare (`ssh-keygen -lF` on a computer that already trusts the server), the host and key type, and the
fingerprint in large monospace, grouped in fours between two rules. **It matches · Trust** pins it; **Cancel** pins
nothing (see [security.md](security.md#host-key-verification-tofu)).

### Server identity changed (P22)

Blocking and full-screen, in red: "Server identity changed. Stopped.", that someone may be intercepting the
connection, **Nothing was sent.**, the trusted and the received fingerprint, and what to do (ask the pool admins;
only if they confirm, forget the fingerprint in Settings). **Close** and **Copy details for admins**. There is
deliberately no way to continue.

### Home (P08)

- Top bar "Pool Print" with a "Ready" status mark.
- "Print a PDF", "Share one from any app, or pick it here.", **Pick a PDF** (Android and macOS; on macOS a PDF can
  also be dropped anywhere on the window, and ⌘O opens the file dialog).
- Rows: **Queue** (See what's printing), **Presets** (n saved), **Settings** (username · key, server, printers).
- Footer: how many printers are known and when the list was last fetched.

### Print sheet (P09–P14, main flow)

Opens when a PDF arrives (share, pick, drop, Open With). A × closes it; **Queue** opens the queue and returns to
the sheet with its options intact.

```
┌──────────────────────────────────────────┐
│ ×                                  Queue │
│ ▯ lecture-05.pdf                         │
│   24 pages · 2.9 MB                      │
│══════════════════════════════════════════│
│ Preset                                   │
│ Lecture slides ▾          Save as preset │
│──────────────────────────────────────────│
│ Printer                                  │
│ tfppr1 [◧ B/W]                         › │
│ Idle                                     │
│──────────────────────────────────────────│
│ Copies                     [−]  1  [+]   │
│──────────────────────────────────────────│
│ Two-sided                          [■ ✓] │
│ 12 sheets                                │
│──────────────────────────────────────────│
│ Advanced options                       ▾ │
│ All pages · 1 per sheet · A4             │
│══════════════════════════════════════════│
│                                          │
│ B/W · 5 ct per page                      │
│ [ Print · 24 pages · 1.20 €          → ] │
└──────────────────────────────────────────┘
```

- **Printer** opens a menu of the known printers. The row shows a type badge (B/W, Colour, Unknown) and, with
  discovery, the printer's state. An unknown type says it is billed as colour.
- **Two-sided** is a switch (long edge). With it on, Advanced options shows **Flip on: Long edge / Short edge**.
- **Advanced options** expands in place: Pages per sheet (1 / 2 / 4), **Pages** (page ranges, monospace),
  Orientation (Portrait / Landscape), Fit to page, Paper size, and a "From tfppr1" section with the printer's
  discovered options. An invalid page range shows an inline error, keeps the section open, greys out Print and
  says "Fix the page range to print" above it.
- Over the warning threshold, a teal note above the button: "This job costs about 3.60 €. Check the copies and
  options before you print."
- **Sending (P12):** the document, "Sending to tfppr1…", a thick progress bar with "1.8 of 2.9 MB" and the
  percentage, and **Cancel**.
- **Sent (P13):** a check mark, "Sent to tfppr1 · job 1234." (the job id when parsed), "Collect it at the
  printer.", the document and the cost, **Done** and **View queue**.
- **Error (P14):** the sheet stays editable; the catalogue error sits above the button, which becomes
  "Try again · 12 pages · 0.60 € ↻".

### Queue (P15–P17, M3)

Top bar with ← and a refresh icon. A printer picker (name and type badge), then "n jobs" and the `lpq` status
line, then the entries: position, file label, owner · size · Printing/Waiting. Own jobs are highlighted, tagged
YOU, show the job id and have **Cancel**, which asks first (P16: "Cancel this job?", **Cancel job 1234** /
**Keep it**). While loading: a progress bar and placeholder lines. Empty: "Nothing in the queue". Errors use the
catalogue with **Refresh**. The result of a cancel shows in a bar at the bottom. Pull-to-refresh is not built.

### Presets (P18–P19, M3)

A list of named option sets, each with a summary such as "tfppr1 · B/W · two-sided · 2 per sheet" and a ⋮ menu:
**Rename** edits the name in place (Save / Cancel); **Delete** asks first. Empty state: "No presets yet", with how
to make one. Presets are saved from the print sheet ("Save as preset") and stored as in D-15: the printer, layout
options and changed discovered options, but no copies and no page ranges.

### Settings (P20–P21)

Sections, each opened by a strong rule and an uppercase label:

- **Account:** the pool username (monospace).
- **This device's key:** the public key, **Copy** and **Share**; **Add a device** (expands the form below);
  **Regenerate key…** (in red; confirms with "Make a new key?", **Regenerate key** / **Keep current key**, then
  onboarding runs again).
- **Server:** the trusted fingerprint with **Forget** (confirms first), or "Not pinned yet".
- **Printers:** **Refresh printers**, with the known count, the last update and the outcome of the last refresh.
- **About:** "Pool Print <version>" (the full name, e.g. `1.0.0-rc.1`), then the vibe-coded, as-is, no-warranty line
  and "no backend, no telemetry".

**Add a device:** each device has its own key (D-19). On the new device, onboarding shows its public key ("Show only
the public key"). On a device that already prints, Settings → Add a device takes that key pasted into a field and
appends it to `authorized_keys` over the existing login; the new device then tests the connection. Anything that
isn't a plain `ssh-ed25519` key line is rejected before connecting.

## Error catalogue

Maps `SshError` and remote failures to user messages: a headline and the next step. Exact strings live in Compose
resources (`error_*_title` and `error_*`).

| Condition | Headline | Next step | Action |
|---|---|---|---|
| `HostUnreachable` | Can't reach the pool server | Off campus? Connect to the university VPN first. Otherwise check your internet connection. | Try again |
| `AuthRejected` during the password key install (3a) | The server didn't accept the password | Check your username and password. If the pool asks for more than a password (for example a one-time code), add the key with the snippet instead. | n/a (type it again) |
| `AuthRejected` | The server doesn't know your key yet | Add the public key to `~/.ssh/authorized_keys` on your pool account, then try again. | Show my key / Show snippet, Try again |
| `HostKeyUnknown` | (full screen) Is this the pool server? | Compare the fingerprint. | It matches · Trust / Cancel |
| `HostKeyChanged` | (full screen) Server identity changed. Stopped. | Ask the pool admins; nothing was sent. | Close, Copy details (no continue) |
| `Timeout` | The server took too long to answer | Check your connection and try again. | Try again |
| Exit ≠ 0, stderr mentions an unknown printer | The printer `<name>` doesn't exist on the server | Pick another one. | n/a |
| Exit ≠ 0, stderr mentions quota or budget | Your print budget may be used up | Check it on the pool CUPS page (intranet). | n/a |
| Exit ≠ 0, other | Printing failed | The server returned exit code N. (+ stderr in Details) | Try again |
| Test connection command fails (exit ≠ 0) | The server returned an error | Exit code N. (+ stderr in Details) | Try again |
| Test connection prints something other than `ok` | The server answered, but not as expected | Is this the right server? | Try again |
| `lpr` not found | The print command isn't available | This server has no lpr, so nothing was printed. | Details |
| Validation error | An inline message under the field, e.g. "Page ranges look like 1-4,7." | | n/a |
| Discovery failed | A small hint, "Using default printer settings." (not an error) | | Refresh |

Stderr pattern matching is best-effort. Unknown output always falls through to the generic row,
with the full stderr available.
