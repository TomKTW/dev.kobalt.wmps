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

package dev.kobalt.waybackmachineproxy.jvm.archive

import dev.kobalt.waybackmachineproxy.jvm.admin.AdminRepository
import dev.kobalt.waybackmachineproxy.jvm.extension.acquireUse
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlin.collections.filter
import kotlin.collections.forEach
import kotlin.collections.map
import kotlin.collections.mutableMapOf
import kotlin.collections.removeLast
import kotlin.collections.set

fun Application.archiveInterceptor(adminUrl: String?, client: HttpClient) {
    /* Header key that is exists if the page is archived. */
    val headerXArchiveSrc = "x-archive-src"
    /* Prefix of header keys that contain original archived headers. */
    val headerXArchiveOriginalPrefix = "x-archive-orig-"
    // Semaphore is used to hold new requests until previous ones are done.
    val semaphore = Semaphore(4)
    /* Intercept the received HTTP request. */
    intercept(io.ktor.server.application.ApplicationCallPipeline.Call) {
        if (adminUrl?.let { call.request.uri.startsWith(it) } == true) {
            proceed(); return@intercept
        }
        val time = AdminRepository.timestamp?.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))!!
        // Block any further requests until current ones are done.
        semaphore.acquireUse {
            runBlocking {
                dev.kobalt.waybackmachineproxy.jvm.admin.AdminRepository.urlHistory.add(0, call.request.uri)
                if (dev.kobalt.waybackmachineproxy.jvm.admin.AdminRepository.urlHistory.size > 10) {
                    dev.kobalt.waybackmachineproxy.jvm.admin.AdminRepository.urlHistory.removeLast()
                }
                val archiveUrl = "https://web.archive.org/web/${time}id_/${call.request.uri}"
                // Check if URL was cached before. If it was, use data from it.
                if (dev.kobalt.waybackmachineproxy.jvm.cache.CacheRepository.exists(time, call.request.uri)) {
                    val data =
                        dev.kobalt.waybackmachineproxy.jvm.cache.CacheRepository.readFileData(time, call.request.uri)
                    val status =
                        dev.kobalt.waybackmachineproxy.jvm.cache.CacheRepository.readFileStatus(time, call.request.uri)
                    val headers =
                        dev.kobalt.waybackmachineproxy.jvm.cache.CacheRepository.readFileHeaders(time, call.request.uri)
                    headers.forEach {
                        call.response.header(it.key, it.value)
                    }
                    call.respondBytes(bytes = data,
                        status = io.ktor.http.HttpStatusCode.fromValue(status),
                        contentType = headers[io.ktor.http.HttpHeaders.ContentType]?.let {
                            io.ktor.http.ContentType.parse(
                                it
                            )
                        })
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
                                .filter { (key, _) -> !io.ktor.http.HttpHeaders.isUnsafe(key) }
                                /* Apply archived headers to new response. */.forEach { (key, value) ->
                                    map[key] = value
                                    call.response.header(key, value)
                                }
                            val data = response.readBytes()
                            dev.kobalt.waybackmachineproxy.jvm.cache.CacheRepository.create(
                                time,
                                call.request.uri,
                                data,
                                response.status.value,
                                map
                            )
                            call.respondBytes(bytes = data,
                                status = response.status,
                                contentType = response.headers[io.ktor.http.HttpHeaders.ContentType]?.let {
                                    io.ktor.http.ContentType.parse(
                                        it
                                    )
                                })
                        } else {
                            val data = response.readBytes()
                            call.respondBytes(bytes = data,
                                status = response.status,
                                contentType = response.headers[io.ktor.http.HttpHeaders.ContentType]?.let {
                                    io.ktor.http.ContentType.parse(
                                        it
                                    )
                                })
                        }
                    }
                }
                finish()
            }
        }
    }
}