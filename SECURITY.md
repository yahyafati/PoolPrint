# Security

Pool Print handles an SSH key that can log in to your pool account, so security reports are welcome. Keep in mind
that this is a vibe-coded hobby project, provided as is (see the [README](README.md#disclaimer)): there is no
security team and no promised response time.

## Reporting a vulnerability

Report it **privately**: on GitHub, open this repository's **Security** tab and choose **Report a vulnerability**.
Please don't open a public issue for it.

Useful to include: the app version (Settings → About), the platform and OS version, what an attacker can do, and
the steps to reproduce.

## Never post secrets

In vulnerability reports **and** in public issues, never post:

- a private key, or the contents of the app's key storage;
- your pool username or password;
- your device's public key line, or the server or host-key fingerprints the app shows;
- `lpq` output with other people's usernames or file names.

Replace them with placeholders such as `<username>` or `ssh-ed25519 AAAA…`. If you posted one by mistake, edit it
out, and for a key: delete its line from `~/.ssh/authorized_keys` on the pool account and use **Settings →
Regenerate key** in the app.

## Scope

The app only runs fixed commands over SSH on `login.informatik.uni-freiburg.de`: `lpr`, `lpq`, `lprm`, `lpstat`,
`lpoptions`, `echo ok` (connection test) and the one that appends a device key to `~/.ssh/authorized_keys`. Problems with the pool server or the printers themselves belong to the TF pool administration, not
to this project. The design and threat model are in [docs/security.md](docs/security.md).
