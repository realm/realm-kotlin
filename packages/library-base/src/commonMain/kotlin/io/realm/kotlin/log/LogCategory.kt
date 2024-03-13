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
    internal val parent: LogCategory? = null,
) {
    internal val path: List<String> = if (parent == null) listOf(name) else parent.path + name
    internal val pathAsString = path.joinToString(".")
    internal val children: MutableMap<String, LogCategory> = mutableMapOf()

    init {
        parent?.children?.put(name, this)
    }

    public companion object {
        internal fun fromCoreValue(categoryValue: String): LogCategory {
            val path = categoryValue.split(".").asSequence()

            var category :LogCategory = Realm

            path.onEach { nextElement ->
                category = category.children[nextElement]!!
            }

            return category
        }

        public val Realm: RealmLogCategory = RealmLogCategory
    }
}

public data object RealmLogCategory : LogCategory("Realm") {

    /**
     * TODO
     */
    public val Storage: StorageLogCategory = StorageLogCategory

    /**
     * TODO
     */
    public val Sync: SyncLogCategory = SyncLogCategory

    /**
     * TODO
     */
    public val App: LogCategory = AppLogCategory

    /**
     * TODO
     */
    public val Sdk: LogCategory = SdkLogCategory
}

public data object StorageLogCategory : LogCategory("Storage", RealmLogCategory) {

    /**
     * TODO
     */
    public val Transaction: LogCategory = TransactionLogCategory

    /**
     * TODO
     */
    public val Query: LogCategory = QueryLogCategory

    /**
     * TODO
     */
    public val Object: LogCategory = ObjectLogCategory

    /**
     * TODO
     */
    public val Notification: LogCategory = NotificationLogCategory
}

public data object TransactionLogCategory : LogCategory("Transaction", StorageLogCategory)
public data object QueryLogCategory : LogCategory("Query", StorageLogCategory)
public data object ObjectLogCategory : LogCategory("Object", StorageLogCategory)
public data object NotificationLogCategory : LogCategory("Notification", StorageLogCategory)

public data object SyncLogCategory : LogCategory("Sync", RealmLogCategory) {
    /**
     * TODO
     */
    public val Client: ClientLogCategory = ClientLogCategory

    /**
     * TODO
     */
    public val Server: LogCategory = ServerLogCategory
}

public data object ClientLogCategory : LogCategory("Client", SyncLogCategory) {
    /**
     * TODO
     */
    public val Session: LogCategory = SessionLogCategory

    /**
     * TODO
     */
    public val Changeset: LogCategory = ChangesetLogCategory

    /**
     * TODO
     */
    public val Network: LogCategory = NetworkLogCategory

    /**
     * TODO
     */
    public val Reset: LogCategory = ResetLogCategory
}

public data object SessionLogCategory : LogCategory("Session", ClientLogCategory)
public data object ChangesetLogCategory : LogCategory("Changeset", ClientLogCategory)
public data object NetworkLogCategory : LogCategory("Network", ClientLogCategory)
public data object ResetLogCategory : LogCategory("Reset", ClientLogCategory)
public data object ServerLogCategory : LogCategory("Server", SyncLogCategory)

public data object AppLogCategory : LogCategory("App", RealmLogCategory)
public data object SdkLogCategory : LogCategory("SDK", RealmLogCategory)
