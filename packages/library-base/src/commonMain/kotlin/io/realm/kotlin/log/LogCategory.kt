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
package io.realm.kotlin.log

import io.realm.kotlin.internal.interop.RealmInterop
/**
 * Hierarchy
 *      Realm
 *      ├─► Storage
 *      │   ├─► Transaction
 *      │   ├─► Query
 *      │   ├─► Object
 *      │   └─► Notification
 *      ├─► Sync
 *      │   ├─► Client
 *      │   │   ├─► Session
 *      │   │   ├─► Changeset
 *      │   │   ├─► Network
 *      │   │   └─► Reset
 *      │   └─► Server
 *      ├─► App
 *      └─► Sdk
 */

/**
 * TODO
 */
public sealed class LogCategory(
    internal val name: String,
    internal val parent: LogCategory? = null
) {
    internal val path: List<String> = if (parent == null) listOf(name) else parent.path + name
    private val pathAsString = path.joinToString(".")

    /**
     * The current [LogLevel] for the category. Changing this will affect all registered loggers.
     */
    public var level: LogLevel
        get() = RealmInterop.realm_get_log_level_category(pathAsString).fromCoreLogLevel()
        set(value) = RealmInterop.realm_set_log_level_category(pathAsString, value.toCoreLogLevel())
}

public data object StorageLogCategory : LogCategory("Storage", RealmLog) {

    /**
     * TODO
     */
    public val TransactionLog: LogCategory = TransactionLogCategory
    /**
     * TODO
     */
    public val QueryLog: LogCategory = QueryLogCategory
    /**
     * TODO
     */
    public val ObjectLog: LogCategory = ObjectLogCategory
    /**
     * TODO
     */
    public val NotificationLog: LogCategory = NotificationLogCategory
}


public data object TransactionLogCategory: LogCategory("Transaction", StorageLogCategory)
public data object QueryLogCategory: LogCategory("Query", StorageLogCategory)
public data object ObjectLogCategory: LogCategory("Object", StorageLogCategory)
public data object NotificationLogCategory: LogCategory("Notification", StorageLogCategory)


public data object SyncLogCategory : LogCategory("Sync", RealmLog) {
    /**
     * TODO
     */
    public val ClientLog: ClientLogCategory = ClientLogCategory
    /**
     * TODO
     */
    public val ServerLog: LogCategory = ServerLogCategory
}

public data object ClientLogCategory : LogCategory("Client", ClientLogCategory) {
    /**
     * TODO
     */
    public val SessionLog: LogCategory = SessionLogCategory
    /**
     * TODO
     */
    public val ChangesetLog: LogCategory = ChangesetLogCategory
    /**
     * TODO
     */
    public val NetworkLog: LogCategory = NetworkLogCategory
    /**
     * TODO
     */
    public val ResetLog: LogCategory = ResetLogCategory
}

public data object SessionLogCategory: LogCategory("Session", ClientLogCategory)
public data object ChangesetLogCategory: LogCategory("Changeset", ClientLogCategory)
public data object NetworkLogCategory: LogCategory("Network", ClientLogCategory)
public data object ResetLogCategory: LogCategory("Reset", ClientLogCategory)
public data object ServerLogCategory: LogCategory("Server", ClientLogCategory)


public data object AppLogCategory: LogCategory("App", RealmLog)
public data object SdkLogCategory: LogCategory("SDK", RealmLog)