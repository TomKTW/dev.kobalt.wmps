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

import dev.kobalt.iflet.lib.extension.ifLet
import dev.kobalt.waybackmachineproxy.jvm.archive.ArchivePlugin
import dev.kobalt.waybackmachineproxy.jvm.database.DatabasePlugin
import dev.kobalt.waybackmachineproxy.jvm.exception.exceptionStatus
import dev.kobalt.waybackmachineproxy.jvm.extension.onShutdownRequest
import dev.kobalt.waybackmachineproxy.jvm.extension.toInstant
import dev.kobalt.waybackmachineproxy.jvm.page.PagePlugin
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
import org.slf4j.event.Level
import java.time.Instant
import java.util.*
import java.util.concurrent.TimeUnit

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val parser = ArgParser("waybackmachineproxy")
    val timestamp by parser.option(ArgType.String, "timestamp", null, null)
    val httpServerPort by parser.option(ArgType.Int, "httpServerPort", null, null)
    val httpServerHost by parser.option(ArgType.String, "httpServerHost", null, null)
    parser.parse(args)
    val server = Netty
    val defaultTimestamp = timestamp?.toInstant("yyyy-MM-dd-HH-mm-ss") ?: Instant.now()
    ifLet(httpServerPort, httpServerHost) { port, host ->
        embeddedServer(server, port, host) {
            install(ForwardedHeaders)
            install(DefaultHeaders)
            install(CachingHeaders)
            install(CallLogging) { level = Level.INFO }
            install(Compression) { gzip() }
            install(StatusPages) { exceptionStatus() }
            install(DatabasePlugin)
            install(PagePlugin)
            install(ArchivePlugin) { this.timestamp = defaultTimestamp }
        }.apply {
            onShutdownRequest { stop(0, 10, TimeUnit.SECONDS) }; start(true)
        }
    }
}