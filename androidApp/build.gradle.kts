import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_11
    }
}
dependencies {
    implementation(project(":shared"))

    implementation(libs.androidx.activity.compose)

    implementation(libs.compose.uiToolingPreview)
    debugImplementation(libs.compose.uiTooling)
}

// Release signing (docs/release.md, Phase 1; D-22). The four `poolprint.signing.*` keys come from the root
// `local.properties` (git-ignored), each falling back to its `POOLPRINT_SIGNING_*` environment variable (CI).
// Gradle doesn't load `local.properties` itself, so it is read through `providers.fileContents`, which makes it a
// configuration-cache input. The values are never logged.
val localProperties = Properties().apply {
    providers.fileContents(rootProject.layout.projectDirectory.file("local.properties"))
        .asText.orNull?.let { load(it.reader()) }
}

fun signingValue(key: String, envVar: String): String? =
    localProperties.getProperty("poolprint.signing.$key")?.trim()?.takeIf { it.isNotEmpty() }
        ?: providers.environmentVariable(envVar).orNull?.trim()?.takeIf { it.isNotEmpty() }

class ReleaseSigning(val storeFile: File, val storePassword: String, val keyAlias: String, val keyPassword: String)

val releaseSigning: ReleaseSigning? = run {
    val values = linkedMapOf(
        "storeFile" to "POOLPRINT_SIGNING_STORE_FILE",
        "storePassword" to "POOLPRINT_SIGNING_STORE_PASSWORD",
        "keyAlias" to "POOLPRINT_SIGNING_KEY_ALIAS",
        "keyPassword" to "POOLPRINT_SIGNING_KEY_PASSWORD",
    ).mapValues { (key, envVar) -> signingValue(key, envVar) }
    val missing = values.filterValues { it == null }.keys
    when {
        missing.size == values.size -> null
        missing.isNotEmpty() -> throw GradleException(
            "Release signing is only partly configured; missing: " +
                missing.joinToString { "poolprint.signing.$it" } + " (local.properties or POOLPRINT_SIGNING_*).",
        )
        else -> {
            val storeFile = file(values.getValue("storeFile")!!)
            if (!storeFile.isFile) {
                throw GradleException("Release keystore not found: poolprint.signing.storeFile = $storeFile")
            }
            ReleaseSigning(
                storeFile = storeFile,
                storePassword = values.getValue("storePassword")!!,
                keyAlias = values.getValue("keyAlias")!!,
                keyPassword = values.getValue("keyPassword")!!,
            )
        }
    }
}

android {
    namespace = "com.yahyafati.poolprint"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.yahyafati.poolprint"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 1
        versionName = "1.0"
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/LICENSE.md"
        }
    }
    signingConfigs {
        if (releaseSigning != null) {
            create("release") {
                storeFile = releaseSigning.storeFile
                storePassword = releaseSigning.storePassword
                keyAlias = releaseSigning.keyAlias
                keyPassword = releaseSigning.keyPassword
            }
        }
    }
    buildTypes {
        release {
            // The release key when configured; otherwise the debug key, so contributors can still run
            // `make install-phone`. Such an APK is not for distribution, and the release script refuses it.
            signingConfig = signingConfigs.getByName(if (releaseSigning != null) "release" else "debug")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
}

// Warn at execution time, so the warning also shows when the configuration cache is reused.
if (releaseSigning == null) {
    tasks.matching { it.name in setOf("assembleRelease", "installRelease", "bundleRelease") }.configureEach {
        doFirst {
            logger.warn(
                "w: Release APK is debug-signed, not for distribution. Set the poolprint.signing.* keys in " +
                    "local.properties (docs/release.md, Phase 1).",
            )
        }
    }
}