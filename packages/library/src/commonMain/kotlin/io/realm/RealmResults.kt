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
import io.realm.internal.RealmId
import io.realm.internal.RealmObjectInternal
import io.realm.internal.link
import io.realm.interop.Link
import io.realm.interop.NativePointer
import io.realm.interop.RealmInterop
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

// FIXME API-QUERY Final query design is tracked in https://github.com/realm/realm-kotlin/issues/84
//  - Lazy API makes it harded to debug
//  - Postponing execution to actually accessing the elements also prevents query parser errors to
//    be raised. Maybe we can get an option to prevalidate queries in the C-API?
class RealmResults<T : RealmObject> : AbstractList<T>, Queryable<T> {

    private val mode: Mode
    private val owner: RealmId
    private val clazz: KClass<T>
    private val schema: Mediator
    private val query: NativePointer
    internal val resultsNativePointer: NativePointer

    private enum class Mode {
        // FIXME: Needed to make working with @LinkingObjects easier.
        EMPTY, // RealmResults that is always empty.
        QUERY, // RealmResults created from a query
        RESULTS // RealmResults wrapping a Realm Core Results.
    }

    // Create Results from query
    private constructor(realm: RealmId, query: () -> NativePointer, clazz: KClass<T>, schema: Mediator) {
        this.mode = Mode.QUERY
        this.owner = realm
        this.query = query()
        this.resultsNativePointer = RealmInterop.realm_query_find_all(this.query)
        this.clazz = clazz
        this.schema = schema
    }

    // Wrap existing native Results class
    private constructor(realm: RealmId, results: NativePointer, clazz: KClass<T>, schema: Mediator) {
        this.mode = Mode.RESULTS
        this.owner = realm
        this.query = object: NativePointer {  }
        this.resultsNativePointer = results
        this.clazz = clazz
        this.schema = schema
    }

    internal companion object {
        internal fun <T : RealmObject> fromQuery(realm: RealmId, query: () -> NativePointer, clazz: KClass<T>, schema: Mediator ): RealmResults<T> {
            return RealmResults(realm, query, clazz, schema)
        }

        internal fun <T : RealmObject> fromResults(realm: RealmId, results: NativePointer, clazz: KClass<T>, schema: Mediator ): RealmResults<T> {
            return RealmResults(realm, results, clazz, schema)

        }
    }

    public var version: VersionId = VersionId(0)
        get() {
            checkClosed()
            return VersionId(RealmInterop.realm_get_version_id(owner.dbPointer))
        }

    override val size: Int
        get() {
            checkClosed()
            return RealmInterop.realm_results_count(resultsNativePointer).toInt()
        }

    override fun get(index: Int): T {
        checkClosed()
        val link: Link = RealmInterop.realm_results_get<T>(resultsNativePointer, index.toLong())
        val model = schema.createInstanceOf(clazz)
        model.link(owner, schema, clazz, link)
        @Suppress("UNCHECKED_CAST")
        return model as T
    }

    // Query string follows the Swift/JS filter/sort-syntax as described in
    // https://realm.io/docs/javascript/latest/#filtering
    // Ex.:
    //   'color = "tan" AND name BEGINSWITH "B" SORT(name DESC) LIMIT(5)'
    @Suppress("SpreadOperator")
    override fun query(query: String, vararg args: Any): RealmResults<T> {
        return fromQuery(
            owner,
            { RealmInterop.realm_query_parse(resultsNativePointer, clazz.simpleName!!, query, *args) },
            clazz,
            schema,
        )
    }

    /**
     * Listen to changes to a Realm result. Updates will continue to be delivered until the Realm is closed
     * or [Cancellable.cancel] has been called on the token returned when registering the listener.
     *
     * Updates are being delivered on the thread defined by [RealmConfiguration.notifierDispatcher].
     *
     * @return a token representing the changes. Call `cancel()` on this token, to stop listening to any further changes.
     */
    fun addChangeListener(callback: Callback<RealmResults<T>>): Cancellable {
        checkClosed()
        return owner.ref.addResultsChangeListener(this, callback)
    }

    /**
     * FIXME
     * Observe changes to the Realm results.
     *
     * @return a flow
     */
    fun observe(): Flow<RealmResults<T>> {
        checkClosed()
        return owner.ref.observeResults(this)
    }

    fun delete() {
        // TODO OPTIMIZE Are there more efficient ways to do this? realm_query_delete_all is not
        //  available in C-API yet, but should probably await final query design
        //  https://github.com/realm/realm-kotlin/issues/84
        RealmInterop.realm_results_delete_all(resultsNativePointer)
    }

    /**
     * Returns a frozen copy of this query result. If it is already frozen, the same instance
     * is returned.
     */
    internal fun freeze(realm: RealmId): RealmResults<T> {
        val frozenDbPointer = realm.dbPointer
        val frozenResults = RealmInterop.realm_results_freeze(resultsNativePointer, frozenDbPointer)
        return fromResults(realm, frozenResults, clazz, schema)
    }

    /**
     * Thaw the frozen query result, turning it back into a live, thread-confined RealmResults.
     */
    internal fun thaw(realm: RealmId): RealmResults<T> {
        val liveDbPointer = realm.dbPointer
        if (RealmInterop.realm_is_frozen(liveDbPointer)) {
            throw IllegalStateException("Should be live")
        }
        val liveResultPtr = RealmInterop.realm_results_thaw(resultsNativePointer, liveDbPointer)
        return fromResults(realm, liveResultPtr, clazz, schema)
    }

    private inline fun checkClosed() {
        // Empty RealmResults can neve be closed as they are not connected to any native file resources.
        if (mode != Mode.EMPTY) {
            owner.ref.checkClosed()
        }
    }
}
