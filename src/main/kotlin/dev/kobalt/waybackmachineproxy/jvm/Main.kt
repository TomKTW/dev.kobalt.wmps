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

import dev.kobalt.waybackmachineproxy.jvm.cache.CacheRepository
import dev.kobalt.waybackmachineproxy.jvm.extension.acquireUse
import dev.kobalt.waybackmachineproxy.jvm.extension.ifLet
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

fun main(args: Array<String>) {
    val parser = ArgParser("waybackmachineproxy")
    val timestampDescription =
        "Timestamp that will be used to fetch archived pages captured at nearest time. Format: YYYYMMDDHHMMSS"
    val timestamp by parser.option(ArgType.String, "timestamp", null, timestampDescription)
    val httpServerPort by parser.option(ArgType.Int, "httpServerPort", null, null)
    val httpServerHost by parser.option(ArgType.String, "httpServerHost", null, null)
    /* Parse all arguments. */
    parser.parse(args)
    /* Header key that is exists if the page is archived. */
    val headerXArchiveSrc = "x-archive-src"
    /* Prefix of header keys that contain original archived headers. */
    val headerXArchiveOriginalPrefix = "x-archive-orig-"
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
    // Semaphore is used to hold new requests until previous ones are done.
    val semaphore = Semaphore(4)
    ifLet(httpServerPort, httpServerHost, timestamp) { port, host, time ->
        /* Prepare proxy server. */
        val server = embeddedServer(Netty, configure = {
            /* Limit the amount of requests that will not be processed immediately. */
            requestQueueLimit = 128
            /* Apply timeout for sending response to 1 minute. */
            responseWriteTimeoutSeconds = 60
        }, port = port, host = host) {
            /* Intercept the received HTTP request. */
            intercept(ApplicationCallPipeline.Call) {
                // Block any further requests until current ones are done.
                semaphore.acquireUse {
                    runBlocking {
                        val archiveUrl = "https://web.archive.org/web/${time}id_/${call.request.uri}"
                        // Check if URL was cached before. If it was, use data from it.
                        if (CacheRepository.exists(time, call.request.uri)) {
                            val data = CacheRepository.readFileData(time, call.request.uri)
                            val status = CacheRepository.readFileStatus(time, call.request.uri)
                            val headers = CacheRepository.readFileHeaders(time, call.request.uri)
                            headers.forEach {
                                call.response.header(it.key, it.value)
                            }
                            call.respondBytes(bytes = data,
                                status = HttpStatusCode.fromValue(status),
                                contentType = headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) })
                        } else {
                            call.application.environment.log.info("Request: $archiveUrl")
                            client.prepareRequest(archiveUrl).execute { response ->
                                val timeDiff = response.responseTime.timestamp - response.requestTime.timestamp
                                call.application.environment.log.info("${response.status}: ${response.request.url} (${timeDiff} ms)")
                                /* Respond with content from request if headers contain x-archive-src that should exist only on archived pages. */
                                if (response.headers.contains(headerXArchiveSrc)) {
                                    val map = mutableMapOf<String, String>()
                                    /* Filter headers that start with x-archive-orig- prefix. */
                                    response.headers.filter { key, _ -> key.startsWith(headerXArchiveOriginalPrefix) }
                                        .flattenEntries()
                                        /* Remove x-archive-orig- prefix from headers. */
                                        .map { (key, value) -> key.removePrefix(headerXArchiveOriginalPrefix) to value }
                                        /* Remove headers that are not allowed to be modified. */
                                        .filter { (key, _) -> !HttpHeaders.isUnsafe(key) }
                                        /* Apply archived headers to new response. */.forEach { (key, value) ->
                                            map[key] = value
                                            call.response.header(key, value)
                                        }
                                    val data = response.readBytes()
                                    CacheRepository.create(time, call.request.uri, data, response.status.value, map)
                                    call.respondBytes(bytes = data,
                                        status = response.status,
                                        contentType = response.headers[HttpHeaders.ContentType]?.let {
                                            ContentType.parse(
                                                it
                                            )
                                        })
                                } else {
                                    call.respond(response.status)
                                }
                            }
                        }
                        finish()
                    }
                }
            }
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