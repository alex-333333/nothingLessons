import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

application {
    mainClass.set("io.github.alex333333.bridge.pc.MainKt")
}

dependencies {
    // Standalone build (./gradlew -p bridge ...) names the root project "bridge"; from the repository root it is nested.
    implementation(project(if (rootProject.name == "bridge") ":protocol" else ":bridge:protocol"))
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    // The CIO engine has no HTTPS support, so the TLS server runs on Netty.
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.network.tls.certificates)
    implementation(libs.jmdns)
    runtimeOnly(libs.slf4j.simple) // Ktor and JmDNS log through SLF4J; show warnings and errors only (simplelogger.properties)
    testImplementation(libs.junit)
}
