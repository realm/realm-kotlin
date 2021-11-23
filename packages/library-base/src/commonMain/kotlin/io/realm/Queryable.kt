/*
 * Copyright 2020 Realm Inc.
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

// FIXME QUERY-API
//  - Realms, Results and Lists are queryable, but might not be needed as an interface
//    dependent on how the final API is going to look like.
//  - Query could alternatively be separated into builder to await constructing new results until
//    actually executing the query
//  https://github.com/realm/realm-kotlin/issues/206
/**
 * Interface holding common query methods.
 */
interface Queryable<T> {
    fun query(query: String = "TRUEPREDICATE", vararg args: Any?): RealmResults<T>
}
