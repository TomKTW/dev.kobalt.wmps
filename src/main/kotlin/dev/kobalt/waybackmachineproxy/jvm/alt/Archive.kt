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

package dev.kobalt.waybackmachineproxy.jvm.alt

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter
import dev.kobalt.uid.lib.database.UidTable
import dev.kobalt.uid.lib.entity.Uid
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
import io.ktor.server.plugins.cachingheaders.*
import io.ktor.server.plugins.callloging.*
import io.ktor.server.plugins.compression.*
import io.ktor.server.plugins.defaultheaders.*
import io.ktor.server.plugins.forwardedheaders.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import kotlinx.cli.ArgParser
import kotlinx.cli.ArgType
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consumeEach
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import org.slf4j.event.Level
import java.time.Instant
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.collections.set
import kotlin.concurrent.thread

class DatabaseRepository {

    private val database = Database.connect("jdbc:h2:./database;DB_CLOSE_DELAY=-1;", "org.h2.Driver")

    init {
        transaction { SchemaUtils.createMissingTablesAndColumns(PageTable) }
    }

    fun <T> transaction(transaction: Transaction.() -> T): T {
        return org.jetbrains.exposed.sql.transactions.transaction(database) { transaction(this) }
    }

}

private val parser = csvReader()
private val writer = csvWriter()
fun String.fromCsv(): Map<String, String> = parser.readAll(this).associate { it[0] to it[1] }

fun Map<String, String>.toCsv(): String {
    val test = map { listOf(it.key, it.value) }
    return writer.writeAllAsString(test)
}

class PageEntity(
    val id: Long,
    val uid: Uid,
    val url: String,
    val timestamp: Instant,
    val code: Int,
    val headers: Map<String, String>,
    val data: ByteArray
)

fun ResultRow.toPageEntity(): PageEntity {
    return PageEntity(
        id = this[PageTable.id].value,
        uid = this[PageTable.uid],
        url = this[PageTable.url],
        timestamp = this[PageTable.timestamp],
        code = this[PageTable.code],
        headers = this[PageTable.headers].fromCsv(),
        data = this[PageTable.data].bytes
    )
}

object PageTable : UidTable("page") {
    val url: Column<String> = varchar("url", 255)
    val timestamp: Column<Instant> = timestamp("timestamp")
    val code: Column<Int> = integer("code")
    val headers: Column<String> = varchar("headers", 65536)
    val data: Column<ExposedBlob> = blob("data")
}

class PageRepository(
    val database: DatabaseRepository
) {

    fun selectItemByUrlAndTimestamp(url: String?, timestamp: Instant): PageEntity? = database.transaction {
        url?.let {
            PageTable.select { (PageTable.url eq it) and (PageTable.timestamp eq timestamp) }.singleOrNull()
                ?.toPageEntity()
        }
    }

    fun selectItem(uid: Uid): PageEntity? = database.transaction {
        PageTable.select {
            (PageTable.uid eq uid)
        }.singleOrNull()?.toPageEntity()
    }

    fun insertItem(url: String, timestamp: Instant, code: Int, headers: Map<String, String>, data: ByteArray) =
        database.transaction {
            PageTable.insertAndGetId {
                it[PageTable.url] = url
                it[PageTable.timestamp] = timestamp
                it[PageTable.code] = code
                it[PageTable.headers] = headers.toCsv()
                it[PageTable.data] = ExposedBlob(data)
            }.let { PageTable.select { PageTable.id eq it }.singleOrNull()?.toPageEntity() }
        }

    fun updateItem(
        uid: Uid,
        url: String,
        timestamp: Instant,
        code: Int,
        headers: Map<String, String>,
        data: ByteArray
    ) = database.transaction {
        selectItem(uid)?.let { old ->
            PageTable.update(where = { PageTable.uid eq uid }) {
                if (old.url != url) it[PageTable.url] = url
                if (old.timestamp != timestamp) it[PageTable.timestamp] = timestamp
                if (old.code != code) it[PageTable.code] = code
                if (old.headers != headers) it[PageTable.headers] = headers.toCsv()
                if (!old.data.contentEquals(data)) it[PageTable.data] = ExposedBlob(data)
            }
        } ?: throw Exception()
    }

    val scope = CoroutineScope(Dispatchers.IO)
    val channel = Channel<Deferred<Any>>(1)

    init {
        CoroutineScope(Dispatchers.IO).launch {
            channel.consumeEach {
                runCatching { it.start() }
            }
        }
    }

    suspend fun submit(url: String, timestamp: Instant): PageEntity = coroutineScope {
        selectItemByUrlAndTimestamp(url, timestamp) ?: runCatching {
            val task = CoroutineScope(Dispatchers.IO).async(start = CoroutineStart.LAZY) {
                get(url, timestamp).let {
                    insertItem(it.url, it.timestamp, it.code, it.headers, it.data)
                } ?: throw Exception()
            }
            channel.send(task)
            task.await()
        }.getOrElse { PageEntity(0, Uid.none, url, timestamp, 500, emptyMap(), byteArrayOf()) }
    }

    fun deleteItem(uid: Uid) = database.transaction {
        selectItem(uid)?.let { PageTable.deleteWhere { PageTable.uid eq uid } }
            ?: throw Exception()
    }

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

    val HttpResponse.responseTimeDifference get() = responseTime.timestamp - requestTime.timestamp
    suspend fun get(url: String, timestamp: Instant): PageEntity {
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
            response.readBytes()
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

val Application.pageRepository: PageRepository get() = attributes[AttributeKey("PageRepository")]

class PageRepositoryConfiguration()

val PageRepositoryPlugin = createApplicationPlugin(
    name = "PageRepository",
    createConfiguration = ::PageRepositoryConfiguration
) {
    application.attributes.put(
        AttributeKey("PageRepository"),
        PageRepository(application.attributes[AttributeKey<DatabaseRepository>("Database")])
    )
}

class DatabasePluginConfiguration()

val DatabaseRepositoryPlugin = createApplicationPlugin(
    name = "DatabaseRepository",
    createConfiguration = ::DatabasePluginConfiguration
) {
    application.attributes.put(
        AttributeKey("Database"), DatabaseRepository()
    )
}

class ArchiveInterceptorConfiguration(
    var timestamp: String = ""
)

val ArchiveInterceptorPlugin = createApplicationPlugin(
    name = "ArchiveInterceptor",
    createConfiguration = ::ArchiveInterceptorConfiguration
) {
    onCall { call ->
        val time = ZonedDateTime.parse(
            "2005-01-01-00-00-00",
            DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss").withZone(ZoneOffset.UTC)
        ).toInstant()
        if (call.request.uri.contains("archive.org")) {
            throw Exception("Test")
        } else {
            call.application.pageRepository.submit(call.request.uri, time).let { page ->
                call.respondBytes(
                    bytes = page.data,
                    status = HttpStatusCode.fromValue(page.code),
                    contentType = page.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
                )
            }
        }
    }
}

fun main(args: Array<String>) {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val parser = ArgParser("maven")
    val timestamp by parser.option(ArgType.String, "timestamp", null, null)
    val httpServerPort by parser.option(ArgType.Int, "httpServerPort", null, null)
    val httpServerHost by parser.option(ArgType.String, "httpServerHost", null, null)
    val adminUrl by parser.option(ArgType.String, "adminUrl", null, null)
    parser.parse(args)
    ifLet(httpServerPort, httpServerHost) { port, host ->
        val server = embeddedServer(Netty, port, host) {
            install(ForwardedHeaders)
            install(DefaultHeaders)
            install(CachingHeaders)
            install(CallLogging) { level = Level.INFO }
            install(Compression) { gzip() }
            install(ArchiveInterceptorPlugin)
            install(DatabaseRepositoryPlugin)
            install(PageRepositoryPlugin)
            install(StatusPages) {
                exception { call: ApplicationCall, cause: Throwable ->
                    if (cause.message != "Test") {
                        call.respond(cause.message.orEmpty())
                        cause.printStackTrace()
                    } else {

                    }
                }
            }
        }
        onShutdownRequest { server.stop(0, 10, TimeUnit.SECONDS) }
        server.start(true)
    }
}

fun onShutdownRequest(method: () -> Unit) =
    Runtime.getRuntime().addShutdownHook(thread(start = false) { method.invoke() })
