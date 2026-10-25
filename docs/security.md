# Security

Pool Print holds a credential that can log into a university account and run commands there. That
makes it security-sensitive even though it's a small app. The rules here are **requirements**, not suggestions.

## Threat model

| Asset | Threat | Mitigation |
|---|---|---|
| SSH private key | Stolen from device storage or backups | Encrypted at rest with a platform-held key (Android Keystore / macOS Keychain); excluded from Android backups; never logged or exported in plaintext |
| Pool account | Arbitrary command execution through crafted input (shell injection) | Typed values only, allow-list validation, per-token shell quoting, injection tests on every field |
| Pool account | Man-in-the-middle impersonating the login server | Host key pinned on first use (TOFU); a changed key **blocks** the connection |
| Pool password | Phishing or storage in the app | The app never asks for or stores it; password auth is disabled in the client |
| Document privacy | Filename or content leaks | No filenames in remote commands; no copies on the remote side; temp copies on the device are deleted after use |
| User privacy | Tracking | No telemetry, analytics or crash reporting; no network traffic except to the pool login server |
| Budget | Accidental huge jobs | `MAX_COPIES` cap, cost estimate on the button, warning above a threshold |

Out of scope: a fully compromised (rooted or jailbroken) device, and a compromised login server.

## SSH client configuration

- **Auth methods:** `publickey` only. Turn off `password`, `keyboard-interactive` and `gssapi-with-mic`
  in the library configuration. Don't just skip them.
- **No forwarding:** no agent forwarding, port forwarding or X11.
- **Host-key algorithm preference** is fixed and explicit, `ssh-ed25519` first, so the negotiated key
  type is stable and the pin doesn't flap between key types.
- **Timeouts:** connect timeout (~10 s) and an overall exec timeout, which is longer for printing
  because of upload time.
- **Exec only:** the app never opens an interactive shell, SFTP or SCP.

## Key handling

### Generation (onboarding)

- Ed25519 key pair, generated on the device by the SSH library or crypto provider.
- Public key in OpenSSH format with a comment: `ssh-ed25519 AAAA… pool-print@<device-model>`.
- Shown with a **Copy** button and instructions (see [ux.md](ux.md#onboarding)).
- Settings has "Regenerate key". It warns the user to remove the old line from `authorized_keys`.

### Android storage

1. (Implemented as `AndroidKeystorePrivateKeyStore`.) Generate an AES-256-GCM key in **AndroidKeyStore** (`PURPOSE_ENCRYPT | PURPOSE_DECRYPT`,
   `BLOCK_MODE_GCM`, `ENCRYPTION_PADDING_NONE`). It's non-exportable by design.
2. Encrypt the serialised private key and store `iv + ciphertext` in app-private storage, in
   `noBackupFilesDir`.
3. That directory is excluded from Auto Backup and device transfer, and the manifest sets
   `allowBackup="false"`. The Keystore key isn't backed up anyway, so a restored blob would be useless
   (decisions.md, D-11).
4. The Keystore key is created **without** `setUserAuthenticationRequired`, so using it never
   prompts (A-11 in [decisions.md](decisions.md)). It's still bound to the device and can't be exported.
5. Decrypt into memory only for the length of a connection, and zero byte arrays where practical. sshj's
   `loadKeys(String, …)` makes a `String` copy of the key that can't be zeroed; it is garbage-collected later (D-01).
6. Possible later: an opt-in "require fingerprint to print" setting, off by default.

### macOS storage

- **Default:** store the private key as a Keychain generic-password item (service `Pool Print`,
  account `ssh-ed25519-private-key@login.informatik.uni-freiburg.de`, not synchronisable). Access goes through JNA
  bindings to the Security framework ([decisions.md](decisions.md#d-04-macos-keychain-access)). The
  secret never goes on a command line, where other processes could see it. The login keychain is
  unlocked when the user logs in, so normal use doesn't prompt. Unsigned builds may ask once after an
  update.
- **Alternative:** the user picks an existing private key from `~/.ssh`. The app stores only the
  **path**, reads the key when it connects and never copies it. If the key is passphrase-protected,
  ask for the passphrase **once** and store it in the Keychain, so later prints don't prompt (A-11).
  That passphrase protects the key; it isn't the pool password.

## Host-key verification (TOFU)

1. **First connection:** show the server's key type and **SHA-256 fingerprint** (`SHA256:…`, the same
   format as `ssh-keygen -lf`). Suggest checking it against a trusted machine
   (`ssh-keygen -lF login.informatik.uni-freiburg.de` from a computer that already trusts it).
   The fingerprint is pinned only after the user explicitly accepts it.
2. **Later connections:** accept only the pinned key for that `host:port`.
3. **Key changed:** abort before authenticating. Show a full-screen, red warning with the expected and
   actual fingerprints. There's **no one-tap "continue"**. Re-pinning means going to Settings → Host key
   → "Forget pinned key", with a second confirmation.
4. The host-key store is not secret, but it is integrity-relevant. Keep it in app-private storage.

## Command construction

The remote side runs the command string through the user's **login shell** on the pool server.
That's usually a POSIX shell, but csh-family shells exist. So defence is layered:

1. **Typed inputs only.** Builders accept enums, validated integers, `PageRanges`, a `Printer` from the
   known list and `DiscoveredSetting` values (made only by `Validators.discoveredSetting` from the cached `lpoptions` of that printer) and
   `OwnedJobId` for `lprm` (digits, in the latest `lpq`, owned by the user). No `String` from the UI goes in directly.
2. **Allow-list validation.** See [print-options.md](print-options.md#validation-rules). Identifiers must
   match `^[A-Za-z0-9_][A-Za-z0-9_.-]{0,63}$`. The first character can't be `-`, which prevents option
   injection. Anything else is **rejected**, never sanitised or escaped into acceptance.
3. **Final token check.** Before quoting, the builder asserts that every token matches
   `^[A-Za-z0-9_.,=#:-]+$`. This catches bugs in layer 2.
4. **Per-token POSIX single-quoting.** Wrap each token in `'…'` and turn any embedded `'` into
   `'\''`. After layer 3 no token can contain `'`, so this is defence in depth. The quoting helper
   still gets tested on hostile input by itself.
5. **`RemoteCommand` has an `internal` constructor.** Only the builders in `core.command` can make one.

Never in a remote command: filenames, document titles, the pool username (it goes into the SSH
connection config instead), preset names, or any free text.

### Adding a device's key

"Add device" (D-19) appends a public key to `~/.ssh/authorized_keys` from a device that can already log in. Its
command (`AuthorizeKeyCommand`) is one **fixed string with no variable part**; the key goes in on **stdin**. So the
shell never sees anything the user typed. The key itself is a `PublicKeyLine`, rebuilt from validated parts: type
`ssh-ed25519`, a 68-character base64 blob of the right shape, and an optional comment `[A-Za-z0-9._@-]{1,64}`.
Options such as `command="…"`, other key types, several lines or any other character are rejected, never sanitised.
Because a key can be authorized this way, only an already-authenticated device can add one; the server's own
`authorized_keys` remains the authority and the user can remove any line there.

### Required injection tests

For **every** user-controlled field (printer, copies, page ranges, paper size or enum mapping,
discovered option key, discovered option value, job id) and also for the **filename** and **preset name**
(which must never appear in the command at all), run a parameterised test with payloads like:

```
;  &&  ||  |  &  `id`  $(id)  ${IFS}  '  "  \  \n  \r  \t  \u0000  #  >  <  >>  *  ?  ~  !
{a,b}  -o x  --help  -P evil  ' ; id ; '  1-4;id  1-4$(id)  ＄(id) (full-width)  é  emoji
```

Every case must either be rejected with a `ValidationError`, or (for filename and preset name) not
show up in the resulting `RemoteCommand.value`. Also round-trip-test `ShellQuote`: for arbitrary
strings, POSIX `sh -c "printf %s <quoted>"` on the dev machine must print the original string. Run
this as a JVM test where `/bin/sh` exists, and skip it on Android host tests.

## Logging

- Never log private keys, decrypted key bytes, PDF contents or full stderr containing paths in release builds.
- Debug logs stay on the device (logcat / stdout). Nothing is sent anywhere.

## Usernames

The pool username is validated with `^[a-z0-9_][a-z0-9_.-]{0,31}$` (adjust if real TF logins differ).
It's used only as the SSH login user.
