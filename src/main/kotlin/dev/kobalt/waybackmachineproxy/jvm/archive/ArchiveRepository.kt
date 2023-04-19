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

package dev.kobalt.waybackmachineproxy.jvm.archive

import dev.kobalt.uid.lib.entity.Uid
import dev.kobalt.waybackmachineproxy.jvm.extension.*
import dev.kobalt.waybackmachineproxy.jvm.page.PageEntity
import dev.kobalt.waybackmachineproxy.jvm.page.pageRepository
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import org.slf4j.LoggerFactory
import java.net.URLEncoder
import java.time.Instant

/** Repository for managing archived content from Wayback Machine. */
class ArchiveRepository(
    private val application: Application,
    private val timestamp: Instant
) {

    /** Coroutine scope used for queuing requests. */
    private val scope = CoroutineScope(Dispatchers.IO)

    /** Channel of async requests for limiting request count. */
    private val channel = Channel<Deferred<Any>>(1)

    /** Logger for Ktor HTTP client. */
    private val logger = LoggerFactory.getLogger("ktor.application")

    /** HTTP client for fetching archived content from Wayback Machine. */
    private val client = HttpClient(Apache) {
        /* Disable terminating a response if its status code is not successful. */
        expectSuccess = false
        /* Apply request timeouts to 1 minute. */
        install(HttpTimeout) {
            connectTimeoutMillis = 60 * 1000
            requestTimeoutMillis = 60 * 1000
            socketTimeoutMillis = 60 * 1000
        }
    }

    init {
        // Launch a coroutine that will let the channel launch incoming async coroutines.
        scope.launch { channel.consumeEach { runCatching { it.start() } } }
    }

    /** Submits given URL to return page entity that contains archived content at given timestamp. */
    suspend fun submit(url: String): PageEntity {
        // If page entity for given URL and timestamp already exists, return it, otherwise, fetch it.
        return application.pageRepository.selectItemByUrlAndTimestamp(url, timestamp) ?: runCatching {
            // Prepare async coroutine for fetching content.
            CoroutineScope(Dispatchers.IO).async(start = CoroutineStart.LAZY) {
                // Fetch the content for given URL and timestamp.
                request(url, timestamp).let {
                    // Store the page if the response was valid.
                    application.pageRepository.insertItem(it.url, it.timestamp, it.code, it.headers, it.data)
                } ?: throw Exception("Test") // Throw the exception if response didn't return valid content.
            }.let {
                // Send it to the channel queue for processing and wait for response.
                channel.send(it); it.await()
            }
        }.onFailure { it.printStackTrace() }.getOrElse {
            // On failure, return empty object.
            PageEntity.empty.copy(url = url, timestamp = timestamp)
        }
    }

    /** Returns page entity with content archived from Wayback Machine at specified timestamp. */
    suspend fun request(url: String, timestamp: Instant): PageEntity {
        // Log request.
        logger.info("Request: GET - $url")
        // Timestamp value in format to be used in URL.
        val timestampValue = timestamp.format("yyyyMMddhhmmss")
        // URL string that will be requested.
        val urlString = "https://web.archive.org/web/${timestampValue}id_/${URLEncoder.encode(url, "UTF-8")}"
        // Response that has been executed with provided request.
        val response = client.prepareRequest(urlString).execute()
        // Difference between response and request time.
        val timeDifference = response.responseTime.timestamp - response.requestTime.timestamp
        // Map of response headers.
        val headers = response.headers.convertAndFilterToMap() // TODO: Fix?
        // Binary content of received response.
        val bytes = response.readBytes()
        // Page entity object that will be used to return data.
        val page = PageEntity(-1, Uid.none, url, timestamp, 500, emptyMap(), byteArrayOf())
        // Log response.
        logger.info("${response.status.description}: ${response.request.method} - $url (${timeDifference}ms)")
        return when {
            // If response is not from archived content, but from actual Wayback Machine website, return blank.
            !response.headers.containsXArchiveValues() -> page
            // Otherwise, return actual page with content.
            else -> page.copy(
                code = response.status.value,
                headers = headers,
                data = when (response.contentType()?.contentType) {
                    // If content type is HTML, process it to match original archived content as much as possible or keep it as is.
                    ContentType.Text.Html.contentType -> runCatching {
                        // Get original charset and convert it. Also, replace any HTTPS links with HTTP to avoid broken usage.
                        bytes.parseCharset()?.let { it.parse(bytes).replace("https://", "http://").toByteArray(it) }
                    }.onFailure { it.printStackTrace() }.getOrNull() ?: bytes

                    else -> bytes
                }
            )
        }
    }
}

