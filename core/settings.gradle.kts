// Lets the pure-Kotlin core be built and tested on its own (no Android SDK needed):
//   ./gradlew -p core test
// When built from the repository root, this file is ignored and :core is a normal subproject.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "core"
