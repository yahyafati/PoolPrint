package com.yahyafati.poolprint.keys

import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.WString
import com.sun.jna.platform.win32.Kernel32Util
import com.sun.jna.platform.win32.WinBase
import com.sun.jna.platform.win32.WinError
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores the private key as a Windows Credential Manager generic credential (D-26), the counterpart of
 * [MacKeychainPrivateKeyStore]: target name `Pool Print`, `CRED_TYPE_GENERIC`, `CRED_PERSIST_LOCAL_MACHINE` (stays on
 * this PC, does not roam). The secret goes through `advapi32` by pointer, never on a command line, and the native
 * copies are zeroed after use. Credential Manager opens it for the same Windows user without a prompt (A-11).
 *
 * The credential is keyed by [service] (the target name); [account] is stored as its user name, for the user's eyes
 * in Control Panel. A key larger than [MAX_BLOB_BYTES] is refused with a clear error.
 */
class WindowsCredentialPrivateKeyStore internal constructor(
    private val service: String,
    private val account: String,
    private val backend: Lazy<CredentialBackend>,
) : PrivateKeyStore {

    constructor(
        service: String = "Pool Print",
        account: String = "ssh-ed25519-private-key@login.informatik.uni-freiburg.de",
    ) : this(service, account, lazy { JnaCredentialBackend() })

    override suspend fun load(): ByteArray? = withContext(Dispatchers.IO) { backend.value.read(service) }

    override suspend fun save(pem: ByteArray) = withContext(Dispatchers.IO) {
        if (pem.size > MAX_BLOB_BYTES) {
            throw CredentialTooLargeException(pem.size)
        }
        backend.value.write(service, account, pem)
    }

    override suspend fun delete() = withContext(Dispatchers.IO) { backend.value.delete(service) }

    class CredentialTooLargeException(size: Int) :
        Exception("The key is $size bytes; Windows Credential Manager stores at most $MAX_BLOB_BYTES bytes per credential.")

    class WindowsCredentialException(operation: String, val errorCode: Int) :
        Exception("$operation failed with Windows error $errorCode: ${messageFor(errorCode)}")

    companion object {
        /** `CRED_MAX_CREDENTIAL_BLOB_SIZE` (5 * 512). */
        const val MAX_BLOB_BYTES = 2560

        private fun messageFor(code: Int): String =
            runCatching { Kernel32Util.formatMessage(code).trim() }.getOrDefault("unknown error")
    }
}

/** What the store needs from Credential Manager; the real one uses JNA, tests can pass an in-memory fake. */
internal interface CredentialBackend {
    /** The stored blob, or `null` if there is no such credential. */
    fun read(target: String): ByteArray?

    /** Creates the credential or overwrites an existing one. */
    fun write(target: String, userName: String, blob: ByteArray)

    /** Removes the credential; succeeds if it is already gone. */
    fun delete(target: String)
}

/** `CREDENTIALW` from `wincred.h`. */
@Structure.FieldOrder(
    "Flags", "Type", "TargetName", "Comment", "LastWritten", "CredentialBlobSize", "CredentialBlob", "Persist",
    "AttributeCount", "Attributes", "TargetAlias", "UserName",
)
internal class CredentialW : Structure {
    @JvmField var Flags: Int = 0
    @JvmField var Type: Int = 0
    @JvmField var TargetName: WString? = null
    @JvmField var Comment: WString? = null
    @JvmField var LastWritten: WinBase.FILETIME = WinBase.FILETIME()
    @JvmField var CredentialBlobSize: Int = 0
    @JvmField var CredentialBlob: Pointer? = null
    @JvmField var Persist: Int = 0
    @JvmField var AttributeCount: Int = 0
    @JvmField var Attributes: Pointer? = null
    @JvmField var TargetAlias: WString? = null
    @JvmField var UserName: WString? = null

    constructor() : super()
    constructor(p: Pointer) : super(p) {
        read()
    }
}

private interface CredApi : Library {
    fun CredWriteW(credential: CredentialW, flags: Int): Boolean
    fun CredReadW(targetName: WString, type: Int, flags: Int, credential: PointerByReference): Boolean
    fun CredDeleteW(targetName: WString, type: Int, flags: Int): Boolean
    fun CredFree(buffer: Pointer)
}

internal class JnaCredentialBackend : CredentialBackend {
    // jna-platform has no Credential Manager bindings (checked for 5.19.1), so the three calls are declared here;
    // jna-platform supplies FILETIME and the Windows error text.
    private val api: CredApi by lazy {
        check(System.getProperty("os.name").orEmpty().startsWith("Windows")) { "Credential Manager is only available on Windows." }
        Native.load("Advapi32", CredApi::class.java)
    }

    override fun read(target: String): ByteArray? {
        val out = PointerByReference()
        if (!api.CredReadW(WString(target), CRED_TYPE_GENERIC, 0, out)) {
            val error = Native.getLastError()
            if (error == WinError.ERROR_NOT_FOUND) return null
            throw WindowsCredentialPrivateKeyStore.WindowsCredentialException("CredReadW", error)
        }
        val native = out.value
        try {
            val credential = CredentialW(native)
            val size = credential.CredentialBlobSize
            val blob = credential.CredentialBlob
            if (size == 0 || blob == null) return ByteArray(0)
            val copy = blob.getByteArray(0, size)
            blob.clear(size.toLong())
            return copy
        } finally {
            api.CredFree(native)
        }
    }

    override fun write(target: String, userName: String, blob: ByteArray) {
        val secret = Memory(maxOf(blob.size, 1).toLong())
        try {
            secret.write(0, blob, 0, blob.size)
            val credential = CredentialW().apply {
                Type = CRED_TYPE_GENERIC
                TargetName = WString(target)
                Comment = WString("Pool Print device key")
                CredentialBlobSize = blob.size
                CredentialBlob = secret
                Persist = CRED_PERSIST_LOCAL_MACHINE
                UserName = WString(userName)
            }
            if (!api.CredWriteW(credential, 0)) {
                throw WindowsCredentialPrivateKeyStore.WindowsCredentialException("CredWriteW", Native.getLastError())
            }
        } finally {
            secret.clear()
            secret.close()
        }
    }

    override fun delete(target: String) {
        if (!api.CredDeleteW(WString(target), CRED_TYPE_GENERIC, 0)) {
            val error = Native.getLastError()
            if (error != WinError.ERROR_NOT_FOUND) {
                throw WindowsCredentialPrivateKeyStore.WindowsCredentialException("CredDeleteW", error)
            }
        }
    }

    private companion object {
        const val CRED_TYPE_GENERIC = 1
        const val CRED_PERSIST_LOCAL_MACHINE = 2
    }
}
