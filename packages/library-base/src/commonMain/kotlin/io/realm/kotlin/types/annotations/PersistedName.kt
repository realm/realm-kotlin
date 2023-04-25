/*
 * Copyright 2022 Realm Inc.
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

@Retention(AnnotationRetention.SOURCE)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.CLASS)
@MustBeDocumented
/**
 * Annotation mapping a Kotlin class or field name to the name persisted in the Realm.
 *
 * This is useful when opening the Realm across different SDKs where code style
 * conventions might differ.
 **
 * For fields with this annotation, queries can be made using either of the names:
 * ```
 * // Class with field using `@PersistedName`
 * class Example : RealmObject {
 *      @PersistedName("myPersistedName")     // or: @PersistedName(name = "myPersistedName")
 *      var myKotlinName = "My value"
 * }
 *
 * // Query by Kotlin name
 * realm.query<Example>("myKotlinName = $0", "My value")
 *      .first()
 *      .find()?
 *      .myKotlinName
 *
 * // Query by persisted name
 * realm.query<Example>("myPersistedName = $0", "My value")
 *      .first()
 *      .find()?
 *      .myKotlinName
 * ```
 *
 * For classes with this annotation, raw queries must be made with the persisted name. Note,
 * only queries involving backlinks use this name:
 * ```
 * @PersistedName("RealmExample")
 * class Example : RealmObject {
 *      var id: Int = 0
 *      var child: Child? = null
 * }
 *
 * class Child: RealmObject {
 *      var name: String = "Jane Doe"
 * }
 *
 * // Query by persisted name
 * realm.query<Child>("@links.RealmExample.child.id == $0", 42).find()
 * ```
 */
public annotation class PersistedName(val name: String)
