// Build logic shared by the app modules. Kept tiny: only what needs a unit test (docs/release.md, Phase 2).
// Run its tests with `./gradlew :buildSrc:test` (part of `make test-all`).
plugins {
    `embedded-kotlin`
}

repositories {
    mavenCentral()
}

dependencies {
    compileOnly(gradleApi())
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
