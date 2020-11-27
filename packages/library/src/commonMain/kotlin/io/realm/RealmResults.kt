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

import io.realm.internal.link
import io.realm.interop.RealmInterop
import io.realm.runtimeapi.Link
import io.realm.runtimeapi.Mediator
import io.realm.runtimeapi.NativePointer
import io.realm.runtimeapi.RealmModel
import io.realm.runtimeapi.RealmModelInternal
import kotlin.reflect.KClass

// FIXME API-QUERY
//  - Lazy API makes it harded to debug
//  - Postponing execution to actually accessing the elements also prevents query parser errors to
//    be raised. Maybe we can get an option to prevalidate queries in the C-API?
class RealmResults<T : RealmModel> constructor(
    private val realm: NativePointer,
    private val queryPointer: () -> NativePointer,
    private val clazz: KClass<T>,
    private val schema: Mediator
) : AbstractList<T>(), Queryable<T> {

    private val query: NativePointer by lazy { queryPointer() }
    private val result: NativePointer by lazy { RealmInterop.realm_query_find_all(query) }
    override val size: Int
        get() = RealmInterop.realm_results_count(result).toInt()

    override fun get(index: Int): T {
        val link: Link = RealmInterop.realm_results_get<T>(result, index.toLong())
        val model = schema.newInstance(clazz) as RealmModelInternal
        model.link(realm, clazz, link)
        return model as T
    }

    override fun query(query: String, vararg args: Any): RealmResults<T> {
        return RealmResults(
            realm,
            @Suppress("SpreadOperator")
            { RealmInterop.realm_query_parse(result, clazz.simpleName!!, query, *args) },
            clazz,
            schema,
        )
    }

    fun delete() {
        // TODO OPTIMIZE Are there more efficient ways to do this? realm_query_delete_all is not
        //  available in C-API yet.
        RealmInterop.realm_results_delete_all(result)
    }
}
