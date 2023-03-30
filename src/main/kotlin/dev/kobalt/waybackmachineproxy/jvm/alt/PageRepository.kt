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

package dev.kobalt.waybackmachineproxy.jvm.alt

import dev.kobalt.uid.lib.entity.Uid
import dev.kobalt.waybackmachineproxy.jvm.database.DatabaseRepository
import dev.kobalt.waybackmachineproxy.jvm.extension.toCsv
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.statements.api.ExposedBlob
import java.time.Instant


class PageRepository(
    val database: DatabaseRepository
) {

    fun selectItemByUrlAndTimestamp(url: String?, timestamp: Instant): PageEntity? = database.transaction {
        url?.let {
            PageTable.select { (PageTable.url eq it) and (PageTable.timestamp eq timestamp) }.singleOrNull()
                ?.toPageEntity()
        }
    }

    fun selectItem(uid: Uid): PageEntity? = database.transaction {
        PageTable.select {
            (PageTable.uid eq uid)
        }.singleOrNull()?.toPageEntity()
    }

    fun insertItem(url: String, timestamp: Instant, code: Int, headers: Map<String, String>, data: ByteArray) =
        database.transaction {
            PageTable.insertAndGetId {
                it[PageTable.url] = url
                it[PageTable.timestamp] = timestamp
                it[PageTable.code] = code
                it[PageTable.headers] = headers.toCsv()
                it[PageTable.data] = ExposedBlob(data)
            }.let { PageTable.select { PageTable.id eq it }.singleOrNull()?.toPageEntity() }
        }

    fun updateItem(
        uid: Uid,
        url: String,
        timestamp: Instant,
        code: Int,
        headers: Map<String, String>,
        data: ByteArray
    ) = database.transaction {
        selectItem(uid)?.let { old ->
            PageTable.update(where = { PageTable.uid eq uid }) {
                if (old.url != url) it[PageTable.url] = url
                if (old.timestamp != timestamp) it[PageTable.timestamp] = timestamp
                if (old.code != code) it[PageTable.code] = code
                if (old.headers != headers) it[PageTable.headers] = headers.toCsv()
                if (!old.data.contentEquals(data)) it[PageTable.data] = ExposedBlob(data)
            }
        } ?: throw Exception()
    }

    fun deleteItem(uid: Uid) = database.transaction {
        selectItem(uid)?.let { PageTable.deleteWhere { PageTable.uid eq uid } }
            ?: throw Exception()
    }

}

