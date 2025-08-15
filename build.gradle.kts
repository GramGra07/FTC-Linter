// ftc-intellij-plugin/build.gradle.kts
plugins {
    // IntelliJ Platform Gradle Plugin (2.x)
    id("org.jetbrains.intellij.platform") version "2.7.2"
    kotlin("jvm") version "2.1.20"

//    kotlin("jvm")
}

group = "com.gentrifiedapps.ftclinter"
version = "0.0.0"

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        local("C:\\Users\\grade\\AppData\\Local\\Programs\\Android Studio")

//         androidStudio("2025.1.2.11")

        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }
}

// IntelliJ Platform (2.x) configuration — replaces old `patchPluginXml {}` usage
intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("241")
            untilBuild.set("251.*")
        }
    }
    // Configure Plugin Verifier IDEs so :verifyPlugin can run
    pluginVerification {
        ides {
            // Android Studio Beetle (2025.1) build line used by the sandbox in this project
            ide("IC-2022.1")
        }
    }
}
kotlin {
    jvmToolchain(17)
}

// build.gradle.kts
tasks.named<Jar>("jar") {
    destinationDirectory.set(file("C:\\Users\\grade\\Downloads"))
}

tasks.withType<Zip>().configureEach {
    if (name == "buildPlugin") {
        from("build/tmp/patchPluginXml") {
            include("plugin.xml")
            into("/")
        }
    }
}
