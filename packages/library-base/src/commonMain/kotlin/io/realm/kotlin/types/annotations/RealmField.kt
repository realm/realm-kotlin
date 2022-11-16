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
@Target(AnnotationTarget.FIELD)
@MustBeDocumented
/**
 * Annotation mapping a Kotlin field name to the internal field name persisted in the Realm.
 *
 * This is useful when opening the Realm across different bindings where code style
 * conventions might differ.
 *
 * Queries can be made using either of the names:
 * ```
 * // Class with field using `@RealmField`
 * class Sample() : RealmObject {
 *      @RealmField("internalName")     // or: @RealmField(name = "internalName")
 *      var kotlinName = "Example"
 * }
 *
 * // Query by Kotlin name
 * realm.query<Sample>("kotlinName = $0", "Example")
 *      .first()
 *      .find()?
 *      .kotlinName
 *
 * // Query by internal name
 * realm.query<Sample>("internalName = $0", "Example")
 *      .first()
 *      .find()?
 *      .kotlinName
 * ```
 */
public annotation class RealmField(val name: String)