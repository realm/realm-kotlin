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
import io.realm.internal.REPLACED_BY_IR
import io.realm.internal.RealmConfigurationImpl
import io.realm.internal.RealmObjectCompanion
import io.realm.internal.interop.sync.PartitionValue
import io.realm.internal.platform.createDefaultSystemLogger
import io.realm.log.RealmLogger
import io.realm.mongodb.internal.SyncConfigurationImpl
import io.realm.mongodb.internal.UserImpl
import kotlin.reflect.KClass

/**
 * TODO
 */
interface SyncConfiguration : RealmConfiguration {

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
            TODO("REPLACED_BY_IR")
        }

        fun defaultConfig(
            user: User,
            partitionValue: Long,
            schema: Set<KClass<out RealmObject>>
        ): SyncConfiguration {
            TODO("REPLACED_BY_IR")
        }

        fun defaultConfig(
            user: User,
            partitionValue: String,
            schema: Set<KClass<out RealmObject>>
        ): SyncConfiguration {
            TODO("REPLACED_BY_IR")
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
    ) : RealmConfiguration.SharedBuilder<Builder>(path, name, schema) {

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

        /**
         * Creates the RealmConfiguration based on the builder properties.
         *
         * @return the created RealmConfiguration.
         */
        override fun build(): SyncConfiguration {
            REPLACED_BY_IR()
        }

        override fun build(
            companionMap: Map<KClass<out RealmObject>, RealmObjectCompanion>
        ): SyncConfiguration {
            val allLoggers = mutableListOf<RealmLogger>()
            if (!removeSystemLogger) {
                allLoggers.add(createDefaultSystemLogger(Realm.DEFAULT_LOG_TAG))
            }
            allLoggers.addAll(userLoggers)
            val localConfiguration = RealmConfiguration.Builder(path, name, schema)
                .build(companionMap)
            return SyncConfigurationImpl(
                localConfiguration as RealmConfigurationImpl,
                partitionValue,
                user as UserImpl
            )
        }
    }
}
