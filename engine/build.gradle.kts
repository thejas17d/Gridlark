import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
}

dependencies {
    testImplementation(libs.junit)
}

tasks.test {
    maxHeapSize = "2g"
    providers.gradleProperty("stats").orNull?.let { systemProperty("tashlark.stats", it) }
    systemProperty("tashlark.samples", providers.gradleProperty("samples").getOrElse("25"))
    testLogging { events("failed"); showStandardStreams = true; exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL }
}
