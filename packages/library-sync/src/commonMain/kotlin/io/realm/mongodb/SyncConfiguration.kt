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
import io.realm.internal.RealmConfigurationImpl
import io.realm.internal.RealmObjectCompanion
import io.realm.internal.interop.sync.PartitionValue
import io.realm.internal.platform.createDefaultSystemLogger
import io.realm.log.RealmLogger
import io.realm.mongodb.internal.SyncConfigurationImpl
import io.realm.mongodb.internal.UserImpl
import kotlin.reflect.KClass

/**
 * A [SyncConfiguration] is used to setup a Realm Database that can be synchronized between
 * devices using MongoDB Realm.
 *
 * A valid [User] is required to create a [SyncConfiguration]. See
 * [Credentials] and [App.login] for more information on how to get a user object.
 *
 * A minimal [SyncConfiguration] can be found below.
 * ```
 *      val app = App.create(AppConfiguration.Builder(appId))
 *      val user = app.login(Credentials.anonymous())
 *      val config = SyncConfiguration.Builder(user, "partition-value")
 *      val realm = Realm.getInstance(config)
 * ```
 */
interface SyncConfiguration : RealmConfiguration {

    val user: User
    val partitionValue: PartitionValue

    companion object {
        fun defaultConfig(
            user: User,
            partitionValue: Int,
            schema: Set<KClass<out RealmObject>>
        ): SyncConfiguration {
            TODO("Add compiler plugin IR modification for this method")
        }

        fun defaultConfig(
            user: User,
            partitionValue: Long,
            schema: Set<KClass<out RealmObject>>
        ): SyncConfiguration {
            TODO("Add compiler plugin IR modification for this method")
        }

        fun defaultConfig(
            user: User,
            partitionValue: String,
            schema: Set<KClass<out RealmObject>>
        ): SyncConfiguration {
            TODO("Add compiler plugin IR modification for this method")
        }
    }

    /**
     * TODO
     */
    class Builder private constructor(
        path: String?, // Full path for Realm (directory + name)
        name: String, // Optional Realm name (default is 'default')
        schema: Set<KClass<out RealmObject>>,
        private var user: User,
        private var partitionValue: PartitionValue
    ) : RealmConfiguration.SharedBuilder<SyncConfiguration, Builder>(path, name, schema) {

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

        init {
            val appLogConfiguration = (user as UserImpl).app.configuration.log.configuration
            this.logLevel = appLogConfiguration.level
            this.userLoggers = appLogConfiguration.loggers
            this.removeSystemLogger = true
        }

        internal fun build(
            companionMap: Map<KClass<out RealmObject>, RealmObjectCompanion>
        ): SyncConfiguration {
            val appLogConfiguration = (user as UserImpl).app.configuration.log.configuration

            @Suppress("invisible_member")
            val localConfiguration = RealmConfiguration.Builder(path, name, schema)
                .log(logLevel, userLoggers)
                // This is already added in the AppConfiguration
                .removeSystemLogger()
                .build(companionMap)
            return SyncConfigurationImpl(
                localConfiguration as RealmConfigurationImpl,
                partitionValue,
                user as UserImpl
            )
        }
    }
}
