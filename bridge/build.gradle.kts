// Standalone build only (./gradlew -p bridge ...): puts the Kotlin plugins on one shared classpath.
// From the repository root this file is ignored; the root build.gradle.kts does the same.
plugins {
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
