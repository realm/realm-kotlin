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

import io.realm.RealmConfiguration
import io.realm.RealmObject
import io.realm.internal.interop.NativePointer
import kotlinx.coroutines.CoroutineDispatcher
import kotlin.reflect.KClass

/**
 * An **internal Realm configuration** that holds internal properties from a
 * [io.realm.RealmConfiguration]. This is needed to make "agnostic" configurations from a base-sync
 * point of view.
 */
interface InternalRealmConfiguration : RealmConfiguration {
    val mapOfKClassWithCompanion: Map<KClass<out RealmObject>, RealmObjectCompanion>
    val mediator: Mediator
    val nativeConfig: NativePointer
    val notificationDispatcher: CoroutineDispatcher
    val writeDispatcher: CoroutineDispatcher

    fun debug(): String {
        return "path=$path\n" +
            " name=$name\n" +
            " maxNumberOfActiveVersions=$maxNumberOfActiveVersions\n" +
            " schemaVersion=$schemaVersion\n" +
            " deleteRealmIfMigrationNeeded=$deleteRealmIfMigrationNeeded\n" +
            " schema=$schema"
    }
}
