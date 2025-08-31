import kotlin.collections.listOf

// ftc-intellij-plugin/build.gradle.kts
plugins {
    id("java")
    id("org.jetbrains.intellij.platform") version "2.7.2"
//    id("org.openjfx.javafxplugin") version "0.1.0"
    kotlin("jvm") version "2.2.10"
}

group = "com.gentrifiedapps.ftclinter"
version = "0.0.4"

repositories {
    mavenCentral()
    // Repositories required by the IntelliJ Platform Gradle Plugin 2.x
    intellijPlatform { defaultRepositories() }
}
val javafxVersion = "21.0.5"
// Declare IntelliJ Platform dependencies and target Android Studio Narwhal Feature Drop 2025.1.2
dependencies {
//    implementation(files("C:\\Users\\grade\\Downloads\\repos\\FTC-Linter\\javafx.base.jar"))
//    implementation(files("C:\\Users\\grade\\Downloads\\repos\\FTC-Linter\\javafx.controls.jar"))
//    implementation(files("C:\\Users\\grade\\Downloads\\repos\\FTC-Linter\\javafx.fxml.jar"))
//    implementation(files("C:\\Users\\grade\\Downloads\\repos\\FTC-Linter\\javafx.graphics.jar"))
//    implementation(files("C:\\Users\\grade\\Downloads\\repos\\FTC-Linter\\javafx.media.jar"))
//    implementation(files("C:\\Users\\grade\\Downloads\\repos\\FTC-Linter\\javafx.swing.jar"))
//    implementation(files("C:\\Users\\grade\\Downloads\\repos\\FTC-Linter\\javafx.web.jar"))
//    listOf("base", "graphics", "controls", "web","swing","media").forEach { m ->
//        implementation("org.openjfx:javafx-$m:$javafxVersion:win")
//        implementation("org.openjfx:javafx-$m:$javafxVersion:mac")
//        implementation("org.openjfx:javafx-$m:$javafxVersion:linux")
//    }
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
//tasks.named<Jar>("jar") {
//    destinationDirectory.set(file("C:\\Users\\grade\\Downloads\\repos\\FTC-Linter\\releases"))
//}
tasks.named<Jar>("jar") {
    destinationDirectory.set(file("C:\\Users\\grade\\Downloads\\repos\\FTC-Linter\\releases"))
//    from("C:\\Users\\grade\\Downloads\\repos\\FTC-Linter\\javafx.base.jar")
//    from("C:\\Users\\grade\\Downloads\\repos\\FTC-Linter\\javafx.controls.jar")
//    from("C:\\Users\\grade\\Downloads\\repos\\FTC-Linter\\javafx.fxml.jar")
//    from("C:\\Users\\grade\\Downloads\\repos\\FTC-Linter\\javafx.graphics.jar")
//    from("C:\\Users\\grade\\Downloads\\repos\\FTC-Linter\\javafx.media.jar")
//    from("C:\\Users\\grade\\Downloads\\repos\\FTC-Linter\\javafx.swing.jar")
//    from("C:\\Users\\grade\\Downloads\\repos\\FTC-Linter\\javafx.web.jar")
}
