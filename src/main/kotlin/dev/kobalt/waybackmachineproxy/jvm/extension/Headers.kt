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

package dev.kobalt.waybackmachineproxy.jvm.extension

import io.ktor.http.*
import io.ktor.util.*

/** Header key that is exists if the page is archived. */
private val headerXArchiveSrc = "x-archive-src"

/** Prefix of header keys that contain original archived headers. */
private val headerXArchiveOriginalPrefix = "x-archive-orig-"

/** Returns true if headers contains a field which indicates that this belongs to archived page.*/
fun Headers.containsXArchiveValues() = contains(headerXArchiveSrc)

/** Returns a converted map that contains all values belonging to archived page. */
fun Headers.convertAndFilterToMap() =
    filter { key, _ -> key.startsWith(headerXArchiveOriginalPrefix) }.flattenEntries().let {
        if (contains(headerXArchiveSrc)) {
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