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

/*
 * Core does not expose the different categories in compile time.
 *
 * This LogCategory design tries to overcome this issue by expressing the categories hierarchy
 * in the kotlin type system, then validate it with a test watchdog.
 */

// at package level as a workaround to ensure compatibility with darwin and jvm
private val categoriesByPath: MutableMap<String, LogCategory> = mutableMapOf()

internal fun newCategory(
    name: String,
    parent: LogCategory? = null,
): LogCategory = LogCategoryImpl(name, parent).also { category ->
    categoriesByPath["$category"] = category
}

/**
 * Defines a log category for the Realm logger.
 */
public sealed interface LogCategory {
    public val parent: LogCategory?

    public operator fun contains(element: LogCategory): Boolean
    override fun toString(): String

    public companion object {
        /**
         * Top level log category for Realm, updating this category level would set all other categories too.
         *
         * Category hierarchy:
         * ```
         * Realm
         * ├─► Storage
         * │   ├─► Transaction
         * │   ├─► Query
         * │   ├─► Object
         * │   └─► Notification
         * ├─► Sync
         * │   ├─► Client
         * │   │   ├─► Session
         * │   │   ├─► Changeset
         * │   │   ├─► Network
         * │   │   └─► Reset
         * │   └─► Server
         * ├─► App
         * └─► Sdk
         * ```
         */
        public val Realm: RealmLogCategory = RealmLogCategory

        internal fun fromCoreValue(categoryPath: String): LogCategory =
            categoriesByPath[categoryPath]!!
    }
}

internal class LogCategoryImpl(
    internal val name: String,
    override val parent: LogCategory? = null,
) : LogCategory {
    override fun contains(element: LogCategory): Boolean = "$element".contains("$this")

    override fun toString(): String = if (parent != null) "$parent.$name" else name
}

/**
 * Top level log category for Realm, updating this category level would set all other categories too.
 *
 * Category hierarchy:
 * ```
 * Realm
 * ├─► Storage
 * │   ├─► Transaction
 * │   ├─► Query
 * │   ├─► Object
 * │   └─► Notification
 * ├─► Sync
 * │   ├─► Client
 * │   │   ├─► Session
 * │   │   ├─► Changeset
 * │   │   ├─► Network
 * │   │   └─► Reset
 * │   └─► Server
 * ├─► App
 * └─► Sdk
 * ```
 */
public data object RealmLogCategory : LogCategory by newCategory("Realm") {
    /**
     * Log category for all storage related logs.
     *
     * Category hierarchy:
     * ```
     * Storage
     * ├─► Transaction
     * ├─► Query
     * ├─► Object
     * └─► Notification
     * ```
     */
    public val Storage: StorageLogCategory = StorageLogCategory

    /**
     * Log category for all sync related logs.
     *
     * Category hierarchy:
     * ```
     * Sync
     * ├─► Client
     * │   ├─► Session
     * │   ├─► Changeset
     * │   ├─► Network
     * │   └─► Reset
     * └─► Server
     * ```
     */
    public val Sync: SyncLogCategory = SyncLogCategory

    /**
     * Log category for all app related logs.
     */
    public val App: LogCategory = AppLogCategory

    /**
     * Log category for all sdk related logs.
     */
    public val Sdk: LogCategory = SdkLogCategory
}

/**
 * Log category for all storage related logs.
 *
 * Category hierarchy:
 * ```
 * Storage
 * ├─► Transaction
 * ├─► Query
 * ├─► Object
 * └─► Notification
 * ```
 */
public data object StorageLogCategory : LogCategory by newCategory("Storage", RealmLogCategory) {

    /**
     * Log category for all transaction related logs.
     */
    public val Transaction: LogCategory = TransactionLogCategory

    /**
     * Log category for all query related logs.
     */
    public val Query: LogCategory = QueryLogCategory

    /**
     * Log category for all object related logs.
     */
    public val Object: LogCategory = ObjectLogCategory

    /**
     * Log category for all notification related logs.
     */
    public val Notification: LogCategory = NotificationLogCategory
}

/**
 * Log category for all transaction related logs.
 */
public data object TransactionLogCategory :
    LogCategory by newCategory("Transaction", StorageLogCategory)

/**
 * Log category for all query related logs.
 */
public data object QueryLogCategory : LogCategory by newCategory("Query", StorageLogCategory)

/**
 * Log category for all object related logs.
 */
public data object ObjectLogCategory : LogCategory by newCategory("Object", StorageLogCategory)

/**
 * Log category for all notification related logs.
 */
public data object NotificationLogCategory :
    LogCategory by newCategory("Notification", StorageLogCategory)

/**
 * Category hierarchy:
 * ```
 * Sync
 * ├─► Client
 * │   ├─► Session
 * │   ├─► Changeset
 * │   ├─► Network
 * │   └─► Reset
 * └─► Server
 * ```
 */
public data object SyncLogCategory :
    LogCategory by newCategory("Sync", RealmLogCategory) {
    /**
     * Log category for all sync client related logs.
     *
     * Category hierarchy:
     * ```
     * Client
     * ├─► Session
     * ├─► Changeset
     * ├─► Network
     * └─► Reset
     * ```
     */
    public val Client: ClientLogCategory = ClientLogCategory

    /**
     * Log category for all sync server related logs.
     */
    public val Server: LogCategory = ServerLogCategory
}

/**
 *
 * Log category for all sync client related logs.
 *
 * Category hierarchy:
 * ```
 * Client
 * ├─► Session
 * ├─► Changeset
 * ├─► Network
 * └─► Reset
 * ```
 */
public data object ClientLogCategory :
    LogCategory by newCategory("Client", SyncLogCategory) {

    /**
     * Log category for all sync session related logs.
     */
    public val Session: LogCategory = SessionLogCategory

    /**
     * Log category for all sync changesets related logs.
     */
    public val Changeset: LogCategory = ChangesetLogCategory

    /**
     * Log category for all sync network logs.
     */
    public val Network: LogCategory = NetworkLogCategory

    /**
     * Log category for all sync reset related logs.
     */
    public val Reset: LogCategory = ResetLogCategory
}

/**
 * Log category for all sync session related logs.
 */
public data object SessionLogCategory :
    LogCategory by newCategory("Session", ClientLogCategory)

/**
 * Log category for all sync changesets related logs.
 */
public data object ChangesetLogCategory :
    LogCategory by newCategory("Changeset", ClientLogCategory)

/**
 * Log category for all sync network logs.
 */
public data object NetworkLogCategory :
    LogCategory by newCategory("Network", ClientLogCategory)

/**
 * Log category for all sync reset related logs.
 */
public data object ResetLogCategory :
    LogCategory by newCategory("Reset", ClientLogCategory)

/**
 * Log category for all sync server related logs.
 */
public data object ServerLogCategory :
    LogCategory by newCategory("Server", SyncLogCategory)

/**
 * Log category for all app related logs.
 */
public data object AppLogCategory : LogCategory by newCategory("App", RealmLogCategory)

/**
 * Log category for all sdk related logs.
 */
public data object SdkLogCategory : LogCategory by newCategory("SDK", RealmLogCategory)
