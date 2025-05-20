plugins {
    kotlin("jvm") version "2.0.20"
}

group = "fi.benaberg.sts"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks {
    withType<Jar> {
        manifest {
            attributes["Main-Class"] = "fi.benaberg.sts.STSServiceKt"
        }
        configurations["compileClasspath"].forEach { file: File ->
            from(zipTree(file.absoluteFile))
        }
    }
}