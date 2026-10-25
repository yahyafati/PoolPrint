package com.yahyafati.poolprint.keys

import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import com.sun.jna.Pointer
import com.sun.jna.ptr.PointerByReference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Stores the private key as a macOS Keychain generic-password item (D-04): service `Pool Print`, not
 * synchronisable. The secret goes through the Security framework by pointer, never on a command line.
 * The login keychain is unlocked at login, so using the item doesn't prompt (A-11); unsigned builds may
 * ask once after an update.
 */
class MacKeychainPrivateKeyStore(
    private val service: String = "Pool Print",
    private val account: String = "ssh-ed25519-private-key@login.informatik.uni-freiburg.de",
) : PrivateKeyStore {

    private interface CoreFoundation : Library {
        fun CFStringCreateWithCString(allocator: Pointer?, cStr: String, encoding: Int): Pointer
        fun CFDataCreate(allocator: Pointer?, bytes: ByteArray, length: Long): Pointer
        fun CFDictionaryCreateMutable(allocator: Pointer?, capacity: Long, keyCallBacks: Pointer?, valueCallBacks: Pointer?): Pointer
        fun CFDictionaryAddValue(dict: Pointer, key: Pointer, value: Pointer)
        fun CFDataGetLength(data: Pointer): Long
        fun CFDataGetBytePtr(data: Pointer): Pointer
        fun CFRelease(cf: Pointer)
    }

    private interface SecurityFramework : Library {
        fun SecItemAdd(attributes: Pointer, result: PointerByReference?): Int
        fun SecItemCopyMatching(query: Pointer, result: PointerByReference): Int
        fun SecItemUpdate(query: Pointer, attributesToUpdate: Pointer): Int
        fun SecItemDelete(query: Pointer): Int
    }

    private class Api {
        val cf: CoreFoundation = Native.load("CoreFoundation", CoreFoundation::class.java)
        val sec: SecurityFramework = Native.load("Security", SecurityFramework::class.java)
        private val cfLib = NativeLibrary.getInstance("CoreFoundation")
        private val secLib = NativeLibrary.getInstance("Security")

        /** Reads an exported `CFTypeRef` constant such as `kSecClass`. */
        fun cfConstant(name: String): Pointer = cfLib.getGlobalVariableAddress(name).getPointer(0)
        fun secConstant(name: String): Pointer = secLib.getGlobalVariableAddress(name).getPointer(0)
        fun cfStruct(name: String): Pointer = cfLib.getGlobalVariableAddress(name)
    }

    private val api by lazy {
        check(System.getProperty("os.name").orEmpty().startsWith("Mac")) { "The Keychain is only available on macOS." }
        Api()
    }

    override suspend fun load(): ByteArray? = withContext(Dispatchers.IO) {
        val a = api
        val released = mutableListOf<Pointer>()
        try {
            val query = baseQuery(a, released)
            a.cf.CFDictionaryAddValue(query, a.secConstant("kSecReturnData"), a.cfConstant("kCFBooleanTrue"))
            a.cf.CFDictionaryAddValue(query, a.secConstant("kSecMatchLimit"), a.secConstant("kSecMatchLimitOne"))
            val result = PointerByReference()
            when (val status = a.sec.SecItemCopyMatching(query, result)) {
                0 -> {
                    val data = result.value
                    try {
                        a.cf.CFDataGetBytePtr(data).getByteArray(0, a.cf.CFDataGetLength(data).toInt())
                    } finally {
                        a.cf.CFRelease(data)
                    }
                }
                ERR_ITEM_NOT_FOUND -> null
                else -> throw KeychainException("SecItemCopyMatching", status)
            }
        } finally {
            released.forEach(api.cf::CFRelease)
        }
    }

    override suspend fun save(pem: ByteArray) = withContext(Dispatchers.IO) {
        val a = api
        val released = mutableListOf<Pointer>()
        try {
            val data = a.cf.CFDataCreate(null, pem, pem.size.toLong()).also(released::add)
            val attributes = baseQuery(a, released)
            a.cf.CFDictionaryAddValue(attributes, a.secConstant("kSecValueData"), data)
            a.cf.CFDictionaryAddValue(attributes, a.secConstant("kSecAttrSynchronizable"), a.cfConstant("kCFBooleanFalse"))
            val status = a.sec.SecItemAdd(attributes, null)
            if (status == ERR_DUPLICATE_ITEM) {
                val update = a.cf.CFDictionaryCreateMutable(null, 0, a.cfStruct("kCFTypeDictionaryKeyCallBacks"), a.cfStruct("kCFTypeDictionaryValueCallBacks"))
                released += update
                a.cf.CFDictionaryAddValue(update, a.secConstant("kSecValueData"), data)
                val updateStatus = a.sec.SecItemUpdate(baseQuery(a, released), update)
                if (updateStatus != 0) throw KeychainException("SecItemUpdate", updateStatus)
            } else if (status != 0) {
                throw KeychainException("SecItemAdd", status)
            }
        } finally {
            released.forEach(api.cf::CFRelease)
        }
    }

    override suspend fun delete() = withContext(Dispatchers.IO) {
        val a = api
        val released = mutableListOf<Pointer>()
        try {
            val status = a.sec.SecItemDelete(baseQuery(a, released))
            if (status != 0 && status != ERR_ITEM_NOT_FOUND) throw KeychainException("SecItemDelete", status)
        } finally {
            released.forEach(api.cf::CFRelease)
        }
    }

    /** `{class: generic password, service, account}`. Everything created is added to [released]. */
    private fun baseQuery(a: Api, released: MutableList<Pointer>): Pointer {
        val dict = a.cf.CFDictionaryCreateMutable(null, 0, a.cfStruct("kCFTypeDictionaryKeyCallBacks"), a.cfStruct("kCFTypeDictionaryValueCallBacks"))
        released += dict
        val serviceRef = a.cf.CFStringCreateWithCString(null, service, UTF8).also(released::add)
        val accountRef = a.cf.CFStringCreateWithCString(null, account, UTF8).also(released::add)
        a.cf.CFDictionaryAddValue(dict, a.secConstant("kSecClass"), a.secConstant("kSecClassGenericPassword"))
        a.cf.CFDictionaryAddValue(dict, a.secConstant("kSecAttrService"), serviceRef)
        a.cf.CFDictionaryAddValue(dict, a.secConstant("kSecAttrAccount"), accountRef)
        return dict
    }

    class KeychainException(operation: String, val status: Int) : Exception("$operation failed with OSStatus $status")

    private companion object {
        const val UTF8 = 0x08000100
        const val ERR_ITEM_NOT_FOUND = -25300
        const val ERR_DUPLICATE_ITEM = -25299
    }
}
