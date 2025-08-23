// ftc-intellij-plugin/build.gradle.kts
plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.7.2"
//    id("org.openjfx.javafxplugin") version "0.1.0"
    kotlin("jvm") version "2.1.20"
}

group = "com.gentrifiedapps.ftclinter"
version = "0.0.3"

repositories {
    mavenCentral()
    // Repositories required by the IntelliJ Platform Gradle Plugin 2.x
    intellijPlatform { defaultRepositories() }
}
val javafxVersion = "21.0.5"
// Declare IntelliJ Platform dependencies and target Android Studio Narwhal Feature Drop 2025.1.2
dependencies {
    listOf("base", "graphics", "controls", "web","swing","media").forEach { m ->
        implementation("org.openjfx:javafx-$m:$javafxVersion:win")
        implementation("org.openjfx:javafx-$m:$javafxVersion:mac")
        implementation("org.openjfx:javafx-$m:$javafxVersion:linux")
    }
    intellijPlatform {
        androidStudio("2025.1.2.11")
        bundledPlugin("com.intellij.java")
        bundledPlugin("org.jetbrains.kotlin")
    }
}

// Configure plugin metadata injection (since/until build)
intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild.set("251")
            untilBuild.set("251.*")
        }
    }
}

kotlin {
    jvmToolchain(17)
}

// build.gradle.kts
tasks.named<Jar>("jar") {
    destinationDirectory.set(file("C:\\Users\\grade\\Downloads\\repos\\FTC-Linter\\releases"))
}
