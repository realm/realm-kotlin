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

package io.realm

import io.realm.internal.RealmConfigurationImpl
import io.realm.internal.platform.createDefaultSystemLogger
import io.realm.internal.platform.singleThreadDispatcher
import io.realm.log.RealmLogger
import io.realm.migration.RealmMigration
import kotlin.reflect.KClass

/**
 * A _Realm Configuration_ defining specific setup and configuration for a Realm instance.
 *
 * The RealmConfiguration can, for simple uses cases, be created directly through the constructor.
 * More advanced setup requires building the RealmConfiguration through
 * [RealmConfiguration.Builder.build].
 *
 * @see Realm
 * @see RealmConfiguration.Builder
 */
public interface RealmConfiguration : Configuration {
    /**
     * Flag indicating whether the realm will be deleted if the schema has changed in a way that
     * requires schema migration.
     */
    public val deleteRealmIfMigrationNeeded: Boolean

    /**
     * Used to create a [RealmConfiguration]. For common use cases, a [RealmConfiguration] can be
     * created using the [RealmConfiguration.with] function.
     */
    public class Builder(
        schema: Set<KClass<out RealmObject>> = setOf()
    ) : Configuration.SharedBuilder<RealmConfiguration, Builder>(schema) {

        /**
         * Setting this will change the behavior of how migration exceptions are handled. Instead of throwing an
         * exception the on-disc Realm will be cleared and recreated with the new Realm schema.
         *
         * **WARNING!** This will result in loss of data.
         */
        public fun deleteRealmIfMigrationNeeded(): Builder = apply { this.deleteRealmIfMigrationNeeded = true }

        /**
         * Sets the migration to handle schema updates.
         *
         * @param migration the [RealmMigration] instance to handle schema and data migration in the
         * event of a schema update.
         *
         * @see RealmMigration
         * @see AutomaticSchemaMigration
         */
        public fun migration(migration: RealmMigration): Builder = apply { this.migration = migration }

        override fun build(): RealmConfiguration {
            val allLoggers = mutableListOf<RealmLogger>()
            if (!removeSystemLogger) {
                allLoggers.add(createDefaultSystemLogger(Realm.DEFAULT_LOG_TAG))
            }
            allLoggers.addAll(userLoggers)
            return RealmConfigurationImpl(
                path,
                name,
                schema,
                LogConfiguration(logLevel, allLoggers),
                maxNumberOfActiveVersions,
                notificationDispatcher ?: singleThreadDispatcher(name),
                writeDispatcher ?: singleThreadDispatcher(name),
                schemaVersion,
                encryptionKey,
                deleteRealmIfMigrationNeeded,
                compactOnLaunchCallback,
                migration
            )
        }
    }

    public companion object {
        /**
         * Creates a configuration from the given schema, with default values for all properties.
         *
         * @param schema the classes of the schema. The elements of the set must be direct class literals.
         */
        public fun with(schema: Set<KClass<out RealmObject>>): RealmConfiguration = Builder(schema).build()
    }
}
