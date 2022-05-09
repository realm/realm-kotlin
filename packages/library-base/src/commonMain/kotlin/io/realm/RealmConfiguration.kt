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
import io.realm.internal.platform.appFilesDirectory
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
        schema: Set<KClass<out BaseRealmObject>>
    ) : Configuration.SharedBuilder<RealmConfiguration, Builder>(schema) {

        protected override var name: String? = Realm.DEFAULT_FILE_NAME
        private var directory: String = appFilesDirectory()
        private var deleteRealmIfMigrationNeeded: Boolean = false
        private var migration: RealmMigration? = null

        /**
         * Sets the path to the directory that contains the realm file. If the directory does not
         * exists, it and all intermediate directories will be created.
         *
         * If not set the realm will be stored at the default app storage location for the platform:
         * ```
         * // For Android the default directory is obtained using
         * Context.getFilesDir()
         *
         * // For JVM platforms the default directory is obtained using
         * System.getProperty("user.dir")
         *
         * // For macOS the default directory is obtained using
         * platform.Foundation.NSFileManager.defaultManager.currentDirectoryPath
         *
         * // For iOS the default directory is obtained using
         * NSFileManager.defaultManager.URLForDirectory(
         *      NSDocumentDirectory,
         *      NSUserDomainMask,
         *      null,
         *      true,
         *      null
         * )
         * ```
         *
         * @param directoryPath either the canonical absolute path or a relative path ('./') from
         * the storage location as defined above.
         */
        public fun directory(directoryPath: String): Builder =
            apply { this.directory = directoryPath }

        /**
         * Setting this will change the behavior of how migration exceptions are handled. Instead of
         * throwing an exception the on-disc Realm will be cleared and recreated with the new Realm
         * schema.
         *
         * **WARNING!** This will result in loss of data.
         */
        public fun deleteRealmIfMigrationNeeded(): Builder =
            apply { this.deleteRealmIfMigrationNeeded = true }

        /**
         * Sets the migration to handle schema updates.
         *
         * @param migration the [RealmMigration] instance to handle schema and data migration in the
         * event of a schema update.
         *
         * @see RealmMigration
         * @see AutomaticSchemaMigration
         */
        public fun migration(migration: RealmMigration): Builder =
            apply { this.migration = migration }

        override fun name(name: String): Builder = apply {
            checkName(name)
            this.name = name
        }

        override fun build(): RealmConfiguration {
            val allLoggers = mutableListOf<RealmLogger>()
            if (!removeSystemLogger) {
                allLoggers.add(createDefaultSystemLogger(Realm.DEFAULT_LOG_TAG))
            }
            allLoggers.addAll(userLoggers)

            // Sync configs might not set 'name' but local configs always do, therefore it will never be null here
            val fileName = name!!
            return RealmConfigurationImpl(
                directory,
                fileName,
                schema,
                LogConfiguration(logLevel, allLoggers),
                maxNumberOfActiveVersions,
                notificationDispatcher ?: singleThreadDispatcher(fileName),
                writeDispatcher ?: singleThreadDispatcher(fileName),
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
        public fun with(schema: Set<KClass<out BaseRealmObject>>): RealmConfiguration =
            Builder(schema).build()
    }
}
