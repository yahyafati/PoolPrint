package com.yahyafati.poolprint.transport.testserver

import java.nio.file.Paths

/** Standalone launcher so the Android emulator (10.0.2.2) can reach a test server on the host. */
fun main(args: Array<String>) {
    val hostKeyFile = Paths.get(args.getOrElse(0) { "spike-hostkey.ser" })
    val server = TestSshServer(hostKeyFile, listenPort = args.getOrElse(1) { "0" }.toInt()).start()
    println("READY port=${server.port} hostkey=${server.hostKeyFingerprint}")
    Thread.currentThread().join()
}
