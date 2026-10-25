package com.yahyafati.poolprint.fakes

import com.yahyafati.poolprint.core.command.RemoteCommand
import com.yahyafati.poolprint.core.model.PoolUsername
import com.yahyafati.poolprint.hostkeys.HostKeyStore
import com.yahyafati.poolprint.hostkeys.PinnedHostKey
import com.yahyafati.poolprint.keys.GeneratedKey
import com.yahyafati.poolprint.keys.KeyGenerator
import com.yahyafati.poolprint.keys.PrivateKeyStore
import com.yahyafati.poolprint.settings.AppSettings
import com.yahyafati.poolprint.transport.ByteSource
import com.yahyafati.poolprint.transport.ConnectionConfig
import com.yahyafati.poolprint.transport.SshClient
import com.yahyafati.poolprint.transport.SshClientFactory
import com.yahyafati.poolprint.transport.SshException
import com.yahyafati.poolprint.transport.SshError
import com.yahyafati.poolprint.transport.SshResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/**
 * Records commands and the stdin bytes, and returns scripted results or errors. The queue of scripted
 * responses is consumed in order; the last one repeats.
 */
class FakeSshClient : SshClient {
    class Call(val command: String, val stdin: ByteArray?)

    sealed interface Script {
        data class Result(val value: SshResult) : Script
        data class Error(val error: SshError) : Script
        /** Suspends forever (until cancelled), after reading nothing: for cancellation tests. */
        data object Hang : Script
    }

    val calls = mutableListOf<Call>()
    var scripts: MutableList<Script> = mutableListOf(Script.Result(SshResult(0, "", "")))
    var uploadChunk = 8

    fun respond(vararg script: Script) {
        scripts = script.toMutableList()
    }

    override suspend fun exec(command: RemoteCommand, stdin: ByteSource?, onUploaded: (Long) -> Unit, timeoutMs: Long): SshResult {
        var bytes: ByteArray? = null
        if (stdin != null) {
            val out = mutableListOf<Byte>()
            val buffer = ByteArray(uploadChunk)
            while (true) {
                val n = stdin.read(buffer)
                if (n < 0) break
                for (i in 0 until n) out += buffer[i]
                onUploaded(out.size.toLong())
            }
            bytes = out.toByteArray()
        }
        calls += Call(command.value, bytes)
        val script = if (scripts.size > 1) scripts.removeAt(0) else scripts.first()
        return when (script) {
            is Script.Result -> script.value
            is Script.Error -> throw SshException(script.error)
            Script.Hang -> kotlinx.coroutines.awaitCancellation()
        }
    }
}

class FakeSshClientFactory(val client: FakeSshClient = FakeSshClient()) : SshClientFactory {
    val configs = mutableListOf<ConnectionConfig>()
    override fun create(config: ConnectionConfig): SshClient {
        configs += config
        return client
    }
}

class FakeSettings(username: PoolUsername? = null) : AppSettings {
    private val usernameState = MutableStateFlow(username)
    private val onboarded = MutableStateFlow(false)
    private val key = MutableStateFlow<String?>(null)
    override val username = usernameState
    override val onboardingComplete = onboarded
    override val publicKey = key
    override suspend fun getUsername() = usernameState.value
    override suspend fun getPublicKey() = key.value
    override suspend fun setUsername(username: PoolUsername) { usernameState.value = username }
    override suspend fun setPublicKey(line: String) { key.value = line }
    override suspend fun setOnboardingComplete(complete: Boolean) { onboarded.value = complete }
    var printDefaults = com.yahyafati.poolprint.core.model.PrintOptions()
    override suspend fun getPrintDefaults(known: List<com.yahyafati.poolprint.core.model.Printer>) = printDefaults
    /** Goes through the same conversion as the DataStore, so only what is really persisted comes back. */
    override suspend fun setPrintDefaults(options: com.yahyafati.poolprint.core.model.PrintOptions) {
        printDefaults = com.yahyafati.poolprint.settings.StoredPrintOptions.fromJson(
            com.yahyafati.poolprint.settings.StoredPrintOptions.from(options).toJson(),
        )
    }
}

class FakeDiscoveryStore(var snapshot: com.yahyafati.poolprint.core.discovery.DiscoverySnapshot = com.yahyafati.poolprint.core.discovery.DiscoverySnapshot()) :
    com.yahyafati.poolprint.settings.DiscoveryStore {
    override suspend fun load() = snapshot
    override suspend fun save(snapshot: com.yahyafati.poolprint.core.discovery.DiscoverySnapshot) { this.snapshot = snapshot }
}

class FakePresetStore : com.yahyafati.poolprint.settings.PresetStore {
    private val state = MutableStateFlow<List<com.yahyafati.poolprint.settings.Preset>>(emptyList())
    override val presets = state
    override suspend fun save(presets: List<com.yahyafati.poolprint.settings.Preset>) { state.value = presets }
}

class FakeHostKeyStore : HostKeyStore {
    val pins = mutableMapOf<String, PinnedHostKey>()
    override suspend fun get(host: String, port: Int) = pins["$host:$port"]
    override suspend fun pin(host: String, port: Int, key: PinnedHostKey) { pins["$host:$port"] = key }
    override suspend fun forget(host: String, port: Int) { pins.remove("$host:$port") }
}

class FakeKeyStore : PrivateKeyStore {
    var stored: ByteArray? = null
    override suspend fun load() = stored?.copyOf()
    override suspend fun save(pem: ByteArray) { stored = pem.copyOf() }
    override suspend fun delete() { stored = null }
}

class FakeKeyGenerator : KeyGenerator {
    var counter = 0
    override fun generate(comment: String): GeneratedKey {
        counter++
        return GeneratedKey("PRIVATE-$counter".encodeToByteArray(), "ssh-ed25519 AAAAKEY$counter $comment")
    }
}

fun user(name: String = "jdoe"): PoolUsername =
    (PoolUsername.parse(name) as com.yahyafati.poolprint.core.validation.Validated.Valid).value

class FakeDocument(
    override val displayName: String = "lecture-05.pdf",
    private val bytes: ByteArray = "%PDF-1.7 fake".encodeToByteArray(),
) : com.yahyafati.poolprint.pdf.PdfDocumentSource {
    override val sizeBytes: Long = bytes.size.toLong()
    var opened = 0
    override fun open(): ByteSource {
        opened++
        return com.yahyafati.poolprint.transport.ByteArraySource(bytes)
    }
}

class TestDeps(username: PoolUsername? = null) {
    val factory = FakeSshClientFactory()
    val client = factory.client
    val settings = FakeSettings(username)
    val hostKeys = FakeHostKeyStore()
    val keyStore = FakeKeyStore()
    val generator = FakeKeyGenerator()
    val pageCounter = FakePageCounter()
    val services = com.yahyafati.poolprint.service.PoolServices(settings, hostKeys, factory)
    val discoveryStore = FakeDiscoveryStore()
    var nowMillis = 1_000_000_000L
    val deps = com.yahyafati.poolprint.app.AppDependencies(
        settings = settings,
        hostKeys = hostKeys,
        keys = com.yahyafati.poolprint.keys.KeyManager(generator, keyStore, settings, "pool-print@test"),
        services = services,
        pageCounter = pageCounter,
        discovery = com.yahyafati.poolprint.service.DiscoveryService(services, discoveryStore) { nowMillis },
        presets = com.yahyafati.poolprint.settings.PresetManager(FakePresetStore()),
        queue = com.yahyafati.poolprint.service.QueueService(services, settings),
    )
}

/** Returns a fixed count (or null for "can't tell"), after an optional suspension for loading-state tests. */
class FakePageCounter(var pages: Int? = 24) : com.yahyafati.poolprint.pdf.PdfPageCounter {
    var gate: kotlinx.coroutines.CompletableDeferred<Unit>? = null
    override suspend fun count(document: com.yahyafati.poolprint.pdf.PdfDocumentSource): Int? {
        gate?.await()
        return pages
    }
}
