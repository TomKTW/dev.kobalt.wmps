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

import io.ktor.client.*
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

fun Application.archiveInterceptor(adminUrl: String?, client: HttpClient) {
    /* Intercept the received HTTP request. */
    intercept(io.ktor.server.application.ApplicationCallPipeline.Call) {
        if (adminUrl?.let { call.request.uri.startsWith(it) } == true) {
            proceed()
        } else {
            val page = ArchiveRepository.load(call.request.uri, client, application.log)
            call.respondBytes(bytes = page.data,
                status = HttpStatusCode.fromValue(page.status),
                contentType = page.headers[io.ktor.http.HttpHeaders.ContentType]?.let {
                    io.ktor.http.ContentType.parse(
                        it
                    )
                })
        }
    }
}