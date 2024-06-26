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

package dev.kobalt.wmps.jvm.extension

import io.ktor.http.*
import io.ktor.util.*

/** Header key that is exists if the page is archived. */
private const val headerXArchiveSrc = "x-archive-src"

/** Prefix of header keys that contain original archived headers. */
private const val headerXArchiveOriginalPrefix = "x-archive-orig-"

/** Returns true if headers contains a field which indicates that this belongs to archived page.*/
fun Headers.containsXArchiveValues() = contains(headerXArchiveSrc)

/** Returns a converted map that contains all values belonging to archived page. */
fun Headers.convertAndFilterToMap(): Map<String, String> {
    val map = mutableMapOf<String, String>()
    /* Filter headers that start with x-archive-orig- prefix. */
    filter { key, _ -> key.startsWith(headerXArchiveOriginalPrefix) }.flattenEntries()
        /* Remove x-archive-orig- prefix from headers. */
        .map { (key, value) -> key.removePrefix(headerXArchiveOriginalPrefix) to value }
        /* Remove headers that are not allowed to be modified. */
        .filter { (key, _) -> !HttpHeaders.isUnsafe(key) }
        /* Apply archived headers to new response. */
        .forEach { (key, value) ->
            map[key] = value
        }
    return map
}