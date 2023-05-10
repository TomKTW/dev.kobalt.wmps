/*
 * dev.kobalt.waybackmachineproxy
 * Copyright (C) 2023 Tom.K
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

package dev.kobalt.waybackmachineproxy.jvm

import dev.kobalt.waybackmachineproxy.jvm.archive.ArchivePlugin
import dev.kobalt.waybackmachineproxy.jvm.database.DatabasePlugin
import dev.kobalt.waybackmachineproxy.jvm.exception.exceptionStatus
import dev.kobalt.waybackmachineproxy.jvm.extension.onShutdownRequest
import dev.kobalt.waybackmachineproxy.jvm.extension.toInstant
import dev.kobalt.waybackmachineproxy.jvm.page.PagePlugin
import io.ktor.network.tls.certificates.*
import io.ktor.network.tls.extensions.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.statuspages.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import org.slf4j.LoggerFactory
import org.slf4j.event.Level
import java.io.File
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit
import javax.security.auth.x500.X500Principal

/** Main method of this application. */
fun main(args: Array<String>) {
    // Apply UTC as default time zone.
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    // Parse program arguments.
    val parser = ArgParser("waybackmachineproxy")
    val timestamp by parser.option(ArgType.String, "timestamp", null, null)
    val httpServerPort by parser.option(ArgType.Int, "httpServerPort", null, null)
    val httpServerHost by parser.option(ArgType.String, "httpServerHost", null, null)
    val httpsServerPort by parser.option(ArgType.Int, "httpsServerPort", null, null)
    val httpsServerHost by parser.option(ArgType.String, "httpsServerHost", null, null)
    parser.parse(args)
    // Generate keystore file for currently broken HTTPS server implementation.
    val keyStoreFile = File("keystore.jks")
    val keyStoreAlias = "WaybackMachine"
    val keyStorePassword = ""
    val keyStorePrivateKeyPassword = ""
    val keyStore = buildKeyStore {
        certificate(keyStoreAlias) {
            subject = X500Principal("CN=Wayback Machine Proxy Server, OU=Tom Kobalt, O=Kobalt Lab, C=HR")
            hash = HashAlgorithm.SHA1
            sign = SignatureAlgorithm.RSA
            keySizeInBits = 1024
            password = keyStorePrivateKeyPassword
            domains = listOf("*")
        }
    }.also { it.saveToFile(keyStoreFile, keyStorePassword) }
    // Prepare HTTP server.
    val environment = applicationEngineEnvironment {
        log = LoggerFactory.getLogger("ktor.application")
        connector {
            port = httpServerPort ?: 8080
            host = httpServerHost ?: "0.0.0.0"
        }
        sslConnector(
            keyStore = keyStore,
            keyAlias = keyStoreAlias,
            keyStorePassword = { keyStorePassword.toCharArray() },
            privateKeyPassword = { keyStorePrivateKeyPassword.toCharArray() }
        ) {
            port = httpsServerPort ?: 8443
            host = httpsServerHost ?: "0.0.0.0"
            enabledProtocols = listOf(/*"TLSv1",*/"TLSv1.3")
            keyStorePath = keyStoreFile
        }
        module {
            install(ForwardedHeaders)
            install(DefaultHeaders)
            install(CachingHeaders)
            install(CallLogging) { level = Level.INFO }
            install(Compression) { gzip() }
            install(StatusPages) { exceptionStatus() }
            install(DatabasePlugin)
            install(PagePlugin)
            install(ArchivePlugin) { this.timestamp = timestamp?.toInstant("yyyy-MM-dd-HH-mm-ss") ?: Instant.now() }
        }
    }
    // Netty is used as HTTP server as others seem to have broken behavior.
    embeddedServer(Netty, environment) {}.apply {
        onShutdownRequest { stop(0, 10, TimeUnit.SECONDS) }; start(true)
    }
}