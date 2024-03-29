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

import com.github.doyaaaaaken.kotlincsv.dsl.csvReader
import com.github.doyaaaaaken.kotlincsv.dsl.csvWriter

/** Instance of CSV parser. */
private val parser = csvReader()

/** Instance of CSV writer. */
private val writer = csvWriter()

/** Returns a map of strings from a string containing CSV data. */
fun String.fromCsv(): Map<String, String> =
    runCatching { parser.readAll(this).associate { it[0] to it[1] } }.getOrNull() ?: emptyMap()

/** Returns a string in CSV format from a map of strings. */
fun Map<String, String>.toCsv(): String {
    val test = map { listOf(it.key, it.value) }
    return writer.writeAllAsString(test)
}