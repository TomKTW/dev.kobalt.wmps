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

package dev.kobalt.wmps.jvm.database

import dev.kobalt.wmps.jvm.page.PageTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.Transaction

/** Repository for managing database for archived content. */
class DatabaseRepository {

    /** Database that is used for storing archived content. */
    private val database = Database.connect("jdbc:h2:./database;DB_CLOSE_DELAY=-1;", "org.h2.Driver")
    // Use for in-memory database.
    // private val database = Database.connect("jdbc:h2:mem:test;DB_CLOSE_DELAY=-1;", "org.h2.Driver")


    init {
        // Create table if it's missing.
        transaction { SchemaUtils.createMissingTablesAndColumns(PageTable) }
    }

    /** Submit a database transaction. */
    fun <T> transaction(transaction: Transaction.() -> T): T {
        return org.jetbrains.exposed.sql.transactions.transaction(database) { transaction(this) }
    }

}