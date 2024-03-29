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

package dev.kobalt.waybackmachineproxy.jvm.page

import dev.kobalt.waybackmachineproxy.jvm.extension.fromCsv
import org.jetbrains.exposed.sql.ResultRow

/** Returns page entity from given result. */
fun ResultRow.toPageEntity(): PageEntity {
    return PageEntity(
        id = this[PageTable.id].value,
        uid = this[PageTable.uid],
        url = this[PageTable.url],
        timestamp = this[PageTable.timestamp],
        code = this[PageTable.code],
        headers = this[PageTable.headers].fromCsv(),
        data = this[PageTable.data].bytes
    )
}