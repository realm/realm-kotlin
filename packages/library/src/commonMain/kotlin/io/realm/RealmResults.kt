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

import io.realm.internal.Mediator
import io.realm.internal.NotificationToken
import io.realm.internal.RealmObjectInternal
import io.realm.internal.TransactionId
import io.realm.internal.checkRealmClosed
import io.realm.internal.link
import io.realm.interop.Link
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlin.reflect.KClass

// FIXME API-QUERY Final query design is tracked in https://github.com/realm/realm-kotlin/issues/84
//  - Lazy API makes it harded to debug
//  - Postponing execution to actually accessing the elements also prevents query parser errors to
//    be raised. Maybe we can get an option to prevalidate queries in the C-API?
class RealmResults<T : RealmObject> constructor(
    private val realmConfiguration: RealmConfiguration,
    private val realm: TransactionId,
    private val queryPointer: () -> NativePointer,
    private val clazz: KClass<T>,
    private val mediator: Mediator
) : AbstractList<T>(), Queryable<T> {

    public var version: VersionId = VersionId(0)
        get() {
            checkRealmClosed(realm, realmConfiguration)
            return VersionId(RealmInterop.realm_get_version_id(realm.dbPointer))
        }

    private val query: NativePointer by lazy { queryPointer() }
    private val result: NativePointer by lazy { RealmInterop.realm_query_find_all(query) }
    override val size: Int
        get() = RealmInterop.realm_results_count(result).toInt()

    override fun get(index: Int): T {
        val link: Link = RealmInterop.realm_results_get<T>(result, index.toLong())
        val model = mediator.createInstanceOf(clazz) as RealmObjectInternal
        model.link(realm, mediator, clazz, link)
        return model as T
    }

    // Query string follows the Swift/JS filter/sort-syntax as described in
    // https://realm.io/docs/javascript/latest/#filtering
    // Ex.:
    //   'color = "tan" AND name BEGINSWITH "B" SORT(name DESC) LIMIT(5)'
    @Suppress("SpreadOperator")
    override fun query(query: String, vararg args: Any): RealmResults<T> {
        return RealmResults(
            realmConfiguration,
            realm,
            { RealmInterop.realm_query_parse(result, clazz.simpleName!!, query, *args) },
            clazz,
            mediator,
        )
    }

    /**
     * Observe changes to a Realm result.
     *
     * Follows the pattern of [Realm.observe]
     */
    fun observe(callback: Callback<RealmResults<T>>): Cancellable {
        val token = RealmInterop.realm_results_add_notification_callback(
            result,
            object : io.realm.interop.Callback {
                override fun onChange(collectionChanges: NativePointer) {
                    // FIXME Need to expose change details to the user
                    //  https://github.com/realm/realm-kotlin/issues/115
                    callback.onChange(this@RealmResults)
                }
            }
        )
        return NotificationToken(callback, token)
    }

    fun delete() {
        // TODO OPTIMIZE Are there more efficient ways to do this? realm_query_delete_all is not
        //  available in C-API yet, but should probably await final query design
        //  https://github.com/realm/realm-kotlin/issues/84
        RealmInterop.realm_results_delete_all(result)
    }
}
