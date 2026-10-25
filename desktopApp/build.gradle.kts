import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(project(":shared"))

    implementation(compose.desktop.currentOs)
    implementation(libs.kotlinx.coroutinesSwing)

    implementation(libs.compose.uiToolingPreview)
}

compose.desktop {
    application {
        mainClass = "com.yahyafati.poolprint.MainKt"

        nativeDistributions {
            // macOS only for now (D-18); add Msi/Deb here if a Windows or Linux build is ever wanted.
            targetFormats(TargetFormat.Dmg)
            packageName = "Pool Print"
            packageVersion = "1.0.0"
            // From suggestRuntimeModules: the jlink runtime otherwise lacks what sshj and PDFBox reach for.
            modules("java.instrument", "java.naming", "java.security.jgss", "java.sql", "jdk.unsupported")
            description = "Print PDFs on the Uni Freiburg TF pool printers"
            vendor = "Yahya Fati"

            macOS {
                bundleID = "com.yahyafati.poolprint"
                dockName = "Pool Print"
                iconFile.set(project.file("icons/PoolPrint.icns"))
                appCategory = "public.app-category.utilities"
                minimumSystemVersion = "12.0"

                // Lets Finder offer "Open With → Pool Print" for PDFs. Rank "Alternate" keeps Preview as the
                // default viewer; the handler itself is IncomingPdfs.installOpenFileHandler().
                infoPlist {
                    extraKeysRawXml = """
                        <key>CFBundleDocumentTypes</key>
                        <array>
                          <dict>
                            <key>CFBundleTypeName</key>
                            <string>PDF Document</string>
                            <key>CFBundleTypeRole</key>
                            <string>Viewer</string>
                            <key>LSHandlerRank</key>
                            <string>Alternate</string>
                            <key>LSItemContentTypes</key>
                            <array>
                              <string>com.adobe.pdf</string>
                            </array>
                          </dict>
                        </array>
                    """.trimIndent()
                }
            }
        }
    }
}
