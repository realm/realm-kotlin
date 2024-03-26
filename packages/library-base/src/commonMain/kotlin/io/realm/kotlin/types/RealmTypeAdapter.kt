/*
 * Copyright 2023 Realm Inc.
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

package io.realm.kotlin.types

import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.types.annotations.TypeAdapter

/**
 * Interface that defines how to translate from a persisted Realm type to a user-defined one.
 *
 * In conjunction with the [TypeAdapter] annotation allows using non Realm types in Realm
 * models. It takes to type parameters, the Realm type [R] that is the type that Realm
 * would use to persist the data, and the public type [P] that would be adapted.
 *
 * Defining a type adapter with an invalid [R] Realm type would throw at compilation time.
 *
 * The type adapter resolution depending if the type adapter has been defined as a class or an
 * object. When defined as an object the type adapter is resolved in compile time, whereas if defined
 * as a class it will be resolved in runtime requiring the RealmConfiguration to hold an instance of
 * the type adapter, see [RealmConfiguration.Builder.typeAdapters].
 *
 * Example of a compile time adapter:
 *
 * ```
 * object RealmInstantDateAdapter: RealmTypeAdapter<RealmInstant, Date> {
 *     override fun fromRealm(realmValue: RealmInstant): Date = TODO()
 *
 *     override fun toRealm(value: Date): RealmInstant = TODO()
 * }
 *
 * class MyObject: RealmObject {
 *     @TypeAdapter(RealmInstantDateAdapter::class)
 *     var date: Date = Date()
 * }
 * ```
 *
 * Example of a runtime adapter:
 *
 * ```
 * class EncryptedStringAdapter(
 *     val encryptionKey: String,
 * ) : RealmTypeAdapter<ByteArray, String> {
 *     override fun fromRealm(realmValue: ByteArray): String = TODO()
 *
 *     override fun toRealm(value: String): ByteArray = TODO()
 * }
 *
 * class MyObject : RealmObject {
 *     @TypeAdapter(EncryptedStringAdapter::class)
 *     var secureString: String = "some content"
 * }
 *
 * fun createRealmConfig() =
 *     RealmConfiguration
 *         .Builder(setOf(MyObject::class))
 *         .typeAdapters {
 *             add(EncryptedStringAdapter("my encryption key"))
 *         }
 *         .build()
 * ```
 *
 * @param R Realm type.
 * @param P Public type.
 */
public interface RealmTypeAdapter<R, P> { // where S is a supported realm type

    /**
     * Converts a value from Realm to the public type.
     *
     * @param value value to convert
     */
    public fun toPublic(value: R): P

    /**
     * Converts a value from a public type to Realm.
     *
     * @param value value to convert
     */
    public fun toRealm(value: P): R
}
