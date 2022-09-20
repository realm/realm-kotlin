/*
 * Copyright 2021 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.realm.kotlin.test.mongodb.util

import io.realm.kotlin.mongodb.sync.SyncSession
import kotlinx.serialization.json.JsonObject

/**
 * Wrapper around App Services Server Admin functions needed for tests.
 */
interface AppAdmin {
    val clientAppId: String

    /**
     * Deletes all currently registered and pending users on the App Services Application .
     *
     * Warning: This will run using `runBlocking`.
     */
    suspend fun deleteAllUsers()

    /**
     * Deactivates Sync on the server. This will not cause existing sessions to fail,
     * they will instead attempt to reconnect later.
     */
    suspend fun pauseSync()

    /**
     * Activates Sync on the server.
     */
    suspend fun startSync()

    /**
     * Trigger a client reset by deleting user-related files in the server. It is possible to
     * specify whether recovery mode is enabled with [withRecoveryModeDisabled]. It is also possible
     * to add a [block] to execute assertions after the client reset even has been triggered and
     * before the session es enabled again.
     */
    suspend fun triggerClientReset(
        session: SyncSession,
        userId: String,
        withRecoveryModeDisabled: Boolean = false,
        block: (suspend () -> Unit)? = null
    )

    /**
     * Changes the permissions for sync. Receives a lambda block which with your test logic.
     * It will safely revert to the original permissions even when an exception was thrown.
     */
    suspend fun changeSyncPermissions(permissions: SyncPermissions, block: suspend () -> Unit)

    /**
     * Set whether or not automatic confirmation is enabled.
     */
    suspend fun setAutomaticConfirmation(enabled: Boolean)

    /**
     * Set whether or not custom confirmation is enabled.
     */
    suspend fun setCustomConfirmation(enabled: Boolean)

    /**
     * Set whether or not using a reset function is available.
     */
    suspend fun setResetFunction(enabled: Boolean)

    /**
     * Return the JSON configuration for the Email/Password auth provider.
     */
    suspend fun getAuthConfigData(): String

    /**
     * Insert a MongoDB document which will be eventually synced as RealmObject.
     */
    suspend fun insertDocument(clazz: String, json: String): JsonObject?

    /**
     * Query the specified database and collection
     */
    suspend fun queryDocument(clazz: String, query: String): JsonObject?

    fun closeClient()
}

class AppAdminImpl(
    private val baasClient: AppServicesClient,
    val app: BaasApp
) : AppAdmin {
    override val clientAppId: String
        get() = app.clientAppId

    override suspend fun deleteAllUsers() {
        baasClient.run {
            app.deleteAllUsers()
        }
    }

    override suspend fun pauseSync() {
        baasClient.run {
            app.pauseSync()
        }
    }

    override suspend fun startSync() {
        baasClient.run {
            app.startSync()
        }
    }

    override suspend fun triggerClientReset(
        session: SyncSession,
        userId: String,
        withRecoveryModeDisabled: Boolean,
        block: (suspend () -> Unit)?
    ) {
        baasClient.run {
            val originalRecoveryMode = app.isRecoveryModeDisabled()

            try {
                session.downloadAllServerChanges()
                session.pause()

                app.setRecoveryModeDisabled(withRecoveryModeDisabled)

                block?.invoke()
                app.triggerClientReset(userId)

                session.resume()
            } catch (e: Throwable) {
                val message = e.message
                message.plus("")
                val cause = e.cause
                cause?.message
            } finally {
                // Restore original recovery mode and make sure Sync is not disabled again
                app.setRecoveryModeDisabled(originalRecoveryMode)
            }
        }
    }

    override suspend fun setAutomaticConfirmation(enabled: Boolean) {
        baasClient.run {
            app.setAutomaticConfirmation(enabled)
        }
    }

    override suspend fun setCustomConfirmation(enabled: Boolean) {
        baasClient.run {
            app.setCustomConfirmation(enabled)
        }
    }

    override suspend fun setResetFunction(enabled: Boolean) {
        baasClient.run {
            app.setResetFunction(enabled)
        }
    }

    override suspend fun getAuthConfigData(): String =
        baasClient.run {
            app.getAuthConfigData()
        }

    override suspend fun insertDocument(clazz: String, json: String): JsonObject? =
        baasClient.run {
            app.insertDocument(clazz, json)
        }

    override suspend fun queryDocument(clazz: String, query: String): JsonObject? =
        baasClient.run {
            app.queryDocument(clazz, query)
        }

    override fun closeClient() {
        baasClient.closeClient()
    }
}
