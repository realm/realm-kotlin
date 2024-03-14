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

private val categoriesByPath: MutableMap<String, LogCategory> = mutableMapOf()
internal fun newCategory(
    name: String,
    parent: LogCategory? = null,
): LogCategory = LogCategoryImpl(name, parent).also { category ->
    categoriesByPath["$category"] = category
}
/**
 * TODO
 */
public sealed interface LogCategory {
    public val parent: LogCategory?

    override fun toString(): String

    public companion object {

        public val Realm: RealmLogCategory = RealmLogCategory

        internal fun fromCoreValue(categoryPath: String): LogCategory =
            categoriesByPath[categoryPath]!!
    }
}

public class LogCategoryImpl(
    internal val name: String,
    override val parent: LogCategory? = null,
) : LogCategory {
    override fun toString(): String = if (parent != null) "$parent.$name" else name
}

public data object RealmLogCategory : LogCategory by newCategory("Realm") {
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

public data object StorageLogCategory :
    LogCategory by newCategory("Storage", RealmLogCategory) {

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

public data object TransactionLogCategory :
    LogCategory by newCategory("Transaction", StorageLogCategory)

public data object QueryLogCategory :
    LogCategory by newCategory("Query", StorageLogCategory)

public data object ObjectLogCategory :
    LogCategory by newCategory("Object", StorageLogCategory)

public data object NotificationLogCategory :
    LogCategory by newCategory("Notification", StorageLogCategory)

public data object SyncLogCategory :
    LogCategory by newCategory("Sync", RealmLogCategory) {
    /**
     * TODO
     */
    public val Client: ClientLogCategory = ClientLogCategory

    /**
     * TODO
     */
    public val Server: LogCategory = ServerLogCategory
}

public data object ClientLogCategory :
    LogCategory by newCategory("Client", SyncLogCategory) {
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

public data object SessionLogCategory :
    LogCategory by newCategory("Session", ClientLogCategory)

public data object ChangesetLogCategory :
    LogCategory by newCategory("Changeset", ClientLogCategory)

public data object NetworkLogCategory :
    LogCategory by newCategory("Network", ClientLogCategory)

public data object ResetLogCategory :
    LogCategory by newCategory("Reset", ClientLogCategory)

public data object ServerLogCategory :
    LogCategory by newCategory("Server", SyncLogCategory)

public data object AppLogCategory : LogCategory by newCategory("App", RealmLogCategory)
public data object SdkLogCategory : LogCategory by newCategory("SDK", RealmLogCategory)
