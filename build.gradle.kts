import kotlin.collections.listOf

// ftc-intellij-plugin/build.gradle.kts
plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.10.5"
    kotlin("jvm") version "2.1.20"
}

group = "com.gentrifiedapps.ftclinter"
version = "0.0.9"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        androidStudio("2025.1.2.11")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("251")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

tasks.named<Jar>("jar") {
    destinationDirectory.set(file("C:\\Users\\grade\\Downloads\\repos\\FTC-Linter\\releases"))
}
