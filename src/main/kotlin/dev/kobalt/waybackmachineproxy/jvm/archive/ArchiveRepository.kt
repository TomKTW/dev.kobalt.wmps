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
import dev.kobalt.waybackmachineproxy.jvm.cache.CacheRepository
import dev.kobalt.waybackmachineproxy.jvm.extension.acquireUse
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.util.*
import io.ktor.util.logging.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import org.jsoup.Jsoup
import java.net.URI

class ArchivedPage(
    val data: ByteArray,
    val status: Int,
    val headers: Map<String, String>
)

object ArchiveRepository {

    /* Header key that is exists if the page is archived. */
    val headerXArchiveSrc = "x-archive-src"

    /* Prefix of header keys that contain original archived headers. */
    val headerXArchiveOriginalPrefix = "x-archive-orig-"

    // Semaphore is used to hold new requests until previous ones are done.
    val semaphore = Semaphore(4)
    val timestamp: String get() = AdminRepository.timestamp?.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))!!

    val queue = mutableListOf<String>()
    fun updateHistory(url: String) {
        AdminRepository.urlHistory.add(0, url)
        if (AdminRepository.urlHistory.size > 10) {
            AdminRepository.urlHistory.removeLast()
        }
    }

    val scope = CoroutineScope(Dispatchers.IO)


    fun schedule(client: HttpClient, log: Logger) {
        scope.launch {
            while (true) {
                runCatching {
                    if (queue.isEmpty()) {
                        // log.info("Fetch: None")
                    } else {
                        val link = queue.removeFirst()
                        log.info("Fetch: $link")
                        load(link, client, log, false)
                    }
                    delay(100)
                }
            }
        }
    }

    private fun addToQueue(url: String, data: ByteArray) {
        val test = Jsoup.parse(data.decodeToString())
        val images = test.select("img")
        val links = test.select("a[href]")

        /*queue.addAll(images.map {
            val link = it.attr("src")
            val originUri = URI.create(url)
            val uri = URI.create(link)
            return@map if (uri.isAbsolute) {
                link
            } else {
                originUri.resolve(link).toString()
            }
        })*/

        queue.addAll(links.map {
            val link = it.attr("href")
            val originUri = URI.create(url)
            val uri = URI.create(link)
            return@map if (uri.isAbsolute) {
                link
            } else {
                originUri.resolve(link).toString()
            }
        })
    }

    suspend fun load(url: String, client: HttpClient, log: Logger, lookup: Boolean = true): ArchivedPage {
        var page = ArchivedPage(
            ByteArray(0),
            500,
            emptyMap()
        )
        // Block any further requests until current ones are done.
        semaphore.acquireUse {
            runBlocking {
                updateHistory(url)
                val archiveUrl = "https://web.archive.org/web/${timestamp}id_/${url}"
                // Check if URL was cached before. If it was, use data from it.
                if (CacheRepository.exists(timestamp, url)) {
                    val data =
                        CacheRepository.readFileData(timestamp, url)
                    val status =
                        CacheRepository.readFileStatus(timestamp, url)
                    val headers =
                        CacheRepository.readFileHeaders(timestamp, url)
                    log.info("Cached: $url")
                    page = ArchivedPage(
                        data,
                        status,
                        headers
                    )
                } else {
                    log.info("Request: $archiveUrl")
                    client.prepareRequest(archiveUrl).execute { response ->
                        val timeDiff = response.responseTime.timestamp - response.requestTime.timestamp
                        log.info("${response.status}: ${response.request.url} (${timeDiff} ms)")
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
                                }
                            val data = response.readBytes()
                            CacheRepository.create(
                                timestamp,
                                url,
                                data,
                                response.status.value,
                                map
                            )
                            page = ArchivedPage(
                                data,
                                response.status.value,
                                map
                            )
                            if (response.contentType()?.match(ContentType.Text.Html) == true && lookup) {
                                addToQueue(url, data)
                            }
                        } else {
                            page = ArchivedPage(
                                response.readBytes(),
                                response.status.value,
                                response.headers.filter { key, _ -> key.startsWith(headerXArchiveOriginalPrefix) }
                                    .flattenEntries().toMap()
                            )
                        }
                    }
                }
            }
        }
        return page
    }

}