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
import io.realm.internal.RealmModelInternal
import io.realm.internal.link
import io.realm.internal.worker.LiveRealm
import io.realm.interop.Link
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

// FIXME API-QUERY Final query design is tracked in https://github.com/realm/realm-kotlin/issues/84
//  - Lazy API makes it harded to debug
//  - Postponing execution to actually accessing the elements also prevents query parser errors to
//    be raised. Maybe we can get an option to prevalidate queries in the C-API?
class RealmResults<T : RealmObject<T>> : AbstractList<T>, Queryable<T> {

    private enum class Mode {
        EMPTY, // RealmResults that is always empty. TODO: Needed to make working with @LinkingObjects easier. */
        QUERY, // RealmResults created from a query
        RESULTS // RealmResults wrapping a Realm Core Results.
    }

    private val mode: Mode
    internal val owner: LiveRealm?
    private val realmPointer: NativePointer
    private val clazz: KClass<T>?
    private val schema: Mediator?
    private val query: NativePointer// by lazy { getQueryPointer() }
    private val result: NativePointer// by lazy { getResultPointer() }
    override val size: Int
        get() = RealmInterop.realm_results_count(result).toInt()


    private constructor() {
        // FIXME: Figure out exactly how to support in a sane way.
        val dummyPointer = object: NativePointer {  }
        mode = Mode.EMPTY
        owner = null
        realmPointer = dummyPointer
        result = dummyPointer
        query = dummyPointer
        clazz = null
        schema = null
    }

    // Create Results from query
    private constructor(realm: LiveRealm, query: () -> NativePointer, clazz: KClass<T>, schema: Mediator) {
        this.mode = Mode.QUERY
        this.owner = realm
        this.realmPointer = realm.dbPointer!!
        this.query = query()
        this.result = RealmInterop.realm_query_find_all(this.query)
        this.clazz = clazz
        this.schema = schema
    }

    // Wrap existing native Results class
    private constructor(realm: LiveRealm, results: NativePointer, clazz: KClass<T>, schema: Mediator) {
        this.mode = Mode.RESULTS
        this.owner = realm
        this.realmPointer = realm.dbPointer!!
        this.query = object: NativePointer {  }
        this.result = results
        this.clazz = clazz
        this.schema = schema
    }

    internal companion object {
        internal fun <T : RealmObject<T>> fromQuery(realm: LiveRealm, query: () -> NativePointer, clazz: KClass<T>, schema: Mediator ): RealmResults<T> {
            return RealmResults(realm, query, clazz, schema)
        }

        internal fun <T : RealmObject<T>> fromResults(realm: LiveRealm, results: NativePointer, clazz: KClass<T>, schema: Mediator ): RealmResults<T> {
            return RealmResults(realm, results, clazz, schema)

        }
    }

    override fun get(index: Int): T {
        val link: Link = RealmInterop.realm_results_get<T>(result, index.toLong())
        val model = schema!!.newInstance(clazz!!) as RealmModelInternal
        model.link(owner!!, schema, clazz!!, link)
        return model as T
    }

    // Query string follows the Swift/JS filter/sort-syntax as described in
    // https://realm.io/docs/javascript/latest/#filtering
    // Ex.:
    //   'color = "tan" AND name BEGINSWITH "B" SORT(name DESC) LIMIT(5)'
    @Suppress("SpreadOperator")
    override fun query(query: String, vararg args: Any): RealmResults<T> {
        return fromQuery(
            owner!!,
            { RealmInterop.realm_query_parse(result, clazz!!.simpleName!!, query, *args) },
            clazz!!,
            schema!!,
        )
    }

    /**
     * Observe changes to a Realm result. This method is only available RealmResults backed by a
     * [LiveRealm].
     */
    internal fun addChangeListener(callback: Callback): Cancellable {
        val token = RealmInterop.realm_results_add_notification_callback(
            result,
            object : io.realm.interop.Callback {
                override fun onChange(collectionChanges: NativePointer) {
                    // FIXME Need to expose change details to the user
                    //  https://github.com/realm/realm-kotlin/issues/115
                    callback.onChange()
                }
            }
        )
        return NotificationToken(callback, token)
    }

    // FIXME: First draft, we should also expose fine-grained collection changes
    fun observe(): Flow<RealmResults<T>> {
        return (owner as Realm).notifierThread.resultsChanged(this)
    }

    fun delete() {
        // TODO OPTIMIZE Are there more efficient ways to do this? realm_query_delete_all is not
        //  available in C-API yet, but should probably await final query design
        //  https://github.com/realm/realm-kotlin/issues/84
        RealmInterop.realm_results_delete_all(result)
    }

    /**
     * Returns a frozen copy of this query result. If it is already frozen, the same instance
     * is returned.
     */
    internal fun freeze(realm: Realm): RealmResults<T> {
        val frozenDbPointer = realm.dbPointer!!
        val frozenResults = RealmInterop.realm_results_freeze(result, frozenDbPointer)
        return fromResults(realm, frozenResults, clazz!!, schema!!)
    }

    /**
     * Thaw the frozen query result, turning it back into a live, thread-confined Realm Results.
     */
    internal fun thaw(realm: LiveRealm): RealmResults<T> {
        val liveDbPointer = realm.dbPointer!!
        val liveResultPtr = RealmInterop.realm_results_thaw(result, liveDbPointer)
        return fromResults(realm, liveResultPtr, clazz!!, schema!!)
    }

    internal fun isFrozen(): Boolean {
        return owner?.isFrozen() ?: true
    }
}
