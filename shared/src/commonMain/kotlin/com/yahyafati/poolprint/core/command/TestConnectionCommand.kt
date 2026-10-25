package com.yahyafati.poolprint.core.command

/** The "Test connection" probe: `echo ok`. */
object TestConnectionCommand {
    const val EXPECTED_OUTPUT = "ok"

    fun build(): RemoteCommand = RemoteCommand.fromTokens(listOf("echo", EXPECTED_OUTPUT))
}
