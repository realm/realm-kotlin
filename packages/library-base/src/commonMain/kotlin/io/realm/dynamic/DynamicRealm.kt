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

package io.realm.dynamic

import io.realm.BaseRealm
import io.realm.query.RealmQuery

/**
 * A **dynamic realm** gives access to the data of the realm through a generic string based
 * API instead of the conventional [Realm] API that uses the schema classes supplied in the
 * configuration.
 *
 * *NOTE:* All objects obtained from a [DynamicRealm] are only valid in the scope of the dynamic
 * realm. Thus they cannot be passed outside of an [RealmMigration] that gives access to a specific
 * [DynamicRealm] instance, etc.
 */
public interface DynamicRealm : BaseRealm {

    /**
     * Returns a query for dynamic realm objects of the specified class.
     *
     * @param className the name of the class of which to query for.
     * @param query the Realm Query Language predicate use when querying.
     * @param args realm values for the predicate.
     * @return a RealmQuery, which can be used to query for specific objects of provided type.
     * @throws IllegalArgumentException if the class with `className` doesn't exist in the realm.
     *
     * @see DynamicRealmObject
     */
    public fun query(
        className: String,
        query: String = "TRUEPREDICATE",
        vararg args: Any?
    ): RealmQuery<out DynamicRealmObject>
}
