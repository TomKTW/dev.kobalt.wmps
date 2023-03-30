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

package dev.kobalt.waybackmachineproxy.jvm.archiveinterceptor

import dev.kobalt.uid.lib.entity.Uid
import dev.kobalt.waybackmachineproxy.jvm.alt.PageEntity
import dev.kobalt.waybackmachineproxy.jvm.alt.PageRepository
import dev.kobalt.waybackmachineproxy.jvm.alt.pageRepository
import dev.kobalt.waybackmachineproxy.jvm.extension.parse
import dev.kobalt.waybackmachineproxy.jvm.extension.toCharset
import io.ktor.client.*
import io.ktor.client.engine.apache.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import org.mozilla.universalchardet.UniversalDetector
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class ArchiveInterceptor(
    val pageRepository: PageRepository,
    val timestamp: Instant
) {

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


    /* Header key that is exists if the page is archived. */
    val headerXArchiveSrc = "x-archive-src"

    /* Prefix of header keys that contain original archived headers. */
    val headerXArchiveOriginalPrefix = "x-archive-orig-"

    val scope = CoroutineScope(Dispatchers.IO)
    val channel = Channel<Deferred<Any>>(1)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            channel.consumeEach {
                runCatching { it.start() }
            }
        }
    }

    suspend fun submit(uri: String): ArchiveInterceptorResult = runCatching {
        return if (uri.contains("archive.org")) {
            throw Exception("Test")
        } else {
            ArchiveInterceptorResult.Success(/*pageRepository.selectItemByUrlAndTimestamp(uri, timestamp) ?:*/
                runCatching {
                    CoroutineScope(Dispatchers.IO).async(start = CoroutineStart.LAZY) {
                        request(uri, timestamp).let {
                            pageRepository.insertItem(it.url, it.timestamp, it.code, it.headers, it.data)
                        } ?: throw Exception("Test")
                    }.let { channel.send(it); it.await() }
                }.getOrElse { PageEntity(0, Uid.none, uri, timestamp, 500, emptyMap(), byteArrayOf()) })
        }
    }.getOrElse { ArchiveInterceptorResult.Failure(it) }

    suspend fun request(url: String, timestamp: Instant): PageEntity {
        val timestampValue = DateTimeFormatter.ofPattern("yyyyMMddhhmmss").withZone(ZoneOffset.UTC).format(timestamp)
        val urlString = "https://web.archive.org/web/${timestampValue}id_/${url}"
        val response = client.prepareRequest(urlString).execute()
        val containsXArchiveSrcHeader = response.headers.contains(headerXArchiveSrc)
        val headers =
            response.headers.filter { key, _ -> key.startsWith(headerXArchiveOriginalPrefix) }.flattenEntries().let {
                if (containsXArchiveSrcHeader) {
                    val map = mutableMapOf<String, String>()
                    /* Remove x-archive-orig- prefix from headers. */
                    it.map { (key, value) -> key.removePrefix(headerXArchiveOriginalPrefix) to value }
                        /* Remove headers that are not allowed to be modified. */
                        .filter { (key, _) -> !HttpHeaders.isUnsafe(key) }
                        /* Apply archived headers to new response. */
                        .forEach { (key, value) -> map[key] = value }
                    map
                } else {
                    it.toMap()
                }
            }
        if (!response.headers.contains(headerXArchiveSrc)) {
            return PageEntity(
                -1,

                Uid.none,
                url,
                timestamp,
                500,
                emptyMap(),
                byteArrayOf()
            )
        }
        val data = when (response.contentType()?.contentType) {
            ContentType.Text.Html.contentType -> {
                val bytes = response.readBytes()
                val encoding = bytes.inputStream().use { UniversalDetector.detectCharset(it) }.toCharset()!!
                encoding.parse(bytes).replace("https://", "http://").toByteArray(encoding)
                /*response.bodyAsChannel().toInputStream().use {
                    val bytes = it.readAllBytes()
                    val encoding = bytes.inputStream().use { UniversalDetector.detectCharset(it) }.toCharset()!!
                    val string = encoding.parse(bytes)
                    Jsoup.parse(string).let {
                        it.toString().replace("https://", "http://").toByteArray(Charset.forName(encoding))
                    }
                }*/

            }

            else -> response.readBytes()
        }
        urlString.toString()
        response.toString()
        containsXArchiveSrcHeader.toString()
        headers.toString()
        return PageEntity(
            -1,
            Uid.none,
            url,
            timestamp,
            response.status.value,
            response.headers.flattenEntries().toMap(),
            data
        )
        /*if (response.headers.contains(headerXArchiveSrc)) {
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
            PageEntity(
                -1,
                Uid.none,
                response.request.url.toString(),
                response.status.value,
                map,
                response.readBytes()
            )
        } else {
            PageEntity(
                -1,
                Uid.none,
                response.request.url.toString(),
                response.status.value,
                response.headers.filter { key, _ -> key.startsWith(headerXArchiveOriginalPrefix) }.flattenEntries().toMap(),
                response.readBytes()
            )
        }*/
    }
}

sealed class ArchiveInterceptorResult {
    class Success(val page: PageEntity) : ArchiveInterceptorResult()
    class Failure(val exception: Throwable) : ArchiveInterceptorResult()
}

class ArchiveInterceptorConfiguration(
    var timestamp: Instant = Instant.now()
) {
    companion object {
        const val name = "ArchiveInterceptor"
    }
}

val HttpResponse.responseTimeDifference get() = responseTime.timestamp - requestTime.timestamp
val Application.archiveInterceptor: ArchiveInterceptor get() = attributes[AttributeKey(ArchiveInterceptorConfiguration.name)]


val ArchiveInterceptorPlugin = createApplicationPlugin(
    name = ArchiveInterceptorConfiguration.name,
    createConfiguration = ::ArchiveInterceptorConfiguration
) {
    application.attributes.put(
        AttributeKey(ArchiveInterceptorConfiguration.name),
        ArchiveInterceptor(application.pageRepository, this.pluginConfig.timestamp)
    )
    onCall { call ->
        call.application.archiveInterceptor.submit(call.request.uri).let { result ->
            when (result) {
                is ArchiveInterceptorResult.Success -> call.respondBytes(
                    bytes = result.page.data,
                    status = HttpStatusCode.fromValue(result.page.code),
                    contentType = result.page.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
                )

                is ArchiveInterceptorResult.Failure -> throw result.exception
            }
        }
    }
}