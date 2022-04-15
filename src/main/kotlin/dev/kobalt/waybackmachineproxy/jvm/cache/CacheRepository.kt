/*
 * dev.kobalt.waybackmachineproxy
 * Copyright (C) 2022 Tom.K
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

package dev.kobalt.waybackmachineproxy.jvm.cache

import jetbrains.exodus.ExodusException
import jetbrains.exodus.env.Environments
import jetbrains.exodus.vfs.VirtualFileSystem
import kotlinx.serialization.json.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException

object CacheRepository {

    val env = Environments.newInstance("./cache")
    val vfs = VirtualFileSystem(env)

    fun exists(timestamp: String, url: String): Boolean {
        var exists = false
        env.executeInTransaction { txn ->
            exists = vfs.openFile(txn, "$timestamp/$url/file", false) != null
        }
        return exists
    }

    fun readFileData(timestamp: String, url: String): ByteArray {
        var data: ByteArray? = null
        env.executeInTransaction { txn ->
            val file = vfs.openFile(txn, "$timestamp/$url/file", false)!!
            try {
                DataInputStream(vfs.readFile(txn, file)).use { data = it.readAllBytes() }
            } catch (e: IOException) {
                throw ExodusException(e)
            }
        }
        return data!!
    }

    fun readFileStatus(timestamp: String, url: String): Int {
        var status = 404
        env.executeInTransaction { txn ->
            val file = vfs.openFile(txn, "$timestamp/$url/status", false)!!
            try {
                DataInputStream(vfs.readFile(txn, file)).use { status = it.readInt() }
            } catch (e: IOException) {
                throw ExodusException(e)
            }
        }
        return status
    }

    fun readFileHeaders(timestamp: String, url: String): Map<String, String> {
        var headers: Map<String, String> = emptyMap()
        env.executeInTransaction { txn ->
            val headersFile = vfs.openFile(txn, "$timestamp/$url/headers", false)!!
            try {
                DataInputStream(vfs.readFile(txn, headersFile)).use { input ->
                    headers = input.readAllBytes().contentToString().let {
                        val json = Json.encodeToJsonElement(it)
                        (json as? JsonArray)?.map {
                            val entry = (it as? JsonObject)?.entries?.firstOrNull()
                            entry?.key.orEmpty() to (entry?.value as? JsonPrimitive)?.contentOrNull.orEmpty()
                        }
                    }?.toMap().orEmpty()
                }
            } catch (e: IOException) {
                throw ExodusException(e)
            }
        }
        return headers
    }

    fun create(timestamp: String, url: String, data: ByteArray, status: Int, headers: Map<String, String>) {
        env.executeInTransaction { txn ->
            val file = vfs.createFile(txn, "$timestamp/$url/file")
            try {
                DataOutputStream(vfs.writeFile(txn, file)).use { output ->
                    output.write(data)
                }
            } catch (e: IOException) {
                throw ExodusException(e)
            }
            val statusFile = vfs.createFile(txn, "$timestamp/$url/status")
            try {
                DataOutputStream(vfs.writeFile(txn, statusFile)).use { output ->
                    output.writeInt(status)
                }
            } catch (e: IOException) {
                throw ExodusException(e)
            }
            val headersFile = vfs.createFile(txn, "$timestamp/$url/headers")
            try {
                DataOutputStream(vfs.writeFile(txn, headersFile)).use { output ->
                    output.writeChars(Json.encodeToJsonElement(headers).toString())
                }
            } catch (e: IOException) {
                throw ExodusException(e)
            }
        }
    }

}