/*
 * dev.kobalt.waybackmachineproxy
 * Copyright (C) 2024 Tom.K
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

plugins {
    kotlin("jvm") version "1.9.22"
    id("com.github.johnrengelman.shadow") version "8.1.0"
}

group = "dev.kobalt"
version = "0000.00.00.00.00.00.000"

repositories {
    mavenCentral()
    maven(url = "https://maven.google.com")
    maven(url = "https://tom.kobalt.dev/maven/repository/")
}

kotlin {
    jvmToolchain(17)
}

fun kobalt(module: String, version: String) = "dev.kobalt:$module:$version"
fun ktor(module: String, version: String) = "io.ktor:ktor-$module:$version"
fun exposed(module: String, version: String) = "org.jetbrains.exposed:exposed-$module:$version"
fun general(module: String, version: String) = "$module:$version"
fun kotlinx(module: String, version: String) = "org.jetbrains.kotlinx:kotlinx-$module:$version"
fun kotlinw(module: String, version: String) = "org.jetbrains.kotlin-wrappers:kotlin-$module:$version"

fun DependencyHandler.httpClient() {
    implementation(ktor("client-apache", "2.3.0"))
}

fun DependencyHandler.httpServer() {
    implementation(ktor("server-netty", "2.3.0"))
    implementation(ktor("server-core", "2.3.0"))
    implementation(ktor("server-sessions", "2.3.0"))
    implementation(ktor("server-forwarded-header", "2.3.0"))
    implementation(ktor("server-default-headers", "2.3.0"))
    implementation(ktor("server-caching-headers", "2.3.0"))
    implementation(ktor("server-call-logging", "2.3.0"))
    implementation(ktor("server-compression", "2.3.0"))
    implementation(ktor("server-status-pages", "2.3.0"))
    implementation(ktor("server-html-builder", "2.3.0"))
    implementation(ktor("network-tls-certificates", "2.3.0"))
}

fun DependencyHandler.serialization() {
    implementation(kotlinx("serialization-json", "1.5.0"))
    implementation(kotlinx("serialization-core", "1.5.0"))
}

fun DependencyHandler.commandLineInterface() {
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
}

fun DependencyHandler.standardLibrary() {
    implementation(kotlin("stdlib", "1.8.20"))
}

fun DependencyHandler.logger() {
    implementation(general("org.slf4j:slf4j-simple", "2.0.3"))
}

fun DependencyHandler.htmlParser() {
    implementation(general("org.jsoup:jsoup", "1.15.4"))
}

fun DependencyHandler.htmlDsl() {
    implementation(kotlinx("html-jvm", "0.8.0"))
}

fun DependencyHandler.cssDsl() {
    implementation(kotlinw("css-jvm", "1.0.0-pre.473"))
}

fun DependencyHandler.ormFramework() {
    // https://github.com/JetBrains/Exposed/issues/1633
    implementation(exposed("core", "0.40.1"))
    implementation(exposed("jdbc", "0.40.1"))
    implementation(exposed("java-time", "0.40.1"))
}

fun DependencyHandler.database() {
    implementation(general("com.h2database:h2", "1.4.200"))
}

fun DependencyHandler.csvParser() {
    implementation(general("com.github.doyaaaaaken:kotlin-csv-jvm", "1.9.0"))
}

fun DependencyHandler.ifLet() {
    implementation(kobalt("iflet.lib", "0000.00.00.00.00.00.000"))
}

fun DependencyHandler.uid() {
    implementation(kobalt("uid.lib", "0000.00.00.00.00.00.000"))
}

fun DependencyHandler.charsetDetect() {
    implementation(general("com.github.albfernandez:juniversalchardet", "2.4.0"))
}

dependencies {
    standardLibrary()
    httpClient()
    httpServer()
    htmlParser()
    htmlDsl()
    cssDsl()
    logger()
    serialization()
    commandLineInterface()
    ormFramework()
    database()
    csvParser()
    ifLet()
    uid()
    charsetDetect()
}

tasks {
    named<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>("shadowJar") {
        archiveFileName.set("waybackmachineproxy.jar")
        mergeServiceFiles()
        // minimize()
        manifest {
            attributes("Main-Class" to "dev.kobalt.waybackmachineproxy.jvm.MainKt")
        }
    }
}