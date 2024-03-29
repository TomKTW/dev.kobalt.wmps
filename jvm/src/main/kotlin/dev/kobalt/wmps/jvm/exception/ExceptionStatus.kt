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

package dev.kobalt.wmps.jvm.exception

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.server.response.*

/** Status response for any thrown exception caught by HTTP server. Stack trace is printed, response will return HTTP 500. */
fun StatusPagesConfig.exceptionStatus() = exception { call: ApplicationCall, cause: Throwable ->
    cause.printStackTrace()
    call.respond(HttpStatusCode.Companion.InternalServerError)
}