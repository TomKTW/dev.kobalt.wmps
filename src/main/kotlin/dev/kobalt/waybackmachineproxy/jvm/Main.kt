/*
 * dev.kobalt.waybackmachineproxy
 * Copyright (C) 2022 Tom.K
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

import dev.kobalt.waybackmachineproxy.jvm.admin.AdminRepository
import dev.kobalt.waybackmachineproxy.jvm.admin.adminRoute
import dev.kobalt.waybackmachineproxy.jvm.archive.archiveInterceptor
import dev.kobalt.waybackmachineproxy.jvm.cache.CacheRepository
import dev.kobalt.waybackmachineproxy.jvm.extension.ifLet
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    val parser = ArgParser("waybackmachineproxy")
    val timestampDescription =
        "Timestamp that will be used to fetch archived pages captured at nearest time. Format: YYYYMMDDHHMMSS"
    val timestamp by parser.option(ArgType.String, "timestamp", null, timestampDescription)
    val httpServerPort by parser.option(ArgType.Int, "httpServerPort", null, null)
    val httpServerHost by parser.option(ArgType.String, "httpServerHost", null, null)
    val adminUrl by parser.option(ArgType.String, "adminUrl", null, null)
    /* Parse all arguments. */
    parser.parse(args)
    /* Client for incoming HTTP requests. */
    val client = HttpClient(Apache) {
        /* Disable terminating a response if its status code is not successful. */
        expectSuccess = false
        /* Apply request timeouts to 1 minute. */
        install(HttpTimeout) {
            connectTimeoutMillis = 60 * 1000
            requestTimeoutMillis = 60 * 1000
            socketTimeoutMillis = 60 * 1000
        }
    }
    ifLet(httpServerPort, httpServerHost, timestamp) { port, host, time ->
        LocalDateTime.parse(time, DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))?.let { AdminRepository.timestamp = it }
        /* Prepare proxy server. */
        val server = embeddedServer(Netty, configure = {
            /* Limit the amount of requests that will not be processed immediately. */
            requestQueueLimit = 128
            /* Apply timeout for sending response to 1 minute. */
            responseWriteTimeoutSeconds = 60
        }, port = port, host = host) {
            install(Routing) {
                adminUrl?.let { adminRoute(it) }
            }
            archiveInterceptor(adminUrl, client)
            /* Install status pages feature to intercept received exceptions. */
            install(StatusPages) {
                exception { call: ApplicationCall, cause: Throwable ->
                    /* Respond with internal server error response. */
                    call.respond(HttpStatusCode.InternalServerError)
                    call.application.environment.log.warn("${cause.javaClass.name}: ${cause.message}: ${call.request.uri}")
                }
            }
        }
        // Add shutdown hook for proper termination.
        Runtime.getRuntime().addShutdownHook(thread(start = false) {
            server.stop(0, 10, TimeUnit.SECONDS)
            CacheRepository.vfs.shutdown()
            CacheRepository.env.close()
        })
        server.start(true)
    }
}