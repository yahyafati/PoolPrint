import org.gradle.api.GradleException
import org.gradle.api.provider.ProviderFactory

/**
 * The app version, read from `poolprint.versionName` and `poolprint.versionCode` in the root
 * `gradle.properties`: the single version source for both apps (docs/release.md, Phase 2; D-23).
 *
 * @property name SemVer `MAJOR.MINOR.PATCH`, optionally with a release-candidate suffix `-rc.N`.
 *   Shown in the app and used as the Android `versionName`.
 * @property code Android `versionCode`: +1 for every build that leaves the owner's Mac.
 */
data class PoolPrintVersion(val name: String, val code: Int) {

    /** The macOS package version: numbers only (D-18), so [name] without its `-rc.N` suffix. */
    val macPackageVersion: String get() = name.substringBefore('-')

    companion object {
        const val NAME_PROPERTY = "poolprint.versionName"
        const val CODE_PROPERTY = "poolprint.versionCode"

        // MAJOR ≥ 1: jpackage rejects a 0.x version on macOS (D-18). No leading zeros, as in SemVer.
        private val NAME = Regex("""([1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-rc\.[1-9]\d*)?""")

        // Google Play's upper bound; sideloading allows more, but there is no reason to go near it.
        private const val MAX_CODE = 2_100_000_000

        /** Parses and checks the two property values; throws [IllegalArgumentException] naming the bad property. */
        fun parse(name: String?, code: String?): PoolPrintVersion {
            val n = name?.trim()
            require(!n.isNullOrEmpty()) { "$NAME_PROPERTY is missing (gradle.properties)." }
            require(NAME.matches(n)) {
                "$NAME_PROPERTY must be MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-rc.N with MAJOR >= 1, got '$n'."
            }
            val c = code?.trim()
            require(!c.isNullOrEmpty()) { "$CODE_PROPERTY is missing (gradle.properties)." }
            val parsed = c.toIntOrNull()
            require(parsed != null && parsed in 1..MAX_CODE) {
                "$CODE_PROPERTY must be a whole number from 1 to $MAX_CODE, got '$c'."
            }
            return PoolPrintVersion(n, parsed)
        }

        /** Reads the version from the Gradle properties, failing the build with a readable message. */
        fun from(providers: ProviderFactory): PoolPrintVersion = try {
            parse(providers.gradleProperty(NAME_PROPERTY).orNull, providers.gradleProperty(CODE_PROPERTY).orNull)
        } catch (e: IllegalArgumentException) {
            throw GradleException(e.message ?: "Invalid app version.", e)
        }
    }
}
