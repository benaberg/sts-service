plugins {
    kotlin("jvm") version "2.0.20"
}

group = "fi.benaberg.sts"
version = "1.0.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.json:json:20090211")
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