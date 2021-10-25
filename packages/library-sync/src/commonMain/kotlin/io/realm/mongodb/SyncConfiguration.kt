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

import io.realm.LogConfiguration
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.internal.RealmConfigurationImpl
import io.realm.internal.RealmObjectCompanion
import io.realm.internal.interop.sync.PartitionValue
import io.realm.internal.platform.createDefaultSystemLogger
import io.realm.internal.platform.singleThreadDispatcher
import io.realm.log.LogLevel
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
 *      val app = App.create(appId)
 *      val user = app.login(Credentials.anonymous())
 *      val config = SyncConfiguration.Builder(user, "partition-value", setOf(YourRealmObject::class)).build()
 *      val realm = Realm.open(config)
 * ```
 */
// FIXME update docs when `with` is ready: https://github.com/realm/realm-kotlin/issues/504
interface SyncConfiguration : RealmConfiguration {

    val user: User
    val partitionValue: PartitionValue

    /**
     * Used to create a [SyncConfiguration]. For common use cases, a [SyncConfiguration] can be
     * created using the [RealmConfiguration.with] function.
     */
    class Builder private constructor(
        private var user: User,
        private var partitionValue: PartitionValue,
        schema: Set<KClass<out RealmObject>>
    ) : RealmConfiguration.SharedBuilder<SyncConfiguration, Builder>(schema) {

        constructor(
            user: User,
            partitionValue: Int,
            schema: Set<KClass<out RealmObject>> = setOf()
        ) : this(user, PartitionValue(partitionValue.toLong()), schema)

        constructor(
            user: User,
            partitionValue: Long,
            schema: Set<KClass<out RealmObject>> = setOf()
        ) : this(user, PartitionValue(partitionValue), schema)

        constructor(
            user: User,
            partitionValue: String,
            schema: Set<KClass<out RealmObject>> = setOf()
        ) : this(user, PartitionValue(partitionValue), schema)

        init {
            // Prime builder with log configuration from AppConfiguration
            val appLogConfiguration = (user as UserImpl).app.configuration.log.configuration
            this.logLevel = appLogConfiguration.level
            this.userLoggers = appLogConfiguration.loggers
            this.removeSystemLogger = true
        }

        override fun log(level: LogLevel, customLoggers: List<RealmLogger>) =
            apply {
                // Will clear any primed configuration
                this.logLevel = level
                this.userLoggers = customLoggers
                this.removeSystemLogger = false
            }

        internal fun build(
            companionMap: Map<KClass<out RealmObject>, RealmObjectCompanion>
        ): SyncConfiguration {
            val allLoggers = userLoggers.toMutableList()
            // TODO This will not remove the system logger if it was added in AppConfiguration and
            //  no overrides are done for this builder. But as removeSystemLogger() is not public
            //  and most people will only specify loggers on the AppConfiguration this is OK for
            //  now.
            if (!removeSystemLogger) {
                allLoggers.add(0, createDefaultSystemLogger(Realm.DEFAULT_LOG_TAG))
            }
            val localConfiguration = RealmConfigurationImpl(
                companionMap,
                path,
                name,
                schema,
                LogConfiguration(logLevel, allLoggers),
                maxNumberOfActiveVersions,
                notificationDispatcher ?: singleThreadDispatcher(name),
                writeDispatcher ?: singleThreadDispatcher(name),
                schemaVersion,
                deleteRealmIfMigrationNeeded,
                encryptionKey
            )

            return SyncConfigurationImpl(localConfiguration, partitionValue, user as UserImpl)
        }
    }
}
