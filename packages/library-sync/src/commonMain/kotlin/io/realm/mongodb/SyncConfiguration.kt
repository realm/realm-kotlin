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

package io.realm.mongodb

import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.internal.interop.NativePointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.sync.PartitionValue
import io.realm.mongodb.internal.UserImpl
import kotlin.reflect.KClass

/**
 * TODO
 */
interface SyncConfiguration/* : RealmConfiguration */ {

    /**
     * TODO
     */
    val user: User

    /**
     * TODO
     */
    val partitionValue: PartitionValue

    companion object {
        fun defaultConfig(
            user: User,
            partitionValue: Int,
            schema: Set<KClass<out RealmObject>>
        ): SyncConfiguration {
            return Builder(schema = schema, user = user, partitionValue = partitionValue)
                .buildSync()
        }

        fun defaultConfig(
            user: User,
            partitionValue: Long,
            schema: Set<KClass<out RealmObject>>
        ): SyncConfiguration {
            return Builder(schema = schema, user = user, partitionValue = partitionValue)
                .buildSync()
        }

        fun defaultConfig(
            user: User,
            partitionValue: String,
            schema: Set<KClass<out RealmObject>>
        ): SyncConfiguration {
            return Builder(schema = schema, user = user, partitionValue = partitionValue)
                .buildSync()
        }
    }

    /**
     *
     */
    class Builder private constructor(
        path: String?, // Full path for Realm (directory + name)
        name: String, // Optional Realm name (default is 'default')
        schema: Set<KClass<out RealmObject>>,
        private var user: User,
        private var partitionValue: PartitionValue
    ) : RealmConfiguration.Builder(path, name, schema) {

        constructor(
            path: String? = null,
            name: String = Realm.DEFAULT_FILE_NAME,
            schema: Set<KClass<out RealmObject>> = setOf(),
            user: User,
            partitionValue: Int
        ) : this(path, name, schema, user, PartitionValue(partitionValue.toLong()))

        constructor(
            path: String? = null,
            name: String = Realm.DEFAULT_FILE_NAME,
            schema: Set<KClass<out RealmObject>> = setOf(),
            user: User,
            partitionValue: Long
        ) : this(path, name, schema, user, PartitionValue(partitionValue))

        constructor(
            path: String? = null,
            name: String = Realm.DEFAULT_FILE_NAME,
            schema: Set<KClass<out RealmObject>> = setOf(),
            user: User,
            partitionValue: String
        ) : this(path, name, schema, user, PartitionValue(partitionValue))

//        /**
//         * Creates the SyncConfiguration based on the builder properties.
//         *
//         * @return the created SyncConfiguration.
//         */
//        // TODO missing compiler plugin logic
//        fun buildConfig(): SyncConfiguration {
//            val message = "This code should have been replaced by the Realm Compiler Plugin. " +
//                    "Has the `realm-kotlin` Gradle plugin been applied to the project?"
//            throw AssertionError(message)
//        }

        // TODO for testing, remove
        fun buildSync(): SyncConfiguration {
            return SyncConfigurationImpl(partitionValue, user as UserImpl)
        }

//        // Called from the compiler plugin
//        internal fun buildSync(companionMap: Map<KClass<out RealmObject>, RealmObjectCompanion>): SyncConfiguration {
//            val allLoggers = mutableListOf<RealmLogger>()
//            if (!removeSystemLogger) {
//                allLoggers.add(createDefaultSystemLogger(Realm.DEFAULT_LOG_TAG))
//            }
//            allLoggers.addAll(userLoggers)
//            val localConfiguration = RealmConfigurationImpl(
//                companionMap,
//                path,
//                name,
//                schema,
//                LogConfiguration(logLevel, allLoggers),
//                maxNumberOfActiveVersions,
//                notificationDispatcher ?: singleThreadDispatcher(name),
//                writeDispatcher ?: singleThreadDispatcher(name),
//                schemaVersion,
//                deleteRealmIfMigrationNeeded,
//                encryptionKey,
//            )
//            return SyncConfigurationImpl(localConfiguration, partitionValue, user as UserImpl)
//        }
    }
}

internal class SyncConfigurationImpl(
//    localConfiguration: RealmConfigurationImpl,
    override val partitionValue: PartitionValue,
    userImpl: UserImpl,
) : /*RealmConfiguration by localConfiguration,*/ SyncConfiguration {

    override val user: User

    private val nativeSyncConfig: NativePointer =
        RealmInterop.realm_sync_config_new(userImpl.nativePointer, partitionValue.asSyncPartition())

    init {
        user = userImpl
    }
}
