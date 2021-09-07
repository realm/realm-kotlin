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

package io.realm.internal

import io.realm.Callback
import io.realm.Cancellable
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.interop.Link
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

// FIXME API-QUERY Final query design is tracked in https://github.com/realm/realm-kotlin/issues/84
//  - Lazy API makes it harded to debug
//  - Postponing execution to actually accessing the elements also prevents query parser errors to
//    be raised. Maybe we can get an option to prevalidate queries in the C-API?

internal class RealmResultsImpl<T : RealmObject> : AbstractList<T>, RealmResults<T>, RealmStateHolder {

    private val mode: Mode
    private val realm: RealmReference
    private val clazz: KClass<T>
    private val schema: Mediator
    internal val result: NativePointer

    private enum class Mode {
        // FIXME Needed to make working with @LinkingObjects easier.
        EMPTY, // RealmResults that is always empty.
        RESULTS // RealmResults wrapping a Realm Core Results.
    }
    // Wrap existing native Results class
    private constructor(realm: RealmReference, results: NativePointer, clazz: KClass<T>, schema: Mediator) {
        this.mode = Mode.RESULTS
        this.realm = realm
        this.result = results
        this.clazz = clazz
        this.schema = schema
    }

    internal companion object {
        internal fun <T : RealmObject> fromQuery(realm: RealmReference, query: NativePointer, clazz: KClass<T>, schema: Mediator): RealmResultsImpl<T> {
            // realm_query_find_all doesn't fully evaluate until you interact with it.
            return RealmResultsImpl(realm, RealmInterop.realm_query_find_all(query), clazz, schema)
        }

        internal fun <T : RealmObject> fromResults(realm: RealmReference, results: NativePointer, clazz: KClass<T>, schema: Mediator): RealmResultsImpl<T> {
            return RealmResultsImpl(realm, results, clazz, schema)
        }
    }

    override fun realmState(): RealmState {
        return realm
    }

    override val size: Int
        get() = RealmInterop.realm_results_count(result).toInt()

    override fun get(index: Int): T {
        val link: Link = RealmInterop.realm_results_get<T>(result, index.toLong())
        val model = schema.createInstanceOf(clazz) as RealmObjectInternal
        model.link(realm, schema, clazz, link)
        @Suppress("UNCHECKED_CAST")
        return model as T
    }

    @Suppress("SpreadOperator")
    override fun query(query: String, vararg args: Any?): RealmResultsImpl<T> {
        return fromQuery(
            realm,
            RealmInterop.realm_query_parse(result, clazz.simpleName!!, query, *args),
            clazz,
            schema,
        )
    }

    /**
     * FIXME Hidden until we can add proper support
     *
     * Observe changes to a Realm result.
     *
     * Follows the pattern of [Realm.addChangeListener]
     */
    internal fun addChangeListener(callback: Callback<RealmResultsImpl<T>>): Cancellable {
        realm.checkClosed()
        return realm.owner.registerResultsChangeListener(this, callback)
    }

    override fun observe(): Flow<RealmResultsImpl<T>> {
        realm.checkClosed()
        return realm.owner.registerResultsObserver(this)
    }

    override fun delete() {
        // TODO OPTIMIZE Are there more efficient ways to do this? realm_query_delete_all is not
        //  available in C-API yet, but should probably await final query design
        //  https://github.com/realm/realm-kotlin/issues/84
        RealmInterop.realm_results_delete_all(result)
    }

    /**
     * Returns a frozen copy of this query result. If it is already frozen, the same instance
     * is returned.
     */
    internal fun freeze(realm: RealmReference): RealmResultsImpl<T> {
        val frozenDbPointer = realm.dbPointer
        val frozenResults = RealmInterop.realm_results_freeze(result, frozenDbPointer)
        return fromResults(realm, frozenResults, clazz, schema)
    }

    /**
     * Thaw the frozen query result, turning it back into a live, thread-confined RealmResults.
     */
    internal fun thaw(realm: RealmReference): RealmResultsImpl<T> {
        val liveDbPointer = realm.dbPointer
        val liveResultPtr = RealmInterop.realm_results_thaw(result, liveDbPointer)
        return fromResults(realm, liveResultPtr, clazz, schema)
    }
}
