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

package io.realm.internal

import io.realm.LogConfiguration
import io.realm.RealmConfiguration
import io.realm.RealmMigration
import io.realm.RealmObject
import io.realm.internal.interop.SchemaMode
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.reflect.KClass

@Suppress("LongParameterList")
internal class RealmConfigurationImpl(
    path: String?,
    name: String,
    schema: Set<KClass<out RealmObject>>,
    logConfig: LogConfiguration,
    maxNumberOfActiveVersions: Long,
    notificationDispatcher: CoroutineDispatcher,
    writeDispatcher: CoroutineDispatcher,
    schemaVersion: Long,
    encryptionKey: ByteArray?,
    override val deleteRealmIfMigrationNeeded: Boolean,
    migration: RealmMigration?
) : ConfigurationImpl(
    path,
    name,
    schema,
    logConfig,
    maxNumberOfActiveVersions,
    notificationDispatcher,
    writeDispatcher,
    schemaVersion,
    when (deleteRealmIfMigrationNeeded) {
        true -> SchemaMode.RLM_SCHEMA_MODE_RESET_FILE
        false -> SchemaMode.RLM_SCHEMA_MODE_AUTOMATIC
    },
    encryptionKey,
    migration
),
    RealmConfiguration
