// Lets the pure-JVM Bridge modules be built and tested on their own (no Android SDK needed):
//   ./gradlew -p bridge :protocol:test
// When built from the repository root, this file is ignored and :bridge:protocol and :bridge:pc are normal subprojects.
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

rootProject.name = "bridge"
include(":protocol", ":pc")
