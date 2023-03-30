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

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

fun String.toInstant(format: String) = runCatching {
    ZonedDateTime.parse(this, DateTimeFormatter.ofPattern(format).withZone(ZoneOffset.UTC)).toInstant()
}.getOrNull()

fun String.toCharset(): Charset? = Charset.forName(this)

fun Charset.parse(bytes: ByteArray) = decode(ByteBuffer.wrap(bytes).clear()).toString()