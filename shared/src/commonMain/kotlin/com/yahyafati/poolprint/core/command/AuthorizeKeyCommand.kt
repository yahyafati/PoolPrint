package com.yahyafati.poolprint.core.command

import com.yahyafati.poolprint.core.model.PublicKeyLine

/**
 * "Add device": appends a key to `~/.ssh/authorized_keys`. The command is a fixed string with no variable part;
 * the key goes in on stdin as a [PublicKeyLine], so nothing the user typed is ever part of the shell command.
 * Works the same in sh- and csh-family login shells.
 */
object AuthorizeKeyCommand {
    private const val SCRIPT =
        "umask 077; mkdir -p ~/.ssh; chmod 700 ~/.ssh; cat >> ~/.ssh/authorized_keys; chmod 600 ~/.ssh/authorized_keys"

    fun build(): RemoteCommand = RemoteCommand(SCRIPT)

    /** A leading newline keeps the key off the last line if the file doesn't end in one; blank lines are ignored by sshd. */
    fun stdin(key: PublicKeyLine): ByteArray = "\n${key.line}\n".encodeToByteArray()
}
