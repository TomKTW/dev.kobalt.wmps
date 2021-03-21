/*
 * Wayback Machine Proxy Server
 * Copyright (C) 2021 Tom.K
 *
 * This file is part of Wayback Machine Proxy Server project.
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
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package dev.kobalt.web.waybackmachine

import io.ktor.application.*
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.features.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.features.*
import io.ktor.http.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.util.*

fun main(args: Array<String>) {
    /* Timestamp that will be used to fetch archived pages captured at nearest time. Format: YYYYMMDDHHMMSS */
    val timestamp = args.firstOrNull() ?: "0"
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
    /* Prepare and start proxy server. */
    embeddedServer(Netty, configure = {
        /* Limit the amount of requests that will not be processed immediately. */
        requestQueueLimit = 128
        /* Apply timeout for sending response to 1 minute. */
        responseWriteTimeoutSeconds = 60
    }, port = 8080) {
        /* Intercept the received HTTP request. */
        intercept(ApplicationCallPipeline.Call) {
            val archiveUrl = "https://web.archive.org/web/${timestamp}id_/${call.request.uri}"
            log.info("Request: $archiveUrl")
            client.request<HttpStatement>(archiveUrl).execute { response ->
                val timeDiff = response.responseTime.timestamp - response.requestTime.timestamp
                log.info("${response.status}: ${response.request.url} (${timeDiff} ms)")
                /* Respond with content from request if headers contain x-archive-src that should exist only on archived pages. */
                if (response.headers.contains(headerXArchiveSrc)) {
                    /* Filter headers that start with x-archive-orig- prefix. */
                    response.headers.filter { key, _ -> key.startsWith(headerXArchiveOriginalPrefix) }.flattenEntries()
                        /* Remove x-archive-orig- prefix from headers. */
                        .map { (key, value) -> key.removePrefix(headerXArchiveOriginalPrefix) to value }
                        /* Remove headers that are not allowed to be modified. */
                        .filter { (key, _) -> !HttpHeaders.isUnsafe(key) }
                        /* Apply archived headers to new response. */
                        .forEach { (key, value) -> call.response.header(key, value) }
                    call.respondBytes(
                        bytes = response.readBytes(),
                        status = response.status,
                        contentType = response.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
                    )
                } else {
                    call.respond(response.status)
                }
            }
            finish()
        }
        /* Install status pages feature to intercept received exceptions. */
        install(StatusPages) {
            exception<Throwable> {
                /* Respond with internal server error response. */
                log.warn("${it.javaClass.name}: ${it.message}: ${call.request.uri}")
                call.respond(HttpStatusCode.InternalServerError)
            }
        }
    }.start(wait = true)
}