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

package io.realm.kotlin.types.annotations

import io.realm.kotlin.types.RealmTypeAdapter
import kotlin.reflect.KClass

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS, AnnotationTarget.TYPE)
@MustBeDocumented
/**
 * Annotations marking a field to use a type adapter.
 *
 * [RealmTypeAdapter] allows using non Realm types in Realm schema models. Data would automatically be
 * converted in and out using the type adapter defined by this annotation.
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
 */
public annotation class TypeAdapter(val adapter: KClass<out RealmTypeAdapter<*, *>>)
