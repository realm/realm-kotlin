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

package io.realm.kotlin.internal

import io.realm.kotlin.internal.schema.RealmClassImpl
import kotlin.reflect.KMutableProperty1

// TODO MEDIATOR/API-INTERNAL Consider adding type parameter for the class
// TODO Public due to being a transitive dependency to Mediator.
@Suppress("VariableNaming")
public interface RealmObjectCompanion {
    public val `io_realm_kotlin_className`: String
    public val `io_realm_kotlin_fields`: Map<String, KMutableProperty1<*, *>>
    public val `io_realm_kotlin_primaryKey`: KMutableProperty1<*, *>?
    public val `io_realm_kotlin_isEmbedded`: Boolean
    public fun `io_realm_kotlin_schema`(): RealmClassImpl
    public fun `io_realm_kotlin_newInstance`(): Any
}
