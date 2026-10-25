package com.yahyafati.poolprint.core.command

import com.yahyafati.poolprint.core.validation.Patterns

/**
 * A command that is already validated and quoted. The constructor is internal, so only the builders in
 * this package can make one (docs/security.md, "Command construction").
 */
class RemoteCommand internal constructor(val value: String) {
    override fun toString() = value

    internal companion object {
        /**
         * Layer 3: every token must match the safe pattern (a failure here is a bug in layer 2, so it throws),
         * then layer 4: each token is shell-quoted individually.
         */
        fun fromTokens(tokens: List<String>): RemoteCommand {
            require(tokens.isNotEmpty())
            for (token in tokens) {
                require(Patterns.SafeToken.matches(token)) { "unsafe command token" }
            }
            return RemoteCommand(tokens.joinToString(" ") { ShellQuote.quote(it) })
        }
    }
}
