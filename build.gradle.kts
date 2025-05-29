plugins {
    kotlin("jvm") version "2.0.20"
}

group = "fi.benaberg.sts"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.slf4j:slf4j-nop:2.0.17")
    implementation("org.json:json:20250517")
    implementation("org.java-websocket:Java-WebSocket:1.6.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
}

tasks {
    withType<Jar> {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE

        manifest {
            attributes["Main-Class"] = "fi.benaberg.sts.service.STSServiceKt"
        }

        configurations["compileClasspath"].forEach { file: File ->
            from(zipTree(file.absoluteFile))
        }
    }
}