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

import org.mozilla.universalchardet.UniversalDetector
import java.nio.charset.Charset

/** Returns character set from given byte array if detected. */
fun ByteArray.parseCharset(): Charset? {
    return inputStream().use {
        runCatching { UniversalDetector.detectCharset(it) }.onFailure { it.printStackTrace() }.getOrNull()
    }?.toCharset()
}