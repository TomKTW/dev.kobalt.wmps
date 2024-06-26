/*
 * dev.kobalt.wmps
 * Copyright (C) 2024 Tom.K
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

package dev.kobalt.wmps.jvm.archive

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.util.*
import java.time.Instant

/** Plugin to provide archive repository. */
val ArchivePlugin = createApplicationPlugin(
    name = ArchiveConfiguration.name,
    createConfiguration = ::ArchiveConfiguration
) {
    // Store archive repository object into application.
    application.attributes.put(
        AttributeKey(ArchiveConfiguration.name),
        ArchiveRepository(application, this.pluginConfig.timestamp)
    )
    // Intercept incoming call request event.
    onCall { call ->
        if (call.request.uri == "http://www.towns.com/dorms/tom/bblboy.htm") {
            call.respondRedirect("http://www.toptown.com/dorms/rick/bblboy.htm")
        } else {
            // On call event, submit URL to repository and respond with data received from it.
            call.application.archiveRepository.submit(call.request.uri).let { page ->
                page.headers.forEach { (key, value) -> call.response.header(key, value) }
                call.respondBytes(
                    bytes = page.data,
                    status = HttpStatusCode.fromValue(page.code),
                    contentType = page.headers[HttpHeaders.ContentType]?.let { ContentType.parse(it) }
                )
            }
        }
    }
}

/** Configuration for archive plugin. */
class ArchiveConfiguration(
    /** Timestamp to be used for fetching content from specific timeline. */
    var timestamp: Instant = Instant.now()
) {
    companion object {
        const val name = "Archive"
    }
}

/** Archive repository instance. */
val Application.archiveRepository: ArchiveRepository get() = attributes[AttributeKey(ArchiveConfiguration.name)]