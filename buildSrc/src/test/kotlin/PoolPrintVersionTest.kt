import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PoolPrintVersionTest {

    @Test
    fun `mac package version strips the release candidate suffix`() {
        assertEquals("1.0.0", PoolPrintVersion.parse("1.0.0-rc.1", "2").macPackageVersion)
        assertEquals("1.2.3", PoolPrintVersion.parse("1.2.3-rc.12", "9").macPackageVersion)
    }

    @Test
    fun `mac package version of a final release is the name itself`() {
        assertEquals("1.0.0", PoolPrintVersion.parse("1.0.0", "4").macPackageVersion)
        assertEquals("10.20.30", PoolPrintVersion.parse("10.20.30", "4").macPackageVersion)
    }

    @Test
    fun `windows package version strips the release candidate suffix`() {
        assertEquals("1.0.0", PoolPrintVersion.parse("1.0.0-rc.2", "2").windowsPackageVersion)
        assertEquals("1.2.3", PoolPrintVersion.parse("1.2.3-rc.12", "9").windowsPackageVersion)
    }

    @Test
    fun `windows package version of a final release is the name itself`() {
        assertEquals("1.0.0", PoolPrintVersion.parse("1.0.0", "4").windowsPackageVersion)
        assertEquals("10.20.30", PoolPrintVersion.parse("10.20.30", "4").windowsPackageVersion)
    }

    @Test
    fun `release candidates and the final release share one windows package version`() {
        val versions = listOf("1.0.0-rc.1", "1.0.0-rc.2", "1.0.0").map { PoolPrintVersion.parse(it, "2").windowsPackageVersion }
        assertEquals(setOf("1.0.0"), versions.toSet())
    }

    @Test
    fun `windows package version accepts the largest msi values`() {
        assertEquals("255.255.65535", PoolPrintVersion.parse("255.255.65535", "2").windowsPackageVersion)
        assertEquals("255.255.65535", PoolPrintVersion.parse("255.255.65535-rc.1", "2").windowsPackageVersion)
    }

    @Test
    fun `windows package version rejects values an msi would not accept`() {
        listOf("256.0.0", "1.256.0", "1.0.65536", "1.0.99999999999999999999", "99999999999.0.0").forEach { name ->
            val e = assertFailsWith<IllegalArgumentException>(name) { PoolPrintVersion.parse(name, "2").windowsPackageVersion }
            assertTrue(e.message!!.contains(name), name)
            assertTrue(e.message!!.contains(PoolPrintVersion.NAME_PROPERTY), name)
        }
    }

    @Test
    fun `keeps the full name and the code`() {
        val v = PoolPrintVersion.parse(" 1.0.0-rc.1 ", " 2 ")
        assertEquals("1.0.0-rc.1", v.name)
        assertEquals(2, v.code)
    }

    @Test
    fun `rejects names macOS or SemVer would not accept`() {
        listOf(
            "0.9.0", // MAJOR must be >= 1 on macOS
            "1.0", "1", "1.0.0.0",
            "01.0.0", "1.00.0",
            "1.0.0-beta", "1.0.0-rc", "1.0.0-rc.0", "1.0.0-rc.1.2", "1.0.0-RC.1",
            "1.0.0+build", "v1.0.0", "1.0.0 rc.1", "1.0.0;ls",
        ).forEach { name ->
            val e = assertFailsWith<IllegalArgumentException>(name) { PoolPrintVersion.parse(name, "2") }
            assertTrue(e.message!!.contains(PoolPrintVersion.NAME_PROPERTY), name)
        }
    }

    @Test
    fun `rejects a missing name`() {
        listOf(null, "", "  ").forEach { name ->
            assertFailsWith<IllegalArgumentException> { PoolPrintVersion.parse(name, "2") }
        }
    }

    @Test
    fun `rejects codes Android would not accept`() {
        listOf(null, "", "0", "-1", "1.5", "two", "2100000001", "99999999999").forEach { code ->
            val e = assertFailsWith<IllegalArgumentException>(code.toString()) { PoolPrintVersion.parse("1.0.0", code) }
            assertTrue(e.message!!.contains(PoolPrintVersion.CODE_PROPERTY), code.toString())
        }
    }
}
