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

package dev.kobalt.waybackmachineproxy.jvm.admin

import io.ktor.server.application.*
import io.ktor.server.html.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.*
import kotlinx.html.*
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@KtorDsl
fun Route.adminRoute(path: String) = route(path) {
    get {
        call.respondHtml {
            head {
                AdminRepository.autoRefreshInterval?.let { meta { httpEquiv = "refresh"; content = it.toString() } }
            }
            body {
                h1 { text("Administration") }
                hr()
                h2 { text("Timestamp") }
                form(action = "/submit", method = FormMethod.post) {
                    label {
                        text("Format: YYYY-MM-DD-HH-MM-SS")
                    }
                    input(name = "timestamp", type = InputType.dateTime) {
                        value = AdminRepository.timestamp.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    }
                    input(InputType.submit)
                }
                hr()
                h2 { text("Auto refresh") }
                form(action = "/submit", method = FormMethod.post) {
                    label {
                        text("Delay (sec) (disabled if blank or invalid)")
                    }
                    input(name = "refresh", type = InputType.number) {
                        value = AdminRepository.autoRefreshInterval?.toString().orEmpty()
                    }
                    input(InputType.submit)
                }
                hr()
                h2 { text("History") }
                ul {
                    AdminRepository.urlHistory.map {
                        li { a(it) { text(it) } }
                    }
                }
            }
        }
    }
    get {
        call.respondRedirect("/")
    }
    post("/submit") {
        call.receiveParameters().apply {
            this["timestamp"]?.let {
                AdminRepository.timestamp = LocalDateTime.parse(it, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
            }
            this["refresh"]?.let { AdminRepository.autoRefreshInterval = it.toIntOrNull() }
        }
        call.respondRedirect("/")
    }
}