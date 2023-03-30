plugins {
    kotlin("jvm") version "1.8.0"
    id("com.github.johnrengelman.shadow") version "8.1.0"
}

group = "dev.kobalt"
version = "0000.00.00.00.00.00.000"

repositories {
    mavenCentral()
    maven(url = "https://maven.google.com")
    maven(url = "https://tom.kobalt.dev/maven/repository/")
}

fun kobalt(module: String, version: String) = "dev.kobalt:$module:$version"
fun ktor(module: String, version: String) = "io.ktor:ktor-$module:$version"
fun exposed(module: String, version: String) = "org.jetbrains.exposed:exposed-$module:$version"
fun general(module: String, version: String) = "$module:$version"
fun kotlinx(module: String, version: String) = "org.jetbrains.kotlinx:kotlinx-$module:$version"
fun kotlinw(module: String, version: String) = "org.jetbrains.kotlin-wrappers:kotlin-$module:$version"

fun DependencyHandler.httpClient() {
    implementation(ktor("client-apache", "2.1.3"))
}

fun DependencyHandler.httpServer() {
    implementation(ktor("server-cio", "2.2.3"))
    implementation(ktor("server-netty", "2.1.3"))
    implementation(ktor("server-core", "2.2.3"))
    implementation(ktor("server-sessions", "2.2.3"))
    implementation(ktor("server-forwarded-header", "2.2.3"))
    implementation(ktor("server-default-headers", "2.2.3"))
    implementation(ktor("server-caching-headers", "2.2.3"))
    implementation(ktor("server-call-logging", "2.2.3"))
    implementation(ktor("server-compression", "2.2.3"))
    implementation(ktor("server-status-pages", "2.2.3"))
    implementation(ktor("server-html-builder", "2.1.3"))
}

fun DependencyHandler.serialization() {
    implementation(kotlinx("serialization-json", "1.4.1"))
    implementation(kotlinx("serialization-core", "1.4.1"))
}

fun DependencyHandler.commandLineInterface() {
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.5")
}

fun DependencyHandler.standardLibrary() {
    implementation(kotlin("stdlib", "1.8.0"))
}

fun DependencyHandler.logger() {
    implementation(general("org.slf4j:slf4j-simple", "2.0.3"))
}

fun DependencyHandler.htmlParser() {
    implementation(general("org.jsoup:jsoup", "1.14.3"))
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
    implementation(general("com.github.doyaaaaaken:kotlin-csv-jvm", "1.8.0"))
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
    implementation("org.jetbrains.xodus:xodus-openAPI:2.0.1")
    implementation("org.jetbrains.xodus:xodus-vfs:2.0.1")
    implementation("org.jetbrains.xodus:xodus-environment:2.0.1")
    implementation("dev.kobalt:iflet.lib:0000.00.00.00.00.00.000")
    implementation("dev.kobalt:uid.lib:0000.00.00.00.00.00.000")
    implementation("com.github.albfernandez:juniversalchardet:2.4.0")
    implementation("io.ktor:ktor-network-tls-certificates:2.2.3")


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