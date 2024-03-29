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

import dev.kobalt.uid.lib.entity.Uid
import java.time.Instant

/** Entity representing a saved paged with defined URL, saved timestamp, response code, headers and binary data. */
data class PageEntity(
    val id: Long,
    val uid: Uid,
    val url: String,
    val timestamp: Instant,
    val code: Int,
    val headers: Map<String, String>,
    val data: ByteArray
) {

    companion object {
        /** Entity representing an empty page without any data.*/
        val empty = PageEntity(-1, Uid.none, "", Instant.MIN, 500, emptyMap(), byteArrayOf())
    }

    // Note: ByteArray requires overriding equals and hashcode for comparison!
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as PageEntity
        if (id != other.id) return false
        if (uid != other.uid) return false
        if (url != other.url) return false
        if (timestamp != other.timestamp) return false
        if (code != other.code) return false
        if (headers != other.headers) return false
        return data.contentEquals(other.data)
    }

    override fun hashCode(): Int {
        var result = id.hashCode()
        result = 31 * result + uid.hashCode()
        result = 31 * result + url.hashCode()
        result = 31 * result + timestamp.hashCode()
        result = 31 * result + code
        result = 31 * result + headers.hashCode()
        result = 31 * result + data.contentHashCode()
        return result
    }

}

