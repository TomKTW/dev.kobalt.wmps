plugins {
    java
    kotlin("jvm") version "1.4.31"
    id("com.github.johnrengelman.shadow") version "6.0.0"
}

group = "dev.kobalt"
version = "0000.00.00.00.00.00.000"

java.sourceCompatibility = JavaVersion.VERSION_1_6
java.targetCompatibility = JavaVersion.VERSION_1_6

repositories {
    mavenCentral()
}

dependencies {
    implementation(kotlin("stdlib", "1.4.31"))
    implementation("org.slf4j:slf4j-simple:1.7.29")
    implementation("io.ktor:ktor-client-apache:1.5.2")
    implementation("io.ktor:ktor-server-netty:1.5.2")
}

tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveFileName.set("waybackmachine.jar")
        mergeServiceFiles()
        manifest {
            attributes("Main-Class" to "dev.kobalt.web.waybackmachine.MainKt")
        }
    }
}