# Security

Pool Print holds a credential that can log into a university account and run commands there. That
makes it security-sensitive even though it's a small app. The rules here are **requirements**, not suggestions.

## Threat model

| Asset | Threat | Mitigation |
|---|---|---|
| SSH private key | Stolen from device storage or backups | Encrypted at rest with a platform-held key (Android Keystore / macOS Keychain / Windows Credential Manager); excluded from Android backups; never logged or exported in plaintext |
| Pool account | Arbitrary command execution through crafted input (shell injection) | Typed values only, allow-list validation, per-token shell quoting, injection tests on every field |
| Pool account | Man-in-the-middle impersonating the login server | Host key pinned on first use (TOFU); a changed key **blocks** the connection |
| Pool password | Phishing, storage in the app, or reuse for other commands | Never stored; asked for at most once, optionally, in onboarding to install the key (D-20); sent only after the host key is verified; that login can run only the fixed key-install command; regular commands are publickey-only |
| Document privacy | Filename or content leaks | No filenames in remote commands; no copies on the remote side; temp copies on the device are deleted after use |
| User privacy | Tracking | No telemetry, analytics or crash reporting; no network traffic except to the pool login server |
| Budget | Accidental huge jobs | `MAX_COPIES` cap, cost estimate on the button, warning above a threshold |

Out of scope: a fully compromised (rooted or jailbroken) device, and a compromised login server.

## SSH client configuration

- **Auth methods:** `exec` (every regular command) offers `publickey` only: `password`, `keyboard-interactive` and
  `gssapi-with-mic` are never attempted, even after a rejection. The only other path is the one-time key install
  below, a separate client method.
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

### Windows storage

Decided in D-26; implemented in W1. Not verified on a real Windows machine until the owner's checklist.

- **Default:** the private key is a Credential Manager generic credential (`CRED_TYPE_GENERIC`,
  `CRED_PERSIST_LOCAL_MACHINE`, target name `Pool Print`, user name `ssh-ed25519-private-key@login.informatik.uni-freiburg.de`, mirroring the Keychain item), written, read and
  deleted with `CredWriteW` / `CredReadW` / `CredDeleteW` through JNA (`jna-platform`). It is bound to the Windows
  user, opens without a prompt (A-11), does not roam, and the user can see and delete it in Control Panel.
- The secret never goes on a command line or into a log; native buffers and byte arrays are zeroed after use. `load`
  returns `null` when nothing is stored (the app then generates a new key), `save` overwrites, `delete` is idempotent.
- The data folder is `%LOCALAPPDATA%\Pool Print` (D-27). It holds only non-secret state (settings, pinned host
  keys, discovery cache) and the single-instance files below.
- A process running as the same Windows user can read the credential. That is the same trust boundary as the Keychain
  (out of scope: a compromised user session).

### Single instance and file hand-over (Windows)

Decided in D-28; implemented in W2. Explorer's "Open with" starts a new process per file, and two processes on one
DataStore file can corrupt it. So a second instance never opens the store: it hands its file to the first and exits.

1. **Who is running:** the process that gets an exclusive `FileChannel` lock on `%LOCALAPPDATA%\Pool Print\instance.lock`
   is *the* instance. The OS drops the lock when the process dies, so a crash leaves no stale lock.
2. **Listener:** the running instance opens a `ServerSocket` bound to the **loopback address only** (127.0.0.1) on an
   ephemeral port, and writes the port and a **random token** (from `SecureRandom`, 128 bits or more) to a file in the
   data folder that only the current user can read (inherited permissions of `%LOCALAPPDATA%` are user-only; the file
   is created fresh with no wider access, and replaced on every start).
3. **Hand-over:** a second instance fails to get the lock, reads the port and token, connects to loopback, sends the
   token and the file path, and exits. It never opens the DataStore, the key store or the network.
4. **What is accepted:** only a file **path**, with the right token, in a bounded-length single line. A wrong token,
   garbage, an oversized or multi-line message, or a path that doesn't exist or isn't a PDF is dropped silently and
   never acted on. No commands, no URLs, no other message types.
5. **What happens next:** the path goes through `isPdfFile` and then into the normal incoming-document path, so it
   opens the usual print sheet. **Nothing prints by itself:** the user still presses Print. The same one-document and
   busy-job rules as for drops and Open With apply (D-18).
6. **Threat considered:** another local user or a web page connecting to the loopback port. A web page cannot read
   the token file and sends no valid token; another Windows user cannot read this user's file; and even a valid
   message can only show a print sheet for an existing PDF, never print, read or send the file. Tests (W2): wrong
   token, garbage, non-PDF path, nonexistent path, oversized message, lock held / released.

## Host-key verification (TOFU)

1. **First connection:** show the server's key type and **SHA-256 fingerprint** (`SHA256:…`, the same
   format as `ssh-keygen -lf`). Suggest checking it against a trusted machine
   (`ssh-keygen -lF login.informatik.uni-freiburg.de` from a computer that already trusts it).
   The fingerprint is pinned only after the user explicitly accepts it.
2. **Later connections:** accept only the pinned key for that `host:port`.
3. **Key changed:** abort before authenticating. Show a full-screen, red warning with the expected and
   actual fingerprints. There's **no one-tap "continue"**. Re-pinning means going to Settings → Server
   → Trusted fingerprint → "Forget", with a second confirmation.
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

### One-time password key install (D-20)

Optional, in onboarding only. `SshClient.installKeyWithPassword` logs in with the pool password and runs exactly the
`AuthorizeKeyCommand` above with the device's own `PublicKeyLine` on stdin; it takes no command argument.
- The host key is checked (TOFU) **before** authentication, as for every connection: the password is never sent to
  an unpinned or changed key.
- `password` is tried first; `keyboard-interactive` only if the server doesn't offer `password`, and then only a
  hidden "Password:"-style prompt is answered. A rejected password is not sent again.
- The password is never stored, logged or put into a command. It travels as a `CharArray` that is zeroed when the
  attempt ends (including copies handed to sshj); the UI field is cleared on submit.
- Tests (`SshjPasswordInstallTest`, `PasswordInstallTest`): key appended and key login works afterwards; password
  array zeroed on success and failure; a wrong password is sent once; keyboard-interactive fallback; no auth
  attempt at all for an unpinned or changed host key; `exec` still never tries the password on a server that
  would accept it; the password never appears in the command or stdin.

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
