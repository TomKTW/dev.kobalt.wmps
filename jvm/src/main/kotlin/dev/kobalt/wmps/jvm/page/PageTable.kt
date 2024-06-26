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

package dev.kobalt.wmps.jvm.page

import dev.kobalt.uid.lib.database.UidTable
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.javatime.timestamp
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import java.time.Instant

/** Table for page entity. */
object PageTable : UidTable("page") {
    val url: Column<String> = text("url")
    val timestamp: Column<Instant> = timestamp("timestamp")
    val code: Column<Int> = integer("code")
    val headers: Column<String> = text("headers")
    val data: Column<ExposedBlob> = blob("data")
} // Note: YES, it does use BLOB for fuck sakes, this was set up for goddamn convenience!