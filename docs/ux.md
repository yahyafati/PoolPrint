# UI / UX

Material 3, light and dark mode (follows the system), Compose Multiplatform. The same screens run on
Android and macOS. Desktop gets a wider layout, not a different flow.

## Principles

- **Three taps after sharing:** share PDF → (sheet opens with the last-used or preset options) →
  confirm the printer/preset if needed → **Print**. The common case is a single tap on Print.
- **Cost before commitment:** the Print button always shows the page count and cost:
  `Print · 12 pages · 0.60 €`.
- **Simple by default:** printer, preset, copies and duplex are visible. Everything else sits under
  an expandable **Advanced options** section.
- **Errors say what to do next**, in plain language, with the raw stderr behind a "Details" toggle.

## Screens

### Onboarding (first run)

1. **Welcome:** one sentence on what the app does, plus "Your password is never needed or stored."
2. **Pool username:** a text field, validated.
3. **Your key:** the app generates an Ed25519 key and shows the public key (monospace, selectable) with
   **Copy**. On macOS there's also **Use an existing key from ~/.ssh…**. Instructions:
   > Log in to the pool once (from a pool computer, or with your password over SSH from a laptop) and run:
   > ```
   > mkdir -p ~/.ssh && chmod 700 ~/.ssh
   > echo '<paste key here>' >> ~/.ssh/authorized_keys
   > chmod 600 ~/.ssh/authorized_keys
   > ```
   Offer a **Share** action too, so the user can send the key to themselves.
4. **Verify server:** the first connection shows the host-key fingerprint and asks the user to accept
   it (see [security.md](security.md#host-key-verification-tofu)).
5. **Test connection:** runs `echo ok` and shows ✓ or a catalogue error. On success, "Done".
   A short note above the button: "Off campus you need to be connected to the university VPN."

After onboarding, nothing asks for a password or passphrase again (A-11).

### Home

- An empty state with **Pick a PDF** (Android) or **Drop a PDF here / Choose file…** (macOS).
- Status chip: connection configured / not configured.
- Entry points: Queue (M3), Presets (M3), Settings.

### Print sheet (main flow)

Opens when a PDF arrives (share, pick, drop, Open With).

```
┌──────────────────────────────────────────┐
│ 📄 lecture-05.pdf · 24 pages             │
│                                          │
│ Preset   [ Lecture slides ▾ ]   (M3)     │
│ Printer  [ tfppr1 · b/w ▾ ]              │
│ Copies   [ – 1 + ]   Duplex [Long edge▾] │
│                                          │
│ ▸ Advanced options                       │
│   Pages per sheet · Page ranges ·        │
│   Orientation · Fit to page · Paper ·    │
│   printer-specific options (M3)          │
│                                          │
│ [   Print · 12 pages · 0.60 €   ]        │
└──────────────────────────────────────────┘
```

- The printer dropdown shows a type badge (b/w / colour) and, with discovery, its state (idle / disabled).
- A page-ranges field with an invalid value shows an inline error and disables Print.
- While printing: progress (bytes uploaded), with Cancel.
- Result: a success screen with the job id when parsed ("Sent to tfppr1 · job 1234. Collect your
  printout at the printer."), or a catalogue error.

### Queue (M3)

A printer selector, then a list of entries (rank, owner, job id, size). Own jobs are highlighted and
have **Cancel** (with a confirmation step). Pull to refresh on Android, a refresh button on desktop.

### Presets (M3)

A list of named option sets, e.g. "Lecture slides: 2-up, duplex, b/w". Save the current options as a
preset from the print sheet. A preset stores the printer and all options, but no page ranges.

### Settings

Username · public key (copy/share) · regenerate key · host key (fingerprint, forget) · discovery
(refresh, last updated) · default printer · about / licences.

## Error catalogue

Maps `SshError` and remote failures to user messages. Exact strings live in Compose resources.

| Condition | Message | Action |
|---|---|---|
| `HostUnreachable` | "Can't reach the pool server. **Off campus? Connect to the university VPN first.** Otherwise check your internet connection." | Retry |
| `AuthRejected` | "Key not authorized. Did you add the public key to `~/.ssh/authorized_keys` on your pool account?" | Show key / Copy |
| `HostKeyUnknown` | "First connection to this server. Check the fingerprint before trusting it." | Show fingerprint, Trust / Cancel |
| `HostKeyChanged` | "**Warning: the server's identity has changed.** This could be an attack. Don't continue unless the pool admins announced a key change." | Details only (no continue) |
| `Timeout` | "The server didn't respond in time. Try again." | Retry |
| Exit ≠ 0, stderr mentions an unknown printer | "The printer `<name>` doesn't exist on the server. Refresh printers or pick another." | Refresh |
| Exit ≠ 0, stderr mentions quota or budget | "Your print budget may be used up. Check it on the pool CUPS page (intranet)." | n/a |
| Exit ≠ 0, other | "Printing failed (exit code N)." + stderr in Details | Retry |
| `lpr` not found | "The print command isn't available on this server." | Details |
| Validation error | An inline message on the field, e.g. "Page ranges look like `1-4,7`." | n/a |
| Discovery failed | A small hint, "Using default printer settings." (not an error) | Refresh |

Stderr pattern matching is best-effort. Unknown output always falls through to the generic row,
with the full stderr available.
