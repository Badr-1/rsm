plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "1.9.0"
    id("org.jlleitschuh.gradle.ktlint") version "11.6.1"
    application
}

group = "org.resume"
version = "2.0.3"

repositories {
    mavenCentral()
    maven { setUrl("https://jitpack.io") }
}

dependencies {
    implementation("com.github.kotlin-inquirer:kotlin-inquirer:0.1.0")
    implementation("org.fusesource.jansi:jansi:2.4.0")
    implementation("org.jline:jline:3.21.0")
    implementation("com.github.ajalt.clikt:clikt:4.2.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.5.1")
    implementation("org.eclipse.jgit:org.eclipse.jgit:7.2.1.202505142326-r")
    implementation("org.slf4j:slf4j-simple:2.0.13")
    implementation("net.java.dev.jna:jna:5.14.0")
    implementation("org.jetbrains:markdown:0.4.1")

    testImplementation(kotlin("test"))
    testImplementation("io.kotest:kotest-runner-junit5:5.6.2")
}
application {
    mainClass.set("MainKt")
}

tasks.test {
    useJUnitPlatform()
}
kotlin {
    jvmToolchain(17)
}

tasks.jar {
    manifest {
        attributes["Main-Class"] = "MainKt"
    }
    configurations["compileClasspath"].forEach { file: File ->
        from(zipTree(file.absoluteFile)) {
            exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA", "META-INF/MANIFEST.MF")
        }
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

// Offline bundle: the JAR plus the install scripts. After unzipping, run
// `./install.sh --jar rsm-<version>.jar` (or install.ps1 on Windows).
tasks.register<Zip>("createDistribution") {
    dependsOn(tasks.jar)
    from(tasks.jar)
    from(listOf("install.sh", "uninstall.sh")) {
        filePermissions {
            unix("rwxr-xr-x")
        }
    }
    from("install.ps1")
    archiveFileName.set("${rootProject.name}-$version.zip")
}
tasks.named("check") {
    dependsOn("ktlintCheck")
}
